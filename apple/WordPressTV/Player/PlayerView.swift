import SwiftUI
import AVKit
import WordPressTVCore

/// Full-screen AVPlayer presentation. Core resolved the `PlaybackAsset.url`;
/// here we just hand it to AVPlayer and use the standard tvOS playback UI.
struct PlayerView: View {
    let asset: PlaybackAsset
    @State private var player: AVPlayer

    init(asset: PlaybackAsset) {
        self.asset = asset
        _player = State(initialValue: AVPlayer(url: asset.url))
    }

    var body: some View {
        VideoPlayer(player: player)
            .ignoresSafeArea()
            .onAppear { player.play() }
            .onDisappear { player.pause() }
    }
}
