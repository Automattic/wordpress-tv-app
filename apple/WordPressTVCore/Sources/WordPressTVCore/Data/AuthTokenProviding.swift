import Foundation

/// Supplies the `Authorization: Bearer` token for a source that needs one.
///
/// The data layer (Core) knows *that* a source is authenticated, but not *how*
/// the token is stored — that lives in the app (Keychain, obtained via the QR
/// broker). This is the seam: the app passes a provider into the repository,
/// which asks for a token just before each authenticated request.
public protocol AuthTokenProviding: Sendable {
    /// The current access token for `source`, or `nil` if none is held (the
    /// caller then makes an unauthenticated request, which a private site
    /// rejects with 401 → re-pair).
    func accessToken(for source: ContentSource) async -> String?
}
