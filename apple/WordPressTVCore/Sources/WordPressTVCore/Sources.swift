import Foundation

/// The content sources the app knows about. The scaffold ships exactly one;
/// adding more later is just another entry in `all`.
public enum Sources {
    public static let wordpressTV = ContentSource(
        id: "wordpresstv",
        displayName: "WordPress.tv",
        wpcomSite: "wordpress.tv",
        blogID: 5_089_392,
        auth: .none,
        needsPlaybackToken: false
    )

    /// a8c.tv — a private WP.com site. Reads require a user OAuth token obtained
    /// through the QR pairing broker; VideoPress playback needs a minted JWT.
    public static let a8cTV = ContentSource(
        id: "a8ctv",
        displayName: "a8c.tv",
        wpcomSite: "a8ctv.wordpress.com",
        blogID: 14_140_874,
        auth: .wpcomOAuth,
        needsPlaybackToken: true
    )

    /// All registered sources, in display order.
    public static let all: [ContentSource] = [wordpressTV, a8cTV]
}
