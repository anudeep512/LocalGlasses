package com.localglasses.app.agent.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class ActionRegistry(
    private val context: Context,
    private val contacts: ContactsResolver,
    private val settings: SettingsController,
) {

    fun execute(actionId: String, arguments: Map<String, Any?>): ActionExecution =
        when (actionId) {
            "call_contact" -> call(arguments["contact"]?.toString())
            "send_sms" -> sendSms(
                arguments["contact"]?.toString(),
                arguments["message"]?.toString(),
            )
            "send_whatsapp" -> sendWhatsapp(
                arguments["contact"]?.toString(),
                arguments["message"]?.toString(),
            )
            "set_device_setting" -> {
                val setting = arguments["setting"] as? String
                val value = (arguments["value"] as? Number)?.toInt()
                if (setting == null || value == null) {
                    ActionExecution.Failed("Setting and value are required")
                } else {
                    settings.apply(setting, value)
                }
            }
            "open_settings_page" -> {
                val page = arguments["page"] as? String
                if (page == null) ActionExecution.Failed("Page is required")
                else settings.openPage(page)
            }
            else -> ActionExecution.Failed("Unknown action: $actionId")
        }

    private fun call(query: String?): ActionExecution {
        if (query.isNullOrBlank()) return ActionExecution.Failed("Contact or number is required")
        if (!granted(Manifest.permission.CALL_PHONE)) {
            return ActionExecution.NeedsPermission(Manifest.permission.CALL_PHONE)
        }
        return when (val resolved = contacts.resolve(query)) {
            is ContactResolution.Unique -> launch(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(resolved.contact.phone)}")),
                "Calling ${resolved.contact.name}",
            )
            is ContactResolution.Multiple -> ActionExecution.NeedsChoice(resolved.candidates)
            ContactResolution.None -> if (!granted(Manifest.permission.READ_CONTACTS)) {
                ActionExecution.NeedsPermission(Manifest.permission.READ_CONTACTS)
            } else {
                ActionExecution.Failed("No contact or number matches $query")
            }
        }
    }

    private fun sendSms(query: String?, message: String?): ActionExecution {
        if (query.isNullOrBlank() || message.isNullOrBlank()) {
            return ActionExecution.Failed("Contact and message are required")
        }
        if (!granted(Manifest.permission.SEND_SMS)) {
            return ActionExecution.NeedsPermission(Manifest.permission.SEND_SMS)
        }
        return when (val resolved = contacts.resolve(query)) {
            is ContactResolution.Unique -> runCatching {
                SmsManager.getDefault().sendTextMessage(
                    resolved.contact.phone,
                    null,
                    message,
                    null,
                    null,
                )
                ActionExecution.Success("SMS sent to ${resolved.contact.name}")
            }.getOrElse { ActionExecution.Failed(it.message ?: "SMS send failed") }
            is ContactResolution.Multiple -> ActionExecution.NeedsChoice(resolved.candidates)
            ContactResolution.None -> if (!granted(Manifest.permission.READ_CONTACTS)) {
                ActionExecution.NeedsPermission(Manifest.permission.READ_CONTACTS)
            } else {
                ActionExecution.Failed("No contact or number matches $query")
            }
        }
    }

    private fun sendWhatsapp(query: String?, message: String?): ActionExecution {
        if (query.isNullOrBlank() || message.isNullOrBlank()) {
            return ActionExecution.Failed("Contact and message are required")
        }
        if (!installed("com.whatsapp")) {
            return ActionExecution.Failed("WhatsApp is not installed")
        }
        return when (val resolved = contacts.resolve(query)) {
            is ContactResolution.Unique -> {
                val digits = waDigits(resolved.contact.phone)
                val uri = Uri.parse(
                    "https://wa.me/$digits?text=${Uri.encode(message)}",
                )
                val launched = launch(
                    Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"),
                    "WhatsApp opened",
                )
                if (launched is ActionExecution.Success) {
                    ActionExecution.Failed(
                        "WhatsApp opened with the draft; automatic send needs the accessibility executor",
                    )
                } else {
                    launched
                }
            }
            is ContactResolution.Multiple -> ActionExecution.NeedsChoice(resolved.candidates)
            ContactResolution.None -> if (!granted(Manifest.permission.READ_CONTACTS)) {
                ActionExecution.NeedsPermission(Manifest.permission.READ_CONTACTS)
            } else {
                ActionExecution.Failed("No contact or number matches $query")
            }
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

    private fun launch(intent: Intent, success: String): ActionExecution = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionExecution.Success(success)
    }.getOrElse { ActionExecution.Failed(it.message ?: "Cannot open app") }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun installed(packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
