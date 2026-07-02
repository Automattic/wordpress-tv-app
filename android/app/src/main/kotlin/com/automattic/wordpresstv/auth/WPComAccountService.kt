package com.automattic.wordpresstv.auth

import com.automattic.wordpresstv.core.domain.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves the signed-in user's profile from WordPress.com.
 *
 * The broker used to call `/me` server-side and hand back the name + avatar. Now
 * it returns the identity (`scope=auth`) token instead, and the app trades it for
 * the account here — so new profile fields never need a broker redeploy.
 * `scope=auth` is identity-only, so `/me` is all this token can do. Mirrors the
 * Apple `WPComAccountService`.
 */
class WPComAccountService(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch the account for [token], or `null` on any failure (network/decode).
     * The caller signs in with a placeholder on `null`, so a transient blip
     * doesn't discard an otherwise-valid pairing.
     */
    suspend fun fetchAccount(token: String): Account? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ME_URL)
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val dto = json.decodeFromString<MeDto>(body)
                val name = listOfNotNull(dto.displayName, dto.username)
                    .firstOrNull { it.isNotEmpty() }
                Account(displayName = name ?: "WordPress.com", avatarUrl = dto.avatarUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** `/me` fields we use. WP.com spells the avatar key `avatar_URL` (not
     *  `avatar_url`), so map it explicitly. */
    @Serializable
    private data class MeDto(
        @SerialName("display_name") val displayName: String? = null,
        val username: String? = null,
        @SerialName("avatar_URL") val avatarUrl: String? = null,
    )

    companion object {
        private const val ME_URL = "https://public-api.wordpress.com/rest/v1.1/me"
    }
}
