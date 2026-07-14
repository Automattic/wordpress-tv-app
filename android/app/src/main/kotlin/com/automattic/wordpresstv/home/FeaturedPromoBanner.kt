package com.automattic.wordpresstv.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.promo.CodeForThePeople
import com.automattic.wordpresstv.promo.FeaturedBadge
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text

/** The dark, faintly-branded ground the still fades into on its left. */
private val PromoNavy = Color(0xFF0B1636)

/**
 * The Home hero: a full-width featured banner for the "Code for the People"
 * documentary, above the rails. A cinematic still bleeds full-height to the right
 * edge and dissolves into [PromoNavy] on the left, so our own title/tagline read
 * over the dark side. NOTE: the intended art is a title-free still — the film's
 * title lives in the copy here, not baked into the image. The whole card is one
 * focusable control that opens [com.automattic.wordpresstv.promo.PromoScreen].
 * Mirrors the Apple `FeaturedPromoBanner`.
 */
@Composable
fun FeaturedPromoBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(190.dp),
        scale = CardDefaults.scale(focusedScale = 1.02f),
    ) {
        Box(Modifier.fillMaxSize().background(PromoNavy)) {
            Image(
                painter = painterResource(R.drawable.code_for_the_people),
                contentDescription = CodeForThePeople.TITLE,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.64f),
            )

            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0.0f to PromoNavy,
                        0.36f to PromoNavy,
                        0.66f to Color.Transparent,
                    ),
                ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.48f)
                    .padding(start = 36.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The film's title treatment lives in the art on the right, so the
                // copy here doesn't repeat it — a short synopsis and the Watch cue.
                FeaturedBadge()
                Text(
                    text = stringResource(R.string.promo_home_synopsis),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlayBadge()
                    Text(
                        text = stringResource(R.string.promo_home_cta),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** A small circular play affordance next to the call to action. */
@Composable
private fun PlayBadge() {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.12f, 0f)
                lineTo(size.width * 0.12f, size.height)
                lineTo(size.width, size.height / 2f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
