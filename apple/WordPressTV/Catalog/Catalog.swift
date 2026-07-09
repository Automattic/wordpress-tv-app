import SwiftUI

/// The app's curated browse structure: the top-nav categories plus small bits of
/// presentation metadata for taxonomy-backed WordCamp event cards.
enum Catalog {
    static let wordCampsSlug = "wordcamptv"

    /// The content-category tabs in the top nav (the mock's Home / WordCamps /
    /// Meetups / Education / How To). `Home` is handled separately as the railed
    /// landing screen; these are the flat-grid browse destinations.
    static let categories: [NavCategory] = [
        NavCategory(title: "WordCamps", slug: wordCampsSlug),
        NavCategory(title: "Meetups", slug: "wordpress-meetup"),
        NavCategory(title: "Education", slug: "learn-wordpress"),
        NavCategory(title: "How To", slug: "how-to"),
    ]

    /// Stable card palettes for event terms, selected from the event slug.
    static let eventPalettes: [[Color]] = [
        [Color(hex: 0x4B2FBF), Color(hex: 0x2A1170)],
        [Color(hex: 0x0E5A70), Color(hex: 0x06303D)],
        [Color(hex: 0x0F5C4E), Color(hex: 0x06302A)],
        [Color(hex: 0xB0325A), Color(hex: 0x5A1030)],
        [Color(hex: 0x7A5C12), Color(hex: 0x3D2C08)],
        [Color(hex: 0x3E6C23), Color(hex: 0x1E3610)],
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

extension Sources {
    /// Resolve a registered source by its id, falling back to public
    /// WordPress.tv (used when replaying a Continue Watching item, which stores
    /// only its source id).
    static func source(withID id: String) -> ContentSource {
        all.first { $0.id == id } ?? wordpressTV
    }
}

extension ContentEvent {
    /// Top-to-bottom gradient for the portrait card art.
    var colors: [Color] {
        let hash = slug.unicodeScalars.reduce(UInt32(0)) { partial, scalar in
            partial &* 31 &+ UInt32(scalar.value)
        }
        let index = Int(hash % UInt32(Catalog.eventPalettes.count))
        return Catalog.eventPalettes[index]
    }

    /// The event place/year, e.g. "Mannheim 2026" — the event name minus the
    /// shared "WordCamp" prefix.
    var displayPlace: String {
        name.replacingOccurrences(of: "WordCamp ", with: "")
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
