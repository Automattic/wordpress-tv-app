package com.automattic.wordpresstv.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.data.RepositoryException
import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.ui.VideoCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/**
 * What a feed screen is showing. All three resolve to the same posts endpoint via
 * [ContentRepository], differing only in filter. Mirrors the Apple `VideoQuery`.
 */
sealed interface VideoQuery {
    data object Latest : VideoQuery
    data class Category(val category: CategoryRef) : VideoQuery
    data class Event(val event: ContentEvent, val applyLanguageFilter: Boolean = false) : VideoQuery
    data class Search(val term: String) : VideoQuery
}

/**
 * Loads one page of a [VideoQuery] for a source and exposes UI state. The
 * generalization of the old `LatestViewModel` — Home, category grids, and search
 * all drive it. UI-state only; all data work is delegated to the repository.
 * Mirrors the Apple `VideoFeedViewModel`.
 */
class VideoFeedViewModel(
    private val repository: ContentRepository,
    val source: ContentSource,
    private val query: VideoQuery,
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
            val videos = fetch()
            state = if (videos.isEmpty()) State.Empty else State.Loaded(videos)
        } catch (_: RepositoryException.Unauthorized) {
            state = State.NeedsAuth
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state = State.Failed
        }
    }

    private suspend fun fetch(): List<Video> = when (query) {
        VideoQuery.Latest -> repository.listLatest(source, page = 1)
        is VideoQuery.Category -> repository.listByCategory(
            source = source,
            category = query.category,
            page = 1,
            applyLanguageFilter = true,
        )
        is VideoQuery.Event -> repository.listByEvent(
            source = source,
            event = query.event,
            page = 1,
            applyLanguageFilter = query.applyLanguageFilter,
        )
        is VideoQuery.Search -> repository.search(source, query.term, page = 1)
    }

    /** Ready-to-load poster URL (token-stamped for private sources). */
    suspend fun posterUrl(video: Video): String? = repository.posterUrl(source, video)
}

/**
 * A focusable grid of videos for a single query — the browse destination behind
 * each category tab, and the search results list. Mirrors the Apple `VideoGrid`.
 */
@Composable
fun VideoGrid(
    repository: ContentRepository,
    source: ContentSource,
    query: VideoQuery,
    onPlay: (Video, ContentSource) -> Unit,
    onAuthRequired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val model = remember(repository, source.id, query) { VideoFeedViewModel(repository, source, query) }
    LaunchedEffect(repository, source.id, query) { model.load() }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        when (val state = model.state) {
            VideoFeedViewModel.State.Loading -> Centered { CircularProgressIndicator(color = Color.White) }

            VideoFeedViewModel.State.Failed -> Centered {
                MessageWithAction(
                    message = stringResource(R.string.videos_load_error),
                    action = stringResource(R.string.retry),
                    onAction = { scope.launch { model.load() } },
                )
            }

            VideoFeedViewModel.State.Empty -> Centered {
                Text(stringResource(R.string.no_videos_here), color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp)
            }

            VideoFeedViewModel.State.NeedsAuth -> Centered {
                MessageWithAction(
                    message = stringResource(R.string.session_expired),
                    action = stringResource(R.string.sign_in),
                    onAction = onAuthRequired,
                )
            }

            is VideoFeedViewModel.State.Loaded -> Grid(
                videos = state.videos,
                resolvePoster = { model.posterUrl(it) },
                onPlay = { onPlay(it, source) },
            )
        }
    }
}

@Composable
private fun Grid(
    videos: List<Video>,
    resolvePoster: suspend (Video) -> String?,
    onPlay: (Video) -> Unit,
) {
    // Android TV needs an element to hold focus before the D-pad can move; focus
    // the first card once the grid is laid out.
    val firstCardFocus = remember { FocusRequester() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(48.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
            VideoCard(
                video = video,
                resolvePoster = resolvePoster,
                onClick = { onPlay(video) },
                focusRequester = if (index == 0) firstCardFocus else null,
            )
        }
    }

    LaunchedEffect(videos.firstOrNull()?.id) {
        repeat(10) {
            if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
}

/**
 * A horizontal shelf of video cards (the "rails" on Home). [firstCardFocus], when
 * set, lets Home land initial focus on this rail's first card.
 */
@Composable
fun VideoRail(
    videos: List<Video>,
    source: ContentSource,
    resolvePoster: suspend (Video) -> String?,
    onPlay: (Video, ContentSource) -> Unit,
    firstCardFocus: FocusRequester? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rowItemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
            VideoCard(
                video = video,
                resolvePoster = resolvePoster,
                onClick = { onPlay(video, source) },
                modifier = Modifier.width(300.dp),
                focusRequester = if (index == 0) firstCardFocus else null,
            )
        }
    }
}

/** A titled shelf: section header above its horizontally scrolling content. */
@Composable
fun RailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        content()
    }
}

@Composable
fun RailPlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun MessageWithAction(message: String, action: String, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(action) }
    }
}
