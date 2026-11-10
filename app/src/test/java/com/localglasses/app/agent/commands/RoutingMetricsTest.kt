package com.localglasses.app.agent.commands

import com.localglasses.app.agent.telemetry.InferenceMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingMetricsTest {

    private val metrics = InferenceMetrics(modelId = "gemma", decodeTps = 9.5)

    @Test
    fun attachesMetricsToEveryVariant() {
        val decisions = listOf(
            RoutingDecision.Action(RegistryActionRequest("call_contact", emptyMap())),
            RoutingDecision.UiPlan("open whatsapp", "com.whatsapp"),
            RoutingDecision.NoMatch("nope"),
            RoutingDecision.Blocked("blocked"),
            RoutingDecision.Failed("failed"),
        )

        decisions.forEach { decision ->
            assertEquals(metrics, decision.withMetrics(metrics).metrics)
        }
    }
}
