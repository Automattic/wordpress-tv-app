import Foundation
import Observation
import WordPressTVCore

/// Owns the a8c.tv authentication state for the whole app.
///
/// It is the composition root's single source of truth for "are we signed in to
/// a8c.tv": it loads the token from the Keychain on launch, hands it to the
/// repository on demand (via `AuthTokenProviding`), and is updated by the
/// pairing flow (`signIn`) and log out (`signOut`).
@MainActor
@Observable
final class AuthManager: AuthTokenProviding {
    /// The current a8c.tv access token. `nil` ⇒ not signed in.
    private(set) var token: String?

    var isAuthenticated: Bool { token != nil }

    private let keychain: KeychainStore
    /// Broker client the pairing screen uses; lives here so the base URL is
    /// configured once at the composition root.
    let broker: BrokerClient

    init(keychain: KeychainStore = KeychainStore(), broker: BrokerClient) {
        self.keychain = keychain
        self.broker = broker
        self.token = keychain.read()
    }

    /// Persist a token obtained from the broker and flip to the signed-in state.
    func signIn(token: String) {
        self.token = token
        keychain.save(token)
    }

    /// Clear the token everywhere (explicit log out, or after a 401/403 re-pair).
    func signOut() {
        token = nil
        keychain.delete()
    }

    // MARK: AuthTokenProviding

    nonisolated func accessToken(for source: ContentSource) async -> String? {
        guard source.auth == .wpcomOAuth else { return nil }
        return await token
    }
}
