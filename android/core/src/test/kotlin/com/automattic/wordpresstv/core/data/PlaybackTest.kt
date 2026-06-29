package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.PlaybackAsset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Playback-asset building from a captured `videos/{guid}` fixture — mirrors the
 * Apple `PlaybackTests`: rendition precedence, filename→absolute-URL joining,
 * progressive override, duration (ms→s), and title fallback.
 */
class PlaybackTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun info(filesJson: String, title: String? = "\"The &amp; Title\"") = """
        {
          "guid": "abc",
          "title": ${title ?: "null"},
          "duration": 754000,
          "poster": "https://v.files.wordpress.com/abc/poster.jpg",
          "original": "https://v.files.wordpress.com/abc/video_dvd.original.mp4",
          "files": $filesJson
        }
    """.trimIndent()

    private fun decode(jsonStr: String) = json.decodeFromString<VideoInfoDto>(jsonStr)

    @Test fun prefersHlsAndJoinsFilenameOntoOriginalDirectory() {
        val asset = Mapping.playbackAsset(
            decode(info("""{ "hd": { "mp4": "video_hd.mp4", "hls": "video_hd.m3u8", "dash": "video_hd.mpd" } }""")),
            fallbackTitle = "fallback",
        )!!
        assertEquals("https://v.files.wordpress.com/abc/video_hd.m3u8", asset.url)
        assertEquals(PlaybackAsset.Kind.HLS, asset.kind)
    }

    @Test fun fallsBackToDashWhenNoHls() {
        val asset = Mapping.playbackAsset(
            decode(info("""{ "hd": { "dash": "video_hd.mpd" } }""")),
            fallbackTitle = "fallback",
        )!!
        assertEquals("https://v.files.wordpress.com/abc/video_hd.mpd", asset.url)
        assertEquals(PlaybackAsset.Kind.DASH, asset.kind)
    }

    @Test fun fallsBackToOriginalMp4WhenNoVariants() {
        val asset = Mapping.playbackAsset(decode(info("{}")), fallbackTitle = "fallback")!!
        assertEquals("https://v.files.wordpress.com/abc/video_dvd.original.mp4", asset.url)
        assertEquals(PlaybackAsset.Kind.MP4, asset.kind)
    }

    @Test fun preferProgressiveForcesOriginalRegardlessOfRenditions() {
        val asset = Mapping.playbackAsset(
            decode(info("""{ "hd": { "hls": "video_hd.m3u8" } }""")),
            fallbackTitle = "fallback",
            preferProgressive = true,
        )!!
        assertEquals("https://v.files.wordpress.com/abc/video_dvd.original.mp4", asset.url)
        assertEquals(PlaybackAsset.Kind.MP4, asset.kind)
    }

    @Test fun decodesTitleAndConvertsDurationMsToSeconds() {
        val asset = Mapping.playbackAsset(decode(info("{}")), fallbackTitle = "fallback")!!
        assertEquals("The & Title", asset.title)
        assertEquals(754, asset.durationSeconds)
    }

    @Test fun usesFallbackTitleWhenInfoTitleMissing() {
        val asset = Mapping.playbackAsset(decode(info("{}", title = null)), fallbackTitle = "fallback")!!
        assertEquals("fallback", asset.title)
    }
}
