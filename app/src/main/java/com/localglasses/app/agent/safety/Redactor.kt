package com.localglasses.app.agent.safety

/**
 * Best-effort redaction applied before screen text or model I/O is stored.
 *
 * Encryption protects the database at rest; this limits what enters it in the first
 * place. Password fields are excluded earlier, during observation.
 */
object Redactor {

    const val MASK = "[REDACTED]"

    private val otpContext = Regex(
        "(?i)(otp|one[- ]time (?:password|code)|verification code|security code|code)" +
            "\\D{0,20}(\\d{4,8})",
    )
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val keyValue = Regex(
        "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|password)" +
            "\\s*[:=]\\s*\\S+",
    )
    private val longHex = Regex("\\b[0-9a-fA-F]{32,}\\b")
    private val cardLike = Regex("\\b(?:\\d[ -]*?){13,19}\\b")

    fun redact(text: String): String {
        var output = otpContext.replace(text) { match ->
            val group = match.groups[2]
            if (group == null) {
                match.value
            } else {
                val start = group.range.first - match.range.first
                val end = group.range.last - match.range.first + 1
                match.value.replaceRange(start, end, MASK)
            }
        }
        output = bearer.replace(output, "Bearer $MASK")
        output = keyValue.replace(output) { match ->
            "${match.groupValues[1]}=$MASK"
        }
        output = longHex.replace(output, MASK)
        output = cardLike.replace(output, MASK)
        return output
    }
}
