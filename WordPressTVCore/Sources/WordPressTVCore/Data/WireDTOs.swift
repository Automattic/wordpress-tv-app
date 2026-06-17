import Foundation

// Wire-format DTOs for the WP.com REST API. These are intentionally `internal`
// and mirror the JSON shape exactly; everything outside this file works with
// domain types. Mapping lives in `Mapping.swift`.
//
// Captured shapes (full reference: companion content-source-spec):
//   GET /rest/v1.1/sites/{site}/posts   -> PostsResponseDTO
//   GET /rest/v1.1/videos/{guid}        -> VideoInfoDTO

// MARK: - Posts (the Latest grid)

struct PostsResponseDTO: Decodable {
    let posts: [PostDTO]
}

struct PostDTO: Decodable {
    let id: Int
    let title: String
    let excerpt: String
    let content: String
    let status: String
    /// Keyed by attachment ID. Usually a single video attachment.
    let attachments: [String: AttachmentDTO]?

    enum CodingKeys: String, CodingKey {
        case id = "ID"
        case title, excerpt, content, status, attachments
    }
}

struct AttachmentDTO: Decodable {
    /// VideoPress GUID, when this attachment is a VideoPress video.
    let videopressGuid: String?
    /// Duration in **seconds**.
    let length: Int?
    let thumbnails: ThumbnailsDTO?

    enum CodingKeys: String, CodingKey {
        case videopressGuid = "videopress_guid"
        case length, thumbnails
    }
}

struct ThumbnailsDTO: Decodable {
    let fmtHd: String?
    let fmtDvd: String?
    let fmtStd: String?

    enum CodingKeys: String, CodingKey {
        case fmtHd = "fmt_hd"
        case fmtDvd = "fmt_dvd"
        case fmtStd = "fmt_std"
    }
}

// MARK: - Video info (playback resolution)

struct VideoInfoDTO: Decodable {
    let guid: String
    let title: String?
    /// Duration in **milliseconds**.
    let duration: Int?
    let poster: String?
    /// Absolute URL of the progressive original. Its directory is the base for
    /// the (filename-only) variant URLs in `files`.
    let original: String
    /// Keyed by rendition name (e.g. `"hd"`); values are bare filenames.
    let files: [String: FileVariantDTO]
}

struct FileVariantDTO: Decodable {
    let mp4: String?
    let hls: String?
    let dash: String?
}
