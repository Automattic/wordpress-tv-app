import Foundation

/// `ContentRepository` backed by the public WP.com REST API (v1.1). No auth,
/// no JWT — that's all wordpress.tv needs. Implements `listLatest` and
/// `resolvePlayback`; the category/search methods throw `.notImplemented`.
public final class WPComContentRepository: ContentRepository {
    private static let apiBase = URL(string: "https://public-api.wordpress.com/rest/v1.1")!

    private let session: URLSession
    private let decoder = JSONDecoder()
    /// Page size for `listLatest` (the `number` query param).
    private let pageSize: Int

    public init(session: URLSession = .shared, pageSize: Int = 24) {
        self.session = session
        self.pageSize = pageSize
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

        let dto: PostsResponseDTO = try await get(url)
        return Mapping.videos(from: dto, sourceID: source.id)
    }

    public func resolvePlayback(source: ContentSource, video: Video) async throws -> PlaybackAsset {
        let url = Self.apiBase.appending(path: "videos/\(video.videoGuid)")
        let dto: VideoInfoDTO = try await get(url)
        guard let asset = Mapping.playbackAsset(from: dto, fallbackTitle: video.title) else {
            throw RepositoryError.notPlayable
        }
        return asset
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

    // MARK: Transport

    private func get<T: Decodable>(_ url: URL) async throws -> T {
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse else {
            throw RepositoryError.invalidResponse
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
