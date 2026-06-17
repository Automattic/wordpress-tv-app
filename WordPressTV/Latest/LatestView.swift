import SwiftUI
import WordPressTVCore

/// The slice's only screen: a focusable grid of Latest videos. Tapping a cell
/// resolves playback and presents the player full-screen.
struct LatestView: View {
    @State private var model: LatestViewModel
    /// Set once playback is resolved; drives the full-screen player cover.
    @State private var playback: PlaybackAsset?

    init(repository: ContentRepository, source: ContentSource) {
        _model = State(initialValue: LatestViewModel(repository: repository, source: source))
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("WordPress.tv")
        }
        .task { await model.load() }
        .fullScreenCover(item: $playback) { asset in
            PlayerView(asset: asset)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .failed(let message):
            VStack(spacing: 32) {
                Text(message)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                Button("Retry") { Task { await model.load() } }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .empty:
            Text("No videos yet.")
                .font(.title3)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .loaded(let videos):
            grid(videos)
        }
    }

    private func grid(_ videos: [Video]) -> some View {
        ScrollView {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 360), spacing: 80)],
                spacing: 80
            ) {
                ForEach(videos) { video in
                    Button { play(video) } label: {
                        VideoCell(video: video)
                    }
                    .buttonStyle(.card) // tvOS focus engine: lift + parallax on focus
                }
            }
            .padding(80)
        }
    }

    private func play(_ video: Video) {
        Task {
            // Best-effort: if resolution fails the cover just doesn't present.
            playback = try? await model.playbackAsset(for: video)
        }
    }
}

/// Poster + title cell. `AsyncImage` falls back to a placeholder while loading
/// or when a poster is missing.
private struct VideoCell: View {
    let video: Video

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            AsyncImage(url: video.posterUrl) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    ZStack {
                        Rectangle().fill(.quaternary)
                        Image(systemName: "play.rectangle.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            Text(video.title)
                .font(.headline)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: 360)
    }
}
