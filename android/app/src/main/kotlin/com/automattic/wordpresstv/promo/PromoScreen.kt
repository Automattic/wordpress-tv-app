package com.automattic.wordpresstv.promo

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.auth.qrBitmap
import com.automattic.wordpresstv.ui.theme.BrandBlue
import kotlinx.coroutines.delay
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

/**
 * The full-screen "Code for the People" detail, opened from the Home hero. The
 * film's key art anchors the right as a poster (its title treatment carries the
 * name); the synopsis and credit sit on the left. Watching offers a clear choice
 * of where — "Watch on YouTube" or "Watch on your phone" (a QR). We always show
 * both. If the YouTube hand-off can't open (no app to handle it), we toast — not
 * divert to the QR, which stays one tap away behind "Watch on your phone".
 * Dismissed with Back. Mirrors the Apple `PromoView`.
 */
@Composable
fun PromoScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    // The QR replaces the two buttons after "Watch on your phone"; Back returns.
    var showQr by remember { mutableStateOf(false) }

    BackHandler { if (showQr) showQr = false else onClose() }

    // Land focus on the primary control for the current view, and re-claim it when
    // the view changes, so the overlay always holds focus (Home is behind it).
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(showQr) {
        repeat(12) {
            if (runCatching { actionFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0B1122), Color(0xFF060609))),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(52.dp),
        ) {
            // Synopsis + credit. The film's name lives in the key art, so the copy
            // leads with the tagline rather than repeating the title.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FeaturedBadge()
                Text(
                    text = CodeForThePeople.TAGLINE,
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = CodeForThePeople.BLURB,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Text(
                    text = CodeForThePeople.CREDIT,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Key art poster + the watch controls. The poster takes weight, so it
            // fills whatever height the controls leave.
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.code_for_the_people),
                        contentDescription = CodeForThePeople.TITLE,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (showQr) {
                    Text(
                        text = stringResource(R.string.promo_scan_hint),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    BrandedQr(148.dp)
                    Text(
                        text = CodeForThePeople.SHARE_LABEL,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.promo_back),
                        modifier = Modifier.trap().focusRequester(actionFocus),
                        onClick = { showQr = false },
                    )
                } else {
                    // Pick where to watch. If no app can open YouTube, toast (like
                    // the system's "no app can do this") rather than diverting to QR.
                    Button(
                        onClick = {
                            if (!openInYouTube(context)) {
                                Toast.makeText(context, R.string.promo_youtube_unavailable, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .trap(keepDown = true)
                            .focusRequester(actionFocus),
                    ) { Text(stringResource(R.string.promo_open_youtube), fontSize = 18.sp) }
                    SecondaryButton(
                        text = stringResource(R.string.promo_watch_phone),
                        modifier = Modifier.fillMaxWidth().trap(keepUp = true),
                        onClick = { showQr = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
        ),
    ) { Text(text, fontSize = 18.sp) }
}

/** A white QR card for [CodeForThePeople.SHARE_URL] with a brand play badge at its
 *  center. The QR uses error-correction "H", so the badge can overlap without
 *  breaking scannability. */
@Composable
private fun BrandedQr(size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.08f)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val qr = remember { qrBitmap(CodeForThePeople.SHARE_URL).asImageBitmap() }
        Image(
            bitmap = qr,
            contentDescription = null,
            filterQuality = FilterQuality.None, // crisp modules when scaled
            modifier = Modifier.fillMaxSize().padding(size * 0.06f),
        )
        Box(
            modifier = Modifier.size(size * 0.26f).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(size * 0.2f).clip(CircleShape).background(BrandBlue),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(size * 0.08f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val path = Path()
                    path.moveTo(w * 0.16f, 0f)
                    path.lineTo(w * 0.16f, h)
                    path.lineTo(w, h / 2f)
                    path.close()
                    drawPath(path, Color.White)
                }
            }
        }
    }
}

/** The small brand-blue "FEATURED" pill used on the Home hero and this screen. */
@Composable
internal fun FeaturedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BrandBlue)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(R.string.promo_badge),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
        )
    }
}

/**
 * Contain D-pad focus within the modal. The promo is drawn over Home, whose cards
 * are still in the focus graph; cancelling the exits keeps focus from escaping to
 * a now-hidden card. [up]/[down] stay open where a sibling control sits that way
 * (so the two watch buttons still reach each other).
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.trap(keepUp: Boolean = false, keepDown: Boolean = false): Modifier = focusProperties {
    left = FocusRequester.Cancel
    right = FocusRequester.Cancel
    if (!keepUp) up = FocusRequester.Cancel
    if (!keepDown) down = FocusRequester.Cancel
}
