package com.automattic.wordpresstv.core.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mapping tests against one captured wp/v2-style `posts` JSON fixture: rendered
 * fields, GUID extraction from markup, drop-if-not-published, drop-if-missing,
 * HTML decoding, and metadata-token extraction. Poster/duration are no longer
 * inline in wp/v2 and are resolved later from video-info.
 */
class MappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val postsFixture = """
        [
          {
            "id": 101,
            "status": "publish",
            "title": { "rendered": "Hello &amp; Welcome" },
            "excerpt": { "rendered": "<p>Intro &mdash; first look</p>" },
            "content": { "rendered": "<iframe src=\"https://video.wordpress.com/embed/EmbedGuid99?metadata_token=tok.en-_123&amp;more\"></iframe>" }
          },
          {
            "id": 102,
            "status": "draft",
            "title": { "rendered": "A Draft" },
            "excerpt": { "rendered": "" },
            "content": { "rendered": "https://video.wordpress.com/embed/draftguid" }
          },
          {
            "id": 103,
            "status": "publish",
            "title": { "rendered": "No Video Here" },
            "excerpt": { "rendered": "" },
            "content": { "rendered": "just some text, nothing playable" }
          }
        ]
    """.trimIndent()

    private fun videos() =
        Mapping.videos(json.decodeFromString<List<PostDto>>(postsFixture), sourceId = "wordpresstv")

    @Test fun dropsDraftsAndUnplayablePosts() {
        assertEquals(listOf("101"), videos().map { it.id })
    }

    @Test fun extractsEmbedGuid() {
        assertEquals("EmbedGuid99", videos()[0].videoGuid)
    }

    @Test fun extractsGuidFromVideoPlayerMarkup() {
        val content = "<div class=\"video-player\"><video poster=" +
            "\"https://videos.files.wordpress.com/lSDB2hhB/video-x_mp4_std.original.jpg\"></video></div>"
        assertEquals("lSDB2hhB", Mapping.embedGuid(content))
    }

    @Test fun decodesHtmlEntitiesAndStripsTags() {
        assertEquals("Hello & Welcome", videos()[0].title)
        assertEquals("Intro — first look", videos()[0].description)
    }

    @Test fun posterAndDurationAreResolvedLazily() {
        assertNull(videos()[0].posterUrl)
        assertNull(videos()[0].durationSeconds)
    }

    @Test fun extractsEmbedPlaybackToken() {
        assertEquals("tok.en-_123", videos()[0].playbackToken)
    }

    @Test fun mapsLanguageTermsFromApi() {
        val terms = listOf(
            TermDto(id = 10, name = "English", slug = "english"),
            TermDto(id = 20, name = "Polish/Polski", slug = "polishpolski"),
            TermDto(id = 20, name = "Duplicate", slug = "duplicate"),
            TermDto(id = 30, name = "", slug = "missing-name"),
        )

        assertEquals(
            listOf(10L to "English", 20L to "Polish/Polski"),
            contentLanguagesFromTerms(terms).map { it.id to it.name },
        )
    }
}
