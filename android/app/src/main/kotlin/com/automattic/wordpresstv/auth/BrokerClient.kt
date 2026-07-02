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
 * 2. [poll] → `GET /session/{id}` (with `X-Poll-Secret`) until the tokens arrive.
 *
 * On success the broker hands back the WP.com access tokens themselves (not
 * denormalized profile fields): the identity token that the app trades for the
 * account at `/me`, plus — for Automatticians — the a8c.tv content token.
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
     * What pairing produced: the identity (`scope=auth`) token — which the app
     * trades for the account (name + avatar) at `/me` — plus the narrow a8c.tv
     * token, `null` for a non-Automattician who is signed in but only ever sees
     * public WordPress.tv.
     */
    data class PairingResult(
        /** Identity token (`scope=auth`): identity-only; used to call `/me`. */
        val authToken: String,
        /** a8c.tv token (`scope=posts videos`); `null` for a non-Automattician. */
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
                "authorized" -> {
                    // On success the broker always returns the identity token;
                    // without it we can't resolve the account, so treat its
                    // absence as invalid.
                    val authToken = dto.authAccessToken ?: throw BrokerException.InvalidResponse
                    PollResult.Authorized(PairingResult(authToken, dto.a8cAccessToken))
                }
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
        @SerialName("auth_access_token") val authAccessToken: String? = null,
        @SerialName("a8c_access_token") val a8cAccessToken: String? = null,
        val error: String? = null,
    )
}

sealed class BrokerException(message: String? = null) : Exception(message) {
    data class Http(val status: Int) : BrokerException("HTTP $status")
    data object InvalidResponse : BrokerException()
}
