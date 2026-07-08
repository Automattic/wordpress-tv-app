package com.automattic.wordpresstv.core.domain

/**
 * A ready-to-play asset: an absolute URL plus the metadata a player needs.
 *
 * This is the heart of the core/app seam — **`:core` resolves the URL, the app
 * feeds it to ExoPlayer.** `:core` never imports Media3.
 */
data class PlaybackAsset(
    val url: String,
    val kind: Kind,
    val title: String,
    val durationSeconds: Int?,
) {
    enum class Kind {
        HLS,   // .m3u8 (preferred)
        DASH,  // .mpd
        MP4,   // progressive original
    }
}
