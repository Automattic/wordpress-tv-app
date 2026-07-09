package com.automattic.wordpresstv.core.data

import kotlinx.serialization.Serializable

// Wire-format DTOs for the WP.com REST API. Intentionally `internal` and mirror
// the JSON shape exactly; everything outside this file works with domain types.
// Mapping lives in `Mapping.kt`.
//
//   GET /wp/v2/sites/{site}/posts        -> List<PostDto>   (bare array)
//   GET /wp/v2/sites/{site}/{taxonomy}   -> List<TermDto>   (taxonomy term id)
//   GET /rest/v1.1/videos/{guid}         -> VideoInfoDto
//
// The posts feed uses the modern wp/v2 endpoint because it filters by the
// custom `language`/`event`/category taxonomies. wp/v2 wraps text fields in
// `{ "rendered": ... }` and carries no attachment block, so VideoPress metadata
// comes from rendered content and the v1.1 video-info endpoint.

// --- Posts (the Latest grid) ---

@Serializable
internal data class PostDto(
    val id: Long,
    val status: String = "",
    val title: RenderedDto = RenderedDto(),
    val excerpt: RenderedDto = RenderedDto(),
    val content: RenderedDto = RenderedDto(),
)

@Serializable
internal data class RenderedDto(val rendered: String = "")

@Serializable
internal data class TermDto(
    val id: Long,
    val name: String = "",
    val slug: String = "",
    val count: Int = 0,
)

// --- Video info (playback resolution) ---

@Serializable
internal data class VideoInfoDto(
    val guid: String = "",
    val title: String? = null,
    /** Duration in **milliseconds**. */
    val duration: Long? = null,
    val poster: String? = null,
    /**
     * Absolute URL of the progressive original. Its directory is the base for
     * the (filename-only) variant URLs in [files].
     */
    val original: String,
    /** Keyed by rendition name (e.g. `"hd"`); values are bare filenames. */
    val files: Map<String, FileVariantDto> = emptyMap(),
)

@Serializable
internal data class FileVariantDto(
    val mp4: String? = null,
    val hls: String? = null,
    val dash: String? = null,
)
