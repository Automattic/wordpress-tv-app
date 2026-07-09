package com.automattic.wordpresstv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.catalog.wordCampCardColors
import com.automattic.wordpresstv.catalog.wordCampDisplayPlace
import com.automattic.wordpresstv.continuewatching.WatchProgress
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.ui.theme.BrandBlue
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text

/**
 * The card vocabulary shared across Home, category grids, and search: a poster
 * primitive plus the three card shapes in the design — landscape video card,
 * portrait WordCamp event card, and the wide Continue Watching card with a
 * resume bar. Mirrors the Apple `Cards.swift`.
 */

private val PosterPlaceholder = Color(0xFF15151A)

/** A poster image over a branded placeholder, shown while it loads or if absent. */
@Composable
fun PosterBox(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(PosterPlaceholder), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Landscape poster + title. Used in every horizontal rail and grid. The poster
 * URL resolves lazily (private a8c.tv posters need a token appended), so only
 * on-screen cards fetch one. The TV [Card] gives the focus lift for free.
 *
 * [modifier] sizes the card (a fixed width in rails, the cell width in grids);
 * [focusRequester], when set, lands initial focus on this card so the D-pad works.
 */
@Composable
fun VideoCard(
    video: Video,
    resolvePoster: suspend (Video) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var posterUrl by remember(video.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(video.id) { posterUrl = resolvePoster(video) }

    Column(modifier) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().focusRequesterOrNone(focusRequester)) {
            PosterBox(posterUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Portrait WordCamp event card. A real cover image from the event's latest
 * video sits behind a brand-tinted scrim, with the WordPress mark and event name
 * over it — a populated poster rather than a flat colour. Falls back to the brand
 * gradient until (or if) the cover resolves. Opens the event's videos.
 */
@Composable
fun PortraitCampCard(
    event: ContentEvent,
    resolveCover: suspend (ContentEvent) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var coverUrl by remember(event.slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(event.slug) { coverUrl = resolveCover(event) }
    val colors = event.wordCampCardColors

    Column(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(230.dp).focusRequesterOrNone(focusRequester),
            scale = CardDefaults.scale(focusedScale = 1.03f),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Brand gradient — the base, and the fallback if no cover.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))

                // Real cover image, sized to the card and cropped.
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Full-card scrim: a light top darkening for cohesion, deepening
                // into the brand colour at the bottom so the cover reads as this
                // camp and the title stays legible over any art.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.1f),
                                colors.last().copy(alpha = 0.98f),
                            ),
                        ),
                    ),
                )

                WordPressMark(
                    modifier = Modifier.align(Alignment.TopStart).padding(18.dp).size(28.dp),
                )

                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text("WordCamp", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(event.wordCampDisplayPlace, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(event.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Wide Continue Watching card: poster with a resume bar showing how far in the
 * viewer got. Tapping resumes from [WatchProgress.positionMs].
 */
@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    resolvePoster: suspend (WatchProgress) -> String?,
    onPosterResolved: (String, String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var posterUrl by remember(progress.videoGuid) { mutableStateOf(progress.posterUrl) }
    LaunchedEffect(progress.videoGuid, progress.posterUrl) {
        posterUrl = progress.posterUrl
        if (posterUrl == null) {
            val resolved = resolvePoster(progress)
            if (resolved != null) {
                posterUrl = resolved
                onPosterResolved(progress.videoGuid, resolved)
            }
        }
    }

    Column(modifier) {
        Card(onClick = onClick, modifier = Modifier.width(320.dp).focusRequesterOrNone(focusRequester)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                PosterBox(posterUrl, Modifier.fillMaxSize())
                ResumeBar(
                    fraction = progress.fractionComplete.toFloat(),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = progress.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A thin rounded resume indicator: full-width track, brand-blue fill. */
@Composable
private fun ResumeBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.5f))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(BrandBlue),
        )
    }
}

/**
 * The official WordPress logo mark, used across the nav bar and WordCamp cards.
 * Rendered from the bundled vector so it tints to its context and stays crisp at
 * any size.
 */
@Composable
fun WordPressMark(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Image(
        painter = painterResource(R.drawable.ic_wordpress_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/** Apply [focusRequester] when present; otherwise leave the modifier untouched. */
private fun Modifier.focusRequesterOrNone(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) this.focusRequester(focusRequester) else this
