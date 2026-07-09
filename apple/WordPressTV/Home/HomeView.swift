import SwiftUI
import Observation

/// The railed landing screen from the design: stacked horizontal shelves —
/// Continue Watching (when the viewer has any), recent WordCamp events, and
/// Latest. Everything is live WordPress.tv content.
struct HomeView: View {
    let repository: ContentRepository
    let source: ContentSource
    let store: WatchProgressStore
    /// Resolve + present the player for a tapped video.
    let onPlay: (Video, ContentSource) -> Void
    /// Open a WordCamp event's full video grid.
    let onOpenEvent: (ContentEvent) -> Void
    /// Resolve an event card's cover image (its newest video poster).
    let resolveCover: (ContentEvent) async -> URL?
    let onAuthRequired: () -> Void

    @State private var model: VideoFeedViewModel
    @State private var wordCampState: WordCampState = .loading

    private enum WordCampState: Equatable {
        case loading
        case loaded([ContentEvent])
        case empty
        case failed
    }

    init(
        repository: ContentRepository,
        source: ContentSource,
        store: WatchProgressStore,
        onPlay: @escaping (Video, ContentSource) -> Void,
        onOpenEvent: @escaping (ContentEvent) -> Void,
        resolveCover: @escaping (ContentEvent) async -> URL?,
        onAuthRequired: @escaping () -> Void
    ) {
        self.repository = repository
        self.source = source
        self.store = store
        self.onPlay = onPlay
        self.onOpenEvent = onOpenEvent
        self.resolveCover = resolveCover
        self.onAuthRequired = onAuthRequired
        _model = State(initialValue: VideoFeedViewModel(repository: repository, source: source, query: .latest))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 56) {
                if !store.items.isEmpty {
                    RailSection(title: "Continue Watching") {
                        continueWatchingRail
                    }
                }

                RailSection(title: "Recent WordCamps") {
                    wordCampRail
                }

                RailSection(title: "Latest") {
                    latestRail
                }
            }
            .padding(.vertical, 40)
        }
        .task { await model.load() }
        .task { await loadWordCampEvents() }
    }

    // MARK: Rails

    private var continueWatchingRail: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 40) {
                ForEach(store.items) { item in
                    ContinueWatchingCard(
                        progress: item,
                        resolvePoster: continueWatchingPoster,
                        onPosterResolved: { guid, posterURL in
                            store.setPosterURL(guid: guid, posterURL: posterURL)
                        }
                    ) {
                        onPlay(item.video, Sources.source(withID: item.sourceID))
                    }
                }
            }
            .padding(.horizontal, 80)
            .padding(.vertical, 20)
        }
    }

    private func continueWatchingPoster(_ progress: WatchProgress) async -> URL? {
        await repository.posterURL(
            source: Sources.source(withID: progress.sourceID),
            video: progress.video
        )
    }

    @ViewBuilder
    private var wordCampRail: some View {
        switch wordCampState {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, minHeight: 300)
        case .failed:
            Placeholder(message: "Couldn’t load WordCamps. Please try again.", action: ("Retry", { Task { await loadWordCampEvents() } }))
                .frame(minHeight: 300)
        case .empty:
            Placeholder(message: "No WordCamps yet.").frame(minHeight: 300)
        case .loaded(let events):
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 40) {
                    ForEach(events) { event in
                        PortraitCampCard(event: event, resolveCover: resolveCover) { onOpenEvent(event) }
                    }
                }
                .padding(.horizontal, 80)
                .padding(.vertical, 20)
            }
        }
    }

    private func loadWordCampEvents() async {
        wordCampState = .loading
        do {
            let events = try await repository.listWordCampEvents(
                source: source,
                limit: Catalog.wordCampEventLimit
            )
            wordCampState = events.isEmpty ? .empty : .loaded(events)
        } catch {
            wordCampState = .failed
        }
    }

    @ViewBuilder
    private var latestRail: some View {
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
            Placeholder(message: "No videos yet.").frame(minHeight: 300)
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

/// A titled shelf: section header above its (horizontally scrolling) content.
struct RailSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 80)
            content
        }
    }
}
