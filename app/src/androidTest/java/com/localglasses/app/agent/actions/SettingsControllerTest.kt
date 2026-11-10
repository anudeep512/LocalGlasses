package com.localglasses.app.agent.actions

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsControllerTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val controller = SettingsController(context)

    @Test
    fun screenTimeoutWriteIsAccepted() {
        val old = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            1_800_000,
        )
        try {
            val result = controller.apply("screen_timeout_seconds", 60)
            assertTrue(result is ActionExecution.Success)
        } finally {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, old)
        }
    }

    @Test
    fun writesBrightnessAndRestoresIt() {
        val oldBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val oldMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        try {
            val result = controller.apply("brightness_percent", 50)
            assertTrue(result is ActionExecution.Success)
            assertTrue(
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0) in 126..129,
            )
        } finally {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, oldBrightness)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, oldMode)
        }
    }

    @Test
    fun writesMediaVolumeAndRestoresIt() {
        val audio = context.getSystemService(AudioManager::class.java)
        val old = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            val result = controller.apply("media_volume_percent", 30)
            assertTrue(result is ActionExecution.Success)
            assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC) >= 0)
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, old, 0)
        }
    }

    @Test
    fun writesRotationAndRestoresIt() {
        val old = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        try {
            assertTrue(controller.apply("rotation_lock", 0) is ActionExecution.Success)
            assertEquals(
                0,
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1),
            )
            assertTrue(controller.apply("rotation_unlock", 0) is ActionExecution.Success)
        } finally {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, old)
        }
    }

    @Test
    fun dndReportsPermissionOrSucceeds() {
        val result = controller.apply("dnd_on", 0)
        assertTrue(result is ActionExecution.Success || result is ActionExecution.NeedsPermission)
        controller.apply("dnd_off", 0)
    }

    @Test
    fun opensSettingsPage() {
        assertTrue(controller.openPage("display") is ActionExecution.Success)
        assertTrue(controller.openPage("not_a_page") is ActionExecution.Failed)
    }

    @Test
    fun flashlightReportsResultWithoutCrashing() {
        val on = controller.apply("flashlight_on", 0)
        assertTrue(on is ActionExecution.Success || on is ActionExecution.Failed)
        controller.apply("flashlight_off", 0)
    }
}
