import Foundation
import Security

/// Tiny Keychain wrapper for the signed-in session blob.
///
/// Device-only (`AfterFirstUnlock`, no iCloud sync) per the design: secure, and
/// it survives tvOS evicting the app's data container — which `UserDefaults` and
/// files do not. One service/account, so write is an upsert. Owns the JSON
/// (de)serialization, so callers store and load `Codable` values directly.
struct KeychainStore {
    private let service: String
    private let account: String

    init(service: String = "com.automattic.wordpresstv.a8ctv", account: String = "session") {
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

    /// The stored value decoded as `T`, or `nil` if absent or undecodable.
    func read<T: Decodable>(_ type: T.Type) -> T? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    /// Upsert `value`, stored as JSON.
    func save<T: Encodable>(_ value: T) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        // Delete any existing item first so this is a clean upsert.
        SecItemDelete(baseQuery as CFDictionary)

        var attributes = baseQuery
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attributes as CFDictionary, nil)
    }

    /// Remove the stored session (log out / re-pair).
    func delete() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
