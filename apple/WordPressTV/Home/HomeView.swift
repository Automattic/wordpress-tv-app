import SwiftUI
import Observation
import WordPressTVCore

/// The railed landing screen from the design: stacked horizontal shelves —
/// Continue Watching (when the viewer has any), the curated Flagship WordCamps,
/// and Latest. Everything but the flagship art is live WordPress.tv content.
struct HomeView: View {
    let repository: ContentRepository
    let source: ContentSource
    let store: WatchProgressStore
    /// Resolve + present the player for a tapped video.
    let onPlay: (Video, ContentSource) -> Void
    /// Open a flagship camp's full video grid.
    let onOpenCamp: (FlagshipCamp) -> Void
    /// Resolve a flagship card's cover image (its newest video poster).
    let resolveCover: (FlagshipCamp) async -> URL?
    let onAuthRequired: () -> Void

    @State private var model: VideoFeedViewModel

    init(
        repository: ContentRepository,
        source: ContentSource,
        store: WatchProgressStore,
        onPlay: @escaping (Video, ContentSource) -> Void,
        onOpenCamp: @escaping (FlagshipCamp) -> Void,
        resolveCover: @escaping (FlagshipCamp) async -> URL?,
        onAuthRequired: @escaping () -> Void
    ) {
        self.repository = repository
        self.source = source
        self.store = store
        self.onPlay = onPlay
        self.onOpenCamp = onOpenCamp
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

                RailSection(title: "Flagship WordCamps") {
                    flagshipRail
                }

                RailSection(title: "Latest") {
                    latestRail
                }
            }
            .padding(.vertical, 40)
        }
        .task { await model.load() }
    }

    // MARK: Rails

    private var continueWatchingRail: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 40) {
                ForEach(store.items) { item in
                    ContinueWatchingCard(progress: item) {
                        onPlay(item.video, Sources.source(withID: item.sourceID))
                    }
                }
            }
            .padding(.horizontal, 80)
            .padding(.vertical, 20)
        }
    }

    private var flagshipRail: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 40) {
                ForEach(Catalog.flagshipCamps) { camp in
                    PortraitCampCard(camp: camp, resolveCover: resolveCover) { onOpenCamp(camp) }
                }
            }
            .padding(.horizontal, 80)
            .padding(.vertical, 20)
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
