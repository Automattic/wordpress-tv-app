import SwiftUI
import Observation

/// What a feed screen is showing. All three resolve to the same posts endpoint
/// via `ContentRepository`, differing only in filter.
enum VideoQuery: Equatable {
    case latest
    case category(CategoryRef)
    case collection(CategoryRef)
    case search(String)
}

/// Loads one page of a `VideoQuery` for a source and exposes UI state. The
/// generalization of the old `LatestViewModel` — Home, category grids, and
/// search all drive it.
@MainActor
@Observable
final class VideoFeedViewModel {
    enum State: Equatable {
        case loading
        case loaded([Video])
        case empty
        case failed(String)
        /// The source needs a (valid) token — route to pairing.
        case needsAuth
    }

    private(set) var state: State = .loading

    let source: ContentSource
    private let repository: ContentRepository
    private let query: VideoQuery

    init(repository: ContentRepository, source: ContentSource, query: VideoQuery) {
        self.repository = repository
        self.source = source
        self.query = query
    }

    var videos: [Video] {
        if case .loaded(let videos) = state { return videos }
        return []
    }

    func load() async {
        state = .loading
        do {
            let videos = try await fetch()
            state = videos.isEmpty ? .empty : .loaded(videos)
        } catch RepositoryError.unauthorized {
            state = .needsAuth
        } catch {
            state = .failed("Couldn’t load videos. Please try again.")
        }
    }

    private func fetch() async throws -> [Video] {
        switch query {
        case .latest:
            return try await repository.listLatest(source: source, page: 1)
        case .category(let ref):
            return try await repository.listByCategory(
                source: source,
                category: ref,
                page: 1,
                applyLanguageFilter: true
            )
        case .collection(let ref):
            return try await repository.listByCategory(
                source: source,
                category: ref,
                page: 1,
                applyLanguageFilter: false
            )
        case .search(let term):
            return try await repository.search(source: source, query: term, page: 1)
        }
    }

    func posterURL(for video: Video) async -> URL? {
        await repository.posterURL(source: source, video: video)
    }
}

/// A focusable grid of videos for a single query — the browse destination behind
/// each category tab, and the search results list.
struct VideoGrid: View {
    @State private var model: VideoFeedViewModel
    private let onPlay: (Video, ContentSource) -> Void
    private let onAuthRequired: () -> Void

    init(
        repository: ContentRepository,
        source: ContentSource,
        query: VideoQuery,
        onPlay: @escaping (Video, ContentSource) -> Void,
        onAuthRequired: @escaping () -> Void = {}
    ) {
        _model = State(initialValue: VideoFeedViewModel(repository: repository, source: source, query: query))
        self.onPlay = onPlay
        self.onAuthRequired = onAuthRequired
    }

    var body: some View {
        content.task { await model.load() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .failed(let message):
            Placeholder(message: message, action: ("Retry", { Task { await model.load() } }))

        case .empty:
            Placeholder(message: "No videos here yet.")

        case .needsAuth:
            Placeholder(
                message: "Your session expired. Sign in again to continue.",
                action: ("Sign in", onAuthRequired)
            )

        case .loaded(let videos):
            grid(videos)
        }
    }

    private func grid(_ videos: [Video]) -> some View {
        ScrollView {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 360), spacing: 60)],
                spacing: 60
            ) {
                ForEach(videos) { video in
                    VideoCard(video: video, resolvePoster: model.posterURL) {
                        onPlay(video, model.source)
                    }
                }
            }
            .padding(.horizontal, 80)
            .padding(.vertical, 40)
        }
    }
}

/// A horizontal shelf of video cards (the "rails" on Home).
struct VideoRail: View {
    let videos: [Video]
    let source: ContentSource
    let resolvePoster: (Video) async -> URL?
    let onPlay: (Video, ContentSource) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 40) {
                ForEach(videos) { video in
                    VideoCard(video: video, resolvePoster: resolvePoster) {
                        onPlay(video, source)
                    }
                }
            }
            .padding(.horizontal, 80)
            // Breathing room so the focus lift isn't clipped by the scroll view.
            .padding(.vertical, 20)
        }
    }
}

/// Centered message + optional action, for the empty/error/needs-auth states.
struct Placeholder: View {
    let message: String
    var action: (title: String, run: () -> Void)? = nil

    var body: some View {
        VStack(spacing: 28) {
            Text(message)
                .font(.title3)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if let action {
                Button(action.title, action: action.run)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
