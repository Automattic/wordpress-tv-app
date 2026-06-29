package com.automattic.wordpresstv.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automattic.wordpresstv.core.data.AuthTokenProvider
import com.automattic.wordpresstv.core.domain.Account
import com.automattic.wordpresstv.core.domain.ContentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the WordPress.com sign-in state for the whole app.
 *
 * Everything it needs comes from the broker's pairing result: the account (name
 * + Gravatar) and — only for Automatticians — the narrow a8c.tv token. A non-a12s
 * is signed in with `token == null`, so [isAuthorizedForA8C] is false and a8c.tv
 * never appears. The session is persisted in DataStore, so a cold launch restores
 * it with no network. Mirrors the Apple `AuthManager`.
 *
 * State is held in Compose [mutableStateOf] so the UI recomposes on sign-in/out.
 */
class AuthManager(
    private val store: SessionStore,
    val broker: BrokerClient,
    private val scope: CoroutineScope,
) : AuthTokenProvider {

    /** The signed-in user (name + avatar). `null` ⇒ signed out. */
    var account by mutableStateOf<Account?>(null)
        private set

    /** The narrow a8c.tv access token. `null` for a signed-in non-a12s. */
    var token by mutableStateOf<String?>(null)
        private set

    /** Whether a user is signed in at all (drives the avatar vs. "Sign in"). */
    val isAuthenticated: Boolean get() = account != null

    /** Whether the signed-in user may see a8c.tv — i.e. we hold an a8c token. */
    val isAuthorizedForA8C: Boolean get() = token != null

    init {
        // Restore a persisted session (no network).
        scope.launch {
            val stored = store.read() ?: return@launch
            // Don't clobber a sign-in that raced ahead of the disk read.
            if (account == null) {
                account = stored.account
                token = stored.token
            }
        }
    }

    /** Apply a completed pairing: show the avatar, keep the a8c token, persist. */
    fun signIn(result: BrokerClient.PairingResult) {
        val name = result.displayName?.takeIf { it.isNotEmpty() } ?: "WordPress.com"
        account = Account(displayName = name, avatarUrl = result.avatarUrl)
        token = result.a8cToken
        val snapshot = StoredSession(account!!, token)
        scope.launch { store.write(snapshot) }
    }

    /** Clear everything (explicit log out, or after a rejected a8c token). */
    fun signOut() {
        account = null
        token = null
        scope.launch { store.clear() }
    }

    override suspend fun accessToken(source: ContentSource): String? {
        if (source.auth != ContentSource.Auth.WPCOM_OAUTH) return null
        return token
    }
}
