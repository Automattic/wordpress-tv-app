import Foundation
import Observation

/// One video's resume point. Persisted locally (no server watch-history exists),
/// so Continue Watching is per-device. Carries just enough to render a card and
/// rebuild a playable `Video` without another network round-trip.
struct WatchProgress: Codable, Identifiable, Equatable {
    let videoGuid: String
    let sourceID: String
    let title: String
    let posterURLString: String?
    /// The VideoPress `metadata_token` for a private (a8c.tv) video; `nil` for
    /// public wordpress.tv. Needed to re-resolve the poster/stream on resume.
    let playbackToken: String?
    var positionSeconds: Double
    var durationSeconds: Double
    var updatedAt: Date

    var id: String { videoGuid }

    /// 0…1 watched fraction, clamped. Drives the resume bar under the card.
    var fractionComplete: Double {
        guard durationSeconds > 0 else { return 0 }
        return min(max(positionSeconds / durationSeconds, 0), 1)
    }

    var posterURL: URL? { posterURLString.flatMap(URL.init(string:)) }

    /// Rebuild a `Video` good enough to re-resolve playback and render a card.
    var video: Video {
        Video(
            id: videoGuid,
            videoGuid: videoGuid,
            title: title,
            description: "",
            posterUrl: posterURL,
            durationSeconds: durationSeconds > 0 ? Int(durationSeconds) : nil,
            sourceID: sourceID,
            playbackToken: playbackToken
        )
    }
}

/// Owns the Continue Watching list. Records progress as the player reports it,
/// drops items once they're essentially finished, and persists to `UserDefaults`
/// as a single JSON blob.
@MainActor
@Observable
final class WatchProgressStore {
    /// In-progress videos, most recently watched first.
    private(set) var items: [WatchProgress] = []

    /// Below this many seconds we treat playback as "not really started" and
    /// don't surface a resume point (avoids cluttering the shelf with 3-second
    /// taps). Above `finishedFraction` we treat it as watched and drop it.
    private let minimumSeconds: Double = 15
    private let finishedFraction: Double = 0.95
    private let maxItems = 20

    private let defaults: UserDefaults
    private let storageKey = "continueWatching.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    /// Look up an existing resume point (used to seek on play).
    func progress(forGuid guid: String) -> WatchProgress? {
        items.first { $0.videoGuid == guid }
    }

    /// Record where the viewer is in `video`. Upserts, re-sorts newest-first,
    /// and evicts once finished. A no-op below `minimumSeconds`.
    func record(video: Video, position: Double, duration: Double) {
        guard duration > 0, position >= minimumSeconds else { return }

        // Finished (or all but) — clear any existing entry and stop tracking.
        if position / duration >= finishedFraction {
            remove(guid: video.videoGuid)
            return
        }

        var next = items.filter { $0.videoGuid != video.videoGuid }
        next.insert(
            WatchProgress(
                videoGuid: video.videoGuid,
                sourceID: video.sourceID,
                title: video.title,
                posterURLString: video.posterUrl?.absoluteString,
                playbackToken: video.playbackToken,
                positionSeconds: position,
                durationSeconds: duration,
                updatedAt: Date()
            ),
            at: 0
        )
        items = Array(next.prefix(maxItems))
        persist()
    }

    func remove(guid: String) {
        let filtered = items.filter { $0.videoGuid != guid }
        guard filtered.count != items.count else { return }
        items = filtered
        persist()
    }

    // MARK: Persistence

    private func persist() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        defaults.set(data, forKey: storageKey)
    }

    private func load() {
        guard let data = defaults.data(forKey: storageKey),
              let stored = try? JSONDecoder().decode([WatchProgress].self, from: data) else { return }
        items = stored.sorted { $0.updatedAt > $1.updatedAt }
    }
}
