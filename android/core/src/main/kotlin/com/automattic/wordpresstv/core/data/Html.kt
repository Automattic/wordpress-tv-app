package com.automattic.wordpresstv.core.data

/**
 * Minimal HTML-to-plain-text helper. Strips tags and decodes the handful of
 * entities WP.com titles and excerpts actually contain (named + numeric, decimal
 * + hex). Mirrors the Apple `HTML` helper.
 */
internal object Html {
    private val tagRegex = Regex("<[^>]+>")

    fun plainText(raw: String): String = decodeEntities(stripTags(raw)).trim()

    fun stripTags(s: String): String = s.replace(tagRegex, "")

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val result = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semicolon = s.indexOf(';', startIndex = i + 1)
                if (semicolon != -1 && semicolon - i <= 12) {
                    val body = s.substring(i + 1, semicolon)
                    val decoded = decode(body)
                    if (decoded != null) {
                        result.append(decoded)
                        i = semicolon + 1
                        continue
                    }
                }
            }
            result.append(c)
            i++
        }
        return result.toString()
    }

    /** [body] is the entity without the leading `&` or trailing `;`. */
    private fun decode(body: String): String? {
        if (body.startsWith("#x") || body.startsWith("#X")) {
            val code = body.substring(2).toIntOrNull(16) ?: return null
            return codePoint(code)
        }
        if (body.startsWith("#")) {
            val code = body.substring(1).toIntOrNull() ?: return null
            return codePoint(code)
        }
        return named[body]
    }

    private fun codePoint(code: Int): String? =
        if (code in 0..0x10FFFF) String(Character.toChars(code)) else null

    private val named: Map<String, String> = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "times" to "×",
    )
}
