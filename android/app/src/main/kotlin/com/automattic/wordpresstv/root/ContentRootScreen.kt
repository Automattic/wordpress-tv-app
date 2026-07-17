package com.automattic.wordpresstv.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.automattic.wordpresstv.BuildConfig
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.auth.AuthManager
import com.automattic.wordpresstv.auth.PairingScreen
import com.automattic.wordpresstv.catalog.Catalog
import com.automattic.wordpresstv.catalog.NavCategory
import com.automattic.wordpresstv.continuewatching.WatchProgressStore
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.Account
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.feed.VideoGrid
import com.automattic.wordpresstv.feed.VideoQuery
import com.automattic.wordpresstv.home.HomeScreen
import com.automattic.wordpresstv.player.PlaybackRequest
import com.automattic.wordpresstv.player.PlayerScreen
import com.automattic.wordpresstv.promo.PromoScreen
import com.automattic.wordpresstv.search.SearchScreen
import com.automattic.wordpresstv.settings.ContentLanguageSelection
import com.automattic.wordpresstv.settings.SettingsScreen
import com.automattic.wordpresstv.ui.WordPressMark
import com.automattic.wordpresstv.ui.theme.BrandBlue
import com.automattic.wordpresstv.wordcamps.WordCampsScreen
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The app shell: a persistent top nav (the design's pill bar) over a body that
 * swaps between the railed Home, a category grid, WordCamp event drill-ins, and
 * search. Playback is hoisted here so any screen can request it through one
 * [play] path — which also wires the resume position and progress recording.
 *
 * WordPress.tv is public and drives the whole visible nav. The private a8c.tv
 * source stays available to signed-in Automatticians as an extra trailing tab,
 * preserving the employee flow without intruding on the public design. Mirrors
 * the Apple `ContentRootView`.
 */
@Composable
fun ContentRootScreen(
    repository: ContentRepository,
    auth: AuthManager,
    store: WatchProgressStore,
    contentLanguageSelection: ContentLanguageSelection,
    onContentLanguageSelectionChange: (ContentLanguageSelection) -> Unit,
) {
    var selected by remember { mutableStateOf<Section>(Section.Home) }
    var showPairing by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // The Code for the People documentary promo, opened from the Home hero.
    var showPromo by remember { mutableStateOf(false) }
    // Hoisted here (not inside a screen) so the player overlays the whole screen —
    // nav bar included — like the tvOS `fullScreenCover`, and every screen plays
    // through one path.
    var playing by remember { mutableStateOf<PlaybackRequest?>(null) }
    val scope = rememberCoroutineScope()

    /**
     * Resolve a tapped video to a playable asset, wire its resume point, and
     * present the player. Best-effort: if resolution fails the player just doesn't
     * open.
     */
    fun play(video: Video, source: ContentSource) {
        scope.launch {
            val asset = runCatching { repository.resolvePlayback(source, video) }.getOrNull() ?: return@launch
            val posterUrl = video.posterUrl ?: runCatching { repository.posterUrl(source, video) }.getOrNull()
            val playableVideo = if (posterUrl != video.posterUrl) video.copy(posterUrl = posterUrl) else video
            val resumeMs = store.progress(video.videoGuid)?.positionMs ?: 0L
            playing = PlaybackRequest(asset = asset, video = playableVideo, resumeAtMs = resumeMs)
        }
    }

    /**
     * A WordCamp card's cover: the newest video's poster in that event.
     * Best-effort — the card keeps its brand gradient if this fails.
     */
    suspend fun eventCover(event: ContentEvent): String? =
        runCatching {
            val video = repository.listByEvent(
                source = Sources.wordpressTV,
                event = event,
                page = 1,
                applyLanguageFilter = false,
            ).firstOrNull()
                ?: return@runCatching null
            repository.posterUrl(Sources.wordpressTV, video)
        }.getOrNull()

    fun routeToPairing() {
        // A grid reported a 401/403 (an a8c.tv session expired) — clear the stale
        // token and re-pair.
        auth.signOut()
        selected = Section.Home
        showPairing = true
    }

    // Back returns to Home from any sub-section rather than exiting the app
    // (the player and dialogs handle their own Back).
    BackHandler(enabled = selected != Section.Home && playing == null && !showPairing && !showAccountDialog && !showSettings && !showPromo) {
        selected = Section.Home
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!showSettings) {
            Column(Modifier.fillMaxSize()) {
                NavBar(
                    selected = selected,
                    showA8c = auth.isAuthorizedForA8C,
                    isAuthenticated = auth.isAuthenticated,
                    account = auth.account,
                    onSelect = { selected = it },
                    onSignIn = { showPairing = true },
                    onAccount = { showAccountDialog = true },
                    onSettings = { showSettings = true },
                )

                // Recreate the body when the selection — or auth state — changes, so
                // signing in/out reloads private content.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(sectionKey(selected), auth.isAuthenticated, repository) {
                        Body(
                            section = selected,
                            repository = repository,
                            store = store,
                            onPlay = ::play,
                            onOpenEvent = { selected = Section.WordCamp(it) },
                            resolveCover = ::eventCover,
                            onAuthRequired = ::routeToPairing,
                            onOpenPromo = { showPromo = true },
                            promoOpen = showPromo,
                        )
                    }
                }
            }
        }

        // Full-screen player overlay (covers the nav bar), dismissed with Back.
        playing?.let { request ->
            PlayerScreen(request = request, store = store, onClose = { playing = null })
        }

        // Full-screen documentary promo overlay, dismissed with Back or Close.
        if (showPromo) {
            PromoScreen(onClose = { showPromo = false })
        }

        if (showPairing) {
            PairingScreen(
                broker = auth.broker,
                onAuthorized = { result ->
                    auth.signIn(result)
                    // Reveal and jump to a8c.tv only for an Automattician; everyone
                    // else lands back on Home, signed in.
                    if (auth.isAuthorizedForA8C) selected = Section.A8c
                    showPairing = false
                },
                onCancel = {
                    showPairing = false
                    if (!auth.isAuthorizedForA8C) selected = Section.Home
                },
            )
        }

        if (showAccountDialog) {
            AccountDialog(
                account = auth.account,
                onLogOut = {
                    showAccountDialog = false
                    auth.signOut()
                    selected = Section.Home
                },
                onDismiss = { showAccountDialog = false },
            )
        }

        if (showSettings) {
            SettingsScreen(
                repository = repository,
                languageSelection = contentLanguageSelection,
                onLanguageSelectionChange = onContentLanguageSelectionChange,
                onDismiss = { showSettings = false },
            )
        }
    }
}

