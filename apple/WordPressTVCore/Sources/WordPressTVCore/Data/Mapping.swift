import Foundation

/// Pure (network-free) mapping from wire DTOs to domain types. Kept separate
/// from transport so it can be unit-tested against captured JSON fixtures.
enum Mapping {

    // MARK: - Posts -> [Video]

    /// Map a posts response into domain videos, in source order. Drops posts
    /// that aren't published or have no resolvable VideoPress GUID.
    static func videos(from response: PostsResponseDTO, sourceID: String) -> [Video] {
        response.posts.compactMap { video(from: $0, sourceID: sourceID) }
    }

    static func video(from post: PostDTO, sourceID: String) -> Video? {
        guard post.status == "publish" else { return nil }

        let attachment = firstAttachment(of: post)
        guard let guid = resolveGuid(attachment: attachment, content: post.content) else {
            return nil // not playable — drop it
        }

        return Video(
            id: String(post.id),
            videoGuid: guid,
            title: HTML.plainText(post.title),
            description: HTML.plainText(post.excerpt),
            posterUrl: poster(from: attachment),
            durationSeconds: attachment?.length,
            sourceID: sourceID
        )
    }

    /// The post's first attachment by ascending numeric key — deterministic
    /// even though JSON objects are unordered. (Posts here carry a single
    /// video attachment; this just makes the choice stable.)
    static func firstAttachment(of post: PostDTO) -> AttachmentDTO? {
        post.attachments?
            .sorted { (Int($0.key) ?? .max) < (Int($1.key) ?? .max) }
            .first?.value
    }

    /// GUID precedence: attachment `videopress_guid`, else the embed `src` in
    /// the post content, else `nil` (caller drops the post).
    static func resolveGuid(attachment: AttachmentDTO?, content: String) -> String? {
        if let guid = attachment?.videopressGuid, !guid.isEmpty { return guid }
        return embedGuid(in: content)
    }

    /// Parse a VideoPress GUID out of a `video.wordpress.com/embed/{guid}`
    /// iframe `src`. GUIDs are alphanumeric, so we read until the first
    /// non-alphanumeric character (typically `?` or a closing quote).
    static func embedGuid(in content: String) -> String? {
        guard let marker = content.range(of: "video.wordpress.com/embed/") else { return nil }
        let guid = content[marker.upperBound...].prefix { $0.isLetter || $0.isNumber }
        return guid.isEmpty ? nil : String(guid)
    }

    /// Poster precedence: `fmt_hd` -> `fmt_dvd` -> `fmt_std` -> nil.
    static func poster(from attachment: AttachmentDTO?) -> URL? {
        guard let t = attachment?.thumbnails else { return nil }
        return (t.fmtHd ?? t.fmtDvd ?? t.fmtStd).flatMap { URL(string: $0) }
    }

    // MARK: - VideoInfo -> PlaybackAsset

    /// Build a ready-to-play asset. Stream precedence: `files.hd.hls` ->
    /// `files.hd.dash` -> `original` (mp4). The `files.*` values are bare
    /// filenames, made absolute by joining onto the directory of `original`.
    static func playbackAsset(from info: VideoInfoDTO, fallbackTitle: String) -> PlaybackAsset? {
        guard let original = URL(string: info.original) else { return nil }
        let directory = original.deletingLastPathComponent()
        let hd = info.files["hd"]

        let url: URL
        let kind: PlaybackAsset.Kind
        if let hls = hd?.hls {
            url = directory.appending(path: hls)
            kind = .hls
        } else if let dash = hd?.dash {
            url = directory.appending(path: dash)
            kind = .dash
        } else {
            url = original
            kind = .mp4
        }

        let decoded = info.title.map(HTML.plainText)
        let title = (decoded?.isEmpty == false) ? decoded! : fallbackTitle
        let durationSeconds = info.duration.map { $0 / 1000 }

        return PlaybackAsset(url: url, kind: kind, title: title, durationSeconds: durationSeconds)
    }
}
