import Foundation

/// The seam between the data layer (Core) and the UI (app target).
///
/// It declares the **full** content contract; the scaffold implements two
/// methods (`listLatest`, `resolvePlayback`) and stubs the rest with
/// `RepositoryError.notImplemented` until their slices land. The app target
/// codes against this protocol, never against a concrete implementation.
public protocol ContentRepository: Sendable {
    /// Newest videos first. `page` is 1-based.
    func listLatest(source: ContentSource, page: Int) async throws -> [Video]

    /// Resolve a ready-to-play asset (absolute URL + metadata) for `video`.
    func resolvePlayback(source: ContentSource, video: Video) async throws -> PlaybackAsset

    /// A ready-to-load poster URL for `video`. For a private source this appends
    /// the VideoPress `metadata_token` (the poster host gates on it just like
    /// playback); for a public source it's simply `video.posterUrl`. Best-effort:
    /// returns the bare URL if a token can't be minted.
    func posterURL(source: ContentSource, video: Video) async -> URL?

    // MARK: Declared, not yet implemented (later slices)

    func listCategories(source: ContentSource) async throws -> [CategoryRef]
    func listByCategory(source: ContentSource, category: CategoryRef, page: Int) async throws -> [Video]
    func search(source: ContentSource, query: String, page: Int) async throws -> [Video]
}

/// Errors surfaced across the repository seam.
public enum RepositoryError: Error, Equatable {
    /// A contract method this slice hasn't built yet.
    case notImplemented
    /// The source returned data, but nothing playable could be resolved.
    case notPlayable
    /// The token was missing, rejected, or expired (HTTP 401/403). The UI
    /// should clear it and re-pair.
    case unauthorized
    /// Could not build a valid request or asset URL.
    case invalidURL
    /// The transport returned something other than an HTTP response.
    case invalidResponse
    /// Non-2xx HTTP status.
    case http(status: Int)
    /// Transport succeeded but the payload wasn't the shape we expected.
    case decodingFailed
}
