import Testing
import Foundation
@testable import WordPressTVCore

/// Playback resolution: stream precedence (hls -> dash -> mp4) and building an
/// absolute URL from a bare variant filename plus the `original` directory.
struct PlaybackTests {
    private func videoInfo() throws -> VideoInfoDTO {
        let url = try #require(
            Bundle.module.url(forResource: "video", withExtension: "json", subdirectory: "Fixtures")
        )
        return try JSONDecoder().decode(VideoInfoDTO.self, from: Data(contentsOf: url))
    }

    @Test func prefersHlsAndBuildsAbsoluteURL() throws {
        let asset = try #require(Mapping.playbackAsset(from: try videoInfo(), fallbackTitle: "x"))
        #expect(asset.kind == .hls)
        #expect(asset.url.absoluteString ==
            "https://videos.files.wordpress.com/ioh6M32j/video-f3c043d32e_mp4_hd.master.m3u8")
    }

    @Test func durationConvertedFromMilliseconds() throws {
        let asset = try #require(Mapping.playbackAsset(from: try videoInfo(), fallbackTitle: "x"))
        #expect(asset.durationSeconds == 1909) // 1909633 / 1000
    }

    @Test func fallsBackToDashWhenNoHls() throws {
        let info = VideoInfoDTO(
            guid: "g", title: "Title", duration: 1000, poster: nil,
            original: "https://videos.files.wordpress.com/g/base.mp4",
            files: ["hd": FileVariantDTO(mp4: "base_hd.mp4", hls: nil, dash: "base_hd.dash.mpd")]
        )
        let asset = try #require(Mapping.playbackAsset(from: info, fallbackTitle: "x"))
        #expect(asset.kind == .dash)
        #expect(asset.url.absoluteString == "https://videos.files.wordpress.com/g/base_hd.dash.mpd")
    }

    @Test func fallsBackToOriginalMp4AndUsesFallbackTitle() throws {
        let info = VideoInfoDTO(
            guid: "g", title: nil, duration: nil, poster: nil,
            original: "https://videos.files.wordpress.com/g/base.mp4",
            files: [:]
        )
        let asset = try #require(Mapping.playbackAsset(from: info, fallbackTitle: "Fallback"))
        #expect(asset.kind == .mp4)
        #expect(asset.url.absoluteString == "https://videos.files.wordpress.com/g/base.mp4")
        #expect(asset.title == "Fallback")
        #expect(asset.durationSeconds == nil)
    }
}
