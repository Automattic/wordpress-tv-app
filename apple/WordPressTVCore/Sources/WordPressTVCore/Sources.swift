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

    /// All registered sources, in display order.
    public static let all: [ContentSource] = [wordpressTV]
}
