package com.localglasses.app.agent.safety

import android.accessibilityservice.AccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureScreenDetectorTest {

    private val detector = SecureScreenDetector(
        SafetyPolicy(blockedPackages = setOf("com.example.secure")),
    )

    @Test
    fun recognizesKnownSecurePackage() {
        assertTrue(detector.isKnownSecure("com.example.secure"))
        assertFalse(detector.isKnownSecure("com.whatsapp"))
    }

    @Test
    fun mapsScreenshotSecureError() {
        assertTrue(
            detector.fromScreenshotError(
                AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
            ).blocked,
        )
        assertFalse(detector.fromScreenshotError(0).blocked)
    }
}
