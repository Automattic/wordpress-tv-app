package com.automattic.wordpresstv.core.domain

/**
 * A single playable video, mapped from a content source's wire format into the
 * app's domain. Deliberately UI-agnostic — `:core` never imports Compose or Media3.
 */
data class Video(
    /** Stable identifier — the source post ID, as a string. */
    val id: String,
    /** VideoPress GUID used to resolve a [PlaybackAsset] via `resolvePlayback`. */
    val videoGuid: String,
    /** Plain-text title: HTML entities decoded, tags stripped. */
    val title: String,
    /** Plain-text description/excerpt: HTML entities decoded, tags stripped. */
    val description: String,
    /** Poster image URL, when the source provides one. */
    val posterUrl: String?,
    /** Duration in seconds, when known. */
    val durationSeconds: Int?,
    /** The [ContentSource.id] this video came from. */
    val sourceId: String,
    /**
     * VideoPress `metadata_token` parsed from the post's private embed. It
     * authorizes both the poster image and the progressive stream for a private
     * video, minted server-side for the authorized viewer. `null` for public
     * sources (wordpress.tv), whose posters and streams are open.
     */
    val playbackToken: String? = null,
)
