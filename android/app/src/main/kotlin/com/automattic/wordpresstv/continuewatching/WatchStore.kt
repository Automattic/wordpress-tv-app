package com.automattic.wordpresstv.continuewatching

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One video's resume point. Persisted locally (no server watch-history exists),
 * so Continue Watching is per-device. Carries just enough to render a card and
 * rebuild a playable [Video] without another network round-trip. Positions are
 * in milliseconds — ExoPlayer's unit — so the player can seek directly.
 * Mirrors the Apple `WatchProgress`.
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
    val fractionComplete: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

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
 * Owns the Continue Watching list. Records progress as the player reports it,
 * drops items once they're essentially finished, and persists to
 * [android.content.SharedPreferences] as a single JSON blob.
 *
 * The Apple side uses `UserDefaults`; SharedPreferences is its Android analog —
 * synchronous, per-app, ideal for a small, frequently-updated local cache. State
 * is held in Compose [mutableStateOf] so rails recompose as playback progresses.
 */
class WatchProgressStore(context: Context) {

    /** In-progress videos, most recently watched first. */
    var items by mutableStateOf<List<WatchProgress>>(emptyList())
        private set

    private val prefs = context.getSharedPreferences("continue_watching", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    /** Look up an existing resume point (used to seek on play). */
    fun progress(videoGuid: String): WatchProgress? = items.firstOrNull { it.videoGuid == videoGuid }

    /**
     * Record where the viewer is in [video]. Upserts, re-sorts newest-first, and
     * evicts once finished. A no-op below [MINIMUM_MS] (avoids cluttering the
     * shelf with a few seconds of an accidental tap).
     */
    fun record(video: Video, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < MINIMUM_MS) return

        // Finished (or all but) — clear any existing entry and stop tracking.
        if (positionMs.toFloat() / durationMs >= FINISHED_FRACTION) {
            remove(video.videoGuid)
            return
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
        items = (listOf(entry) + items.filterNot { it.videoGuid == video.videoGuid }).take(MAX_ITEMS)
        persist()
    }

    fun remove(videoGuid: String) {
        val filtered = items.filterNot { it.videoGuid == videoGuid }
        if (filtered.size == items.size) return
        items = filtered
        persist()
    }

    // --- Persistence ---

    private fun persist() {
        prefs.edit().putString(KEY, json.encodeToString(items)).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        items = runCatching { json.decodeFromString<List<WatchProgress>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "items_v1"

        /** Below this many ms we treat playback as "not really started". */
        const val MINIMUM_MS = 15_000L

        /** Above this fraction we treat the video as watched and drop it. */
        const val FINISHED_FRACTION = 0.95f

        const val MAX_ITEMS = 20
    }
}
