package com.localglasses.app.agent.commands

import com.google.ai.edge.litertlm.tool
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.safety.SafetyPolicy

/**
 * Stateless command router. Each call builds a fresh conversation with the same
 * system prompt and only the current command, then returns exactly one decision.
 */
class CommandRouter(
    private val engineHolder: EngineHolder,
    private val safetyPolicy: SafetyPolicy,
    private val suggestions: (String) -> List<String> = ::defaultSuggestions,
) {

    suspend fun route(command: String): RoutingDecision {
        safetyPolicy.checkCommand(command).takeIf { it.blocked }?.let {
            return RoutingDecision.Blocked(it.reason ?: "Blocked by safety policy")
        }

        if (asksForUi(command)) {
            return RoutingDecision.UiPlan(goal = command, app = appFrom(command))
        }

        val engine = engineHolder.ensureLoaded().getOrElse { error ->
            return RoutingDecision.Failed(error.message ?: "Model unavailable")
        }

        val reply = try {
            engine.conversation(
                systemPrompt = SYSTEM_PROMPT,
                tools = listOf(tool(RouterTools())),
                automaticToolCalling = false,
            ).use { conversation ->
                conversation.send(command)
            }
        } catch (error: Exception) {
            return RoutingDecision.Failed(error.message ?: "Model inference failed")
        }

        val decision = ToolCallParser.parse(reply.toolCalls)
        return if (decision is RoutingDecision.NoMatch && decision.suggestions.isEmpty()) {
            decision.copy(suggestions = suggestions(command))
        } else {
            decision
        }
    }

    private fun asksForUi(command: String): Boolean {
        val text = command.lowercase()
        return "using the ui" in text || "on-screen" in text || "on screen" in text
    }

    private fun appFrom(command: String): String? {
        val text = command.lowercase()
        return when {
            "whatsapp" in text -> "com.whatsapp"
            "youtube music" in text -> "com.google.android.apps.youtube.music"
            "youtube" in text -> "com.google.android.youtube"
            else -> null
        }
    }

    companion object {
        val SYSTEM_PROMPT = """
            You are the LocalGlasses command router.
            Choose exactly one tool that matches the user's command.
            Never answer in prose and never chain tools.
            Installed targets: WhatsApp, YouTube, YouTube Music.
            Settings ids: screen_timeout_seconds, brightness_percent, media_volume_percent,
            dnd_on, dnd_off, rotation_lock, rotation_unlock, flashlight_on, flashlight_off.
            "sleep time" or "screen timeout" maps to screen_timeout_seconds.
            Examples:
            call Amma -> call_contact(contact="Amma")
            call 7396512369 -> call_contact(contact="7396512369")
            send an SMS to 7396512369 saying hello -> send_sms(contact="7396512369", message="hello")
            change the sleep time to 60 seconds -> set_device_setting(setting="screen_timeout_seconds", value=60)
            play any song by Kanye West -> play_media(query="Kanye West", service="youtube")
            open WhatsApp -> plan_ui_task(goal="open WhatsApp", app="com.whatsapp")
            Open an app with plan_ui_task, never with open_settings_page.
            If the user says "using the UI" or "on-screen", call plan_ui_task and never a registry action.
            open_settings_page is only for Android Settings pages such as display or wifi.
            If no tool fits, return no tool call.
        """.trimIndent()

        private val templates = listOf(
            "call a contact",
            "send an sms",
            "send a whatsapp message",
            "play a song on youtube",
            "change screen timeout",
            "change brightness",
            "set media volume",
            "open a settings page",
        )

        fun defaultSuggestions(command: String): List<String> {
            val tokens = command.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 2 }
                .toSet()
            return templates
                .map { template ->
                    template to template.split(" ").count { it in tokens }
                }
                .sortedByDescending { it.second }
                .take(3)
                .map { it.first }
        }
    }
}
