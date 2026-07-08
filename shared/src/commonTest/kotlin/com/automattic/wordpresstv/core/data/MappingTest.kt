package com.automattic.wordpresstv.core.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mapping tests against one captured `posts` JSON fixture — mirrors the Apple
 * `MappingTests`: guid extraction + fallback + drop-if-missing, HTML-entity
 * decode, duration + poster precedence, and metadata-token extraction.
 */
class MappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Four posts: a published one with an attachment guid + token, a published
    // one whose guid comes from the embed, a draft (dropped), and a published one
    // with no resolvable video (dropped).
    private val postsFixture = """
        {
          "posts": [
            {
              "ID": 101,
              "title": "Hello &amp; Welcome",
              "excerpt": "<p>Intro &mdash; first look</p>",
              "content": "video.wordpress.com/embed/IGNOREDsinceAttachmentWins ... src=\"https://video.wordpress.com/embed/abc?metadata_token=tok.en-_123&amp;more\"",
              "status": "publish",
              "attachments": {
                "55": {
                  "videopress_guid": "GUIDfromAttach",
                  "length": 320,
                  "thumbnails": { "fmt_dvd": "https://x/dvd.jpg", "fmt_hd": "https://x/hd.jpg" }
                }
              }
            },
            {
              "ID": 102,
              "title": "From Embed",
              "excerpt": "",
              "content": "<iframe src=\"https://video.wordpress.com/embed/EmbedGuid99?foo=bar\"></iframe>",
              "status": "publish",
              "attachments": null
            },
            {
              "ID": 103,
              "title": "A Draft",
              "excerpt": "",
              "content": "https://video.wordpress.com/embed/draftguid",
              "status": "draft",
              "attachments": null
            },
            {
              "ID": 104,
              "title": "No Video Here",
              "excerpt": "",
              "content": "just some text, nothing playable",
              "status": "publish",
              "attachments": null
            }
          ]
        }
    """.trimIndent()

    private fun videos() =
        Mapping.videos(json.decodeFromString<PostsResponseDto>(postsFixture), sourceId = "wordpresstv")

    @Test fun dropsDraftsAndUnplayablePosts() {
        // 103 is a draft, 104 has no resolvable guid -> only 101 and 102 survive.
        assertEquals(listOf("101", "102"), videos().map { it.id })
    }

    @Test fun prefersAttachmentGuidOverEmbed() {
        assertEquals("GUIDfromAttach", videos()[0].videoGuid)
    }

    @Test fun fallsBackToEmbedGuid() {
        assertEquals("EmbedGuid99", videos()[1].videoGuid)
    }

    @Test fun decodesHtmlEntitiesAndStripsTags() {
        assertEquals("Hello & Welcome", videos()[0].title)
        assertEquals("Intro — first look", videos()[0].description)
    }

    @Test fun mapsDurationAndPosterPrecedence() {
        // fmt_hd wins over fmt_dvd; length is carried straight through.
        assertEquals(320, videos()[0].durationSeconds)
        assertEquals("https://x/hd.jpg", videos()[0].posterUrl)
    }

    @Test fun extractsEmbedPlaybackToken() {
        // Reads until the first character outside the URL-safe-base64 + `.` set.
        assertEquals("tok.en-_123", videos()[0].playbackToken)
        assertNull(videos()[1].playbackToken)
    }
}
