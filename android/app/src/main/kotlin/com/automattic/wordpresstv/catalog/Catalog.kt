package com.automattic.wordpresstv.catalog

import androidx.compose.ui.graphics.Color
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentSource

/**
 * The app's curated browse structure: the top-nav categories plus small bits of
 * presentation metadata for taxonomy-backed WordCamp event cards. Mirrors the
 * Apple `Catalog`.
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

    /** Number of recent WordCamp event taxonomy terms to show on Home. */
    const val wordCampEventLimit: Int = 8

    private val eventPalettes: List<List<Color>> = listOf(
        listOf(Color(0xFF4B2FBF), Color(0xFF2A1170)),
        listOf(Color(0xFF0E5A70), Color(0xFF06303D)),
        listOf(Color(0xFF0F5C4E), Color(0xFF06302A)),
        listOf(Color(0xFFB0325A), Color(0xFF5A1030)),
        listOf(Color(0xFF7A5C12), Color(0xFF3D2C08)),
        listOf(Color(0xFF3E6C23), Color(0xFF1E3610)),
    )

    fun colorsForEventSlug(slug: String): List<Color> =
        eventPalettes[slug.stablePaletteIndex(eventPalettes.size)]
}

/** A top-nav content category backed by a real WordPress.tv category slug. */
data class NavCategory(val title: String, val slug: String) {
    /** The [CategoryRef] the repository's `listByCategory` expects. */
    val ref: CategoryRef get() = CategoryRef(id = slug, name = title, slug = slug)
}

/** Top-to-bottom gradient for the portrait event card art. */
val ContentEvent.wordCampCardColors: List<Color>
    get() = Catalog.colorsForEventSlug(slug)

/** The event place/year, e.g. "Mannheim 2026", minus the shared prefix. */
val ContentEvent.wordCampDisplayPlace: String
    get() = name.removePrefix("WordCamp ")

/**
 * Resolve a registered source by its id, falling back to public WordPress.tv
 * (used when replaying a Continue Watching item, which stores only its source
 * id). Mirrors the Apple `Sources.source(withID:)`.
 */
fun Sources.sourceWithId(id: String): ContentSource =
    all.firstOrNull { it.id == id } ?: wordpressTV

private fun String.stablePaletteIndex(size: Int): Int {
    val hash = fold(0) { partial, char -> partial * 31 + char.code }
    return hash.floorMod(size)
}

private fun Int.floorMod(divisor: Int): Int {
    val mod = this % divisor
    return if (mod >= 0) mod else mod + divisor
}
