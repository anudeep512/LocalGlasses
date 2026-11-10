package com.localglasses.app.agent.actions

object PhoneNumbers {

    private val phoneChars = Regex("[+0-9()\\-\\s.]+")

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    fun isPhoneNumber(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!phoneChars.matches(trimmed)) return false
        return trimmed.count(Char::isDigit) in 7..15
    }
}
