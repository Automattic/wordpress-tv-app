import SwiftUI
import Observation

/// What a feed screen is showing. All three resolve to the same posts endpoint
/// via `ContentRepository`, differing only in filter.
enum VideoQuery: Equatable {
    case latest
    case category(CategoryRef)
    case event(ContentEvent, applyLanguageFilter: Bool)
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
        case .event(let event, let applyLanguageFilter):
            return try await repository.listByEvent(
                source: source,
                event: event,
                page: 1,
                applyLanguageFilter: applyLanguageFilter
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

/// The WordCamps tab: event-taxonomy rails loaded page by page as the viewer
/// scrolls down.
struct WordCampsView: View {
    let repository: ContentRepository
    let source: ContentSource
    let onPlay: (Video, ContentSource) -> Void

    @State private var rails: [WordCampRail] = []
    @State private var nextEventPage = 1
    @State private var isLoadingEventPage = false
    @State private var canLoadMoreEvents = true
    @State private var eventPageFailed = false

    init(
        repository: ContentRepository,
        source: ContentSource,
        onPlay: @escaping (Video, ContentSource) -> Void
    ) {
        self.repository = repository
        self.source = source
        self.onPlay = onPlay
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 56) {
                ForEach(rails) { rail in
                    RailSection(title: rail.event.name) {
                        VideoRail(
                            videos: rail.videos,
                            source: source,
                            resolvePoster: { video in await repository.posterURL(source: source, video: video) },
                            onPlay: onPlay
                        )
                    }
                }

                eventPaginationFooter
            }
            .padding(.vertical, 40)
        }
    }

    @ViewBuilder
    private var eventPaginationFooter: some View {
        if isLoadingEventPage {
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, minHeight: 300)
        } else if eventPageFailed {
            Placeholder(
                message: "Couldn’t load more WordCamps. Please try again.",
                action: ("Retry", { Task { await loadNextEventPageIfNeeded() } })
            )
            .frame(minHeight: 300)
            .padding(.horizontal, 80)
        } else if canLoadMoreEvents {
            Color.clear
                .frame(maxWidth: .infinity, minHeight: 300)
                .onAppear {
                    Task { await loadNextEventPageIfNeeded() }
                }
        }
    }

    private func loadNextEventPageIfNeeded() async {
        guard !isLoadingEventPage && canLoadMoreEvents else { return }
        isLoadingEventPage = true
        eventPageFailed = false
        var pageNumber = nextEventPage
        var loadedRails = rails
        let startingRailCount = loadedRails.count
        var knownIDs = Set(loadedRails.map { $0.event.id })
        do {
            while loadedRails.count - startingRailCount < minWordCampRailsPerBatch {
                let page = try await repository.listWordCampEvents(source: source, page: pageNumber)
                if page.isEmpty {
                    canLoadMoreEvents = false
                    break
                }
                for event in page where !knownIDs.contains(event.id) {
                    knownIDs.insert(event.id)
                    let videos = try await repository.listByEvent(
                        source: source,
                        event: event,
                        page: 1,
                        applyLanguageFilter: true
                    )
                    if !videos.isEmpty {
                        loadedRails.append(WordCampRail(event: event, videos: videos))
                        rails = loadedRails
                    }
                }
                pageNumber += 1
            }
            rails = loadedRails
            nextEventPage = pageNumber
        } catch {
            rails = loadedRails
            nextEventPage = pageNumber
            eventPageFailed = true
        }
        isLoadingEventPage = false
    }
}

private let minWordCampRailsPerBatch = 3

private struct WordCampRail: Identifiable {
    var id: Int64 { event.id }
    let event: ContentEvent
    let videos: [Video]
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
