import Foundation

/// `ContentRepository` backed by the WP.com REST API (v1.1).
///
/// For a public source (wordpress.tv, `auth == .none`) it sends no token, just
/// like before. For a private source (a8c.tv, `auth == .wpcomOAuth`) it asks the
/// injected `AuthTokenProviding` for the user's token, sets `Authorization:
/// Bearer`, and — when the source `needsPlaybackToken` — mints a VideoPress
/// playback JWT and appends it to the stream URL. A 401/403 surfaces as
/// `RepositoryError.unauthorized` so the UI can clear the token and re-pair.
public final class WPComContentRepository: ContentRepository {
    private static let apiBase = URL(string: "https://public-api.wordpress.com/rest/v1.1")!
    private static let wpcomV2Base = URL(string: "https://public-api.wordpress.com/wpcom/v2")!

    private let session: URLSession
    private let decoder = JSONDecoder()
    /// Page size for `listLatest` (the `number` query param).
    private let pageSize: Int
    /// Supplies the Bearer token for authenticated sources; `nil` for a
    /// public-only build.
    private let authProvider: AuthTokenProviding?

    public init(
        session: URLSession = .shared,
        pageSize: Int = 24,
        authProvider: AuthTokenProviding? = nil
    ) {
        self.session = session
        self.pageSize = pageSize
        self.authProvider = authProvider
    }

    // MARK: Implemented

    public func listLatest(source: ContentSource, page: Int) async throws -> [Video] {
        guard var components = URLComponents(
            url: Self.apiBase.appending(path: "sites/\(source.wpcomSite)/posts"),
            resolvingAgainstBaseURL: false
        ) else { throw RepositoryError.invalidURL }

        components.queryItems = [
            URLQueryItem(name: "number", value: String(pageSize)),
            URLQueryItem(name: "page", value: String(max(1, page))),
            URLQueryItem(name: "order_by", value: "date"),
        ]
        guard let url = components.url else { throw RepositoryError.invalidURL }

        let dto: PostsResponseDTO = try await get(url, token: await token(for: source))
        return Mapping.videos(from: dto, sourceID: source.id)
    }

    public func resolvePlayback(source: ContentSource, video: Video) async throws -> PlaybackAsset {
        let token = await token(for: source)
        let url = Self.apiBase.appending(path: "videos/\(video.videoGuid)")
        let dto: VideoInfoDTO = try await get(url, token: token)
        // Private VideoPress (a8c.tv) plays the progressive `original` MP4 with a
        // metadata token appended; public videos (wordpress.tv) keep HLS.
        guard let asset = Mapping.playbackAsset(
            from: dto,
            fallbackTitle: video.title,
            preferProgressive: source.needsPlaybackToken
        ) else {
            throw RepositoryError.notPlayable
        }

        guard source.needsPlaybackToken else { return asset }
        guard let token else { throw RepositoryError.unauthorized }
        // Mint the VideoPress playback JWT and append it to the MP4 URL, exactly
        // as the proven a8c.tv playback path does:
        //   "<original>?metadata_token=<token>"
        let metadataToken = try await playbackToken(site: source.wpcomSite, guid: video.videoGuid, token: token)
        return asset.appendingQuery(name: "metadata_token", value: metadataToken)
    }

    public func posterURL(source: ContentSource, video: Video) async -> URL? {
        guard let poster = video.posterUrl else { return nil }
        // Public sources serve posters openly.
        guard source.needsPlaybackToken, let token = await token(for: source) else { return poster }
        // Private VideoPress posters live on videos.files.wordpress.com and need
        // the same per-video metadata token as playback. Best-effort: fall back
        // to the bare URL (the cell just shows its placeholder) if minting fails.
        guard let metadataToken = try? await playbackToken(site: source.wpcomSite, guid: video.videoGuid, token: token) else {
            return poster
        }
        return poster.appendingQueryItem(name: "metadata_token", value: metadataToken)
    }

    // MARK: Stubbed (later slices)

    public func listCategories(source: ContentSource) async throws -> [CategoryRef] {
        throw RepositoryError.notImplemented
    }

    public func listByCategory(source: ContentSource, category: CategoryRef, page: Int) async throws -> [Video] {
        throw RepositoryError.notImplemented
    }

    public func search(source: ContentSource, query: String, page: Int) async throws -> [Video] {
        throw RepositoryError.notImplemented
    }

    // MARK: Auth helpers

    private func token(for source: ContentSource) async -> String? {
        guard source.auth != .none else { return nil }
        return await authProvider?.accessToken(for: source)
    }

    /// Mint a VideoPress playback JWT for a private video. Returns the
    /// `metadata_token` to append to the stream URL.
    private func playbackToken(site: String, guid: String, token: String) async throws -> String {
        let url = Self.wpcomV2Base.appending(path: "sites/\(site)/media/videopress-playback-jwt/\(guid)")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let dto: PlaybackJWTDTO = try await send(request)
        return dto.token
    }

    // MARK: Transport

    private func get<T: Decodable>(_ url: URL, token: String?) async throws -> T {
        var request = URLRequest(url: url)
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return try await send(request)
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw RepositoryError.invalidResponse
        }
        if http.statusCode == 401 || http.statusCode == 403 {
            throw RepositoryError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            throw RepositoryError.http(status: http.statusCode)
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw RepositoryError.decodingFailed
        }
    }
}

/// The VideoPress playback-JWT response. WP.com has shipped this token under a
/// couple of key names over time, so decode tolerantly.
private struct PlaybackJWTDTO: Decodable {
    let token: String

    private enum CodingKeys: String, CodingKey {
        case metadataToken = "metadata_token"
        case jwtToken = "jwt_token"
        case token
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let v = try c.decodeIfPresent(String.self, forKey: .metadataToken) {
            token = v
        } else if let v = try c.decodeIfPresent(String.self, forKey: .jwtToken) {
            token = v
        } else if let v = try c.decodeIfPresent(String.self, forKey: .token) {
            token = v
        } else {
            throw RepositoryError.decodingFailed
        }
    }
}

private extension PlaybackAsset {
    /// Return a copy with an extra query item on the stream URL (used to append
    /// the VideoPress `metadata_token`).
    func appendingQuery(name: String, value: String) -> PlaybackAsset {
        PlaybackAsset(
            url: url.appendingQueryItem(name: name, value: value),
            kind: kind,
            title: title,
            durationSeconds: durationSeconds
        )
    }
}

extension URL {
    /// Append a single query item, preserving any existing ones.
    func appendingQueryItem(name: String, value: String) -> URL {
        guard var components = URLComponents(url: self, resolvingAgainstBaseURL: false) else { return self }
        components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: name, value: value)]
        return components.url ?? self
    }
}