/** A destination in the top nav (plus the WordCamp drill-in, which no pill selects). */
sealed interface Section {
    data object Home : Section
    data class Category(val category: NavCategory) : Section
    data class WordCamp(val event: ContentEvent) : Section
    data object Search : Section
    data object A8c : Section
}

/** Stable string for `key(...)` — associated values make `Section` awkward to key directly. */
private fun sectionKey(section: Section): String = when (section) {
    Section.Home -> "home"
    is Section.Category -> "cat-${section.category.slug}"
    is Section.WordCamp -> "event-${section.event.slug}"
    Section.Search -> "search"
    Section.A8c -> "a8c"
}

@Composable
private fun Body(
    section: Section,
    repository: ContentRepository,
    store: WatchProgressStore,
    onPlay: (Video, ContentSource) -> Unit,
    onOpenEvent: (ContentEvent) -> Unit,
    resolveCover: suspend (ContentEvent) -> String?,
    onAuthRequired: () -> Unit,
    onOpenPromo: () -> Unit,
    promoOpen: Boolean,
) {
    when (section) {
        Section.Home -> HomeScreen(
            repository = repository,
            source = Sources.wordpressTV,
            store = store,
            onPlay = onPlay,
            onOpenEvent = onOpenEvent,
            resolveCover = resolveCover,
            onAuthRequired = onAuthRequired,
            onOpenPromo = onOpenPromo,
            promoOpen = promoOpen,
        )

        is Section.Category -> if (section.category.slug == Catalog.wordCampsSlug) {
            WordCampsScreen(
                repository = repository,
                source = Sources.wordpressTV,
                onPlay = onPlay,
            )
        } else {
            VideoGrid(
                repository = repository,
                source = Sources.wordpressTV,
                query = VideoQuery.Category(section.category.ref),
                onPlay = onPlay,
                onAuthRequired = onAuthRequired,
            )
        }

        is Section.WordCamp -> Column(Modifier.fillMaxSize()) {
            Text(
                text = section.event.name,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 56.dp, vertical = 16.dp),
            )
            VideoGrid(
                repository = repository,
                source = Sources.wordpressTV,
                query = VideoQuery.Event(section.event),
                onPlay = onPlay,
                onAuthRequired = onAuthRequired,
                modifier = Modifier.weight(1f),
            )
        }

        Section.Search -> SearchScreen(
            repository = repository,
            source = Sources.wordpressTV,
            onPlay = onPlay,
        )

        Section.A8c -> VideoGrid(
            repository = repository,
            source = Sources.a8cTV,
            query = VideoQuery.Latest,
            onPlay = onPlay,
            onAuthRequired = onAuthRequired,
        )
    }
}

