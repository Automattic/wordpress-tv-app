package tv.a8c.broker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

    @Test
    fun `create mints distinct high-entropy id and poll secret`() {
        val store = SessionStore(ttlSeconds = 300)
        val a = store.create(nowMs = 0)
        val b = store.create(nowMs = 0)

        assertNotEquals(a.id, b.id)
        assertNotEquals(a.id, a.pollSecret)
        // 32 bytes base64url, unpadded → 43 chars
        assertEquals(43, a.id.length)
        assertEquals(SessionStatus.PENDING, a.status)
    }

    @Test
    fun `get returns the live session before expiry`() {
        val store = SessionStore(ttlSeconds = 300)
        val s = store.create(nowMs = 1_000)
        assertEquals(s.id, store.get(s.id, nowMs = 200_000)?.id)
    }

    @Test
    fun `get evicts and returns null after TTL`() {
        val store = SessionStore(ttlSeconds = 300)
        val s = store.create(nowMs = 0)
        // 300s later, exactly at the boundary, it's expired
        assertNull(store.get(s.id, nowMs = 300_000))
        assertEquals(0, store.size())
    }

    @Test
    fun `unknown id returns null`() {
        val store = SessionStore(ttlSeconds = 300)
        assertNull(store.get("nope", nowMs = 0))
    }

    @Test
    fun `remove deletes the record (single-use collect)`() {
        val store = SessionStore(ttlSeconds = 300)
        val s = store.create(nowMs = 0)
        store.remove(s.id)
        assertNull(store.get(s.id, nowMs = 0))
        assertTrue(store.size() == 0)
    }
}
