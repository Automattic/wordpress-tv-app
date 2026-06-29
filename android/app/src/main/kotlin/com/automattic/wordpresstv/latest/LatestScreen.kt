package com.automattic.wordpresstv.latest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text

private val PosterPlaceholder = Color(0xFF15151A)

/**
 * A focusable grid of Latest videos. Tapping a cell resolves playback and
 * presents the player full-screen. Mirrors the Apple `LatestView`.
 */
@Composable
fun LatestScreen(
    repository: ContentRepository,
    source: ContentSource,
    onAuthRequired: () -> Unit,
    onPlay: (PlaybackAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(source.id) { LatestViewModel(repository, source) }
    LaunchedEffect(source.id) { model.load() }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        when (val state = model.state) {
            LatestViewModel.State.Loading -> Centered {
                CircularProgressIndicator(color = Color.White)
            }

            LatestViewModel.State.Failed -> Centered {
                MessageWithAction(
                    message = stringResource(R.string.videos_load_error),
                    action = stringResource(R.string.retry),
                    onAction = { scope.launch { model.load() } },
                )
            }

            LatestViewModel.State.Empty -> Centered {
                Text(stringResource(R.string.no_videos), color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp)
            }

            LatestViewModel.State.NeedsAuth -> Centered {
                MessageWithAction(
                    message = stringResource(R.string.session_expired),
                    action = stringResource(R.string.sign_in),
                    onAction = onAuthRequired,
                )
            }

            is LatestViewModel.State.Loaded -> Grid(
                videos = state.videos,
                resolvePoster = { model.posterUrl(it) },
                onClick = { video ->
                    // Best-effort: if resolution fails the player just doesn't open.
                    scope.launch { runCatching { model.playbackAsset(video) }.getOrNull()?.let(onPlay) }
                },
            )
        }
    }
}

@Composable
private fun Grid(
    videos: List<Video>,
    resolvePoster: suspend (Video) -> String?,
    onClick: (Video) -> Unit,
) {
    // Android TV needs an element to hold focus before the D-pad can move; without
    // it, arrow keys do nothing. Focus the first card once the grid is laid out.
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
                onClick = { onClick(video) },
                modifier = if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier,
            )
        }
    }

    LaunchedEffect(videos.firstOrNull()?.id) {
        // Retry briefly: the first item may not be attached on the first frame.
        repeat(10) {
            if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    resolvePoster: suspend (Video) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve the poster lazily (private a8c.tv posters need a token appended), so
    // only on-screen cells request one.
    var posterUrl by remember(video.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(video.id) { posterUrl = resolvePoster(video) }

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(PosterPlaceholder),
            contentAlignment = Alignment.Center,
        ) {
            posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun MessageWithAction(message: String, action: String, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(action) }
    }
}
