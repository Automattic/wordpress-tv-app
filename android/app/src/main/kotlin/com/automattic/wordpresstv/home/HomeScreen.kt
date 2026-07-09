package com.automattic.wordpresstv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.catalog.sourceWithId
import com.automattic.wordpresstv.continuewatching.WatchProgress
import com.automattic.wordpresstv.continuewatching.WatchProgressStore
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.feed.MessageWithAction
import com.automattic.wordpresstv.feed.VideoFeedViewModel
import com.automattic.wordpresstv.feed.VideoQuery
import com.automattic.wordpresstv.feed.VideoRail
import com.automattic.wordpresstv.ui.ContinueWatchingCard
import com.automattic.wordpresstv.ui.PortraitCampCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Text

/**
 * The railed landing screen from the design: stacked horizontal shelves —
 * Continue Watching (when the viewer has any), flagship WordCamp events, and
 * Latest. Everything is live WordPress.tv content.
 * Mirrors the Apple `HomeView`.
 */
@Composable
fun HomeScreen(
    repository: ContentRepository,
    source: ContentSource,
    store: WatchProgressStore,
    onPlay: (Video, ContentSource) -> Unit,
    onOpenEvent: (ContentEvent) -> Unit,
    resolveCover: suspend (ContentEvent) -> String?,
    onAuthRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(repository, source.id) { VideoFeedViewModel(repository, source, VideoQuery.Latest) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, source.id) { model.load() }
    var wordCampState by remember(repository, source.id) { mutableStateOf<WordCampState>(WordCampState.Loading) }
    LaunchedEffect(repository, source.id) {
        wordCampState = WordCampState.Loading
        wordCampState = loadWordCampState(repository, source)
    }

    // Land initial focus on the first card of the topmost rail so the D-pad works
    // immediately; pressing Up from there reaches the nav bar.
    val firstCardFocus = remember { FocusRequester() }
    val continueWatching = store.items
    val initialFocusKey = continueWatching.firstOrNull()?.videoGuid
        ?: (wordCampState as? WordCampState.Loaded)?.events?.firstOrNull()?.id

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item {
                RailSection(stringResource(R.string.continue_watching)) {
                    ContinueWatchingRail(
                        items = continueWatching,
                        resolvePoster = { item ->
                            runCatching {
                                repository.posterUrl(Sources.sourceWithId(item.sourceId), item.video)
                            }.getOrNull()
                        },
                        onPosterResolved = store::setPosterUrl,
                        onPlay = onPlay,
                        firstCardFocus = firstCardFocus,
                    )
                }
            }
        }

        item {
            RailSection(stringResource(R.string.flagship_wordcamps)) {
                WordCampRail(
                    state = wordCampState,
                    resolveCover = resolveCover,
                    onOpenEvent = onOpenEvent,
                    onRetry = {
                        wordCampState = WordCampState.Loading
                        scope.launch {
                            wordCampState = loadWordCampState(repository, source)
                        }
                    },
                    firstCardFocus = if (continueWatching.isEmpty()) firstCardFocus else null,
                )
            }
        }

        item {
            RailSection(stringResource(R.string.latest)) {
                LatestRail(model = model, source = source, onPlay = onPlay, onAuthRequired = onAuthRequired)
            }
        }
    }

    LaunchedEffect(initialFocusKey) {
        repeat(10) {
            if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
}

private sealed interface WordCampState {
    data object Loading : WordCampState
    data class Loaded(val events: List<ContentEvent>) : WordCampState
    data object Empty : WordCampState
    data object Failed : WordCampState
}

private suspend fun loadWordCampState(repository: ContentRepository, source: ContentSource): WordCampState =
    runCatching {
        val events = repository.listFlagshipWordCampEvents(source)
        if (events.isEmpty()) WordCampState.Empty else WordCampState.Loaded(events)
    }.getOrDefault(WordCampState.Failed)

@Composable
private fun ContinueWatchingRail(
    items: List<WatchProgress>,
    resolvePoster: suspend (WatchProgress) -> String?,
    onPosterResolved: (String, String) -> Unit,
    onPlay: (Video, ContentSource) -> Unit,
    firstCardFocus: FocusRequester?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(items, key = { _, item -> item.videoGuid }) { index, item ->
            ContinueWatchingCard(
                progress = item,
                resolvePoster = resolvePoster,
                onPosterResolved = onPosterResolved,
                onClick = { onPlay(item.video, Sources.sourceWithId(item.sourceId)) },
                focusRequester = if (index == 0) firstCardFocus else null,
            )
        }
    }
}

@Composable
private fun WordCampRail(
    state: WordCampState,
    resolveCover: suspend (ContentEvent) -> String?,
    onOpenEvent: (ContentEvent) -> Unit,
    onRetry: () -> Unit,
    firstCardFocus: FocusRequester?,
) {
    when (state) {
        WordCampState.Loading -> RailPlaceholder { CircularProgressIndicator(color = Color.White) }
        WordCampState.Failed -> RailPlaceholder {
            MessageWithAction(
                message = stringResource(R.string.wordcamps_load_error),
                action = stringResource(R.string.retry),
                onAction = onRetry,
            )
        }
        WordCampState.Empty -> RailPlaceholder {
            Text(stringResource(R.string.no_wordcamps), color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
        }
        is WordCampState.Loaded -> WordCampRailContent(
            events = state.events,
            resolveCover = resolveCover,
            onOpenEvent = onOpenEvent,
            firstCardFocus = firstCardFocus,
        )
    }
}

@Composable
private fun WordCampRailContent(
    events: List<ContentEvent>,
    resolveCover: suspend (ContentEvent) -> String?,
    onOpenEvent: (ContentEvent) -> Unit,
    firstCardFocus: FocusRequester?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
            PortraitCampCard(
                event = event,
                resolveCover = resolveCover,
                onClick = { onOpenEvent(event) },
                modifier = Modifier.width(180.dp),
                focusRequester = if (index == 0) firstCardFocus else null,
            )
        }
    }
}

@Composable
private fun LatestRail(
    model: VideoFeedViewModel,
    source: ContentSource,
    onPlay: (Video, ContentSource) -> Unit,
    onAuthRequired: () -> Unit,
) {
    when (val state = model.state) {
        VideoFeedViewModel.State.Loading -> RailPlaceholder { CircularProgressIndicator(color = Color.White) }

        VideoFeedViewModel.State.Failed -> RailPlaceholder {
            MessageWithAction(
                message = stringResource(R.string.videos_load_error),
                action = stringResource(R.string.retry),
                onAction = { /* reselect Home to retry */ },
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
            Text(stringResource(R.string.no_videos), color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
        }

        is VideoFeedViewModel.State.Loaded -> VideoRail(
            videos = state.videos,
            source = source,
            resolvePoster = { model.posterUrl(it) },
            onPlay = onPlay,
        )
    }
}

/** A titled shelf: section header above its (horizontally scrolling) content. */
@Composable
private fun RailSection(title: String, content: @Composable () -> Unit) {
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
private fun RailPlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
