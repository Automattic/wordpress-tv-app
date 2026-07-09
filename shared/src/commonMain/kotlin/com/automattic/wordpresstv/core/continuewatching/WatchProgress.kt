package com.automattic.wordpresstv.core.continuewatching

import com.automattic.wordpresstv.core.domain.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One video's local resume point. The app keeps this per device; the shared model
 * keeps Android and tvOS on the same field names, units, and pruning rules.
 */
@Serializable
data class WatchProgress(
    val videoGuid: String,
    val sourceId: String,
    val title: String,
    val posterUrl: String?,
    /**
     * The VideoPress `metadata_token` for a private (a8c.tv) video; `null` for
     * public wordpress.tv. Needed to re-resolve the poster/stream on resume.
     */
    val playbackToken: String?,
    val positionMs: Long,
    val durationMs: Long,
) {
    /** 0…1 watched fraction, clamped. Drives the resume bar under the card. */
    val fractionComplete: Double
        get() = if (durationMs > 0) {
            (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

    /** Rebuild a [Video] good enough to re-resolve playback and render a card. */
    val video: Video
        get() = Video(
            id = videoGuid,
            videoGuid = videoGuid,
            title = title,
            description = "",
            posterUrl = posterUrl,
            durationSeconds = if (durationMs > 0) (durationMs / 1000).toInt() else null,
            sourceId = sourceId,
            playbackToken = playbackToken,
        )
}

/**
 * Shared, UI-free Continue Watching rules. Platform adapters own persistence and
 * observation; this class owns the list mutation semantics and JSON format.
 */
class WatchProgressStoreCore {
    var items: List<WatchProgress>
        private set

    constructor(encodedItems: String? = null) {
        items = decode(encodedItems)
    }

    constructor(initialItems: List<WatchProgress>) {
        items = initialItems.take(MAX_ITEMS)
    }

    fun progress(videoGuid: String): WatchProgress? =
        items.firstOrNull { it.videoGuid == videoGuid }

    /**
     * Record where the viewer is in [video]. Returns `true` only when [items]
     * changed, so adapters can avoid unnecessary persistence notifications.
     */
    fun record(video: Video, positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0 || positionMs < MINIMUM_MS) return false

        if (positionMs.toDouble() / durationMs.toDouble() >= FINISHED_FRACTION) {
            return remove(video.videoGuid)
        }

        val entry = WatchProgress(
            videoGuid = video.videoGuid,
            sourceId = video.sourceId,
            title = video.title,
            posterUrl = video.posterUrl,
            playbackToken = video.playbackToken,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        val next = (listOf(entry) + items.filterNot { it.videoGuid == video.videoGuid }).take(MAX_ITEMS)
        if (next == items) return false
        items = next
        return true
    }

    fun setPosterUrl(videoGuid: String, posterUrl: String): Boolean {
        if (posterUrl.isBlank()) return false
        var changed = false
        val next = items.map { item ->
            if (item.videoGuid == videoGuid && item.posterUrl != posterUrl) {
                changed = true
                item.copy(posterUrl = posterUrl)
            } else {
                item
            }
        }
        if (!changed) return false
        items = next
        return true
    }

    fun remove(videoGuid: String): Boolean {
        val next = items.filterNot { it.videoGuid == videoGuid }
        if (next.size == items.size) return false
        items = next
        return true
    }

    fun encodedItems(): String = encode(items)

    companion object {
        private const val MINIMUM_MS = 15_000L
        private const val FINISHED_FRACTION = 0.95
        private const val MAX_ITEMS = 20

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(encodedItems: String?): List<WatchProgress> {
            if (encodedItems.isNullOrBlank()) return emptyList()
            return runCatching {
                json.decodeFromString<List<WatchProgress>>(encodedItems)
                    .take(MAX_ITEMS)
            }.getOrDefault(emptyList())
        }

        fun encode(items: List<WatchProgress>): String =
            json.encodeToString(items.take(MAX_ITEMS))
    }
}
