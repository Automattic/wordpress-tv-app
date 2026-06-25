import Foundation

/// HTTP client for the WordPress.com OAuth pairing broker (the Kotlin service in
/// `/broker`). The TV only ever talks to the broker — never to WP.com OAuth
/// directly. Two calls matter on the TV side:
///
/// 1. `createSession()` → `POST /session` to start a pairing session and get the
///    `qr_url` to render plus a `pollSecret` only this TV holds.
/// 2. `poll(id:secret:)` → `GET /session/{id}` until the token arrives.
struct BrokerClient {
    /// Base URL of the deployed broker (e.g. `https://wordpresstv-broker.fly.dev`).
    /// Configured at the composition root from Info.plist.
    let baseURL: URL
    var session: URLSession = .shared

    /// A freshly created pairing session.
    struct Session {
        let id: String
        let pollSecret: String
        /// The URL to encode in the QR code; the phone opens it in Safari.
        let qrURL: String
        /// Seconds until the session expires and the QR must be regenerated.
        let ttl: TimeInterval
    }

    /// What pairing produced: the signed-in user (for the avatar), plus the
    /// narrow a8c.tv token — `nil` for a non-Automattician, who is signed in but
    /// only ever sees public WordPress.tv.
    struct PairingResult: Equatable {
        let displayName: String?
        let avatarURL: URL?
        let a8cToken: String?
    }

    /// The outcome of a single poll.
    enum PollResult: Equatable {
        case pending
        case authorized(PairingResult)
        case failed(reason: String)
        case expired
    }

    func createSession() async throws -> Session {
        var request = URLRequest(url: baseURL.appending(path: "session"))
        request.httpMethod = "POST"
        let dto: CreateSessionDTO = try await decode(request)
        return Session(id: dto.sessionId, pollSecret: dto.pollSecret, qrURL: dto.qrUrl, ttl: dto.ttl)
    }

    func poll(id: String, secret: String) async throws -> PollResult {
        var request = URLRequest(url: baseURL.appending(path: "session").appending(path: id))
        request.setValue(secret, forHTTPHeaderField: "X-Poll-Secret")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw BrokerError.invalidResponse }
        // The broker deletes a session on collect/expiry → 410 Gone.
        if http.statusCode == 410 { return .expired }
        guard (200..<300).contains(http.statusCode) else {
            throw BrokerError.http(status: http.statusCode)
        }

        let dto = try JSONDecoder.broker.decode(SessionStatusDTO.self, from: data)
        switch dto.status {
        case "pending":
            return .pending
        case "authorized":
            return .authorized(
                PairingResult(
                    displayName: dto.account?.displayName,
                    avatarURL: dto.account?.avatarUrl.flatMap(URL.init(string:)),
                    a8cToken: dto.a8cAccessToken
                )
            )
        case "error":
            return .failed(reason: dto.error ?? "unknown")
        default:
            throw BrokerError.invalidResponse
        }
    }

    private func decode<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw BrokerError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw BrokerError.http(status: http.statusCode) }
        return try JSONDecoder.broker.decode(T.self, from: data)
    }
}

enum BrokerError: Error {
    case invalidResponse
    case http(status: Int)
}

// MARK: - Wire DTOs (snake_case → camelCase via the shared decoder)

private struct CreateSessionDTO: Decodable {
    let sessionId: String
    let pollSecret: String
    let qrUrl: String
    let ttl: TimeInterval
}

private struct SessionStatusDTO: Decodable {
    let status: String
    let account: AccountDTO?
    let a8cAccessToken: String?
    let error: String?
}

private struct AccountDTO: Decodable {
    let displayName: String?
    let avatarUrl: String?
}

private extension JSONDecoder {
    static let broker: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}
