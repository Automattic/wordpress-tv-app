package tv.a8c.broker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.http.parameters

class WpComException(message: String) : RuntimeException(message)

/**
 * Talks to WordPress.com's OAuth endpoints (configurable via WPCOM_AUTHORIZE_URL / WPCOM_TOKEN_URL).
 * Holds the `client_secret`, which is exactly why the code-for-token exchange
 * happens here, server-side, and never on the TV or in the QR.
 */
class WpComClient(private val config: Config) {
    private val http = HttpClient(CIO)

    /** Builds the `/oauth2/authorize` URL the phone is redirected to, carrying the session id as `state`. */
    fun authorizeUrl(state: String): String {
        val query = ParametersBuilder().apply {
            append("client_id", config.clientId)
            append("redirect_uri", config.redirectUri)
            append("response_type", "code")
            append("blog", config.blogId)
            append("state", state)
            if (config.scope.isNotBlank()) append("scope", config.scope)
        }.build().formUrlEncode()
        return "${config.authorizeUrl}?$query"
    }

    /** Exchanges an authorization `code` for an access token. Throws [WpComException] on a non-2xx. */
    suspend fun exchangeCode(code: String): WpComTokenResponse {
        val response = http.submitForm(
            url = config.tokenUrl,
            formParameters = parameters {
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("code", code)
                append("redirect_uri", config.redirectUri)
                append("grant_type", "authorization_code")
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Body may echo the bad code; do NOT log it at call sites.
            throw WpComException("token endpoint returned ${response.status}")
        }
        return brokerJson.decodeFromString(WpComTokenResponse.serializer(), body)
    }
}
