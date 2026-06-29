package com.automattic.wordpresstv.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import kotlinx.coroutines.delay
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/**
 * The sign-in screen. The TV asks the broker for a pairing session, renders the
 * `qr_url` as a QR, and polls in the background; when the phone finishes signing
 * in the broker hands back the token and we dismiss. Mirrors the Apple
 * `PairingView`: brand on the left, a white QR card on the right.
 */
@Composable
fun PairingScreen(
    broker: BrokerClient,
    onAuthorized: (BrokerClient.PairingResult) -> Unit,
    onCancel: () -> Unit,
) {
    val model = remember(broker) { PairingViewModel(broker) }
    var retryToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryToken) { model.run() }
    BackHandler(onBack = onCancel)

    val state = model.state
    val latestOnAuthorized by rememberUpdatedState(onAuthorized)
    LaunchedEffect(state) {
        if (state is PairingViewModel.State.Success) {
            delay(800) // let the checkmark land
            latestOnAuthorized(state.result)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(80.dp),
        ) {
            Brand(Modifier.weight(1f))
            QrPanel(state = state, onRetry = { retryToken++ }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Brand(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = stringResource(R.string.pairing_headline),
            color = Color.White,
            fontSize = 42.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.pairing_subhead),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun QrPanel(
    state: PairingViewModel.State,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(380.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is PairingViewModel.State.Creating ->
                    CircularProgressIndicator(color = Color.Black)

                is PairingViewModel.State.Showing -> {
                    val image = remember(state.qrUrl) { qrBitmap(state.qrUrl).asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        filterQuality = FilterQuality.None, // crisp modules when scaled
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                    )
                }

                is PairingViewModel.State.Success -> Success()

                is PairingViewModel.State.Failed -> Failed(state.failure, onRetry)
            }
        }
        Status(state)
    }
}

@Composable
private fun Success() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("✓", color = Color(0xFF2E7D32), fontSize = 110.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.pairing_signed_in), color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Failed(failure: PairingViewModel.Failure, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { retryFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }
    Column(
        modifier = Modifier.padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(failure.messageRes()),
            color = Color.Black,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
            Text(stringResource(R.string.pairing_try_again))
        }
    }
}

@Composable
private fun Status(state: PairingViewModel.State) {
    when (state) {
        is PairingViewModel.State.Showing ->
            Text(stringResource(R.string.pairing_waiting), color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
        is PairingViewModel.State.Success ->
            Text(stringResource(R.string.pairing_signing_in), color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
        else ->
            // Keep the column height stable across states.
            Box(Modifier.height(30.dp))
    }
}

private fun PairingViewModel.Failure.messageRes(): Int = when (this) {
    PairingViewModel.Failure.BrokerUnreachable -> R.string.broker_unreachable
    PairingViewModel.Failure.OAuthDenied -> R.string.sign_in_cancelled
    PairingViewModel.Failure.TokenExchange -> R.string.sign_in_incomplete
    PairingViewModel.Failure.Unknown -> R.string.sign_in_error
}
