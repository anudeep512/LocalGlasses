package com.localglasses.app.agent.safety

import android.content.Context
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SafetyDenylist(
    val packages: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

data class SafetyVerdict(
    val blocked: Boolean,
    val reason: String? = null,
) {
    companion object {
        val Allowed = SafetyVerdict(blocked = false)

        fun block(reason: String) = SafetyVerdict(blocked = true, reason = reason)
    }
}

/**
 * Non-overridable limits on what the agent may read or automate.
 *
 * Checks run before planning, before approval, and before every UI step. Approval
 * cannot turn a blocked verdict into an allowed one.
 */
class SafetyPolicy(
    private val blockedPackages: Set<String> = emptySet(),
    private val blockedKeywords: Set<String> = emptySet(),
) {

    fun checkCommand(command: String): SafetyVerdict {
        val text = command.lowercase()
        if (installOrUninstall.containsMatchIn(text)) {
            return SafetyVerdict.block("App installation or removal is blocked")
        }
        if (deletePattern.containsMatchIn(text)) {
            return SafetyVerdict.block("Deleting files, chats, or photos is blocked")
        }
        if (accountSecurity.containsMatchIn(text)) {
            return SafetyVerdict.block("Account password or security changes are blocked")
        }
        if (otpPattern.containsMatchIn(text)) {
            return SafetyVerdict.block("OTP access is blocked")
        }
        if (blockedKeywords.any { text.contains(it.lowercase()) }) {
            return SafetyVerdict.block("This command touches a blocked category")
        }
        return SafetyVerdict.Allowed
    }

    fun checkPackage(packageName: String?): SafetyVerdict {
        val name = packageName?.lowercase() ?: return SafetyVerdict.Allowed
        if (blockedPackages.any { it.equals(packageName, ignoreCase = true) }) {
            return SafetyVerdict.block("Blocked package: $packageName")
        }
        if (blockedPackageFragments.any { name.contains(it) }) {
            return SafetyVerdict.block("Blocked package category: $packageName")
        }
        return SafetyVerdict.Allowed
    }

    fun checkPlan(plan: Plan): SafetyVerdict {
        plan.steps.forEach { step ->
            when (step) {
                is PlanStep.OpenApp -> checkPackage(step.packageName)
                is PlanStep.Tap -> checkTarget(null, step.target.text, isPassword = false)
                is PlanStep.TypeText -> checkTarget(
                    packageName = null,
                    targetText = step.target.text,
                    isPassword = false,
                    screenText = step.text,
                )
                else -> SafetyVerdict.Allowed
            }.takeIf { it.blocked }?.let { return it }
        }
        return SafetyVerdict.Allowed
    }

    fun checkTarget(
        packageName: String?,
        targetText: String?,
        isPassword: Boolean,
        screenText: String = "",
    ): SafetyVerdict {
        checkPackage(packageName).takeIf { it.blocked }?.let { return it }
        if (isPassword) return SafetyVerdict.block("Password fields are blocked")
        val haystack = listOfNotNull(targetText, screenText).joinToString(" ").lowercase()
        if (otpPattern.containsMatchIn(haystack)) {
            return SafetyVerdict.block("OTP content is blocked")
        }
        if (credentialKeywords.any { haystack.contains(it) }) {
            return SafetyVerdict.block("Credential or payment screen is blocked")
        }
        return SafetyVerdict.Allowed
    }

    companion object {
        private val installOrUninstall = Regex("(?i)\\b(install|uninstall|remove app)\\b")
        private val deletePattern = Regex(
            "(?i)\\b(delete|erase|remove all|format|factory reset)\\b",
        )
        private val accountSecurity = Regex(
            "(?i)\\b(change|reset|update)\\b.{0,20}\\b(password|passcode|pin|2fa|security)\\b",
        )
        private val otpPattern = Regex(
            "(?i)\\b(otp|one[- ]time (?:password|code)|verification code|2fa code|security code)\\b",
        )
        private val blockedPackageFragments = listOf(
            "bank",
            "pay",
            "wallet",
            "upi",
            "password",
            "authenticator",
            "credential",
        )
        private val credentialKeywords = listOf(
            "card number",
            "cvv",
            "upi pin",
            "bank account",
            "account number",
            "password",
            "passcode",
            "one-time password",
            "otp",
            "verify your identity",
        )

        fun fromAssets(context: Context): SafetyPolicy {
            val raw = context.assets.open("safety_denylist.json").bufferedReader().use { it.readText() }
            val list = Json { ignoreUnknownKeys = true }.decodeFromString<SafetyDenylist>(raw)
            return SafetyPolicy(
                blockedPackages = list.packages.toSet(),
                blockedKeywords = list.keywords.toSet(),
            )
        }
    }
}
