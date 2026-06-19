import Foundation

/// A place videos come from. The scaffold registers exactly one source
/// (WordPress.tv), but the type is general so more can be added later without
/// touching the UI.
public struct ContentSource: Identifiable, Equatable, Sendable {
    /// How a source authenticates. Only `.none` is exercised in the scaffold;
    /// token/QR-broker flows (a8c.tv) come later.
    public enum Auth: Equatable, Sendable {
        case none
    }

    public let id: String
    public let displayName: String
    /// WP.com site slug, e.g. `"wordpress.tv"`.
    public let wpcomSite: String
    /// WP.com blog ID, e.g. `5089392`.
    public let blogID: Int
    public let auth: Auth
    /// Whether `resolvePlayback` must mint a VideoPress token. `false` for wordpress.tv.
    public let needsPlaybackToken: Bool

    public init(
        id: String,
        displayName: String,
        wpcomSite: String,
        blogID: Int,
        auth: Auth,
        needsPlaybackToken: Bool
    ) {
        self.id = id
        self.displayName = displayName
        self.wpcomSite = wpcomSite
        self.blogID = blogID
        self.auth = auth
        self.needsPlaybackToken = needsPlaybackToken
    }
}
