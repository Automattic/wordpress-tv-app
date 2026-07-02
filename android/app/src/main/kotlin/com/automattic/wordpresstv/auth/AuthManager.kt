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
 * Pairing hands back two tokens: the identity (`scope=auth`) token, which we
 * trade for the account (name + Gravatar) at `/me`, and — only for Automatticians
 * — the narrow a8c.tv token. So the access decision is still the broker's (a
 * non-a12s is signed in with `token == null`, so [isAuthorizedForA8C] is false and
 * a8c.tv never appears), but the profile is now resolved app-side. The session
 * (account + both tokens) is persisted in DataStore, so a cold launch restores it
 * with no network. Mirrors the Apple `AuthManager`.
 *
 * State is held in Compose [mutableStateOf] so the UI recomposes on sign-in/out.
 */
class AuthManager(
    private val store: SessionStore,
    val broker: BrokerClient,
    private val scope: CoroutineScope,
    private val accounts: WPComAccountService = WPComAccountService(),
) : AuthTokenProvider {

    /** The signed-in user (name + avatar). `null` ⇒ signed out. */
    var account by mutableStateOf<Account?>(null)
        private set

    /** The identity (`scope=auth`) token — resolves the account at `/me`. */
    var authToken by mutableStateOf<String?>(null)
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
                authToken = stored.authToken
                token = stored.a8cToken
            }
        }
    }

    /**
     * Apply a completed pairing: keep both tokens and sign in immediately with a
     * placeholder, then resolve the real account from `/me` in the background.
     * Pairing never blocks on the network, and a `/me` blip just leaves the
     * placeholder — a valid pairing still lands (the avatar fills in when it
     * arrives).
     */
    fun signIn(result: BrokerClient.PairingResult) {
        authToken = result.authToken
        token = result.a8cToken
        account = Account(displayName = "WordPress.com", avatarUrl = null)
        persist()
        scope.launch {
            val resolved = accounts.fetchAccount(result.authToken) ?: return@launch
            account = resolved
            persist()
        }
    }

    /** Clear everything (explicit log out, or after a rejected a8c token). */
    fun signOut() {
        account = null
        authToken = null
        token = null
        scope.launch { store.clear() }
    }

    private fun persist() {
        val account = account ?: return
        val authToken = authToken ?: return
        val snapshot = StoredSession(account, authToken, token)
        scope.launch { store.write(snapshot) }
    }

    override suspend fun accessToken(source: ContentSource): String? {
        if (source.auth != ContentSource.Auth.WPCOM_OAUTH) return null
        return token
    }
}
