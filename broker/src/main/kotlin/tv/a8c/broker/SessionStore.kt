package tv.a8c.broker

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

enum class SessionStatus { PENDING, AUTHORIZED, ERROR }

/**
 * One pairing session: the rendezvous record correlating "the phone that logged
 * in" with "the TV that's polling", via [id] (also carried as OAuth `state`).
 */
class PairingSession(
    val id: String,
    /** Secret returned only to the creating TV; required to poll. Binds the poll to that TV. */
    val pollSecret: String,
    val expiresAtEpochMs: Long,
) {
    @Volatile var status: SessionStatus = SessionStatus.PENDING
        private set
    @Volatile var accessToken: String? = null
        private set
    @Volatile var errorMessage: String? = null
        private set

    /** Token exchange succeeded — the TV may now collect [token] on its next poll. */
    fun authorize(token: String) {
        accessToken = token
        status = SessionStatus.AUTHORIZED
    }

    /** Pairing failed (denied, missing code, exchange error); [reason] is surfaced to the TV. */
    fun fail(reason: String) {
        errorMessage = reason
        status = SessionStatus.ERROR
    }

    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtEpochMs
}

/**
 * In-memory, single-instance session map with a TTL. At a8c-internal scale one
 * instance is plenty; a restart just means re-scan. Sessions expire lazily on
 * access (and are removed when collected), so no external store is needed.
 */
class SessionStore(private val ttlSeconds: Long) {
    private val sessions = ConcurrentHashMap<String, PairingSession>()
    private val random = SecureRandom()

    /** 32 bytes = 256 bits of CSPRNG entropy, URL-safe and unpadded. */
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

    /** Returns the live session, or null if unknown/expired (expired ones are evicted). */
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
