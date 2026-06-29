package com.automattic.wordpresstv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import kotlinx.coroutines.delay
import androidx.tv.material3.Text

private const val SEEK_STEP_MS = 10_000L
private const val AUTO_HIDE_MS = 4_000L

/**
 * Full-screen player with **custom Compose-for-TV controls** (the stock Media3
 * `PlayerView` controller is phone-oriented and looks dated on a 10-foot screen).
 * `:core` resolved the [PlaybackAsset.url]; ExoPlayer plays it, and a Compose
 * overlay draws the transport: title, big play/pause, a scrubber, and times.
 *
 * One focus target (the whole surface). When the controls are showing:
 * Center/Enter toggles play-pause, Left/Right seek ∓10s. Any key wakes the
 * controls; they auto-hide after a few seconds while playing. Back exits.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(asset: PlaybackAsset, onClose: () -> Unit) {
    val context = LocalContext.current

    val player = remember(asset.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(asset.url))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var wakeTick by remember { mutableIntStateOf(0) }

    fun wake() {
        controlsVisible = true
        wakeTick++
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(target)
        position = target
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Poll position/buffered while on screen (ExoPlayer doesn't push these).
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            val d = player.duration
            if (d > 0) duration = d
            delay(500)
        }
    }

    // Auto-hide the controls after inactivity — but only while playing.
    LaunchedEffect(controlsVisible, isPlaying, wakeTick) {
        if (controlsVisible && isPlaying) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    BackHandler(onBack = onClose)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // First press just wakes the controls; subsequent presses act.
                val wasVisible = controlsVisible
                wake()
                if (!wasVisible) return@onKeyEvent true
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        togglePlay(); true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> { seekBy(-SEEK_STEP_MS); true }
                    Key.DirectionRight, Key.MediaFastForward -> { seekBy(SEEK_STEP_MS); true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // we draw our own controls
                    isFocusable = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
        )

        if (controlsVisible) {
            Controls(
                title = asset.title,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                buffered = buffered,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun Controls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    modifier: Modifier = Modifier,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
            .padding(start = 56.dp, end = 56.dp, top = 96.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PlayPauseIcon(isPlaying = isPlaying, modifier = Modifier.size(40.dp))
            Text(formatTime(position), color = Color.White, fontSize = 16.sp)
            Scrubber(
                fraction = fraction,
                bufferedFraction = bufferedFraction,
                modifier = Modifier.weight(1f).height(24.dp),
            )
            Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        }
    }
}

/** Track + buffered + played + thumb, drawn so it reads from across the room. */
@Composable
private fun Scrubber(fraction: Float, bufferedFraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        val thickness = 6.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, y), Offset(size.width, y), thickness, StrokeCap.Round)
        if (bufferedFraction > 0f) {
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(0f, y),
                Offset(size.width * bufferedFraction.coerceIn(0f, 1f), y),
                thickness,
                StrokeCap.Round,
            )
        }
        val playedX = size.width * fraction.coerceIn(0f, 1f)
        drawLine(Color.White, Offset(0f, y), Offset(playedX, y), thickness, StrokeCap.Round)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(playedX, y))
    }
}

/** Dependency-free play/pause glyph. */
@Composable
private fun PlayPauseIcon(isPlaying: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.24f
            val gap = w * 0.18f
            val top = h * 0.1f
            val barH = h * 0.8f
            val radius = CornerRadius(barW * 0.3f, barW * 0.3f)
            drawRoundRect(Color.White, Offset(w / 2f - gap / 2f - barW, top), Size(barW, barH), radius)
            drawRoundRect(Color.White, Offset(w / 2f + gap / 2f, top), Size(barW, barH), radius)
        } else {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.1f)
                lineTo(w * 0.22f, h * 0.9f)
                lineTo(w * 0.86f, h * 0.5f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
