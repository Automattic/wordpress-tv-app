import SwiftUI
import AVFoundation
import UIKit

/// Plays the bundled intro clip full-screen on cold launch, then calls
/// `onFinished`. Auto-advances when the clip ends; a safety timeout and a
/// remote press both guarantee we never strand the user on the splash.
struct SplashView: View {
    /// Invoked exactly once when the intro is done (ended, skipped, or timed out).
    let onFinished: () -> Void

    /// The intro runs ~5s; give a little headroom before the timeout fires.
    private static let safetyTimeout: Duration = .seconds(8)

    @State private var player: AVPlayer? = {
        guard let url = Bundle.main.url(forResource: "Intro", withExtension: "mp4") else { return nil }
        return AVPlayer(url: url)
    }()
    @State private var didFinish = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let player {
                VideoLayerView(player: player)
                    .ignoresSafeArea()
            }
        }
        // Menu/play-pause press skips straight into the app.
        .onExitCommand(perform: finish)
        // The splash owns the only player, so an unfiltered end notification is
        // unambiguous. Hop to main since the notification can post off-thread.
        .onReceive(
            NotificationCenter.default
                .publisher(for: .AVPlayerItemDidPlayToEndTime)
                .receive(on: DispatchQueue.main)
        ) { _ in finish() }
        .task {
            player?.play()
            // Belt-and-suspenders: advance even if the end notification never
            // fires (e.g. the asset is missing or fails to load).
            try? await Task.sleep(for: Self.safetyTimeout)
            finish()
        }
    }

    private func finish() {
        guard !didFinish else { return }
        didFinish = true
        player?.pause()
        onFinished()
    }
}

/// Renders an `AVPlayer` full-screen via `AVPlayerLayer` — no playback chrome,
/// unlike `VideoPlayer`, which shows the tvOS transport controls.
private struct VideoLayerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ uiView: PlayerLayerView, context: Context) {
        uiView.playerLayer.player = player
    }

    /// A `UIView` whose backing layer is an `AVPlayerLayer`, so video sizing
    /// follows the view bounds automatically.
    final class PlayerLayerView: UIView {
        override static var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }

        override init(frame: CGRect) {
            super.init(frame: frame)
            playerLayer.videoGravity = .resizeAspectFill
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    }
}
