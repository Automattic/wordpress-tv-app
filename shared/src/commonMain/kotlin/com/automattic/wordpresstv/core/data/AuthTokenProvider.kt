package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.ContentSource

/**
 * Supplies the `Authorization: Bearer` token for a source that needs one.
 *
 * `:core` knows *that* a source is authenticated, but not *how* the token is
 * stored — that lives in the app (DataStore, obtained via the QR broker). This is
 * the seam: the app passes a provider into the repository, which asks for a token
 * just before each authenticated request.
 */
fun interface AuthTokenProvider {
    /**
     * The current access token for [source], or `null` if none is held (the
     * caller then makes an unauthenticated request, which a private site rejects
     * with 401 → re-pair).
     */
    suspend fun accessToken(source: ContentSource): String?
}
