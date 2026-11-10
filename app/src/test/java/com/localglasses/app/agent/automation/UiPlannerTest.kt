package com.localglasses.app.agent.automation

import com.google.ai.edge.litertlm.ToolProvider
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.model.InferenceConversation
import com.localglasses.app.agent.model.InferenceEngine
import com.localglasses.app.agent.model.InferenceReply
import com.localglasses.app.agent.model.InferenceToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPlannerTest {

    @Test
    fun parsesSubmitPlanToolCall() = runTest {
        val engine = FakeEngine(
            InferenceReply(
                "",
                listOf(
                    InferenceToolCall(
                        "submit_plan",
                        mapOf(
                            "steps" to listOf(
                                mapOf("type" to "open_app", "package" to "com.whatsapp"),
                                mapOf("type" to "wait", "millis" to 500),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val planner = UiPlanner(
            EngineHolder({ engine }, Dispatchers.Unconfined),
        )

        val plan = planner.plan("open WhatsApp", "open WhatsApp", "com.whatsapp").getOrThrow()

        assertEquals(2, plan.steps.size)
        assertTrue(plan.steps.first() is com.localglasses.app.agent.approval.PlanStep.OpenApp)
    }

    @Test
    fun rejectsEmptyPlan() = runTest {
        val engine = FakeEngine(InferenceReply("", emptyList()))
        val planner = UiPlanner(EngineHolder({ engine }, Dispatchers.Unconfined))

        assertTrue(planner.plan("x", "x", null).isFailure)
    }

    private class FakeEngine(private val reply: InferenceReply) : InferenceEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override fun load() { loaded = true }
        override fun close() { loaded = false }
        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation = object : InferenceConversation {
            override fun send(command: String): InferenceReply = reply
            override fun close() = Unit
        }
    }
}
