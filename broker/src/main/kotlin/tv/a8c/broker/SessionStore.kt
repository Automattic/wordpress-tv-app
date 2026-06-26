package tv.a8c.broker

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

enum class SessionStatus { PENDING, AUTHORIZED, ERROR }

/** Rendezvous record correlating the phone that logged in with the polling TV,
 *  keyed by [id] (also carried as OAuth `state`). */
class PairingSession(
    val id: String,
    /** Returned only to the creating TV; required to poll. Binds the poll to that TV. */
    val pollSecret: String,
    val expiresAtEpochMs: Long,
) {
    @Volatile var status: SessionStatus = SessionStatus.PENDING
        private set
    @Volatile var a8cAccessToken: String? = null
        private set
    @Volatile var displayName: String? = null
        private set
    @Volatile var avatarUrl: String? = null
        private set
    /** Set after phase 1 for an a12s — the next `/callback` is phase 2 (a8c.tv). */
    @Volatile var awaitingA8c: Boolean = false
        private set
    @Volatile var errorMessage: String? = null
        private set

    fun setAccount(displayName: String?, avatarUrl: String?) {
        this.displayName = displayName
        this.avatarUrl = avatarUrl
    }

    fun awaitA8c() {
        awaitingA8c = true
    }

    /** [a8cToken] is null for a non-a12s (signed in, public content only). */
    fun authorize(a8cToken: String?) {
        a8cAccessToken = a8cToken
        status = SessionStatus.AUTHORIZED
    }

    fun fail(reason: String) {
        errorMessage = reason
        status = SessionStatus.ERROR
    }

    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtEpochMs
}

/** In-memory, single-instance store; sessions expire lazily on access, so there's no
 *  external store and a restart just means re-scan. */
class SessionStore(private val ttlSeconds: Long) {
    private val sessions = ConcurrentHashMap<String, PairingSession>()
    private val random = SecureRandom()

    /** 256 bits of CSPRNG entropy, URL-safe and unpadded. */
    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun create(nowMs: Long): PairingSession {
        val session = PairingSession(
            id = newToken(),
            pollSecret = newToken(),
            expiresAtEpochMs = nowMs + ttlSeconds * 1000,
        )
        sessions[session.id] = session
        return session
    }

    fun get(id: String, nowMs: Long): PairingSession? {
        if (id.isEmpty()) return null
        val session = sessions[id] ?: return null
        if (session.isExpired(nowMs)) {
            sessions.remove(id)
            return null
        }
        return session
    }

    fun remove(id: String) {
        sessions.remove(id)
    }

    fun size(): Int = sessions.size
}
