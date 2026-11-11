package com.localglasses.app.agent.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAudioSourceTest {

    @Test
    fun `denied permission is a typed unavailable result`() {
        val source = PhoneAudioSource(hasPermission = { false }, recordFactory = { null })

        val availability = source.availability()

        assertTrue(availability is SourceAvailability.Unavailable)
        assertEquals(
            PhoneAudioSource.MISSING_PERMISSION,
            (availability as SourceAvailability.Unavailable).reason,
        )
    }

    @Test
    fun `granted permission reports ready`() {
        val source = PhoneAudioSource(hasPermission = { true }, recordFactory = { null })

        assertEquals(SourceAvailability.Ready as SourceAvailability, source.availability())
    }

    @Test
    fun `phone source identifies itself`() {
        val source = PhoneAudioSource(hasPermission = { true }, recordFactory = { null })

        assertEquals(AudioSourceKind.PHONE, source.kind)
    }
}
