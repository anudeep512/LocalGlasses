package com.localglasses.app.agent.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.automation.AccessibilityGateway
import kotlinx.coroutines.delay

/**
 * WhatsApp has no public send API. The approved action opens the prefilled
 * conversation, then the Accessibility gateway taps the send control. If the
 * control is missing, the draft stays open and the action fails closed.
 */
class WhatsAppSender(
    private val context: Context,
    private val contacts: ContactsResolver,
) {

    suspend fun send(
        contactQuery: String?,
        message: String?,
        gateway: AccessibilityGateway,
    ): ActionExecution {
        if (contactQuery.isNullOrBlank() || message.isNullOrBlank()) {
            return ActionExecution.Failed("Contact and message are required")
        }
        if (!installed()) {
            return ActionExecution.Failed("WhatsApp is not installed")
        }
        val resolved = contacts.resolve(contactQuery)
        val target = when (resolved) {
            is ContactResolution.Unique -> resolved.contact
            is ContactResolution.Multiple -> return ActionExecution.NeedsChoice(resolved.candidates)
            ContactResolution.None -> return ActionExecution.Failed("No contact or number matches $contactQuery")
        }

        val digits = waDigits(target.phone)
        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        val launched = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage("com.whatsapp")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!launched) return ActionExecution.Failed("Could not open WhatsApp")

        delay(1_500)
        val tapped = gateway.tap(NodeSelector(viewId = "com.whatsapp:id/send")) ||
            gateway.tap(NodeSelector(contentDescription = "Send"))
        return if (tapped) {
            ActionExecution.Success("WhatsApp message sent to ${target.name}")
        } else {
            ActionExecution.Failed("WhatsApp send control not found; draft left open")
        }
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
            android.telephony.PhoneNumberUtils.formatNumberToE164(normalized, iso)
                ?: normalized
        }
        return e164.filter { it.isDigit() }
    }

    private fun installed(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.whatsapp", 0)
        true
    }.getOrDefault(false)
}
