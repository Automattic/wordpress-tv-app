package tv.a8c.broker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.http.parameters

class WpComException(message: String) : RuntimeException(message)

/** The signed-in user's profile, as far as the TV cares. */
data class WpComAccount(val displayName: String?, val avatarUrl: String?)

/**
 * Talks to WordPress.com's OAuth + REST endpoints (configurable via
 * WPCOM_AUTHORIZE_URL / WPCOM_TOKEN_URL / WPCOM_API_BASE).
 *
 * Holds the `client_secret`, which is exactly why the code-for-token exchange
 * happens here, server-side, and never on the TV or in the QR. The TV never
 * sees the phase-1 identity token either — only the narrow a8c.tv token.
 */
class WpComClient(private val config: Config) {
    private val http = HttpClient(CIO)

    /**
     * Builds an `/oauth2/authorize` URL the phone is redirected to.
     *
     * - Phase 1 (identity): `scope=auth`, no `blog` — a token good only for
     *   `/me` and `/internal/automattician`, no site access.
     * - Phase 2 (a8c.tv): `scope=posts videos` (space-separated — a comma is
     *   rejected as an invalid scope), `blog=a8ctv.wordpress.com` — a token that
     *   can read only posts and videos, only on a8c.tv.
     */
    fun authorizeUrl(state: String, scope: String, blog: String?, clientId: String): String {
        val query = ParametersBuilder().apply {
            append("client_id", clientId)
            append("redirect_uri", config.redirectUri)
            append("response_type", "code")
            if (!blog.isNullOrBlank()) append("blog", blog)
            append("state", state)
            if (scope.isNotBlank()) append("scope", scope)
        }.build().formUrlEncode()
        return "${config.authorizeUrl}?$query"
    }

    /** Exchanges an authorization `code` for an access token, using the same
     *  client that issued the code. Throws [WpComException] on a non-2xx. */
    suspend fun exchangeCode(code: String, clientId: String, clientSecret: String): WpComTokenResponse {
        val response = http.submitForm(
            url = config.tokenUrl,
            formParameters = parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("code", code)
                append("redirect_uri", config.redirectUri)
                append("grant_type", "authorization_code")
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Surface WP.com's own error fields (error / error_description /
            // message) so failures are diagnosable — but never the raw body,
            // which can echo the authorization code.
            val reason = runCatching {
                brokerJson.decodeFromString(WpComErrorResponse.serializer(), body)
            }.getOrNull()?.summary() ?: "non-JSON body (${body.length} chars)"
            throw WpComException("token endpoint returned ${response.status} — $reason")
        }
        return brokerJson.decodeFromString(WpComTokenResponse.serializer(), body)
    }

    /** `GET /me` → the signed-in user's name + Gravatar (sized up for the TV). */
    suspend fun fetchAccount(token: String): WpComAccount {
        val dto = brokerJson.decodeFromString(MeResponse.serializer(), authedGet("${config.apiBaseUrl}/me", token))
        return WpComAccount(
            displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.username,
            avatarUrl = resizeAvatar(dto.avatarUrl, AVATAR_SIZE),
        )
    }

    /** `GET /internal/automattician` → whether this WP.com user is an a12s. */
    suspend fun isAutomattician(token: String): Boolean {
        val dto = brokerJson.decodeFromString(
            AutomatticianResponse.serializer(),
            authedGet("${config.apiBaseUrl}/internal/automattician", token),
        )
        return dto.isAutomattician
    }

    private suspend fun authedGet(url: String, token: String): String {
        val response = http.get(url) { header("Authorization", "Bearer $token") }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WpComException("GET $url returned ${response.status}")
        }
        return body
    }

    private companion object {
        const val AVATAR_SIZE = 256

        /** Force a TV-sized Gravatar: drop any existing `s`/`size`, set our own. */
        fun resizeAvatar(url: String?, size: Int): String? {
            if (url.isNullOrBlank()) return url
            return try {
                URLBuilder(url).apply {
                    parameters.remove("s")
                    parameters.remove("size")
                    parameters.append("s", size.toString())
                }.buildString()
            } catch (_: Exception) {
                url
            }
        }
    }
}
