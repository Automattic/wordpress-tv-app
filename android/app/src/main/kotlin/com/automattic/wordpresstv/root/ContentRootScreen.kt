package com.automattic.wordpresstv.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.auth.AuthManager
import com.automattic.wordpresstv.auth.PairingScreen
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.Account
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.latest.LatestScreen
import com.automattic.wordpresstv.player.PlayerScreen
import com.automattic.wordpresstv.ui.theme.BrandBlue
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

/**
 * The app's home after the splash. Shows the public WordPress.tv grid out of the
 * box — no account needed — with a "Sign in" affordance in the top bar.
 *
 * Signing in is plain WordPress.com OAuth (the QR pairing flow). Once a token
 * lands, an Automattician gets the private a8c.tv source revealed and selected;
 * anyone else stays on WordPress.tv, signed in, with no a8c.tv entry point shown.
 * Mirrors the Apple `ContentRootView`.
 */
@Composable
fun ContentRootScreen(repository: ContentRepository, auth: AuthManager) {
    var selected by remember { mutableStateOf(Sources.wordpressTV) }
    var showPairing by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    // Hoisted here (not inside LatestScreen) so the player overlays the whole
    // screen — source bar included — like the tvOS `fullScreenCover`.
    var playing by remember { mutableStateOf<PlaybackAsset?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            SourceBar(
                selected = selected,
                visibleSources = Sources.all.filter { it.auth == ContentSource.Auth.NONE || auth.isAuthorizedForA8C },
                isAuthenticated = auth.isAuthenticated,
                account = auth.account,
                onSelect = { selected = it },
                onSignIn = { showPairing = true },
                onAccount = { showAccountDialog = true },
            )

            // Recreate the grid when the source — or auth state — changes, so
            // signing in/out triggers a fresh load. The weight lives on the
            // wrapper Box (ColumnScope); `key {}` resets the screen's state.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(selected.id, auth.isAuthenticated) {
                    LatestScreen(
                        repository = repository,
                        source = selected,
                        onAuthRequired = {
                            // A 401/403 surfaced (an a8c.tv session expired) —
                            // clear the stale token and re-pair.
                            auth.signOut()
                            selected = Sources.wordpressTV
                            showPairing = true
                        },
                        onPlay = { playing = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Full-screen player overlay (covers the source bar), dismissed with Back.
        playing?.let { asset ->
            PlayerScreen(asset = asset, onClose = { playing = null })
        }

        if (showPairing) {
            PairingScreen(
                broker = auth.broker,
                onAuthorized = { result ->
                    auth.signIn(result)
                    // Reveal and jump to a8c.tv only for an Automattician; everyone
                    // else lands back on WordPress.tv, signed in.
                    if (auth.isAuthorizedForA8C) selected = Sources.a8cTV
                    showPairing = false
                },
                onCancel = {
                    showPairing = false
                    if (!auth.isAuthorizedForA8C) selected = Sources.wordpressTV
                },
            )
        }

        if (showAccountDialog) {
            AccountDialog(
                account = auth.account,
                onLogOut = {
                    showAccountDialog = false
                    auth.signOut()
                    selected = Sources.wordpressTV
                },
                onDismiss = { showAccountDialog = false },
            )
        }
    }
}

@Composable
private fun SourceBar(
    selected: ContentSource,
    visibleSources: List<ContentSource>,
    isAuthenticated: Boolean,
    account: Account?,
    onSelect: (ContentSource) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp)
            .padding(top = 40.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("WordPress TV", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f))

        visibleSources.forEach { source ->
            SourcePill(source = source, isSelected = source.id == selected.id, onClick = { onSelect(source) })
        }

        if (isAuthenticated) {
            Button(onClick = onAccount, colors = unselectedColors()) { Avatar(account) }
        } else {
            Button(onClick = onSignIn, colors = unselectedColors()) { Text(stringResource(R.string.sign_in)) }
        }
    }
}

@Composable
private fun SourcePill(source: ContentSource, isSelected: Boolean, onClick: () -> Unit) {
    // A persistent brand-blue selected pill vs. a faint translucent one stays
    // unambiguous wherever focus sits (the TV focus highlight brightens the
    // focused pill, so a white "selected" tint would be indistinguishable).
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) BrandBlue else Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
        ),
    ) {
        Text(source.displayName, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun unselectedColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.16f),
    contentColor = Color.White,
)

@Composable
private fun Avatar(account: Account?) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(BrandBlue),
        contentAlignment = Alignment.Center,
    ) {
        val url = account?.avatarUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.account),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Text((account?.displayName?.take(1) ?: "W").uppercase(), color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun AccountDialog(account: Account?, onLogOut: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    // Move focus into the dialog so the D-pad can reach Log out / Cancel —
    // otherwise focus stays on the grid behind it and the dialog is unreachable.
    val logOutFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { logOutFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(30)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1E1E))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(account?.displayName ?: stringResource(R.string.account), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Button(onClick = onLogOut, modifier = Modifier.focusRequester(logOutFocus)) { Text(stringResource(R.string.log_out)) }
            Button(onClick = onDismiss, colors = unselectedColors()) { Text(stringResource(R.string.cancel)) }
        }
    }
}
