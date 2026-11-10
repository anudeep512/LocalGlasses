package com.localglasses.app.agent.commands

import com.google.ai.edge.litertlm.ToolProvider
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.model.InferenceConversation
import com.localglasses.app.agent.model.InferenceEngine
import com.localglasses.app.agent.model.InferenceReply
import com.localglasses.app.agent.model.InferenceToolCall
import com.localglasses.app.agent.safety.SafetyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {

    @Test
    fun registryCommandBecomesAction() = runTest {
        val engine = FakeEngine(
            InferenceReply("", listOf(InferenceToolCall("call_contact", mapOf("contact" to "Amma")))),
        )
        val router = router(engine)

        val decision = router.route("call Amma")

        assertTrue(decision is RoutingDecision.Action)
        assertEquals(1, engine.loads)
        assertEquals("test-model", decision.metrics?.modelId)
        assertTrue((decision.metrics?.inferenceLatencyMs ?: -1) >= 0)
    }

    @Test
    fun genericCommandBecomesUiPlan() = runTest {
        val engine = FakeEngine(
            InferenceReply(
                "",
                listOf(InferenceToolCall("plan_ui_task", mapOf("goal" to "send a message"))),
            ),
        )
        val router = router(engine)

        assertTrue(router.route("say hi to Amma") is RoutingDecision.UiPlan)
    }

    @Test
    fun noToolCallProducesSuggestions() = runTest {
        val router = router(FakeEngine(InferenceReply("", emptyList())))

        val decision = router.route("make me a sandwich")

        assertTrue(decision is RoutingDecision.NoMatch)
        assertEquals(3, (decision as RoutingDecision.NoMatch).suggestions.size)
    }

    @Test
    fun blockedCommandNeverLoadsModel() = runTest {
        val engine = FakeEngine(InferenceReply("", emptyList()))
        val router = router(engine)

        val decision = router.route("install a new app")

        assertTrue(decision is RoutingDecision.Blocked)
        assertEquals(0, engine.loads)
        assertEquals(null, decision.metrics)
    }

    @Test
    fun commandsAreStateless() = runTest {
        val engine = FakeEngine(InferenceReply("", emptyList()))
        val router = router(engine)

        router.route("first command")
        router.route("second command")

        assertEquals(listOf("first command", "second command"), engine.sent)
        assertEquals(1, engine.systemPrompts.distinct().size)
    }

    @Test
    fun inferenceFailureKeepsLatencyMetrics() = runTest {
        val engine = FakeEngine(InferenceReply("", emptyList()), failSend = true)
        val router = router(engine)

        val decision = router.route("call Amma")

        assertTrue(decision is RoutingDecision.Failed)
        assertEquals("test-model", decision.metrics?.modelId)
        assertTrue((decision.metrics?.inferenceLatencyMs ?: -1) >= 0)
    }

    @Test
    fun loadFailureIsReported() = runTest {
        val holder = EngineHolder(
            factory = { error("model missing") },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val router = CommandRouter(holder, SafetyPolicy())

        assertTrue(router.route("call Amma") is RoutingDecision.Failed)
    }

    private fun router(engine: FakeEngine): CommandRouter {
        val holder = EngineHolder(
            factory = { engine },
            ioDispatcher = Dispatchers.Unconfined,
        )
        return CommandRouter(holder, SafetyPolicy())
    }

    private class FakeEngine(
        private val reply: InferenceReply,
        private val failSend: Boolean = false,
    ) : InferenceEngine {
        var loads = 0
        val sent = mutableListOf<String>()
        val systemPrompts = mutableListOf<String>()
        private var loaded = false

        override val isLoaded: Boolean get() = loaded
        override val modelId: String = "test-model"
        override val backendName: String = "CPU"

        override fun load() {
            loads += 1
            loaded = true
        }

        override fun close() {
            loaded = false
        }

        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation {
            systemPrompts += systemPrompt
            return object : InferenceConversation {
                override fun send(command: String): InferenceReply {
                    sent += command
                    if (failSend) error("inference failed")
                    return reply
                }

                override fun close() = Unit
            }
        }
    }
}
