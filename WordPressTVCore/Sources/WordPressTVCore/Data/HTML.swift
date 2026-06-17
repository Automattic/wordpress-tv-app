import Foundation

/// Minimal HTML-to-plain-text helper.
///
/// We can't use `NSAttributedString`'s HTML importer — it's unavailable on
/// tvOS — so this strips tags and decodes the handful of entities WP.com
/// titles and excerpts actually contain (named + numeric, decimal + hex).
enum HTML {
    static func plainText(_ raw: String) -> String {
        decodeEntities(stripTags(raw)).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func stripTags(_ s: String) -> String {
        s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
    }

    static func decodeEntities(_ s: String) -> String {
        guard s.contains("&") else { return s }
        var result = ""
        var index = s.startIndex
        while index < s.endIndex {
            let char = s[index]
            if char == "&",
               let semicolon = s[index...].firstIndex(of: ";"),
               s.distance(from: index, to: semicolon) <= 12 {
                let body = String(s[s.index(after: index)..<semicolon])
                if let decoded = decode(entity: body) {
                    result.append(decoded)
                    index = s.index(after: semicolon)
                    continue
                }
            }
            result.append(char)
            index = s.index(after: index)
        }
        return result
    }

    /// `body` is the entity without the leading `&` or trailing `;`.
    private static func decode(entity body: String) -> Character? {
        if body.hasPrefix("#x") || body.hasPrefix("#X") {
            guard let code = UInt32(body.dropFirst(2), radix: 16),
                  let scalar = Unicode.Scalar(code) else { return nil }
            return Character(scalar)
        }
        if body.hasPrefix("#") {
            guard let code = UInt32(body.dropFirst()),
                  let scalar = Unicode.Scalar(code) else { return nil }
            return Character(scalar)
        }
        return named[body]
    }

    private static let named: [String: Character] = [
        "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'",
        "nbsp": "\u{00A0}", "hellip": "…", "mdash": "—", "ndash": "–",
        "rsquo": "’", "lsquo": "‘", "rdquo": "”", "ldquo": "“", "times": "×",
    ]
}