/**
 * The design's top bar: the WordPress mark, one translucent capsule holding the
 * section tabs and search, and the account control — laid out edge to edge with
 * the capsule centered.
 */
@Composable
private fun NavBar(
    selected: Section,
    showA8c: Boolean,
    isAuthenticated: Boolean,
    account: Account?,
    onSelect: (Section) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(horizontal = 56.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        WordPressMark(Modifier.size(42.dp))

        Box(Modifier.weight(1f))

        NavCapsule(selected = selected, showA8c = showA8c, onSelect = onSelect)

        Box(Modifier.weight(1f))

        NavIconButton(onClick = onSettings) {
            SettingsIcon(Modifier.size(22.dp))
        }

        if (isAuthenticated) {
            NavAvatarButton(account = account, onClick = onAccount)
        } else if (BuildConfig.DEBUG) {
            // Sign-in relies on the QR pairing backend, which isn't ready yet, so
            // the entry point is limited to debug builds until it ships.
            NavTab(selected = false, onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
        }
    }
}

@Composable
private fun NavIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White.copy(alpha = 0.72f),
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun SettingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension * 0.42f
        val inner = size.minDimension * 0.16f

        for (i in 0 until 8) {
            val angle = (Math.PI * 2.0 * i / 8.0).toFloat()
            val start = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(angle) * outer * 0.78f,
                y = center.y + kotlin.math.sin(angle) * outer * 0.78f,
            )
            val end = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(angle) * outer,
                y = center.y + kotlin.math.sin(angle) * outer,
            )
            drawLine(Color.White, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
        }

        drawCircle(Color.White, radius = outer * 0.72f, center = center, style = Stroke(width = stroke))
        drawCircle(Color.White, radius = inner, center = center, style = Stroke(width = stroke))
    }
}

/**
 * The single pill from the mock: text tabs (the selected one a solid blue pill)
 * plus a trailing search glyph, all inside one translucent capsule.
 */
@Composable
private fun NavCapsule(selected: Section, showA8c: Boolean, onSelect: (Section) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavTab(selected = selected == Section.Home, onClick = { onSelect(Section.Home) }) {
            Text(stringResource(R.string.home))
        }
        Catalog.categories.forEach { category ->
            NavTab(
                selected = selected == Section.Category(category),
                onClick = { onSelect(Section.Category(category)) },
            ) { Text(category.title) }
        }
        if (showA8c) {
            NavTab(selected = selected == Section.A8c, onClick = { onSelect(Section.A8c) }) {
                Text(Sources.a8cTV.displayName)
            }
        }
        NavTab(selected = selected == Section.Search, onClick = { onSelect(Section.Search) }) {
            com.automattic.wordpresstv.search.SearchIcon(Modifier.size(20.dp))
        }
    }
}

/**
 * One tab inside the capsule: a rounded pill that is brand-blue when selected, a
 * subtle translucent fill when focused, and transparent otherwise. The focus
 * highlight is contained (no scale lift) so the capsule reads as one control.
 */
@Composable
private fun NavTab(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) BrandBlue else Color.Transparent,
            focusedContainerColor = if (selected) BrandBlue else Color.White.copy(alpha = 0.22f),
            pressedContainerColor = if (selected) BrandBlue else Color.White.copy(alpha = 0.22f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { content() }
    }
}

/** The Gravatar tab: a round, focusable control that opens the account dialog. */
@Composable
private fun NavAvatarButton(account: Account?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
        ),
    ) {
        Avatar(account, Modifier.padding(4.dp))
    }
}

@Composable
private fun Avatar(account: Account?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(BrandBlue),
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
    // otherwise focus stays on the body behind it and the dialog is unreachable.
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ),
            ) { Text(stringResource(R.string.cancel)) }
        }
    }
}
