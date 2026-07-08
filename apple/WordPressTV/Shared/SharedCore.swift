import Foundation
import WordPressTVSharedCore

private typealias SharedAccount = WordPressTVSharedCore.Account
private typealias SharedCategoryRef = WordPressTVSharedCore.CategoryRef
private typealias SharedContentLanguage = WordPressTVSharedCore.ContentLanguage
private typealias SharedContentSource = WordPressTVSharedCore.ContentSource
private typealias SharedPlaybackAsset = WordPressTVSharedCore.PlaybackAsset
private typealias SharedRepository = WordPressTVSharedCore.WpComContentRepository
private typealias SharedVideo = WordPressTVSharedCore.Video

struct Account: Equatable, Sendable, Codable {
    let displayName: String
    let avatarURL: URL?
}

struct CategoryRef: Equatable, Sendable {
    let id: String
    let name: String
    let slug: String
}

struct ContentLanguage: Identifiable, Equatable, Sendable {
    let id: Int64
    let name: String
    let slug: String
}

struct ContentSource: Identifiable, Equatable, Sendable {
    enum Auth: Equatable, Sendable {
        case none
        case wpcomOAuth
    }

    let id: String
    let displayName: String
    let wpcomSite: String
    let blogID: Int
    let auth: Auth
    let needsPlaybackToken: Bool
}

struct PlaybackAsset: Identifiable, Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case hls
        case dash
        case mp4
    }

    var id: URL { url }
    let url: URL
    let kind: Kind
    let title: String
    let durationSeconds: Int?
}

struct Video: Identifiable, Equatable, Sendable {
    let id: String
    let videoGuid: String
    let title: String
    let description: String
    let posterUrl: URL?
    let durationSeconds: Int?
    let sourceID: String
    let playbackToken: String?
}

protocol AuthTokenProviding: Sendable {
    func accessToken(for source: ContentSource) async -> String?
}

protocol ContentRepository: Sendable {
    func listLatest(source: ContentSource, page: Int) async throws -> [Video]
    func resolvePlayback(source: ContentSource, video: Video) async throws -> PlaybackAsset
    func posterURL(source: ContentSource, video: Video) async -> URL?
    func listCategories(source: ContentSource) async throws -> [CategoryRef]
    func listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        applyLanguageFilter: Bool
    ) async throws -> [Video]
    func search(source: ContentSource, query: String, page: Int) async throws -> [Video]
    func listLanguages(source: ContentSource) async throws -> [ContentLanguage]
    func setContentLanguageTermIds(_ ids: [Int64])
}

extension ContentRepository {
    func listByCategory(source: ContentSource, category: CategoryRef, page: Int) async throws -> [Video] {
        try await listByCategory(source: source, category: category, page: page, applyLanguageFilter: true)
    }

    func listLanguages(source: ContentSource) async throws -> [ContentLanguage] { [] }

    func setContentLanguageTermIds(_ ids: [Int64]) {}
}

enum RepositoryError: Error, Equatable {
    case notImplemented
    case notPlayable
    case unauthorized
    case invalidURL
    case invalidResponse
    case http(status: Int)
    case decodingFailed
}

enum Sources {
    static let wordpressTV = ContentSource(
        id: "wordpresstv",
        displayName: "WordPress.tv",
        wpcomSite: "wordpress.tv",
        blogID: 5_089_392,
        auth: .none,
        needsPlaybackToken: false
    )

    static let a8cTV = ContentSource(
        id: "a8ctv",
        displayName: "a8c.tv",
        wpcomSite: "a8ctv.wordpress.com",
        blogID: 14_140_874,
        auth: .wpcomOAuth,
        needsPlaybackToken: true
    )

    static let all: [ContentSource] = [wordpressTV, a8cTV]
}

final class WPComContentRepository: ContentRepository, @unchecked Sendable {
    private var core: SharedRepository
    private let lock = NSLock()
    private let pageSize: Int
    private let authProvider: AuthTokenProviding?

