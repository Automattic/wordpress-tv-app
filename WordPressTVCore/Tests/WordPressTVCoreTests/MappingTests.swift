import Testing
import Foundation
@testable import WordPressTVCore

/// Mapping tests run against one captured `posts` fixture that exercises the
/// happy path plus every drop/fallback rule. Five posts in; three survive.
struct MappingTests {
    private func mappedVideos() throws -> [Video] {
        let url = try #require(
            Bundle.module.url(forResource: "posts", withExtension: "json", subdirectory: "Fixtures")
        )
        let dto = try JSONDecoder().decode(PostsResponseDTO.self, from: Data(contentsOf: url))
        return Mapping.videos(from: dto, sourceID: "wordpresstv")
    }

    @Test func keepsOnlyPublishedPlayablePosts() throws {
        let videos = try mappedVideos()
        #expect(videos.count == 3)
        #expect(videos.allSatisfy { $0.sourceID == "wordpresstv" })
        // The text-only post and the draft are dropped.
        #expect(!videos.contains { $0.id == "140300" })
        #expect(!videos.contains { $0.id == "140250" })
    }

    @Test func extractsGuidFromAttachment() throws {
        #expect(try mappedVideos()[0].videoGuid == "ioh6M32j")
    }

    @Test func fallsBackToEmbedGuidWhenAttachmentHasNone() throws {
        // Post 140380 has an empty attachment GUID; the GUID comes from the
        // iframe embed in its content.
        let second = try mappedVideos()[1]
        #expect(second.id == "140380")
        #expect(second.videoGuid == "Znvmzhpy")
    }

    @Test func decodesEntitiesAndStripsTags() throws {
        let first = try mappedVideos()[0]
        // Numeric (&#8211; -> –) and named (&amp; -> &) entities, no markup.
        #expect(first.title == "WordCamp Europe 2026 – Opening & Keynote")
        #expect(!first.description.contains("<p>"))
        #expect(first.description.contains("…")) // &hellip;
    }

    @Test func posterPrecedenceHdThenDvdThenStd() throws {
        let videos = try mappedVideos()
        #expect(videos[0].posterUrl?.absoluteString.contains("video_hd") == true)  // fmt_hd
        #expect(videos[1].posterUrl?.absoluteString.contains("video_std") == true) // only fmt_std
        #expect(videos[2].posterUrl?.absoluteString.contains("video_dvd") == true) // no hd -> fmt_dvd
    }

    @Test func durationComesFromAttachmentLength() throws {
        let videos = try mappedVideos()
        #expect(videos[0].durationSeconds == 1910)
        #expect(videos[1].durationSeconds == nil) // no length on the embed-only post
        #expect(videos[2].durationSeconds == 4258)
    }
}
