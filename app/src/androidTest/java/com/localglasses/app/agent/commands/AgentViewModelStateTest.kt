package com.localglasses.app.agent.commands

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.ToolProvider
import com.localglasses.app.LocalGlassesApp
import com.localglasses.app.agent.approval.PlanCoordinator
import com.localglasses.app.agent.approval.PlanState
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.model.InferenceConversation
import com.localglasses.app.agent.model.InferenceEngine
import com.localglasses.app.agent.model.InferenceReply
import com.localglasses.app.agent.model.InferenceToolCall
import com.localglasses.app.agent.model.ModelState
import com.localglasses.app.agent.safety.SafetyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentViewModelStateTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<LocalGlassesApp>()

    @Test
    fun needsModelWhenStateIsAbsent() = runTest {
        val viewModel = viewModel(reply = null, modelState = ModelState.Absent)

        viewModel.onCommandChange("call Amma")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status.contains("Model is not ready"))
        assertEquals(PlanState.Idle, viewModel.planState.value)
    }

    @Test
    fun successCreatesPendingPlanAndSecondCommandIsRejected() = runTest {
        val viewModel = viewModel(
            reply = InferenceReply(
                "",
                listOf(InferenceToolCall("call_contact", mapOf("contact" to "Amma"))),
            ),
        )

        viewModel.onCommandChange("call Amma")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.planState.value is PlanState.Pending)
        assertTrue(viewModel.uiState.value.status.contains("Plan ready"))

        viewModel.onCommandChange("call Appa")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.status.contains("pending plan"))
    }

    @Test
    fun noMatchIsReported() = runTest {
        val viewModel = viewModel(reply = InferenceReply("", emptyList()))

        viewModel.onCommandChange("make me a sandwich")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status.contains("Not understood"))
    }

    @Test
    fun blockedCommandNeverLoadsModel() = runTest {
        val fake = FakeEngine(null)
        val viewModel = viewModel(fake = fake)

        viewModel.onCommandChange("install a new app")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status.contains("Blocked"))
        assertEquals(0, fake.loads)
    }

    @Test
    fun loadFailureIsReported() = runTest {
        val viewModel = viewModel(fake = FakeEngine(null, failLoad = true))

        viewModel.onCommandChange("call Amma")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status.contains("Failed"))
    }

    @Test
    fun missingAccessibilityIsReportedForUiTask() = runTest {
        val viewModel = viewModel(
            reply = InferenceReply(
                "",
                listOf(InferenceToolCall("plan_ui_task", mapOf("goal" to "open WhatsApp"))),
            ),
            accessibility = false,
        )

        viewModel.onCommandChange("open WhatsApp")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status.contains("Accessibility service is not enabled"))
        assertEquals(PlanState.Idle, viewModel.planState.value)
    }

    private fun viewModel(
        reply: InferenceReply? = null,
        fake: FakeEngine = FakeEngine(reply),
        modelState: ModelState = ModelState.Ready,
        accessibility: Boolean = true,
    ): AgentViewModel {
        val holder = EngineHolder({ fake }, Dispatchers.Unconfined)
        return AgentViewModel(
            app = app,
            routerOverride = CommandRouter(holder, SafetyPolicy()),
            modelStateOverride = MutableStateFlow(modelState),
            planCoordinatorOverride = PlanCoordinator(),
            accessibilityOverride = accessibility,
        )
    }

    private class FakeEngine(
        private val reply: InferenceReply?,
        private val failLoad: Boolean = false,
    ) : InferenceEngine {
        var loads = 0
        private var loaded = false

        override val isLoaded: Boolean get() = loaded

        override fun load() {
            loads += 1
            if (failLoad) error("model load failed")
            loaded = true
        }

        override fun close() {
            loaded = false
        }

        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation = object : InferenceConversation {
            override fun send(command: String): InferenceReply =
                reply ?: error("no reply configured")

            override fun close() = Unit
        }
    }
}
