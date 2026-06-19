import Foundation

/// A ready-to-play asset: an absolute URL plus the metadata a player needs.
///
/// This is the heart of the Core/app seam — **Core resolves the URL, the app
/// feeds it to AVPlayer.** Core never imports AVKit. `Identifiable` (by `url`)
/// so the app can drive a `fullScreenCover(item:)` directly off it.
public struct PlaybackAsset: Identifiable, Equatable, Sendable {
    public enum Kind: String, Equatable, Sendable {
        case hls   // .m3u8 (preferred)
        case dash  // .mpd
        case mp4   // progressive original
    }

    public var id: URL { url }
    public let url: URL
    public let kind: Kind
    public let title: String
    public let durationSeconds: Int?

    public init(url: URL, kind: Kind, title: String, durationSeconds: Int?) {
        self.url = url
        self.kind = kind
        self.title = title
        self.durationSeconds = durationSeconds
    }
}
