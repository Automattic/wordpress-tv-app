import Foundation
import Observation
import WordPressTVCore

/// Owns the WordPress.com sign-in state for the whole app.
///
/// Pairing hands back two tokens: the identity (`scope=auth`) token, which we
/// trade for the account (name + Gravatar) at `/me`, and — only for
/// Automatticians — the narrow a8c.tv token. So the access decision is still the
/// broker's (a non-a12s, e.g. an App Review tester, is signed in with
/// `token == nil`, so `isAuthorizedForA8C` is false and a8c.tv never appears),
/// but the profile is now resolved app-side.
///
/// The session (account + both tokens) is persisted in the Keychain, so a cold
/// launch restores it with no network.
@MainActor
@Observable
final class AuthManager: AuthTokenProviding {
    /// The signed-in user (name + avatar). `nil` ⇒ signed out.
    private(set) var account: Account?
    /// The identity (`scope=auth`) token — resolves the account at `/me`.
    private(set) var authToken: String?
    /// The narrow a8c.tv access token. `nil` for a signed-in non-a12s.
    private(set) var token: String?

    /// Whether a user is signed in at all (drives the avatar vs. "Sign in" button).
    var isAuthenticated: Bool { account != nil }
    /// Whether the signed-in user may see a8c.tv — i.e. we hold an a8c token.
    var isAuthorizedForA8C: Bool { token != nil }

    private let keychain: KeychainStore
    /// Trades the identity token for the account at `/me`.
    private let accounts: WPComAccountService
    /// Broker client the pairing screen uses; lives here so the base URL is
    /// configured once at the composition root.
    let broker: BrokerClient

    init(
        keychain: KeychainStore = KeychainStore(),
        accounts: WPComAccountService = WPComAccountService(),
        broker: BrokerClient
    ) {
        self.keychain = keychain
        self.accounts = accounts
        self.broker = broker
        loadStored()
    }

    /// Apply a completed pairing: keep both tokens and sign in immediately with
    /// a placeholder, then resolve the real account from `/me` in the background.
    /// Pairing never blocks on the network, and a `/me` blip just leaves the
    /// placeholder — a valid pairing still lands (`@Observable` updates the
    /// avatar when it arrives).
    func signIn(result: BrokerClient.PairingResult) {
        authToken = result.authToken
        token = result.a8cToken
        account = Account(displayName: "WordPress.com", avatarURL: nil)
        persist()
        Task {
            guard let account = await accounts.fetchAccount(token: result.authToken) else { return }
            self.account = account
            persist()
        }
    }

    /// Clear everything (explicit log out, or after a rejected a8c token).
    func signOut() {
        account = nil
        authToken = nil
        token = nil
        keychain.delete()
    }

    // MARK: Persistence

    private func persist() {
        guard let account, let authToken else {
            keychain.delete()
            return
        }
        keychain.save(StoredSession(account: account, authToken: authToken, a8cToken: token))
    }

    private func loadStored() {
        // Nothing stored, or a pre-update value we can't decode → stay signed out.
        guard let stored = keychain.read(StoredSession.self) else { return }
        account = stored.account
        authToken = stored.authToken
        token = stored.a8cToken
    }

    // MARK: AuthTokenProviding

    nonisolated func accessToken(for source: ContentSource) async -> String? {
        guard source.auth == .wpcomOAuth else { return nil }
        return await token
    }
}

/// What we persist between launches: the signed-in account, the identity token,
/// and the optional a8c.tv token. Stored as one JSON blob in the Keychain.
private struct StoredSession: Codable {
    let account: Account
    let authToken: String
    let a8cToken: String?
}
