package com.localglasses.app.agent.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun masksOtpNearContext() {
        val out = Redactor.redact("Your OTP is 483920")
        assertFalse(out.contains("483920"))
        assertTrue(out.contains(Redactor.MASK))
    }

    @Test
    fun masksBearerToken() {
        val out = Redactor.redact("Authorization: Bearer abc123.def-456")
        assertFalse(out.contains("abc123.def-456"))
        assertTrue(out.contains("Bearer [REDACTED]"))
    }

    @Test
    fun masksKeyValueSecret() {
        val out = Redactor.redact("api_key=sk-live-123456")
        assertFalse(out.contains("sk-live-123456"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun masksLongHex() {
        val out = Redactor.redact("hash 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c")
        assertFalse(out.contains("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"))
    }

    @Test
    fun masksCardLikeNumber() {
        val out = Redactor.redact("card 4111 1111 1111 1111")
        assertFalse(out.contains("4111 1111 1111 1111"))
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        assertEquals("call Amma at 7", Redactor.redact("call Amma at 7"))
        assertEquals("9876543210", Redactor.redact("9876543210"))
    }
}
