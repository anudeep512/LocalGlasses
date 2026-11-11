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

    @Test
    fun retriesOnceWhenTheModelEmitsAnUnparseableToolCall() = runTest {
        val valid = InferenceReply(
            "",
            listOf(
                InferenceToolCall(
                    "submit_plan",
                    mapOf(
                        "steps" to listOf(mapOf("type" to "open_app", "package" to "com.whatsapp")),
                    ),
                ),
            ),
        )
        val engine = FlakyEngine(
            failures = 1,
            reply = valid,
        )
        val planner = UiPlanner(EngineHolder({ engine }, Dispatchers.Unconfined))

        val plan = planner.plan("open WhatsApp", "open WhatsApp", "com.whatsapp").getOrThrow()

        assertEquals(1, plan.steps.size)
        assertEquals(2, engine.attempts)
        assertTrue(
            "the retry must restate the output contract",
            engine.prompts.last().contains("Return exactly one submit_plan tool call"),
        )
    }

    @Test
    fun aSecondParseFailureIsReportedPlainly() = runTest {
        val engine = FlakyEngine(failures = 2, reply = InferenceReply("", emptyList()))
        val planner = UiPlanner(EngineHolder({ engine }, Dispatchers.Unconfined))

        val error = planner.plan("open YouTube", "play a video", "com.google.android.youtube")
            .exceptionOrNull()

        assertEquals("The model returned an unreadable plan. Try rephrasing.", error?.message)
        assertEquals(2, engine.attempts)
    }

    private class FlakyEngine(
        private val failures: Int,
        private val reply: InferenceReply,
    ) : InferenceEngine {
        private var loaded = false
        var attempts = 0
        val prompts = mutableListOf<String>()
        override val isLoaded: Boolean get() = loaded
        override val modelId: String = "test-model"
        override val backendName: String = "GPU"
        override fun load() { loaded = true }
        override fun close() { loaded = false }
        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation = object : InferenceConversation {
            override fun send(command: String): InferenceReply {
                attempts++
                prompts += command
                if (attempts <= failures) {
                    throw IllegalStateException(
                        "Failed to call nativeSendMessage: INVALID_ARGUMENT: " +
                            "Failed to parse tool calls from code block",
                    )
                }
                return reply
            }

            override fun close() = Unit
        }
    }

    private class FakeEngine(private val reply: InferenceReply) : InferenceEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override val modelId: String = "test-model"
        override val backendName: String = "CPU"
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
