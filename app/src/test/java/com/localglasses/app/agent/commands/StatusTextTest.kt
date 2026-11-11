package com.localglasses.app.agent.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextTest {

    @Test
    fun shortMessagesPassThroughUnchanged() {
        assertEquals("Plan ready for review", StatusText.shorten("Plan ready for review"))
    }

    @Test
    fun aParserDumpIsCollapsedToOneLine() {
        val dump = "Failed to call nativeSendMessage: INVALID_ARGUMENT: Failed to parse tool calls\n" +
            "full response: <|\"|>call:submit_plan{steps:[...]\n" +
            "error: FallThrough(ParseCancelledError(InputMismatchError { offending_token: ... }))"

        val status = StatusText.shorten(dump)

        assertEquals(false, status.contains("\n"))
        assertTrue("length was ${status.length}", status.length <= StatusText.DEFAULT_MAX_CHARS)
        assertTrue(status.endsWith("…"))
        assertTrue(status.startsWith("Failed to call nativeSendMessage"))
    }

    @Test
    fun exactlyTheLimitIsNotTruncated() {
        val text = "a".repeat(StatusText.DEFAULT_MAX_CHARS)

        assertEquals(text, StatusText.shorten(text))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("Blocked: no match", StatusText.shorten("  Blocked: no match  "))
    }

    @Test
    fun anEmptyMessageStaysEmpty() {
        assertEquals("", StatusText.shorten("   "))
    }
}
