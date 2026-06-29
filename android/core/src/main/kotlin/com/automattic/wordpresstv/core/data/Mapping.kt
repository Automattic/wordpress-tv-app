package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure (network-free) mapping from wire DTOs to domain types. Kept separate from
 * transport so it can be unit-tested against captured JSON fixtures.
 */
internal object Mapping {

    // --- Posts -> List<Video> ---

    /**
     * Map a posts response into domain videos, in source order. Drops posts that
     * aren't published or have no resolvable VideoPress GUID.
     */
    fun videos(response: PostsResponseDto, sourceId: String): List<Video> =
        response.posts.mapNotNull { video(it, sourceId) }

    fun video(post: PostDto, sourceId: String): Video? {
        if (post.status != "publish") return null

        val attachment = firstAttachment(post)
        val guid = resolveGuid(attachment, post.content) ?: return null // not playable — drop it

        return Video(
            id = post.id.toString(),
            videoGuid = guid,
            title = Html.plainText(post.title),
            description = Html.plainText(post.excerpt),
            posterUrl = poster(attachment),
            durationSeconds = attachment?.length,
            sourceId = sourceId,
            playbackToken = embedPlaybackToken(post.content),
        )
    }

    /**
     * Parse the VideoPress `metadata_token` out of the private embed in the post
     * content. WP.com mints this per-video for the authorized viewer and bakes it
     * into the rendered embed iframe `src`, so the app can reuse it for the poster
     * and stream without minting one itself (which needs the broad `global` OAuth
     * scope). The token is URL-safe base64 with `.` separators; read until the
     * first character outside that set. `null` for public posts (no token).
     */
    fun embedPlaybackToken(content: String): String? {
        val marker = "metadata_token="
        val start = content.indexOf(marker)
        if (start == -1) return null
        val token = content.substring(start + marker.length)
            .takeWhile { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        return token.ifEmpty { null }
    }

    /**
     * The post's first attachment by ascending numeric key — deterministic even
     * though JSON objects are unordered.
     */
    fun firstAttachment(post: PostDto): AttachmentDto? =
        post.attachments
            ?.toList()
            ?.sortedBy { (key, _) -> key.toIntOrNull() ?: Int.MAX_VALUE }
            ?.firstOrNull()
            ?.second

    /**
     * GUID precedence: attachment `videopress_guid`, else the embed `src` in the
     * post content, else `null` (caller drops the post).
     */
    fun resolveGuid(attachment: AttachmentDto?, content: String): String? {
        val guid = attachment?.videopressGuid
        if (!guid.isNullOrEmpty()) return guid
        return embedGuid(content)
    }

    /**
     * Parse a VideoPress GUID out of a `video.wordpress.com/embed/{guid}` iframe
     * `src`. GUIDs are alphanumeric, so read until the first non-alphanumeric char.
     */
    fun embedGuid(content: String): String? {
        val marker = "video.wordpress.com/embed/"
        val start = content.indexOf(marker)
        if (start == -1) return null
        val guid = content.substring(start + marker.length).takeWhile { it.isLetterOrDigit() }
        return guid.ifEmpty { null }
    }

    /** Poster precedence: `fmt_hd` -> `fmt_dvd` -> `fmt_std` -> null. */
    fun poster(attachment: AttachmentDto?): String? {
        val t = attachment?.thumbnails ?: return null
        return t.fmtHd ?: t.fmtDvd ?: t.fmtStd
    }

    // --- VideoInfo -> PlaybackAsset ---

    /**
     * Build a ready-to-play asset. Stream precedence: `files.hd.hls` ->
     * `files.hd.dash` -> `original` (mp4). The `files.*` values are bare
     * filenames, made absolute by joining onto the directory of `original`.
     *
     * [preferProgressive] forces the `original` MP4 regardless of available
     * renditions: private VideoPress (a8c.tv) plays the `original` URL with a
     * `metadata_token` appended, which the HLS/DASH manifests don't honor.
     */
    fun playbackAsset(
        info: VideoInfoDto,
        fallbackTitle: String,
        preferProgressive: Boolean = false,
    ): PlaybackAsset? {
        val original = info.original.toHttpUrlOrNull() ?: return null
        val hd = info.files["hd"]

        val (url, kind) = when {
            preferProgressive -> original.toString() to PlaybackAsset.Kind.MP4
            hd?.hls != null -> directoryUrl(original, hd.hls) to PlaybackAsset.Kind.HLS
            hd?.dash != null -> directoryUrl(original, hd.dash) to PlaybackAsset.Kind.DASH
            else -> original.toString() to PlaybackAsset.Kind.MP4
        }

        val decoded = info.title?.let { Html.plainText(it) }
        val title = if (!decoded.isNullOrEmpty()) decoded else fallbackTitle
        val durationSeconds = info.duration?.let { (it / 1000).toInt() }

        return PlaybackAsset(url = url, kind = kind, title = title, durationSeconds = durationSeconds)
    }

    /** Replace the last path segment (the filename) of [original] with [filename]. */
    private fun directoryUrl(original: HttpUrl, filename: String): String =
        original.newBuilder()
            .removePathSegment(original.pathSize - 1)
            .addPathSegment(filename)
            .build()
            .toString()
}
