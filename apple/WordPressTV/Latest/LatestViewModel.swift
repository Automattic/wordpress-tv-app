import Foundation
import Observation
import WordPressTVCore

/// Drives the Latest screen: loads page 1 from the repository and resolves a
/// `PlaybackAsset` when a video is tapped. UI-state only — all data work is
/// delegated to `ContentRepository`.
@MainActor
@Observable
final class LatestViewModel {
    enum State {
        case loading
        case loaded([Video])
        case empty
        case failed(String)
        /// The source needs a (valid) token — the UI should route to pairing.
        case needsAuth
    }

    private(set) var state: State = .loading

    private let repository: ContentRepository
    private let source: ContentSource

    init(repository: ContentRepository, source: ContentSource) {
        self.repository = repository
        self.source = source
    }

    func load() async {
        state = .loading
        do {
            // Page 1 only — paging is in the contract but not exercised yet.
            let videos = try await repository.listLatest(source: source, page: 1)
            state = videos.isEmpty ? .empty : .loaded(videos)
        } catch RepositoryError.unauthorized {
            state = .needsAuth
        } catch {
            state = .failed("Couldn’t load videos. Please try again.")
        }
    }

    func playbackAsset(for video: Video) async throws -> PlaybackAsset {
        try await repository.resolvePlayback(source: source, video: video)
    }

    /// Ready-to-load poster URL (token-stamped for private sources).
    func posterURL(for video: Video) async -> URL? {
        await repository.posterURL(source: source, video: video)
    }
}
