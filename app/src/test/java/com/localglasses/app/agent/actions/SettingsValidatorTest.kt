package com.localglasses.app.agent.actions

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidatorTest {

    @Test
    fun validatesRanges() {
        assertNull(SettingsValidator.validate("screen_timeout_seconds", 60))
        assertNotNull(SettingsValidator.validate("screen_timeout_seconds", 5))
        assertNull(SettingsValidator.validate("brightness_percent", 50))
        assertNotNull(SettingsValidator.validate("brightness_percent", 101))
        assertNull(SettingsValidator.validate("media_volume_percent", 0))
        assertNotNull(SettingsValidator.validate("media_volume_percent", -1))
    }

    @Test
    fun nonRangeSettingsAreAccepted() {
        assertNull(SettingsValidator.validate("dnd_on", 0))
        assertNull(SettingsValidator.validate("flashlight_on", 0))
    }
}
