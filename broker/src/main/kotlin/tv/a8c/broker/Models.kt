package tv.a8c.broker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON codec: lenient about unknown keys so upstream additions don't break us. */
val brokerJson = Json { ignoreUnknownKeys = true }

/** Response to `POST /session`. */
@Serializable
data class CreateSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("poll_secret") val pollSecret: String,
    @SerialName("qr_url") val qrUrl: String,
    val ttl: Long,
)

/** The signed-in user, returned to the TV (for the avatar) regardless of a8c access. */
@Serializable
data class AccountDTO(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Response to `GET /session/{id}` (the TV's poll). On `authorized`, `account` is
 * always present; `a8c_access_token` is non-null only for Automatticians (it's
 * the narrow posts/videos token scoped to a8c.tv). A non-a12s is signed in with
 * `a8c_access_token: null` and only ever sees public WordPress.tv.
 */
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

/** The subset of WordPress.com's `/oauth2/token` response we care about. */
@Serializable
data class WpComTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

/** The subset of `GET /me` we use. `avatar_URL` keeps WP.com's exact casing. */
@Serializable
data class MeResponse(
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_URL") val avatarUrl: String? = null,
)

/** `GET /internal/automattician` → `{ "is_automattician": true }`. */
@Serializable
data class AutomatticianResponse(
    @SerialName("is_automattician") val isAutomattician: Boolean = false,
)

/** WP.com error envelope, decoded only to log *why* a request failed. The fields
 *  vary by endpoint (OAuth uses error/error_description; REST uses error/message). */
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
