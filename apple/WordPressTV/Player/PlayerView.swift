import SwiftUI
import AVKit
import WordPressTVCore

/// Everything the player needs for one presentation: the resolved asset, the
/// `Video` it came from (so progress can be recorded), and where to resume.
struct PlaybackRequest: Identifiable {
    let asset: PlaybackAsset
    let video: Video
    let resumeAt: Double
    var id: String { video.videoGuid }
}

/// Full-screen AVPlayer. Core resolved the URL; here we play it with the
/// standard tvOS UI, resume from a saved position, and report progress back to
/// the `WatchProgressStore` so Continue Watching stays current.
struct PlayerView: View {
    let request: PlaybackRequest
    let store: WatchProgressStore

    @State private var player: AVPlayer
    @State private var observer: Any?

    init(request: PlaybackRequest, store: WatchProgressStore) {
        self.request = request
        self.store = store
        _player = State(initialValue: AVPlayer(url: request.asset.url))
    }

    var body: some View {
        VideoPlayer(player: player)
            .ignoresSafeArea()
            .onAppear(perform: start)
            .onDisappear(perform: stop)
    }

    private func start() {
        if request.resumeAt > 0 {
            player.seek(to: CMTime(seconds: request.resumeAt, preferredTimescale: 1))
        }
        // Sample progress every 5s so Continue Watching survives a crash or a
        // hard exit, not just a clean dismiss.
        observer = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 5, preferredTimescale: 1),
            queue: .main
        ) { _ in
            // Delivered on the main queue, so hop to the main actor to touch the
            // store.
            MainActor.assumeIsolated { record() }
        }
        player.play()
    }

    private func stop() {
        record()
        if let observer {
            player.removeTimeObserver(observer)
            self.observer = nil
        }
        player.pause()
    }

    private func record() {
        let position = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds
            ?? Double(request.asset.durationSeconds ?? 0)
        guard position.isFinite, duration.isFinite, duration > 0 else { return }
        store.record(video: request.video, position: position, duration: duration)
    }
}
