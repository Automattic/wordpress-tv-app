package tv.a8c.broker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient about unknown keys so new upstream fields don't break decoding. */
val brokerJson = Json { ignoreUnknownKeys = true }

@Serializable
data class CreateSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("poll_secret") val pollSecret: String,
    @SerialName("qr_url") val qrUrl: String,
    val ttl: Long,
)

@Serializable
data class AccountDTO(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** `a8c_access_token` is non-null only for Automatticians; a non-a12s is signed in
 *  with it null and only ever sees public WordPress.tv. */
@Serializable
data class SessionStatusResponse(
    val status: String,
    val account: AccountDTO? = null,
    @SerialName("a8c_access_token") val a8cAccessToken: String? = null,
    val error: String? = null,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null,
)

@Serializable
data class WpComTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

/** `avatar_URL` keeps WP.com's exact (odd) casing. */
@Serializable
data class MeResponse(
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_URL") val avatarUrl: String? = null,
)

@Serializable
data class AutomatticianResponse(
    @SerialName("is_automattician") val isAutomattician: Boolean = false,
)

/** Error fields vary by endpoint (OAuth: error/error_description; REST: error/message). */
@Serializable
data class WpComErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val message: String? = null,
) {
    fun summary(): String = listOfNotNull(error, errorDescription ?: message)
        .joinToString(": ")
        .ifBlank { "unknown error" }
}
