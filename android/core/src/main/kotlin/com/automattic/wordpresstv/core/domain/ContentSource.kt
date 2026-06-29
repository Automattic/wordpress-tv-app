package com.automattic.wordpresstv.core.domain

/**
 * A place videos come from. The scaffold registers two sources (WordPress.tv and
 * a8c.tv), but the type is general so more can be added without touching the UI.
 */
data class ContentSource(
    val id: String,
    val displayName: String,
    /** WP.com site slug, e.g. `"wordpress.tv"`. */
    val wpcomSite: String,
    /** WP.com blog ID, e.g. `5089392`. */
    val blogId: Long,
    val auth: Auth,
    /** Whether `resolvePlayback` must stamp a VideoPress token. `false` for wordpress.tv. */
    val needsPlaybackToken: Boolean,
) {
    /**
     * How a source authenticates.
     *
     * - [NONE]: public site, no token (wordpress.tv).
     * - [WPCOM_OAUTH]: a private WP.com site read with a user `Authorization:
     *   Bearer` token obtained via the QR pairing broker (a8c.tv).
     */
    enum class Auth { NONE, WPCOM_OAUTH }
}
