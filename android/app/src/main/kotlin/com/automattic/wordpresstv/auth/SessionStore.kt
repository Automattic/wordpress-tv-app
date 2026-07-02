package com.automattic.wordpresstv.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automattic.wordpresstv.core.domain.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persisted session: the signed-in account, the identity token, and the
 * optional a8c.tv token.
 */
data class StoredSession(
    val account: Account,
    val authToken: String,
    val a8cToken: String?,
)

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Persists the signed-in session between launches. The Apple side uses the
 * Keychain; on Android the app's DataStore is already sandboxed per-app, so a
 * cold launch restores the session with no network. Stored as one JSON blob.
 */
class SessionStore(private val context: Context) {
    private val key = stringPreferencesKey("session_json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): StoredSession? {
        val raw = context.sessionDataStore.data.map { it[key] }.first() ?: return null
        return try {
            val dto = json.decodeFromString<StoredSessionDto>(raw)
            StoredSession(Account(dto.displayName, dto.avatarUrl), dto.authToken, dto.a8cToken)
        } catch (_: Exception) {
            null // pre-update or corrupt value → stay signed out
        }
    }

    suspend fun write(session: StoredSession) {
        val dto = StoredSessionDto(
            displayName = session.account.displayName,
            avatarUrl = session.account.avatarUrl,
            authToken = session.authToken,
            a8cToken = session.a8cToken,
        )
        context.sessionDataStore.edit { it[key] = json.encodeToString(StoredSessionDto.serializer(), dto) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }

    @Serializable
    private data class StoredSessionDto(
        @SerialName("display_name") val displayName: String,
        @SerialName("avatar_url") val avatarUrl: String?,
        @SerialName("auth_token") val authToken: String,
        @SerialName("a8c_token") val a8cToken: String?,
    )
}
