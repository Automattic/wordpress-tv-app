package com.automattic.wordpresstv.continuewatching

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.wordpresstv.core.continuewatching.WatchProgressStoreCore
import com.automattic.wordpresstv.core.domain.Video

typealias WatchProgress = com.automattic.wordpresstv.core.continuewatching.WatchProgress

/**
 * Android adapter for the shared Continue Watching rules. Persistence stays in
 * SharedPreferences; mutation semantics and JSON format live in `:shared`.
 */
class WatchProgressStore(context: Context) {

    /** In-progress videos, most recently watched first. */
    var items by mutableStateOf<List<WatchProgress>>(emptyList())
        private set

    private val prefs = context.getSharedPreferences("continue_watching", Context.MODE_PRIVATE)
    private val core = WatchProgressStoreCore(prefs.getString(KEY, null))

    init {
        items = core.items
    }

    /** Look up an existing resume point (used to seek on play). */
    fun progress(videoGuid: String): WatchProgress? = core.progress(videoGuid)

    /**
     * Record where the viewer is in [video]. The shared store guards the "barely
     * started" and "essentially finished" cases.
     */
    fun record(video: Video, positionMs: Long, durationMs: Long) {
        if (core.record(video, positionMs, durationMs)) syncAndPersist()
    }

    fun setPosterUrl(videoGuid: String, posterUrl: String) {
        if (core.setPosterUrl(videoGuid, posterUrl)) syncAndPersist()
    }

    fun remove(videoGuid: String) {
        if (core.remove(videoGuid)) syncAndPersist()
    }

    private fun syncAndPersist() {
        items = core.items
        prefs.edit().putString(KEY, core.encodedItems()).apply()
    }

    private companion object {
        const val KEY = "items_v1"
    }
}
