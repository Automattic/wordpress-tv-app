package com.automattic.wordpresstv.catalog

import androidx.compose.ui.graphics.Color
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentSource

/**
 * The app's curated browse structure: the top-nav categories and the flagship
 * WordCamps shelf. Both are deliberately hand-picked rather than pulled from the
 * site's 400+ raw taxonomy terms — WordPress.tv exposes everything as flat
 * categories, so the app decides which few belong in the primary navigation and
 * which events are "flagship". Each entry maps to a real WordPress.tv category
 * slug, so the content behind it is live, not stubbed. Mirrors the Apple `Catalog`.
 */
object Catalog {

    /**
     * The content-category tabs in the top nav (the mock's Home / WordCamps /
     * Meetups / Education / How To). `Home` is handled separately as the railed
     * landing screen; these are the flat-grid browse destinations.
     */
    val categories: List<NavCategory> = listOf(
        NavCategory(title = "WordCamps", slug = "wordcamptv"),
        NavCategory(title = "Meetups", slug = "wordpress-meetup"),
        NavCategory(title = "Education", slug = "learn-wordpress"),
        NavCategory(title = "How To", slug = "how-to"),
    )

    /**
     * The "Flagship WordCamps" shelf on Home. Portrait cards, one per flagship
     * event, each opening that camp's videos. WordPress.tv has no key-art per
     * event, so the card art is an app-provided brand gradient + wordmark (with
     * the camp's newest poster layered behind it when it resolves).
     */
    val flagshipCamps: List<FlagshipCamp> = listOf(
        FlagshipCamp(title = "WordCamp Asia", slug = "asia", colors = listOf(Color(0xFF4B2FBF), Color(0xFF2A1170))),
        FlagshipCamp(title = "WordCamp Europe", slug = "europe", colors = listOf(Color(0xFF0E2A6B), Color(0xFF081536))),
        FlagshipCamp(title = "WordCamp Canada", slug = "canada", colors = listOf(Color(0xFF0F5C4E), Color(0xFF06302A))),
        FlagshipCamp(title = "WordCamp US", slug = "us", colors = listOf(Color(0xFFB0325A), Color(0xFF5A1030))),
    )
}

/** A top-nav content category backed by a real WordPress.tv category slug. */
data class NavCategory(val title: String, val slug: String) {
    /** The [CategoryRef] the repository's `listByCategory` expects. */
    val ref: CategoryRef get() = CategoryRef(id = slug, name = title, slug = slug)
}

/** A curated flagship WordCamp on the Home shelf. */
data class FlagshipCamp(
    val title: String,
    val slug: String,
    /** Top-to-bottom gradient for the portrait card art. */
    val colors: List<Color>,
) {
    val ref: CategoryRef get() = CategoryRef(id = slug, name = title, slug = slug)
}

/**
 * Resolve a registered source by its id, falling back to public WordPress.tv
 * (used when replaying a Continue Watching item, which stores only its source
 * id). Mirrors the Apple `Sources.source(withID:)`.
 */
fun Sources.sourceWithId(id: String): ContentSource =
    all.firstOrNull { it.id == id } ?: wordpressTV