    init(pageSize: Int = 24, authProvider: AuthTokenProviding? = nil, contentLanguageTermIds: [Int64] = []) {
        self.pageSize = pageSize
        self.core = Self.makeCore(pageSize: pageSize, contentLanguageTermIds: contentLanguageTermIds)
        self.authProvider = authProvider
    }

    func listLatest(source: ContentSource, page: Int) async throws -> [Video] {
        let core = currentCore()
        let accessToken = await token(for: source)
        let videos = try await mapErrors {
            try await core.listLatest(
                source: source.shared,
                page: Int32(page),
                accessToken: accessToken
            )
        }
        return videos.map(Video.init(shared:))
    }

    func resolvePlayback(source: ContentSource, video: Video) async throws -> PlaybackAsset {
        let core = currentCore()
        let accessToken = await token(for: source)
        let asset = try await mapErrors {
            try await core.resolvePlayback(
                source: source.shared,
                video: video.shared,
                accessToken: accessToken
            )
        }
        return try PlaybackAsset(shared: asset)
    }

    func posterURL(source: ContentSource, video: Video) async -> URL? {
        let core = currentCore()
        let accessToken = await token(for: source)
        let value = try? await core.posterUrl(
            source: source.shared,
            video: video.shared,
            accessToken: accessToken
        )
        return value.flatMap(URL.init(string:))
    }

    func listCategories(source: ContentSource) async throws -> [CategoryRef] {
        throw RepositoryError.notImplemented
    }

    func listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        applyLanguageFilter: Bool
    ) async throws -> [Video] {
        let core = currentCore()
        let accessToken = await token(for: source)
        let videos = try await mapErrors {
            try await core.listByCategory(
                source: source.shared,
                category: category.shared,
                page: Int32(page),
                applyLanguageFilter: applyLanguageFilter,
                accessToken: accessToken
            )
        }
        return videos.map(Video.init(shared:))
    }

    func search(source: ContentSource, query: String, page: Int) async throws -> [Video] {
        let core = currentCore()
        let accessToken = await token(for: source)
        let videos = try await mapErrors {
            try await core.search(
                source: source.shared,
                query: query,
                page: Int32(page),
                accessToken: accessToken
            )
        }
        return videos.map(Video.init(shared:))
    }

    func listLanguages(source: ContentSource) async throws -> [ContentLanguage] {
        let core = currentCore()
        let accessToken = await token(for: source)
        let languages = try await mapErrors {
            try await core.listLanguages(source: source.shared, accessToken: accessToken)
        }
        return languages.map(ContentLanguage.init(shared:))
    }

    func setContentLanguageTermIds(_ ids: [Int64]) {
        lock.withLock {
            core = Self.makeCore(pageSize: pageSize, contentLanguageTermIds: ids)
        }
    }

    private func currentCore() -> SharedRepository {
        lock.withLock { core }
    }

    private static func makeCore(pageSize: Int, contentLanguageTermIds: [Int64]) -> SharedRepository {
        SharedRepository(
            pageSize: Int32(pageSize),
            authProvider: nil,
            contentLanguageTermIds: contentLanguageTermIds.map { KotlinLong(longLong: $0) }
        )
    }

    private func token(for source: ContentSource) async -> String? {
        guard source.auth == .wpcomOAuth else { return nil }
        return await authProvider?.accessToken(for: source)
    }

    private func mapErrors<T>(_ operation: () async throws -> T) async throws -> T {
        do {
            return try await operation()
        } catch {
            throw Self.repositoryError(from: error) ?? error
        }
    }

    private static func repositoryError(from error: Error) -> RepositoryError? {
        let exception = (error as NSError).kotlinException
        switch exception {
        case is WordPressTVSharedCore.RepositoryException.NotImplemented:
            return .notImplemented
        case is WordPressTVSharedCore.RepositoryException.NotPlayable:
            return .notPlayable
        case is WordPressTVSharedCore.RepositoryException.Unauthorized:
            return .unauthorized
        case is WordPressTVSharedCore.RepositoryException.InvalidUrl:
            return .invalidURL
        case is WordPressTVSharedCore.RepositoryException.InvalidResponse:
            return .invalidResponse
        case is WordPressTVSharedCore.RepositoryException.DecodingFailed:
            return .decodingFailed
        case let http as WordPressTVSharedCore.RepositoryException.Http:
            return .http(status: Int(http.status))
        default:
            return nil
        }
    }
}

