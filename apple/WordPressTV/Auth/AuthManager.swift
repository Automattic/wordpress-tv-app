import Foundation
import Observation
import WordPressTVCore

/// Owns the WordPress.com sign-in state for the whole app.
///
/// Everything it needs now comes from the broker's pairing result: the account
/// (name + Gravatar) and — only for Automatticians — the narrow a8c.tv token.
/// So there's no `/me` call or access probe here; the broker already decided. A
/// non-a12s (e.g. an App Review tester) is signed in with `token == nil`, so
/// `isAuthorizedForA8C` is false and a8c.tv never appears.
///
/// The session (account + token) is persisted in the Keychain, so a cold launch
/// restores it with no network.
@MainActor
@Observable
final class AuthManager: AuthTokenProviding {
    /// The signed-in user (name + avatar). `nil` ⇒ signed out.
    private(set) var account: Account?
    /// The narrow a8c.tv access token. `nil` for a signed-in non-a12s.
    private(set) var token: String?

    /// Whether a user is signed in at all (drives the avatar vs. "Sign in" button).
    var isAuthenticated: Bool { account != nil }
    /// Whether the signed-in user may see a8c.tv — i.e. we hold an a8c token.
    var isAuthorizedForA8C: Bool { token != nil }

    private let keychain: KeychainStore
    /// Broker client the pairing screen uses; lives here so the base URL is
    /// configured once at the composition root.
    let broker: BrokerClient

    init(keychain: KeychainStore = KeychainStore(), broker: BrokerClient) {
        self.keychain = keychain
        self.broker = broker
        loadStored()
    }

    /// Apply a completed pairing: show the avatar, keep the a8c token (if any),
    /// and persist both.
    func signIn(result: BrokerClient.PairingResult) {
        let name = result.displayName?.isEmpty == false ? result.displayName! : "WordPress.com"
        account = Account(displayName: name, avatarURL: result.avatarURL)
        token = result.a8cToken
        persist()
    }

    /// Clear everything (explicit log out, or after a rejected a8c token).
    func signOut() {
        account = nil
        token = nil
        keychain.delete()
    }

    // MARK: Persistence

    private func persist() {
        guard let account else {
            keychain.delete()
            return
        }
        keychain.save(StoredSession(account: account, a8cToken: token))
    }

    private func loadStored() {
        // Nothing stored, or a pre-update value we can't decode → stay signed out.
        guard let stored = keychain.read(StoredSession.self) else { return }
        account = stored.account
        token = stored.a8cToken
    }

    // MARK: AuthTokenProviding

    nonisolated func accessToken(for source: ContentSource) async -> String? {
        guard source.auth == .wpcomOAuth else { return nil }
        return await token
    }
}

/// What we persist between launches: the signed-in account plus the optional
/// a8c.tv token. Stored as one JSON blob in the Keychain.
private struct StoredSession: Codable {
    let account: Account
    let a8cToken: String?
}
