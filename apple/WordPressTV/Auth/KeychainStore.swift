import Foundation
import Security

/// Tiny Keychain wrapper for the single a8c.tv access token.
///
/// Device-only (`AfterFirstUnlock`, no iCloud sync) per the design: secure, and
/// it survives tvOS evicting the app's data container — which `UserDefaults` and
/// files do not. One service/account, so write is an upsert.
struct KeychainStore {
    private let service: String
    private let account: String

    init(service: String = "com.automattic.wordpresstv.a8ctv", account: String = "access-token") {
        self.service = service
        self.account = account
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    /// The stored token, or `nil` if none.
    func read() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Upsert the token.
    func save(_ token: String) {
        let data = Data(token.utf8)
        // Delete any existing item first so this is a clean upsert.
        SecItemDelete(baseQuery as CFDictionary)

        var attributes = baseQuery
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attributes as CFDictionary, nil)
    }

    /// Remove the token (log out / re-pair).
    func delete() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