private extension NSLock {
    func withLock<T>(_ operation: () throws -> T) rethrows -> T {
        lock()
        defer { unlock() }
        return try operation()
    }
}

private extension Account {
    init(shared: SharedAccount) {
        self.init(
            displayName: shared.displayName,
            avatarURL: shared.avatarUrl.flatMap(URL.init(string:))
        )
    }

    var shared: SharedAccount {
        SharedAccount(displayName: displayName, avatarUrl: avatarURL?.absoluteString)
    }
}

private extension CategoryRef {
    init(shared: SharedCategoryRef) {
        self.init(id: shared.id, name: shared.name, slug: shared.slug)
    }

    var shared: SharedCategoryRef {
        SharedCategoryRef(id: id, name: name, slug: slug)
    }
}

private extension ContentLanguage {
    init(shared: SharedContentLanguage) {
        self.init(id: shared.id, name: shared.name, slug: shared.slug)
    }
}

private extension ContentSource {
    init(shared: SharedContentSource) {
        self.init(
            id: shared.id,
            displayName: shared.displayName,
            wpcomSite: shared.wpcomSite,
            blogID: Int(shared.blogId),
            auth: Auth(shared: shared.auth),
            needsPlaybackToken: shared.needsPlaybackToken
        )
    }

    var shared: SharedContentSource {
        SharedContentSource(
            id: id,
            displayName: displayName,
            wpcomSite: wpcomSite,
            blogId: Int64(blogID),
            auth: auth.shared,
            needsPlaybackToken: needsPlaybackToken
        )
    }
}

private extension ContentSource.Auth {
    init(shared: SharedContentSource.Auth) {
        self = shared == .wpcomOauth ? .wpcomOAuth : .none
    }

    var shared: SharedContentSource.Auth {
        switch self {
        case .none: .none
        case .wpcomOAuth: .wpcomOauth
        }
    }
}

private extension PlaybackAsset {
    init(shared: SharedPlaybackAsset) throws {
        guard let url = URL(string: shared.url) else {
            throw RepositoryError.invalidURL
        }
        self.init(
            url: url,
            kind: Kind(shared: shared.kind),
            title: shared.title,
            durationSeconds: shared.durationSeconds.map { Int($0.intValue) }
        )
    }
}

private extension PlaybackAsset.Kind {
    init(shared: SharedPlaybackAsset.Kind) {
        switch shared {
        case .hls: self = .hls
        case .dash: self = .dash
        default: self = .mp4
        }
    }

    var shared: SharedPlaybackAsset.Kind {
        switch self {
        case .hls: .hls
        case .dash: .dash
        case .mp4: .mp4
        }
    }
}

private extension Video {
    init(shared: SharedVideo) {
        self.init(
            id: shared.id,
            videoGuid: shared.videoGuid,
            title: shared.title,
            description: shared.description_,
            posterUrl: shared.posterUrl.flatMap(URL.init(string:)),
            durationSeconds: shared.durationSeconds.map { Int($0.intValue) },
            sourceID: shared.sourceId,
            playbackToken: shared.playbackToken
        )
    }

    var shared: SharedVideo {
        SharedVideo(
            id: id,
            videoGuid: videoGuid,
            title: title,
            description: description,
            posterUrl: posterUrl?.absoluteString,
            durationSeconds: durationSeconds.map { KotlinInt(int: Int32($0)) },
            sourceId: sourceID,
            playbackToken: playbackToken
        )
    }
}
