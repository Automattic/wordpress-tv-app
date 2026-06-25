import Foundation

/// The signed-in WordPress.com user, as far as the UI cares: a name to show and
/// an avatar (Gravatar) to render. The broker resolves it (it calls `/me`) and
/// returns it in the pairing result; the app persists and restores it.
public struct Account: Equatable, Sendable, Codable {
    public let displayName: String
    /// Gravatar URL, sized for the TV. `nil` if the account has no avatar.
    public let avatarURL: URL?

    public init(displayName: String, avatarURL: URL?) {
        self.displayName = displayName
        self.avatarURL = avatarURL
    }
}
