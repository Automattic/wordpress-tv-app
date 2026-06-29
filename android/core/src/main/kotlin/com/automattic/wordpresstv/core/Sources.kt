package com.automattic.wordpresstv.core

import com.automattic.wordpresstv.core.domain.ContentSource

/**
 * The content sources the app knows about. Mirrors the Apple `Sources`:
 * WordPress.tv is public; a8c.tv is private (OAuth + VideoPress token).
 */
object Sources {
    val wordpressTV = ContentSource(
        id = "wordpresstv",
        displayName = "WordPress.tv",
        wpcomSite = "wordpress.tv",
        blogId = 5_089_392,
        auth = ContentSource.Auth.NONE,
        needsPlaybackToken = false,
    )

    /**
     * a8c.tv — a private WP.com site. Reads require a user OAuth token obtained
     * through the QR pairing broker; VideoPress playback needs the embed's token.
     */
    val a8cTV = ContentSource(
        id = "a8ctv",
        displayName = "a8c.tv",
        wpcomSite = "a8ctv.wordpress.com",
        blogId = 14_140_874,
        auth = ContentSource.Auth.WPCOM_OAUTH,
        needsPlaybackToken = true,
    )

    /** All registered sources, in display order. */
    val all: List<ContentSource> = listOf(wordpressTV, a8cTV)
}
