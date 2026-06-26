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

data class WpComAccount(val displayName: String?, val avatarUrl: String?)

/**
 * Holds the `client_secret`, which is why the code-for-token exchange happens here
 * server-side, never on the TV or in the QR. The TV never sees the phase-1 identity
 * token either — only the narrow a8c.tv token.
 */
class WpComClient(private val config: Config) {
    private val http = HttpClient(CIO)

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
            // Surface WP.com's error fields, never the raw body — it can echo the code.
            val reason = runCatching {
                brokerJson.decodeFromString(WpComErrorResponse.serializer(), body)
            }.getOrNull()?.summary() ?: "non-JSON body (${body.length} chars)"
            throw WpComException("token endpoint returned ${response.status} — $reason")
        }
        return brokerJson.decodeFromString(WpComTokenResponse.serializer(), body)
    }

    suspend fun fetchAccount(token: String): WpComAccount {
        val dto = brokerJson.decodeFromString(MeResponse.serializer(), authedGet("${config.apiBaseUrl}/me", token))
        return WpComAccount(
            displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.username,
            avatarUrl = resizeAvatar(dto.avatarUrl, AVATAR_SIZE),
        )
    }

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
