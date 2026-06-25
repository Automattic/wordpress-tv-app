import Foundation
import Security

/// Tiny Keychain wrapper for the signed-in session blob.
///
/// Device-only (`AfterFirstUnlock`, no iCloud sync) per the design: secure, and
/// it survives tvOS evicting the app's data container — which `UserDefaults` and
/// files do not. One service/account, so write is an upsert. Owns the JSON
/// (de)serialization, so callers store and load `Codable` values directly.
///
/// DEBUG builds also mirror the blob to a file (see below): unsigned simulator
/// builds have no keychain access group, so `SecItemAdd` fails and the session
/// would otherwise be lost on every relaunch.
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
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
           let data = item as? Data,
           let value = try? JSONDecoder().decode(type, from: data) {
            return value
        }
        #if DEBUG
        return debugFileRead(type)
        #else
        return nil
        #endif
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

        #if DEBUG
        // Unsigned simulator/debug builds have no keychain access group, so the
        // SecItemAdd above silently fails (errSecMissingEntitlement) and the
        // session vanishes on relaunch. Mirror it to disk so the dev loop stays
        // signed in. Release builds are code-signed and never read this file.
        debugFileWrite(data)
        #endif
    }

    /// Remove the stored session (log out / re-pair).
    func delete() {
        SecItemDelete(baseQuery as CFDictionary)
        #if DEBUG
        try? FileManager.default.removeItem(at: debugFileURL)
        #endif
    }

    #if DEBUG
    /// Persistent (across relaunch) location inside the app sandbox.
    private var debugFileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("\(service).\(account).json")
    }

    private func debugFileWrite(_ data: Data) {
        let url = debugFileURL
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: url)
    }

    private func debugFileRead<T: Decodable>(_ type: T.Type) -> T? {
        guard let data = try? Data(contentsOf: debugFileURL) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
    #endif
}
