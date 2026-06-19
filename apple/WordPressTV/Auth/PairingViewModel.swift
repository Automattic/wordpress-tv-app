import Foundation
import Observation

/// Drives the pairing screen: creates a broker session, surfaces the `qr_url` to
/// render, and polls until the broker hands back a token (or the session fails).
///
/// One long-lived task owns the whole flow so an expired QR just loops back and
/// regenerates itself without UI flicker.
@MainActor
@Observable
final class PairingViewModel {
    enum State: Equatable {
        case creating
        case showing(qrURL: String)
        case success
        case failed(String)
    }

    private(set) var state: State = .creating

    private let broker: BrokerClient
    private let onAuthorized: (String) -> Void
    private var task: Task<Void, Never>?

    /// How often the TV asks the broker whether the phone has finished.
    private static let pollInterval: Duration = .seconds(2)

    init(broker: BrokerClient, onAuthorized: @escaping (String) -> Void) {
        self.broker = broker
        self.onAuthorized = onAuthorized
    }

    func start() {
        guard task == nil else { return }
        task = Task { await run() }
    }

    func retry() {
        task?.cancel()
        task = Task { await run() }
    }

    func stop() {
        task?.cancel()
        task = nil
    }

    private func run() async {
        // Outer loop lets an expired session transparently regenerate.
        while !Task.isCancelled {
            state = .creating
            let session: BrokerClient.Session
            do {
                session = try await broker.createSession()
            } catch {
                state = .failed("Couldn't reach the sign-in service. Make sure the broker is running, then try again.")
                return
            }
            state = .showing(qrURL: session.qrURL)

            if await pollUntilDone(session) { return } // terminal (success/failure)
            // Otherwise the session expired → loop and mint a fresh one.
        }
    }

    /// Polls one session. Returns `true` when the flow reached a terminal state
    /// (handled here), `false` when the session expired and should be reissued.
    private func pollUntilDone(_ session: BrokerClient.Session) async -> Bool {
        while !Task.isCancelled {
            try? await Task.sleep(for: Self.pollInterval)
            if Task.isCancelled { return true }

            let result: BrokerClient.PollResult
            do {
                result = try await broker.poll(id: session.id, secret: session.pollSecret)
            } catch {
                continue // transient network blip — keep polling
            }

            switch result {
            case .pending:
                continue
            case .authorized(let token):
                state = .success
                try? await Task.sleep(for: .seconds(0.8)) // let the checkmark land
                onAuthorized(token)
                return true
            case .failed(let reason):
                state = .failed(Self.message(for: reason))
                return true
            case .expired:
                return false
            }
        }
        return true
    }

    private static func message(for reason: String) -> String {
        switch reason {
        case "oauth_denied":
            return "Sign-in was cancelled on your phone. Try again."
        case "token_exchange_failed":
            return "Sign-in didn't complete. Try again."
        default:
            return "Something went wrong signing in. Try again."
        }
    }
}
