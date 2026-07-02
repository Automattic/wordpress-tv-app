import Foundation
import WordPressTVCore

/// Resolves the signed-in user's profile from WordPress.com.
///
/// The broker used to call `/me` server-side and hand back the name + avatar.
/// Now it returns the identity (`scope=auth`) token instead, and the app trades
/// it for the account here — so new profile fields never need a broker redeploy.
/// `scope=auth` is identity-only, so `/me` is all this token can do.
struct WPComAccountService {
    private static let meURL = URL(string: "https://public-api.wordpress.com/rest/v1.1/me")!
    var session: URLSession = .shared

    /// Fetch the account for `token`, or `nil` on any failure (network/decode).
    /// The caller signs in with a placeholder on `nil`, so a transient blip
    /// doesn't discard an otherwise-valid pairing.
    func fetchAccount(token: String) async -> Account? {
        var request = URLRequest(url: Self.meURL)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        guard let (data, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              let dto = try? JSONDecoder().decode(MeDTO.self, from: data)
        else { return nil }

        let name = [dto.displayName, dto.username]
            .compactMap { $0 }
            .first { !$0.isEmpty }
        return Account(
            displayName: name ?? "WordPress.com",
            avatarURL: dto.avatarURL.flatMap(URL.init(string:))
        )
    }
}

/// `/me` fields we use. WP.com spells the avatar key `avatar_URL` (not
/// `avatar_url`), so map keys explicitly rather than via snake-case conversion.
private struct MeDTO: Decodable {
    let displayName: String?
    let username: String?
    let avatarURL: String?

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
        case username
        case avatarURL = "avatar_URL"
    }
}
