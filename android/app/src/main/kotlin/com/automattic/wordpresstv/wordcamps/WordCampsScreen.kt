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
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.feed.MessageWithAction
import com.automattic.wordpresstv.feed.RailPlaceholder
import com.automattic.wordpresstv.feed.RailSection
import com.automattic.wordpresstv.feed.VideoRail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The WordCamps tab: event-taxonomy rails loaded page by page as the viewer
 * scrolls down.
 */
@Composable
fun WordCampsScreen(
    repository: ContentRepository,
    source: ContentSource,
    onPlay: (Video, ContentSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rails by remember(repository, source.id) { mutableStateOf<List<WordCampRail>>(emptyList()) }
    var nextEventPage by remember(repository, source.id) { mutableStateOf(1) }
    var isLoadingEventPage by remember(repository, source.id) { mutableStateOf(false) }
    var canLoadMoreEvents by remember(repository, source.id) { mutableStateOf(true) }
    var eventPageFailed by remember(repository, source.id) { mutableStateOf(false) }

    suspend fun loadEventPages() {
        if (isLoadingEventPage || !canLoadMoreEvents) return
        isLoadingEventPage = true
        eventPageFailed = false
        var pageNumber = nextEventPage
        val loadedRails = rails.toMutableList()
        val startingRailCount = loadedRails.size
        val knownIds = loadedRails.map { it.event.id }.toMutableSet()
        try {
            while (loadedRails.size - startingRailCount < MIN_WORDCAMP_RAILS_PER_BATCH) {
                val page = repository.listWordCampEvents(source, pageNumber)
                if (page.isEmpty()) {
                    canLoadMoreEvents = false
                    break
                }
                page.filter { knownIds.add(it.id) }.forEach { event ->
                    val videos = repository.listByEvent(
                        source = source,
                        event = event,
                        page = 1,
                        applyLanguageFilter = true,
                    )
                    if (videos.isNotEmpty()) {
                        loadedRails += WordCampRail(event = event, videos = videos)
                        rails = loadedRails.toList()
                    }
                }
                pageNumber += 1
            }
            rails = loadedRails
            nextEventPage = pageNumber
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            rails = loadedRails
            nextEventPage = pageNumber
            eventPageFailed = true
        } finally {
            isLoadingEventPage = false
        }
    }

    LaunchedEffect(repository, source.id) {
        rails = emptyList()
        nextEventPage = 1
        isLoadingEventPage = false
        canLoadMoreEvents = true
        eventPageFailed = false
        loadEventPages()
    }

    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        items(rails, key = { rail -> rail.event.id }) { rail ->
            RailSection(rail.event.name) {
                VideoRail(
                    videos = rail.videos,
                    source = source,
                    resolvePoster = { repository.posterUrl(source, it) },
                    onPlay = onPlay,
                )
            }
        }

        if (isLoadingEventPage || eventPageFailed) {
            item(key = "wordcamp-pagination") {
                when {
                    isLoadingEventPage -> RailPlaceholder { CircularProgressIndicator(color = Color.White) }
                    eventPageFailed -> RailPlaceholder {
                        MessageWithAction(
                            message = stringResource(R.string.wordcamps_load_error),
                            action = stringResource(R.string.retry),
                            onAction = { scope.launch { loadEventPages() } },
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_WORDCAMP_RAILS_PER_BATCH = 3

private data class WordCampRail(
    val event: ContentEvent,
    val videos: List<Video>,
)
