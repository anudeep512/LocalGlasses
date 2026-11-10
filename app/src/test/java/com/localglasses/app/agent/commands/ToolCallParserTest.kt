package com.localglasses.app.agent.commands

import com.localglasses.app.agent.model.InferenceToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun noCallsIsNoMatch() {
        assertTrue(ToolCallParser.parse(emptyList()) is RoutingDecision.NoMatch)
    }

    @Test
    fun oneCallBecomesAction() {
        val decision = ToolCallParser.parse(
            listOf(InferenceToolCall("call_contact", mapOf("contact" to "Amma"))),
        )
        assertTrue(decision is RoutingDecision.Action)
        assertEquals("call_contact", (decision as RoutingDecision.Action).request.actionId)
        assertEquals("Amma", decision.request.arguments["contact"])
    }

    @Test
    fun camelCaseToolNameIsAccepted() {
        val decision = ToolCallParser.parse(
            listOf(InferenceToolCall("callContact", mapOf("contact" to "Amma"))),
        )
        assertTrue(decision is RoutingDecision.Action)
    }

    @Test
    fun multipleCallsAreRejected() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall("call_contact", mapOf("contact" to "Amma")),
                InferenceToolCall("call_contact", mapOf("contact" to "Appa")),
            ),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun unknownToolIsRejected() {
        val decision = ToolCallParser.parse(listOf(InferenceToolCall("delete_everything", emptyMap())))
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun missingArgumentIsRejected() {
        val decision = ToolCallParser.parse(listOf(InferenceToolCall("send_sms", mapOf("contact" to "Amma"))))
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun unexpectedArgumentIsRejected() {
        val decision = ToolCallParser.parse(
            listOf(InferenceToolCall("call_contact", mapOf("contact" to "Amma", "number" to "123"))),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun wrongValueTypeIsRejected() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall(
                    "set_device_setting",
                    mapOf("setting" to "brightness_percent", "value" to "50"),
                ),
            ),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun validSettingsCallIsAccepted() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall(
                    "set_device_setting",
                    mapOf("setting" to "screen_timeout_seconds", "value" to 60),
                ),
            ),
        )
        assertTrue(decision is RoutingDecision.Action)
    }

    @Test
    fun unknownSettingIsRejected() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall(
                    "set_device_setting",
                    mapOf("setting" to "arbitrary_key", "value" to 1),
                ),
            ),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun planToolBecomesUiPlan() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall(
                    "plan_ui_task",
                    mapOf("goal" to "send a message", "app" to "com.whatsapp"),
                ),
            ),
        )
        assertTrue(decision is RoutingDecision.UiPlan)
        assertEquals("com.whatsapp", (decision as RoutingDecision.UiPlan).app)
    }

    @Test
    fun blankPlanGoalIsRejected() {
        val decision = ToolCallParser.parse(
            listOf(InferenceToolCall("plan_ui_task", mapOf("goal" to " "))),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }

    @Test
    fun invalidMediaServiceIsRejected() {
        val decision = ToolCallParser.parse(
            listOf(
                InferenceToolCall(
                    "play_media",
                    mapOf("query" to "Kanye West", "service" to "spotify"),
                ),
            ),
        )
        assertTrue(decision is RoutingDecision.NoMatch)
    }
}
