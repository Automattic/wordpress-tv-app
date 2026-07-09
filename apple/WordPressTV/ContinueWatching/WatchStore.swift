import Foundation
import Observation
import WordPressTVSharedCore

private typealias SharedWatchProgress = WordPressTVSharedCore.WatchProgress
private typealias SharedWatchProgressStoreCore = WordPressTVSharedCore.WatchProgressStoreCore

/// One video's resume point. Persisted locally (no server watch-history exists),
/// so Continue Watching is per-device. Backed by the shared Kotlin model so tvOS
/// and Android use the same fields, units, JSON format, and pruning rules.
struct WatchProgress: Identifiable, Equatable {
    fileprivate let shared: SharedWatchProgress

    var videoGuid: String { shared.videoGuid }
    var sourceID: String { shared.sourceId }
    var title: String { shared.title }
    var posterURLString: String? { shared.posterUrl }
    var playbackToken: String? { shared.playbackToken }
    var positionSeconds: Double { Double(shared.positionMs) / 1_000 }
    var durationSeconds: Double { Double(shared.durationMs) / 1_000 }
    var id: String { videoGuid }

    /// 0…1 watched fraction, clamped. Drives the resume bar under the card.
    var fractionComplete: Double { shared.fractionComplete }

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

    static func == (lhs: WatchProgress, rhs: WatchProgress) -> Bool {
        lhs.videoGuid == rhs.videoGuid &&
        lhs.sourceID == rhs.sourceID &&
        lhs.title == rhs.title &&
        lhs.posterURLString == rhs.posterURLString &&
        lhs.playbackToken == rhs.playbackToken &&
        lhs.positionSeconds == rhs.positionSeconds &&
        lhs.durationSeconds == rhs.durationSeconds
    }
}

/// Owns the Continue Watching list. Platform persistence/observation stays here;
/// shared Kotlin owns record/remove/poster-update rules and JSON encoding.
@MainActor
@Observable
final class WatchProgressStore {
    /// In-progress videos, most recently watched first.
    private(set) var items: [WatchProgress] = []

    private let defaults: UserDefaults
    private let storageKey = "continueWatching.v1"
    private let core: SharedWatchProgressStoreCore

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        if let encoded = defaults.string(forKey: storageKey) {
            self.core = SharedWatchProgressStoreCore(encodedItems: encoded)
        } else if let legacy = Self.loadLegacyItems(defaults: defaults, storageKey: storageKey) {
            self.core = SharedWatchProgressStoreCore(initialItems: legacy)
            defaults.set(core.encodedItems(), forKey: storageKey)
        } else {
            self.core = SharedWatchProgressStoreCore(encodedItems: nil)
        }

        syncItems()
    }

    /// Look up an existing resume point (used to seek on play).
    func progress(forGuid guid: String) -> WatchProgress? {
        core.progress(videoGuid: guid).map(WatchProgress.init(shared:))
    }

    /// Record where the viewer is in `video`. The shared store guards the "barely
    /// started" and "essentially finished" cases.
    func record(video: Video, position: Double, duration: Double) {
        guard position.isFinite, duration.isFinite else { return }
        let positionMs = Int64(position * 1_000)
        let durationMs = Int64(duration * 1_000)
        if core.record(video: video.shared, positionMs: positionMs, durationMs: durationMs) {
            syncAndPersist()
        }
    }

    func setPosterURL(guid: String, posterURL: URL) {
        if core.setPosterUrl(videoGuid: guid, posterUrl: posterURL.absoluteString) {
            syncAndPersist()
        }
    }

    func remove(guid: String) {
        if core.remove(videoGuid: guid) {
            syncAndPersist()
        }
    }

    private func syncAndPersist() {
        syncItems()
        persist()
    }

    private func syncItems() {
        items = core.items.map(WatchProgress.init(shared:))
    }

    private func persist() {
        defaults.set(core.encodedItems(), forKey: storageKey)
    }

    private static func loadLegacyItems(defaults: UserDefaults, storageKey: String) -> [SharedWatchProgress]? {
        guard let data = defaults.data(forKey: storageKey),
              let stored = try? JSONDecoder().decode([LegacyWatchProgress].self, from: data) else {
            return nil
        }
        return stored.sorted { $0.updatedAt > $1.updatedAt }.map(\.shared)
    }
}

private struct LegacyWatchProgress: Codable {
    let videoGuid: String
    let sourceID: String
    let title: String
    let posterURLString: String?
    let playbackToken: String?
    var positionSeconds: Double
    var durationSeconds: Double
    var updatedAt: Date

    var shared: SharedWatchProgress {
        SharedWatchProgress(
            videoGuid: videoGuid,
            sourceId: sourceID,
            title: title,
            posterUrl: posterURLString,
            playbackToken: playbackToken,
            positionMs: Int64(positionSeconds * 1_000),
            durationMs: Int64(durationSeconds * 1_000)
        )
    }
}
