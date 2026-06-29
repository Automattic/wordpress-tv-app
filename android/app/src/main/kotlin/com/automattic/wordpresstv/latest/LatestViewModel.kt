package com.automattic.wordpresstv.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.data.RepositoryException
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.coroutines.CancellationException

/**
 * Drives the Latest screen: loads page 1 from the repository and resolves a
 * [PlaybackAsset] when a video is tapped. UI-state only — all data work is
 * delegated to [ContentRepository]. The screen owns the coroutine scope and
 * drives these suspend functions, mirroring SwiftUI's `.task { await model… }`.
 */
class LatestViewModel(
    private val repository: ContentRepository,
    private val source: ContentSource,
) {
    sealed interface State {
        data object Loading : State
        data class Loaded(val videos: List<Video>) : State
        data object Empty : State
        data object Failed : State
        /** The source needs a (valid) token — the UI should route to pairing. */
        data object NeedsAuth : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    suspend fun load() {
        state = State.Loading
        try {
            // Page 1 only — paging is in the contract but not exercised yet.
            val videos = repository.listLatest(source, page = 1)
            state = if (videos.isEmpty()) State.Empty else State.Loaded(videos)
        } catch (_: RepositoryException.Unauthorized) {
            state = State.NeedsAuth
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state = State.Failed
        }
    }

    suspend fun playbackAsset(video: Video): PlaybackAsset =
        repository.resolvePlayback(source, video)

    /** Ready-to-load poster URL (token-stamped for private sources). */
    suspend fun posterUrl(video: Video): String? =
        repository.posterUrl(source, video)
}
