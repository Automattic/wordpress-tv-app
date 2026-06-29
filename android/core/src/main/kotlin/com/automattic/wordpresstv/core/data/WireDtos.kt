package com.automattic.wordpresstv.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire-format DTOs for the WP.com REST API. Intentionally `internal` and mirror
// the JSON shape exactly; everything outside this file works with domain types.
// Mapping lives in `Mapping.kt`.
//
//   GET /rest/v1.1/sites/{site}/posts   -> PostsResponseDto
//   GET /rest/v1.1/videos/{guid}        -> VideoInfoDto

// --- Posts (the Latest grid) ---

@Serializable
internal data class PostsResponseDto(
    val posts: List<PostDto> = emptyList(),
)

@Serializable
internal data class PostDto(
    @SerialName("ID") val id: Long,
    val title: String = "",
    val excerpt: String = "",
    val content: String = "",
    val status: String = "",
    /** Keyed by attachment ID. Usually a single video attachment. */
    val attachments: Map<String, AttachmentDto>? = null,
)

@Serializable
internal data class AttachmentDto(
    /** VideoPress GUID, when this attachment is a VideoPress video. */
    @SerialName("videopress_guid") val videopressGuid: String? = null,
    /** Duration in **seconds**. */
    val length: Int? = null,
    val thumbnails: ThumbnailsDto? = null,
)

@Serializable
internal data class ThumbnailsDto(
    @SerialName("fmt_hd") val fmtHd: String? = null,
    @SerialName("fmt_dvd") val fmtDvd: String? = null,
    @SerialName("fmt_std") val fmtStd: String? = null,
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
