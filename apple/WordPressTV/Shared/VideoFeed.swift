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
    let onAuthRequired: () -> Void
    let onChangeLanguage: () -> Void
    let isLanguageFiltered: Bool

    @State private var events: [ContentEvent] = []
    @State private var nextEventPage = 1
    @State private var isLoadingEventPage = false
    @State private var canLoadMoreEvents = true
    @State private var eventPageFailed = false
    @State private var didStart = false

    init(
        repository: ContentRepository,
        source: ContentSource,
        onPlay: @escaping (Video, ContentSource) -> Void,
        onAuthRequired: @escaping () -> Void = {},
        onChangeLanguage: @escaping () -> Void = {},
        isLanguageFiltered: Bool = false
    ) {
        self.repository = repository
        self.source = source
        self.onPlay = onPlay
        self.onAuthRequired = onAuthRequired
        self.onChangeLanguage = onChangeLanguage
        self.isLanguageFiltered = isLanguageFiltered
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if isLanguageFiltered {
                LanguageFilterNotice(onChangeLanguage: onChangeLanguage)
                    .padding(.top, 20)
                    .padding(.bottom, 8)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 56) {
                    ForEach(Array(events.enumerated()), id: \.element.id) { index, event in
                        QueryVideoRail(
                            title: event.name,
                            model: VideoFeedViewModel(repository: repository, source: source, query: .event(event, applyLanguageFilter: true)),
                            source: source,
                            onPlay: onPlay,
                            onAuthRequired: onAuthRequired,
                            hideWhenEmpty: true
                        )
                        .task {
                            if index == events.count - 1 {
                                await loadNextEventPageIfNeeded()
                            }
                        }
                    }

                    eventPaginationFooter
                }
                .padding(.vertical, 40)
            }
        }
        .task {
            guard !didStart else { return }
            didStart = true
            await loadNextEventPageIfNeeded()
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
        }
    }

    private func loadNextEventPageIfNeeded() async {
        guard !isLoadingEventPage && canLoadMoreEvents else { return }
        isLoadingEventPage = true
        eventPageFailed = false
        do {
            let page = try await repository.listWordCampEvents(source: source, page: nextEventPage)
            let knownIDs = Set(events.map(\.id))
            let newEvents = page.filter { !knownIDs.contains($0.id) }
            events += newEvents
            nextEventPage += 1
            canLoadMoreEvents = !page.isEmpty
        } catch {
            eventPageFailed = true
        }
        isLoadingEventPage = false
    }
}

private struct QueryVideoRail: View {
    let title: String
    @State private var model: VideoFeedViewModel
    let source: ContentSource
    let onPlay: (Video, ContentSource) -> Void
    let onAuthRequired: () -> Void
    let hideWhenEmpty: Bool

    init(
        title: String,
        model: VideoFeedViewModel,
        source: ContentSource,
        onPlay: @escaping (Video, ContentSource) -> Void,
        onAuthRequired: @escaping () -> Void,
        hideWhenEmpty: Bool = false
    ) {
        self.title = title
        _model = State(initialValue: model)
        self.source = source
        self.onPlay = onPlay
        self.onAuthRequired = onAuthRequired
        self.hideWhenEmpty = hideWhenEmpty
    }

    var body: some View {
        Group {
            if hideWhenEmpty, case .empty = model.state {
                EmptyView()
            } else {
                RailSection(title: title) {
                    content
                }
            }
        }
        .task { await model.load() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, minHeight: 300)
        case .needsAuth:
            Placeholder(message: "Sign in again to continue.", action: ("Sign in", onAuthRequired))
                .frame(minHeight: 300)
        case .failed(let message):
            Placeholder(message: message, action: ("Retry", { Task { await model.load() } }))
                .frame(minHeight: 300)
        case .empty:
            Placeholder(message: "No videos here yet.").frame(minHeight: 300)
        case .loaded(let videos):
            VideoRail(
                videos: videos,
                source: source,
                resolvePoster: model.posterURL,
                onPlay: onPlay
            )
        }
    }
}

private struct LanguageFilterNotice: View {
    let onChangeLanguage: () -> Void

    var body: some View {
        HStack(spacing: 24) {
            Text("Some WordCamps are hidden by your language setting.")
                .font(.callout.weight(.medium))
                .foregroundStyle(.white.opacity(0.78))

            Button("Change Language", action: onChangeLanguage)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.08)))
        .padding(.horizontal, 80)
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
