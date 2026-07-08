package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video

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
    fun videos(posts: List<PostDto>, sourceId: String): List<Video> =
        posts.mapNotNull { video(it, sourceId) }

    fun video(post: PostDto, sourceId: String): Video? {
        if (post.status != "publish") return null

        val content = post.content.rendered
        val guid = embedGuid(content) ?: return null // not playable — drop it

        return Video(
            id = post.id.toString(),
            videoGuid = guid,
            title = Html.plainText(post.title.rendered),
            description = Html.plainText(post.excerpt.rendered),
            posterUrl = null,
            durationSeconds = null,
            sourceId = sourceId,
            playbackToken = embedPlaybackToken(content),
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
     * Parse a VideoPress GUID out of rendered post content. WP.com can render
     * the player as a `video.wordpress.com/embed/{guid}` iframe or as a
     * `videos.files.wordpress.com/{guid}/...` video asset. The GUID is the
     * alphanumeric path segment after either marker.
     */
    fun embedGuid(content: String): String? {
        for (marker in listOf("video.wordpress.com/embed/", "videos.files.wordpress.com/")) {
            val start = content.indexOf(marker)
            if (start == -1) continue
            val guid = content.substring(start + marker.length).takeWhile { it.isLetterOrDigit() }
            if (guid.isNotEmpty()) return guid
        }
        return null
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
        val original = info.original.takeIf { it.isHttpUrl() } ?: return null
        val hd = info.files["hd"]

        val (url, kind) = when {
            preferProgressive -> original to PlaybackAsset.Kind.MP4
            hd?.hls != null -> directoryUrl(original, hd.hls) to PlaybackAsset.Kind.HLS
            hd?.dash != null -> directoryUrl(original, hd.dash) to PlaybackAsset.Kind.DASH
            else -> original to PlaybackAsset.Kind.MP4
        }

        val decoded = info.title?.let { Html.plainText(it) }
        val title = if (!decoded.isNullOrEmpty()) decoded else fallbackTitle
        val durationSeconds = info.duration?.let { (it / 1000).toInt() }

        return PlaybackAsset(url = url, kind = kind, title = title, durationSeconds = durationSeconds)
    }

    /** Replace the last path segment (the filename) of [original] with [filename]. */
    private fun directoryUrl(original: String, filename: String): String =
        "${original.substringBeforeLast("/")}/$filename"

    private fun String.isHttpUrl(): Boolean =
        startsWith("https://") || startsWith("http://")
}
