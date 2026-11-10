package com.localglasses.app.agent.actions

import android.content.Context
import android.net.Uri
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.approval.PlanValidator

/**
 * A generic UI plan, not a registry action: open the prefilled wa.me URI, wait for
 * WhatsApp, then tap Send. It is shown in the normal editable plan sheet.
 */
class WhatsAppUiPlanFactory(
    private val context: Context,
    private val contacts: ContactsResolver,
) {

    fun build(command: String, goal: String, app: String?): Plan? {
        if (app != "com.whatsapp" && !goal.contains("whatsapp", ignoreCase = true)) return null
        val number = NUMBER.find(goal)?.value ?: return null
        val message = MESSAGE.find(goal)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (message.isBlank()) return null

        val phone = (contacts.resolve(number) as? ContactResolution.Unique)?.contact?.phone ?: number
        val query = "?text=${Uri.encode(message)}"
        val plan = Plan(
            command = command,
            steps = listOf(
                PlanStep.OpenUri("https://wa.me/${waDigits(phone)}$query"),
                PlanStep.Wait(1_500),
                PlanStep.Tap(NodeSelector(contentDescription = "Send")),
            ),
            targetPackage = "com.whatsapp",
        )
        return PlanValidator.validate(plan).getOrNull()
    }

    private fun waDigits(phone: String): String {
        val normalized = PhoneNumbers.normalize(phone)
        val e164 = if (normalized.startsWith("+")) {
            normalized
        } else {
            val telephony = context.getSystemService(android.telephony.TelephonyManager::class.java)
            val iso = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
                ?: java.util.Locale.getDefault().country
            android.telephony.PhoneNumberUtils.formatNumberToE164(normalized, iso) ?: normalized
        }
        return e164.filter { it.isDigit() }
    }

    private companion object {
        val NUMBER = Regex("""(?:\+?\d[\d\s-]{6,}\d)""")
        val MESSAGE = Regex("""saying\s+(.+)$""", RegexOption.IGNORE_CASE)
    }
}
