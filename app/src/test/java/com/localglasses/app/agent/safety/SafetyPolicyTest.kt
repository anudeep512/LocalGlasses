package com.localglasses.app.agent.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {

    private val policy = SafetyPolicy(
        blockedPackages = setOf("com.example.payment"),
        blockedKeywords = setOf("bank", "otp"),
    )

    @Test
    fun blocksInstallAndUninstall() {
        assertTrue(policy.checkCommand("install a new app").blocked)
        assertTrue(policy.checkCommand("uninstall WhatsApp").blocked)
    }

    @Test
    fun blocksDeletion() {
        assertTrue(policy.checkCommand("delete this chat").blocked)
        assertTrue(policy.checkCommand("factory reset the phone").blocked)
    }

    @Test
    fun blocksAccountSecurityChanges() {
        assertTrue(policy.checkCommand("change my password").blocked)
        assertTrue(policy.checkCommand("reset the security code").blocked)
    }

    @Test
    fun blocksOtpRequests() {
        assertTrue(policy.checkCommand("read my OTP").blocked)
    }

    @Test
    fun allowsBenignCommands() {
        assertFalse(policy.checkCommand("call Amma").blocked)
        assertFalse(policy.checkCommand("change the sleep time to 60 seconds").blocked)
    }

    @Test
    fun blocksDeniedPackage() {
        assertTrue(policy.checkPackage("com.example.payment").blocked)
        assertTrue(policy.checkPackage("com.example.bankapp").blocked)
        assertFalse(policy.checkPackage("com.whatsapp").blocked)
    }

    @Test
    fun blocksPasswordField() {
        assertTrue(policy.checkTarget("com.whatsapp", null, isPassword = true).blocked)
    }

    @Test
    fun blocksCredentialScreen() {
        assertTrue(
            policy.checkTarget(
                "com.example.unknown",
                "Enter CVV",
                isPassword = false,
            ).blocked,
        )
    }

    @Test
    fun blocksOtpInScreenText() {
        assertTrue(
            policy.checkTarget(
                "com.example.unknown",
                "Verify",
                isPassword = false,
                screenText = "Your one-time password is ready",
            ).blocked,
        )
    }

    @Test
    fun allowsNormalScreen() {
        assertFalse(
            policy.checkTarget(
                "com.whatsapp",
                "Send",
                isPassword = false,
                screenText = "Chat with Amma",
            ).blocked,
        )
    }
}
