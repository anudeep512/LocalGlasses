package com.localglasses.app.agent.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import com.localglasses.app.agent.trace.ScreenshotStore
import java.io.ByteArrayOutputStream

/**
 * Captures a failure screenshot through the Accessibility API. Secure windows return
 * an error and are never written.
 */
class ScreenshotTaker(
    private val service: AccessibilityService,
    private val store: ScreenshotStore,
) {

    fun capture(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (bitmap == null) return
                    val bytes = ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        bitmap.recycle()
                        output.toByteArray()
                    }
                    store.save(name, bytes)
                }

                override fun onFailure(errorCode: Int) {
                    // ERROR_TAKE_SCREENSHOT_SECURE_WINDOW and other failures store nothing.
                }
            },
        )
    }
}
