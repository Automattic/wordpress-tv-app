import Foundation

/// A single playable video, mapped from a content source's wire format into the
/// app's domain. Deliberately UI-agnostic — Core never imports SwiftUI or AVKit.
public struct Video: Identifiable, Equatable, Sendable {
    /// Stable identifier — the source post ID, as a string.
    public let id: String
    /// VideoPress GUID used to resolve a `PlaybackAsset` via `resolvePlayback`.
    public let videoGuid: String
    /// Plain-text title: HTML entities decoded, tags stripped.
    public let title: String
    /// Plain-text description/excerpt: HTML entities decoded, tags stripped.
    public let description: String
    /// Poster image, when the source provides one.
    public let posterUrl: URL?
    /// Duration in seconds, when known.
    public let durationSeconds: Int?
    /// The `ContentSource.id` this video came from.
    public let sourceID: String

    public init(
        id: String,
        videoGuid: String,
        title: String,
        description: String,
        posterUrl: URL?,
        durationSeconds: Int?,
        sourceID: String
    ) {
        self.id = id
        self.videoGuid = videoGuid
        self.title = title
        self.description = description
        self.posterUrl = posterUrl
        self.durationSeconds = durationSeconds
        self.sourceID = sourceID
    }
}
