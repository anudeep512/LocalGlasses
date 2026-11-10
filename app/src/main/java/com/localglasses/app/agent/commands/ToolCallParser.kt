package com.localglasses.app.agent.commands

import com.localglasses.app.agent.model.InferenceToolCall

data class RegistryActionRequest(
    val actionId: String,
    val arguments: Map<String, Any?>,
)

sealed interface RoutingDecision {
    data class Action(val request: RegistryActionRequest) : RoutingDecision
    data class UiPlan(val goal: String, val app: String?) : RoutingDecision
    data class NoMatch(
        val reason: String,
        val suggestions: List<String> = emptyList(),
    ) : RoutingDecision
    data class Blocked(val reason: String) : RoutingDecision
    data class Failed(val reason: String) : RoutingDecision
}

/**
 * Converts model tool calls into exactly one task.
 *
 * The model is allowed to be wrong; this parser is the boundary that rejects unknown
 * tools, missing or unexpected arguments, wrong types, and more than one call.
 */
object ToolCallParser {

    private val mediaServices = setOf("youtube", "youtube_music")
    private val settings = setOf(
        "screen_timeout_seconds",
        "brightness_percent",
        "media_volume_percent",
        "dnd_on",
        "dnd_off",
        "rotation_lock",
        "rotation_unlock",
        "flashlight_on",
        "flashlight_off",
        "open_page",
    )

    fun parse(calls: List<InferenceToolCall>): RoutingDecision {
        if (calls.size != 1) {
            return RoutingDecision.NoMatch(
                if (calls.isEmpty()) "The model selected no task" else "The model selected more than one task",
            )
        }
        val call = calls.single()
        val args = call.arguments
        return when (normalize(call.name)) {
            RouterToolNames.CALL_CONTACT -> action(
                "call_contact",
                args,
                setOf("contact"),
                "contact" to ::nonBlank,
            )
            RouterToolNames.SEND_SMS -> action(
                "send_sms",
                args,
                setOf("contact", "message"),
                "contact" to ::nonBlank,
                "message" to ::nonBlank,
            )
            RouterToolNames.SEND_WHATSAPP -> action(
                "send_whatsapp",
                args,
                setOf("contact", "message"),
                "contact" to ::nonBlank,
                "message" to ::nonBlank,
            )
            RouterToolNames.PLAY_MEDIA -> action(
                "play_media",
                args,
                setOf("query", "service"),
                "query" to ::nonBlank,
                "service" to { value -> nonBlank(value) && value.toString() in mediaServices },
            )
            RouterToolNames.SET_DEVICE_SETTING -> action(
                "set_device_setting",
                args,
                setOf("setting", "value"),
                "setting" to { value -> value?.toString()?.let { it in settings } == true },
                "value" to { value -> value is Number },
            )
            RouterToolNames.OPEN_SETTINGS_PAGE -> action(
                "open_settings_page",
                args,
                setOf("page"),
                "page" to ::nonBlank,
            )
            RouterToolNames.PLAN_UI_TASK -> plan(args)
            else -> RoutingDecision.NoMatch("Unknown tool ${call.name}")
        }
    }

    private fun plan(args: Map<String, Any?>): RoutingDecision {
        if (args.keys.any { it != "goal" && it != "app" }) {
            return RoutingDecision.NoMatch("Unexpected plan arguments")
        }
        val goal = args["goal"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return RoutingDecision.NoMatch("Plan goal is missing")
        val app = args["app"]?.toString()?.takeIf { it.isNotBlank() }
        return RoutingDecision.UiPlan(goal, app)
    }

    private fun action(
        actionId: String,
        args: Map<String, Any?>,
        allowed: Set<String>,
        vararg checks: Pair<String, (Any?) -> Boolean>,
    ): RoutingDecision {
        if (args.keys != allowed) {
            return RoutingDecision.NoMatch("Unexpected arguments for $actionId")
        }
        for ((name, check) in checks) {
            if (!check(args[name])) {
                return RoutingDecision.NoMatch("Invalid $name for $actionId")
            }
        }
        return RoutingDecision.Action(RegistryActionRequest(actionId, args))
    }

    private fun normalize(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    private fun nonBlank(value: Any?): Boolean =
        value is String && value.isNotBlank()
}
