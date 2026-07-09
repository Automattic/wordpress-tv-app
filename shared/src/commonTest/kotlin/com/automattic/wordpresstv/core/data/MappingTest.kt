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

    @Test fun mapsLatestFlagshipWordCampEventTermsFromApiOrder() {
        val terms = listOf(
            TermDto(id = 40, name = "WordCamp Mannheim 2026", slug = "wordcamp-mannheim-2026", count = 18),
            TermDto(id = 37, name = "WordCamp Europe 2025", slug = "wordcamp-europe-2025", count = 52),
            TermDto(id = 36, name = "WordCamp US 2025", slug = "wordcamp-us-2025", count = 43),
            TermDto(id = 35, name = "WordCamp Asia 2026", slug = "wordcamp-asia-2026", count = 42),
            TermDto(id = 34, name = "WordCamp Europe 2026", slug = "wordcamp-europe-2026", count = 51),
            TermDto(
                id = 30,
                name = "WordCamp Europe 2026 Contributor Day Online",
                slug = "wordcamp-europe-2026-contributor-day-online",
                count = 1,
            ),
            TermDto(id = 20, name = "WordPress Meetup Badajoz", slug = "wordpress-meetup-badajoz", count = 1),
            TermDto(id = 10, name = "WordCamp Empty 2026", slug = "wordcamp-empty-2026", count = 0),
            TermDto(id = 40, name = "Duplicate", slug = "wordcamp-duplicate-2026", count = 12),
        )

        assertEquals(
            listOf(
                35L to "WordCamp Asia 2026",
                34L to "WordCamp Europe 2026",
                36L to "WordCamp US 2025",
            ),
            flagshipWordCampEventsFromTerms(terms).map { it.id to it.name },
        )
    }
}
