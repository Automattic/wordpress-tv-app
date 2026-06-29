package com.automattic.wordpresstv.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP client for the WordPress.com OAuth pairing broker (the Kotlin service in
 * `/broker`). The TV only ever talks to the broker — never to WP.com OAuth
 * directly. Two calls matter on the TV side:
 *
 * 1. [createSession] → `POST /session` to start a pairing session and get the
 *    `qr_url` to render plus a `poll_secret` only this TV holds.
 * 2. [poll] → `GET /session/{id}` (with `X-Poll-Secret`) until the token arrives.
 *
 * Mirrors the Apple `BrokerClient`.
 */
class BrokerClient(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    /** A freshly created pairing session. */
    data class Session(
        val id: String,
        val pollSecret: String,
        /** The URL to encode in the QR code; the phone opens it in its browser. */
        val qrUrl: String,
        /** Seconds until the session expires and the QR must be regenerated. */
        val ttlSeconds: Long,
    )

    /**
     * What pairing produced: the signed-in user (for the avatar), plus the narrow
     * a8c.tv token — `null` for a non-Automattician, who is signed in but only
     * ever sees public WordPress.tv.
     */
    data class PairingResult(
        val displayName: String?,
        val avatarUrl: String?,
        val a8cToken: String?,
    )

    /** The outcome of a single poll. */
    sealed interface PollResult {
        data object Pending : PollResult
        data class Authorized(val result: PairingResult) : PollResult
        data class Failed(val reason: String) : PollResult
        data object Expired : PollResult
    }

    suspend fun createSession(): Session = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/session")
            .post(ByteArray(0).toRequestBody())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BrokerException.Http(response.code)
            val body = response.body?.string() ?: throw BrokerException.InvalidResponse
            val dto = json.decodeFromString<CreateSessionDto>(body)
            Session(dto.sessionId, dto.pollSecret, dto.qrUrl, dto.ttl)
        }
    }

    suspend fun poll(id: String, secret: String): PollResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/session/$id")
            .header("X-Poll-Secret", secret)
            .build()
        client.newCall(request).execute().use { response ->
            // The broker deletes a session on collect/expiry → 410 Gone.
            if (response.code == 410) return@use PollResult.Expired
            if (!response.isSuccessful) throw BrokerException.Http(response.code)
            val body = response.body?.string() ?: throw BrokerException.InvalidResponse
            val dto = json.decodeFromString<SessionStatusDto>(body)
            when (dto.status) {
                "pending" -> PollResult.Pending
                "authorized" -> PollResult.Authorized(
                    PairingResult(
                        displayName = dto.account?.displayName,
                        avatarUrl = dto.account?.avatarUrl,
                        a8cToken = dto.a8cAccessToken,
                    ),
                )
                "error" -> PollResult.Failed(dto.error ?: "unknown")
                else -> throw BrokerException.InvalidResponse
            }
        }
    }

    // --- Wire DTOs ---

    @Serializable
    private data class CreateSessionDto(
        @SerialName("session_id") val sessionId: String,
        @SerialName("poll_secret") val pollSecret: String,
        @SerialName("qr_url") val qrUrl: String,
        val ttl: Long = 0,
    )

    @Serializable
    private data class SessionStatusDto(
        val status: String,
        val account: AccountDto? = null,
        @SerialName("a8c_access_token") val a8cAccessToken: String? = null,
        val error: String? = null,
    )

    @Serializable
    private data class AccountDto(
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )
}

sealed class BrokerException(message: String? = null) : Exception(message) {
    data class Http(val status: Int) : BrokerException("HTTP $status")
    data object InvalidResponse : BrokerException()
}
