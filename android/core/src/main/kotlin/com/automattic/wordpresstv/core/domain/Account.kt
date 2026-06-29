package com.automattic.wordpresstv.core.domain

/**
 * The signed-in WordPress.com user, as far as the UI cares: a name to show and
 * an avatar (Gravatar) to render. The broker resolves it and returns it in the
 * pairing result; the app persists and restores it.
 */
data class Account(
    val displayName: String,
    /** Gravatar URL, sized for the TV. `null` if the account has no avatar. */
    val avatarUrl: String?,
)
