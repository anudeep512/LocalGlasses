package com.localglasses.app.agent.safety

import android.accessibilityservice.AccessibilityService

/**
 * Secure-screen detection available without MediaProjection.
 *
 * Android does not expose FLAG_SECURE through AccessibilityWindowInfo, so the app
 * relies on the package denylist and the screenshot API's secure-window error.
 */
class SecureScreenDetector(private val policy: SafetyPolicy) {

    fun isKnownSecure(packageName: String?): Boolean =
        policy.checkPackage(packageName).blocked

    fun fromScreenshotError(code: Int): SafetyVerdict =
        if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
            SafetyVerdict.block("Secure screen")
        } else {
            SafetyVerdict.Allowed
        }
}
