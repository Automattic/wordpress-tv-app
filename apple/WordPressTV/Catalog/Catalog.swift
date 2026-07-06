import SwiftUI
import WordPressTVCore

/// The app's curated browse structure: the top-nav categories and the flagship
/// WordCamps shelf. Both are deliberately hand-picked rather than pulled from
/// the site's 400+ raw taxonomy terms — WordPress.tv exposes everything as flat
/// categories, so the app decides which few belong in the primary navigation
/// and which events are "flagship". Each entry maps to a real WordPress.tv
/// category slug, so the content behind it is live, not stubbed.
enum Catalog {

    /// The content-category tabs in the top nav (the mock's Home / WordCamps /
    /// Meetups / Education / How To). `Home` is handled separately as the railed
    /// landing screen; these are the flat-grid browse destinations.
    static let categories: [NavCategory] = [
        NavCategory(title: "WordCamps", slug: "wordcamptv"),
        NavCategory(title: "Meetups", slug: "wordpress-meetup"),
        NavCategory(title: "Education", slug: "learn-wordpress"),
        NavCategory(title: "How To", slug: "how-to"),
    ]

    /// The "Flagship WordCamps" shelf on Home. Portrait cards, one per flagship
    /// event, each opening that camp's videos. WordPress.tv has no key-art per
    /// event, so the card art is an app-provided brand gradient + wordmark.
    static let flagshipCamps: [FlagshipCamp] = [
        FlagshipCamp(
            title: "WordCamp Asia",
            slug: "asia",
            colors: [Color(hex: 0x4B2FBF), Color(hex: 0x2A1170)]
        ),
        FlagshipCamp(
            title: "WordCamp Europe",
            slug: "europe",
            colors: [Color(hex: 0x0E2A6B), Color(hex: 0x081536)]
        ),
        FlagshipCamp(
            title: "WordCamp Canada",
            slug: "canada",
            colors: [Color(hex: 0x0F5C4E), Color(hex: 0x06302A)]
        ),
        FlagshipCamp(
            title: "WordCamp US",
            slug: "us",
            colors: [Color(hex: 0xB0325A), Color(hex: 0x5A1030)]
        ),
    ]
}

/// A top-nav content category backed by a real WordPress.tv category slug.
struct NavCategory: Identifiable, Hashable {
    let title: String
    let slug: String
    var id: String { slug }

    /// The `CategoryRef` the repository's `listByCategory` expects.
    var ref: CategoryRef { CategoryRef(id: slug, name: title, slug: slug) }
}

/// A curated flagship WordCamp on the Home shelf.
struct FlagshipCamp: Identifiable, Hashable {
    let title: String
    let slug: String
    /// Top-to-bottom gradient for the portrait card art.
    let colors: [Color]
    var id: String { slug }

    var ref: CategoryRef { CategoryRef(id: slug, name: title, slug: slug) }
}

extension Sources {
    /// Resolve a registered source by its id, falling back to public
    /// WordPress.tv (used when replaying a Continue Watching item, which stores
    /// only its source id).
    static func source(withID id: String) -> ContentSource {
        all.first { $0.id == id } ?? wordpressTV
    }
}

extension Color {
    /// Build a `Color` from a 24-bit RGB hex literal, e.g. `0x4B2FBF`.
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
