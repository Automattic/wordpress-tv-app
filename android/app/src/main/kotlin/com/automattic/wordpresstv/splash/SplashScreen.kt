package com.automattic.wordpresstv.splash

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.automattic.wordpresstv.R
import kotlinx.coroutines.delay

/**
 * Plays the bundled intro clip full-screen on cold launch, then calls
 * [onFinished]. Auto-advances when the clip ends; a safety timeout and a remote
 * Back press both guarantee we never strand the user on the splash. Mirrors the
 * Apple `SplashView`.
 */
@OptIn(UnstableApi::class)
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            latestOnFinished()
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = "android.resource://${context.packageName}/${R.raw.intro}".toUri()
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // The splash owns the only player, so STATE_ENDED is unambiguous.
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Belt-and-suspenders: advance even if the end signal never fires (~5s clip).
    LaunchedEffect(Unit) {
        delay(8_000)
        finish()
    }

    // Menu/Back press skips straight into the app.
    BackHandler(onBack = ::finish)

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // aspect-fill
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
    )
}
