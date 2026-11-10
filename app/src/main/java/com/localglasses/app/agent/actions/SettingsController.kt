package com.localglasses.app.agent.actions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings

object SettingsValidator {
    fun validate(setting: String, value: Int): String? = when (setting) {
        "screen_timeout_seconds" -> if (value !in 15..1800) "Screen timeout must be 15–1800 seconds" else null
        "brightness_percent" -> if (value !in 0..100) "Brightness must be 0–100" else null
        "media_volume_percent" -> if (value !in 0..100) "Volume must be 0–100" else null
        else -> null
    }
}

class SettingsController(private val context: Context) {

    fun apply(setting: String, value: Int): ActionExecution = when (setting) {
        "screen_timeout_seconds" -> {
            if (value !in 15..1800) {
                ActionExecution.Failed("Screen timeout must be 15–1800 seconds")
            } else {
                writeSystemSetting(
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    value * 1000,
                    "Screen timeout set to $value seconds",
                )
            }
        }
        "brightness_percent" -> {
            if (value !in 0..100) {
                ActionExecution.Failed("Brightness must be 0–100")
            } else {
                val result = writeSystemSetting(
                    Settings.System.SCREEN_BRIGHTNESS,
                    value * 255 / 100,
                    "Brightness set to $value%",
                )
                if (result is ActionExecution.Success) {
                    writeSystemSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, 0, "Brightness")
                }
                result
            }
        }
        "media_volume_percent" -> setVolume(value)
        "dnd_on" -> setDnd(NotificationManager.INTERRUPTION_FILTER_NONE, "Do not disturb on")
        "dnd_off" -> setDnd(NotificationManager.INTERRUPTION_FILTER_ALL, "Do not disturb off")
        "rotation_lock" -> writeSystemSetting(Settings.System.ACCELEROMETER_ROTATION, 0, "Rotation locked")
        "rotation_unlock" -> writeSystemSetting(Settings.System.ACCELEROMETER_ROTATION, 1, "Rotation unlocked")
        "flashlight_on" -> setTorch(true)
        "flashlight_off" -> setTorch(false)
        else -> ActionExecution.Failed("Unsupported setting: $setting")
    }

    fun openPage(page: String): ActionExecution {
        val action = when (page) {
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notification" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> return ActionExecution.Failed("Unsupported Settings page: $page")
        }
        return runCatching {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
            ActionExecution.Success("Opened Settings")
        }.getOrElse { ActionExecution.Failed(it.message ?: "Cannot open Settings") }
    }

    private fun writeSystemSetting(key: String, value: Int, message: String): ActionExecution {
        if (!Settings.System.canWrite(context)) {
            return ActionExecution.NeedsPermission(Manifest.permission.WRITE_SETTINGS)
        }
        return runCatching {
            val written = Settings.System.putInt(context.contentResolver, key, value)
            if (written) {
                ActionExecution.Success(message)
            } else {
                ActionExecution.Failed("Settings write was rejected")
            }
        }.getOrElse { ActionExecution.Failed(it.message ?: "Settings write failed") }
    }

    private fun setVolume(percent: Int): ActionExecution {
        if (percent !in 0..100) return ActionExecution.Failed("Volume must be 0–100")
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, percent * max / 100, 0)
            ActionExecution.Success("Media volume set to $percent%")
        }.getOrElse { ActionExecution.Failed(it.message ?: "Volume change failed") }
    }

    private fun setDnd(filter: Int, message: String): ActionExecution {
        val notification = context.getSystemService(NotificationManager::class.java)
        if (!notification.isNotificationPolicyAccessGranted) {
            return ActionExecution.NeedsPermission("android.app.NotificationManager.Policy")
        }
        return runCatching {
            notification.setInterruptionFilter(filter)
            ActionExecution.Success(message)
        }.getOrElse { ActionExecution.Failed(it.message ?: "Do not disturb change failed") }
    }

    private fun setTorch(on: Boolean): ActionExecution {
        val manager = context.getSystemService(CameraManager::class.java)
        return runCatching {
            val id = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionExecution.Failed("No camera flash available")
            manager.setTorchMode(id, on)
            ActionExecution.Success(if (on) "Flashlight on" else "Flashlight off")
        }.getOrElse { ActionExecution.Failed(it.message ?: "Flashlight change failed") }
    }
}
