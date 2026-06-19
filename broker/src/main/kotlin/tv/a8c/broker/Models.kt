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

/** Response to `GET /session/{id}` (the TV's poll). */
@Serializable
data class SessionStatusResponse(
    val status: String,
    @SerialName("access_token") val accessToken: String? = null,
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
