package com.localglasses.app.agent.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun recognizesNumbersAndFormattedNumbers() {
        assertTrue(PhoneNumbers.isPhoneNumber("7396512369"))
        assertTrue(PhoneNumbers.isPhoneNumber("+91 73965-12369"))
        assertFalse(PhoneNumbers.isPhoneNumber("Amma"))
        assertFalse(PhoneNumbers.isPhoneNumber("call my mother"))
    }

    @Test
    fun normalizesFormatting() {
        assertEquals("7396512369", PhoneNumbers.normalize("73965 12369"))
        assertEquals("+917396512369", PhoneNumbers.normalize("+91 73965-12369"))
    }
}
