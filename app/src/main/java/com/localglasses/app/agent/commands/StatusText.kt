package com.localglasses.app.agent.commands

/**
 * Formats user-facing status text.
 *
 * A raw runtime exception can be hundreds of lines of parser internals; showing it verbatim
 * buried the command screen. Status is therefore one line, capped, and the full detail belongs
 * in the trace, not the status bar.
 */
object StatusText {

    const val DEFAULT_MAX_CHARS = 180

    fun shorten(message: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val singleLine = message.replace(WHITESPACE, " ").trim()
        if (singleLine.length <= maxChars) return singleLine
        return singleLine.take((maxChars - 1).coerceAtLeast(0)).trimEnd() + "…"
    }

    private val WHITESPACE = Regex("\\s+")
}
