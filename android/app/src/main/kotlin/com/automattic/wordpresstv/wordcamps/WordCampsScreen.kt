package com.automattic.wordpresstv.wordcamps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.catalog.NavCategory
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.feed.MessageWithAction
import com.automattic.wordpresstv.feed.RailPlaceholder
import com.automattic.wordpresstv.feed.RailSection
import com.automattic.wordpresstv.feed.VideoFeedViewModel
import com.automattic.wordpresstv.feed.VideoQuery
import com.automattic.wordpresstv.feed.VideoRail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.tv.material3.Text

/**
 * The WordCamps tab: a broad language-filtered WordCamp category rail at the
 * top, followed by event-taxonomy rails loaded page by page as the viewer
 * scrolls down.
 */
@Composable
fun WordCampsScreen(
    repository: ContentRepository,
    source: ContentSource,
    category: NavCategory,
    onPlay: (Video, ContentSource) -> Unit,
    onAuthRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var events by remember(repository, source.id) { mutableStateOf<List<ContentEvent>>(emptyList()) }
    var nextEventPage by remember(repository, source.id) { mutableStateOf(1) }
    var isLoadingEventPage by remember(repository, source.id) { mutableStateOf(false) }
    var canLoadMoreEvents by remember(repository, source.id) { mutableStateOf(true) }
    var eventPageFailed by remember(repository, source.id) { mutableStateOf(false) }

    suspend fun loadNextEventPage() {
        if (isLoadingEventPage || !canLoadMoreEvents) return
        isLoadingEventPage = true
        eventPageFailed = false
        try {
            val page = repository.listWordCampEvents(source, nextEventPage)
            val knownIds = events.map { it.id }.toSet()
            events = events + page.filter { it.id !in knownIds }
            nextEventPage += 1
            canLoadMoreEvents = page.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            eventPageFailed = true
        } finally {
            isLoadingEventPage = false
        }
    }

    LaunchedEffect(repository, source.id) {
        events = emptyList()
        nextEventPage = 1
        isLoadingEventPage = false
        canLoadMoreEvents = true
        eventPageFailed = false
        loadNextEventPage()
    }

    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            QueryVideoRail(
                title = stringResource(R.string.latest_wordcamp_videos),
                repository = repository,
                source = source,
                query = VideoQuery.Category(category.ref),
                onPlay = onPlay,
                onAuthRequired = onAuthRequired,
            )
        }

        items(events, key = { it.id }) { event ->
            QueryVideoRail(
                title = event.name,
                repository = repository,
                source = source,
                query = VideoQuery.Event(event),
                onPlay = onPlay,
                onAuthRequired = onAuthRequired,
            )
        }

        item {
            when {
                isLoadingEventPage -> RailPlaceholder { CircularProgressIndicator(color = Color.White) }
                eventPageFailed -> RailPlaceholder {
                    MessageWithAction(
                        message = stringResource(R.string.wordcamps_load_error),
                        action = stringResource(R.string.retry),
                        onAction = { scope.launch { loadNextEventPage() } },
                    )
                }
                canLoadMoreEvents -> LaunchedEffect(nextEventPage, events.size) { loadNextEventPage() }
            }
        }
    }
}

@Composable
private fun QueryVideoRail(
    title: String,
    repository: ContentRepository,
    source: ContentSource,
    query: VideoQuery,
    onPlay: (Video, ContentSource) -> Unit,
    onAuthRequired: () -> Unit,
) {
    val model = remember(repository, source.id, query) { VideoFeedViewModel(repository, source, query) }
    LaunchedEffect(repository, source.id, query) { model.load() }
    val scope = rememberCoroutineScope()

    RailSection(title) {
        when (val state = model.state) {
            VideoFeedViewModel.State.Loading -> RailPlaceholder { CircularProgressIndicator(color = Color.White) }

            VideoFeedViewModel.State.Failed -> RailPlaceholder {
                MessageWithAction(
                    message = stringResource(R.string.videos_load_error),
                    action = stringResource(R.string.retry),
                    onAction = { scope.launch { model.load() } },
                )
            }

            VideoFeedViewModel.State.NeedsAuth -> RailPlaceholder {
                MessageWithAction(
                    message = stringResource(R.string.session_expired),
                    action = stringResource(R.string.sign_in),
                    onAction = onAuthRequired,
                )
            }

            VideoFeedViewModel.State.Empty -> RailPlaceholder {
                Text(stringResource(R.string.no_videos_here), color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
            }

            is VideoFeedViewModel.State.Loaded -> VideoRail(
                videos = state.videos,
                source = source,
                resolvePoster = { model.posterUrl(it) },
                onPlay = onPlay,
            )
        }
    }
}
