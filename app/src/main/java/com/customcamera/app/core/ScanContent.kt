package com.customcamera.app.core

/**
 * Classified content of a scanned QR/barcode, independent of ML Kit so the
 * parsing rules can be unit-tested and reused.
 */
sealed class ScanContent {
    abstract val raw: String

    data class Url(override val raw: String, val url: String) : ScanContent()
    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String?,
        val security: String,
        val hidden: Boolean,
    ) : ScanContent()
    data class Email(override val raw: String, val address: String, val subject: String?, val body: String?) : ScanContent()
    data class Phone(override val raw: String, val number: String) : ScanContent()
    data class Sms(override val raw: String, val number: String, val message: String?) : ScanContent()
    data class Geo(override val raw: String, val latitude: Double, val longitude: Double) : ScanContent()
    data class Text(override val raw: String) : ScanContent()

    companion object {
        private val URL_REGEX = Regex("""^(https?://)?([\w-]+\.)+[a-z]{2,}(:\d+)?(/\S*)?$""", RegexOption.IGNORE_CASE)

        /** Parses raw barcode text into the most specific [ScanContent] type. */
        fun parse(raw: String): ScanContent {
            val text = raw.trim()
            val lower = text.lowercase()
            return when {
                lower.startsWith("wifi:") -> parseWifi(text) ?: Text(raw)
                lower.startsWith("mailto:") -> parseMailto(text)
                lower.startsWith("matmsg:") -> parseMatmsg(text)
                lower.startsWith("tel:") -> Phone(raw, text.substring(4))
                lower.startsWith("smsto:") || lower.startsWith("sms:") -> parseSms(text)
                lower.startsWith("geo:") -> parseGeo(text) ?: Text(raw)
                lower.startsWith("http://") || lower.startsWith("https://") -> Url(raw, text)
                !text.contains(' ') && URL_REGEX.matches(text) -> Url(raw, "https://$text")
                else -> Text(raw)
            }
        }

        /**
         * WIFI:T:WPA;S:MyNet;P:secret;H:false;;
         * Fields may appear in any order; `\` escapes `;`, `,`, `:` and `\`.
         */
        internal fun parseWifi(text: String): Wifi? {
            val fields = splitEscaped(text.substring(5))
            val ssid = fields["S"] ?: return null
            if (ssid.isEmpty()) return null
            val security = fields["T"]?.uppercase()?.ifEmpty { null } ?: "nopass"
            return Wifi(
                raw = text,
                ssid = ssid,
                password = fields["P"]?.ifEmpty { null },
                security = if (security.equals("NOPASS", ignoreCase = true)) "nopass" else security,
                hidden = fields["H"].equals("true", ignoreCase = true),
            )
        }

        private fun splitEscaped(body: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val current = StringBuilder()
            var escaped = false
            fun flush() {
                val token = current.toString()
                current.clear()
                val colon = indexOfUnescapedColon(token)
                if (colon > 0) {
                    val key = token.substring(0, colon).uppercase()
                    result.putIfAbsent(key, unescape(token.substring(colon + 1)))
                }
            }
            for (c in body) {
                when {
                    escaped -> { current.append('\\').append(c); escaped = false }
                    c == '\\' -> escaped = true
                    c == ';' -> flush()
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) flush()
            return result
        }

        private fun indexOfUnescapedColon(token: String): Int {
            var i = 0
            while (i < token.length) {
                if (token[i] == '\\') { i += 2; continue }
                if (token[i] == ':') return i
                i++
            }
            return -1
        }

        private fun unescape(s: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) { out.append(s[i + 1]); i += 2 } else { out.append(s[i]); i++ }
            }
            return out.toString()
        }

        private fun parseMailto(text: String): Email {
            val body = text.substring(7)
            val address = body.substringBefore('?')
            val query = body.substringAfter('?', "")
            val params = query.split('&').filter { '=' in it }.associate {
                it.substringBefore('=').lowercase() to decodePercent(it.substringAfter('='))
            }
            return Email(text, decodePercent(address), params["subject"], params["body"])
        }

        /** MATMSG:TO:a@b.c;SUB:Hello;BODY:Hi;; */
        private fun parseMatmsg(text: String): Email {
            val fields = splitEscaped(text.substring(7))
            return Email(text, fields["TO"].orEmpty(), fields["SUB"], fields["BODY"])
        }

        private fun parseSms(text: String): Sms {
            val body = text.substringAfter(':')
            val number = body.substringBefore(':').substringBefore('?')
            val message = when {
                ':' in body -> body.substringAfter(':')
                "body=" in body -> decodePercent(body.substringAfter("body="))
                else -> null
            }
            return Sms(text, number, message?.ifEmpty { null })
        }

        private fun parseGeo(text: String): Geo? {
            val coords = text.substring(4).substringBefore('?').substringBefore(';').split(',')
            if (coords.size < 2) return null
            val lat = coords[0].trim().toDoubleOrNull() ?: return null
            val lon = coords[1].trim().toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return Geo(text, lat, lon)
        }

        private fun decodePercent(s: String): String = try {
            java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            s
        }
    }
}
