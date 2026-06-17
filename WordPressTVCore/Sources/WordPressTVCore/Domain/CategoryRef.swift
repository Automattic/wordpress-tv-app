import Foundation

/// Reference to a category within a source. Used by the (currently stubbed)
/// category APIs on `ContentRepository`.
public struct CategoryRef: Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let slug: String

    public init(id: String, name: String, slug: String) {
        self.id = id
        self.name = name
        self.slug = slug
    }
}
