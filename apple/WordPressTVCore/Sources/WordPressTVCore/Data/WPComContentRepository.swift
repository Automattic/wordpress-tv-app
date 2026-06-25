import Foundation

/// `ContentRepository` backed by the WP.com REST API (v1.1).
///
/// For a public source (wordpress.tv, `auth == .none`) it sends no token, just
/// like before. For a private source (a8c.tv, `auth == .wpcomOAuth`) it asks the
/// injected `AuthTokenProviding` for the user's token and sets `Authorization:
/// Bearer`. When the source `needsPlaybackToken`, the poster and stream URLs get
/// a VideoPress `metadata_token` appended — reusing the per-video token WP.com
/// mints into the post's private embed (`Video.playbackToken`) rather than
/// minting one (that needs the broad `global` scope the narrow token lacks). A
/// 401/403 surfaces as `RepositoryError.unauthorized` so the UI can re-pair.
public final class WPComContentRepository: ContentRepository {
    private static let apiBase = URL(string: "https://public-api.wordpress.com/rest/v1.1")!

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
        // Private VideoPress (a8c.tv) plays the progressive `original` MP4 with the
        // VideoPress metadata token appended:  "<original>?metadata_token=<token>".
        // Reuse the token WP.com minted into the post's embed (carried on the
        // Video) — minting our own needs the broad `global` OAuth scope. A post
        // without a token is a public video; play it bare rather than treating
        // the absence as an auth failure (which would force a needless re-pair).
        guard let playbackToken = video.playbackToken else { return asset }
        return asset.appendingQuery(name: "metadata_token", value: playbackToken)
    }

    public func posterURL(source: ContentSource, video: Video) async -> URL? {
        // Public sources (wordpress.tv): the posts list already carried a poster
        // from the video's attachment thumbnails — serve it openly.
        guard source.needsPlaybackToken else { return video.posterUrl }
        // Private VideoPress (a8c.tv): posts carry no attachment, so resolve the
        // real poster from the video-info endpoint (the user's `videos` scope
        // authorizes it). Append the embed's metadata token when the post had one
        // — a private poster on videos.files.wordpress.com is 403 without it; a
        // public video's poster serves either way. Best-effort: nil (placeholder)
        // on any failure.
        guard let token = await token(for: source) else { return video.posterUrl }
        let infoURL = Self.apiBase.appending(path: "videos/\(video.videoGuid)")
        do {
            let info: VideoInfoDTO = try await get(infoURL, token: token)
            guard let posterString = info.poster, let poster = URL(string: posterString) else {
                return nil
            }
            guard let playbackToken = video.playbackToken else { return poster }
            return poster.appendingQueryItem(name: "metadata_token", value: playbackToken)
        } catch {
            return nil
        }
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
