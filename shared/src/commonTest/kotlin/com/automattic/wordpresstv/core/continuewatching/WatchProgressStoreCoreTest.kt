package com.automattic.wordpresstv.core.continuewatching

import com.automattic.wordpresstv.core.domain.Video
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchProgressStoreCoreTest {
    @Test fun recordsStartedVideoWithPosterAndToken() {
        val store = WatchProgressStoreCore()
        val changed = store.record(video(posterUrl = "https://example.com/poster.jpg", playbackToken = "token"), positionMs = 20_000, durationMs = 100_000)

        assertTrue(changed)
        assertEquals(1, store.items.size)
        assertEquals("guid-1", store.items[0].videoGuid)
        assertEquals("https://example.com/poster.jpg", store.items[0].posterUrl)
        assertEquals("token", store.items[0].playbackToken)
        assertEquals(0.2, store.items[0].fractionComplete)
    }

    @Test fun ignoresBarelyStartedVideo() {
        val store = WatchProgressStoreCore()

        assertFalse(store.record(video(), positionMs = 14_999, durationMs = 100_000))
        assertEquals(emptyList(), store.items)
    }

    @Test fun removesFinishedVideo() {
        val store = WatchProgressStoreCore()
        store.record(video(), positionMs = 20_000, durationMs = 100_000)

        assertTrue(store.record(video(), positionMs = 95_000, durationMs = 100_000))
        assertEquals(emptyList(), store.items)
    }

    @Test fun upsertsMostRecentVideoFirst() {
        val store = WatchProgressStoreCore()
        val first = video(guid = "first", title = "First")
        val second = video(guid = "second", title = "Second")

        store.record(first, positionMs = 20_000, durationMs = 100_000)
        store.record(second, positionMs = 20_000, durationMs = 100_000)
        store.record(first.copy(title = "First updated"), positionMs = 30_000, durationMs = 100_000)

        assertEquals(listOf("first", "second"), store.items.map { it.videoGuid })
        assertEquals("First updated", store.items[0].title)
        assertEquals(30_000, store.items[0].positionMs)
    }

    @Test fun updatesMissingPosterWithoutReordering() {
        val store = WatchProgressStoreCore(
            initialItems = listOf(
                progress("first", posterUrl = null),
                progress("second", posterUrl = null),
            ),
        )

        assertTrue(store.setPosterUrl("second", "https://example.com/second.jpg"))

        assertEquals(listOf("first", "second"), store.items.map { it.videoGuid })
        assertNull(store.items[0].posterUrl)
        assertEquals("https://example.com/second.jpg", store.items[1].posterUrl)
    }

    @Test fun encodedItemsRoundTripPreservesOrder() {
        val encoded = WatchProgressStoreCore.encode(
            listOf(
                progress("first", posterUrl = "https://example.com/first.jpg"),
                progress("second", posterUrl = "https://example.com/second.jpg"),
            ),
        )

        val decoded = WatchProgressStoreCore(encoded).items

        assertEquals(listOf("first", "second"), decoded.map { it.videoGuid })
        assertEquals("https://example.com/first.jpg", decoded[0].posterUrl)
        assertEquals("https://example.com/second.jpg", decoded[1].posterUrl)
    }

    private fun video(
        guid: String = "guid-1",
        title: String = "Title",
        posterUrl: String? = null,
        playbackToken: String? = null,
    ) = Video(
        id = guid,
        videoGuid = guid,
        title = title,
        description = "",
        posterUrl = posterUrl,
        durationSeconds = null,
        sourceId = "wordpresstv",
        playbackToken = playbackToken,
    )

    private fun progress(guid: String, posterUrl: String?) = WatchProgress(
        videoGuid = guid,
        sourceId = "wordpresstv",
        title = guid,
        posterUrl = posterUrl,
        playbackToken = null,
        positionMs = 20_000,
        durationMs = 100_000,
    )
}
