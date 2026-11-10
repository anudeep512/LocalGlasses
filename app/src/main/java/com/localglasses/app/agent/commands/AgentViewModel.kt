package com.localglasses.app.agent.commands

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localglasses.app.LocalGlassesApp
import com.localglasses.app.agent.actions.ActionExecution
import com.localglasses.app.agent.automation.AgentAccessibilityService
import com.localglasses.app.agent.automation.AndroidAccessibilityGateway
import com.localglasses.app.agent.automation.PlanExecutor
import com.localglasses.app.agent.automation.UiPlanner
import com.localglasses.app.agent.approval.ExecutionOverlay
import com.localglasses.app.agent.automation.ScreenshotTaker
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanCoordinator
import com.localglasses.app.agent.approval.PlanFactory
import com.localglasses.app.agent.approval.PlanReplanner
import com.localglasses.app.agent.approval.PlanState
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.model.AgentWarmService
import com.localglasses.app.agent.PermissionsCoordinator
import com.localglasses.app.agent.model.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AgentUiState(
    val command: String = "",
    val status: String = "",
    val isSubmitting: Boolean = false,
    val suggestions: List<String> = emptyList(),
)

class AgentViewModel @JvmOverloads constructor(
    app: Application,
    private val routerOverride: CommandRouter? = null,
    private val modelStateOverride: StateFlow<ModelState>? = null,
    private val planCoordinatorOverride: PlanCoordinator? = null,
    private val accessibilityOverride: Boolean? = null,
) : AndroidViewModel(app) {

    private val container = (app as LocalGlassesApp).agentContainer
    private val router = routerOverride ?: container.commandRouter
    private val coordinator = planCoordinatorOverride ?: container.planCoordinator

    val modelState: StateFlow<ModelState> = modelStateOverride ?: container.modelRepository.state
    val planState: StateFlow<PlanState> = coordinator.state

    private val accessibilityAvailable: Boolean
        get() = accessibilityOverride
            ?: (AgentAccessibilityService.instance != null ||
                AgentAccessibilityService.isEnabled(getApplication()))

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var activeTraceId: String? = null

    init {
        viewModelScope.launch {
            modelState.collect { state ->
                if (state == ModelState.Ready && modelStateOverride == null) {
                    AgentWarmService.start(getApplication())
                }
            }
        }
    }

    fun onCommandChange(value: String) {
        _uiState.update { it.copy(command = value) }
    }

    fun submit() {
        val command = _uiState.value.command.trim()
        if (command.isBlank()) {
            setStatus("Enter a command")
            return
        }
        if (planState.value is PlanState.Pending || planState.value is PlanState.Executing) {
            setStatus("Approve or cancel the pending plan first")
            return
        }
        if (modelState.value != ModelState.Ready) {
            setStatus("Model is not ready", speak = true)
            return
        }

        _uiState.update {
            it.copy(isSubmitting = true, status = "Thinking…", suggestions = emptyList())
        }
        activeTraceId = container.traceRecorder.start(command)
        viewModelScope.launch {
            when (val decision = router.route(command)) {
                is RoutingDecision.Action -> {
                    val plan = PlanFactory.fromAction(command, decision.request)
                    container.traceRecorder.decision(
                        id = activeTraceId ?: return@launch,
                        command = command,
                        taskType = "action",
                        targetPackage = null,
                        modelPrompt = null,
                        toolCallsJson = decision.request.toString(),
                        planJson = plan.steps.toString(),
                        outcome = "pending_approval",
                    )
                    if (coordinator.offer(plan)) {
                        setStatus("Plan ready for review", speak = true)
                    } else {
                        setStatus("A plan is already pending")
                    }
                }
                is RoutingDecision.UiPlan -> {
                    if (!accessibilityAvailable) {
                        setStatus("Accessibility service is not enabled", speak = true)
                        activeTraceId?.let {
                            container.traceRecorder.finish(
                                it, command, "ui_plan", decision.app, "needs_permission",
                                "accessibility",
                            )
                        }
                        return@launch
                    }
                    if (!PermissionsCoordinator.overlayGranted(getApplication())) {
                        setStatus("Display-over-other-apps permission is required", speak = true)
                        activeTraceId?.let {
                            container.traceRecorder.finish(
                                it, command, "ui_plan", decision.app, "needs_permission",
                                "overlay",
                            )
                        }
                        return@launch
                    }
                    val planResult = container.whatsAppUiPlanFactory
                        .build(command, decision.goal, decision.app)
                        ?.let { Result.success(it) }
                        ?: UiPlanner(container.engineHolder).plan(command, decision.goal, decision.app)
                    planResult.fold(
                            onSuccess = { plan ->
                                container.traceRecorder.decision(
                                    activeTraceId ?: "",
                                    command,
                                    "ui_plan",
                                    decision.app,
                                    UiPlanner.SYSTEM_PROMPT,
                                    decision.goal,
                                    plan.steps.toString(),
                                    "pending_approval",
                                )
                                if (coordinator.offer(plan)) {
                                    setStatus("Plan ready for review", speak = true)
                                } else {
                                    setStatus("A plan is already pending")
                                }
                            },
                            onFailure = { error ->
                                setStatus(
                                    "Could not plan: ${error.message}",
                                    speak = true,
                                )
                                activeTraceId?.let {
                                    container.traceRecorder.finish(
                                        it, command, "ui_plan", decision.app, "failed",
                                        error.message,
                                    )
                                }
                            },
                        )
                }
                is RoutingDecision.NoMatch -> {
                    activeTraceId?.let {
                        container.traceRecorder.decision(
                            it, command, "no_match", null, null, null, null, "no_match",
                        )
                        container.traceRecorder.finish(it, command, "no_match", null, "no_match")
                    }
                    val suggestions = decision.suggestions.joinToString(", ")
                    setStatus(
                        buildString {
                            append("Not understood. ")
                            if (suggestions.isNotBlank()) append("Try: $suggestions")
                        },
                        speak = true,
                    )
                    _uiState.update { it.copy(suggestions = decision.suggestions) }
                }
                is RoutingDecision.Blocked -> {
                    activeTraceId?.let {
                        container.traceRecorder.decision(
                            it, command, "blocked", null, null, null, null, "blocked",
                        )
                        container.traceRecorder.finish(
                            it, command, "blocked", null, "blocked", decision.reason,
                        )
                    }
                    setStatus("Blocked: ${decision.reason}", speak = true)
                }
                is RoutingDecision.Failed -> {
                    activeTraceId?.let {
                        container.traceRecorder.decision(
                            it, command, "failed", null, null, null, null, "failed",
                        )
                        container.traceRecorder.finish(
                            it, command, "failed", null, "failed", decision.reason,
                        )
                    }
                    setStatus("Failed: ${decision.reason}", speak = true)
                }
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun approvePlan() {
        val plan = coordinator.approve() ?: return
        viewModelScope.launch {
            container.safetyPolicy.checkPlan(plan).takeIf { it.blocked }?.let { verdict ->
                val reason = verdict.reason ?: "Blocked by safety policy"
                coordinator.fail(reason)
                activeTraceId?.let {
                    container.traceRecorder.finish(it, plan.command, "blocked", plan.targetPackage, "blocked", reason)
                }
                activeTraceId = null
                setStatus("Blocked: $reason", speak = true)
                return@launch
            }
            val step = plan.steps.singleOrNull() as? PlanStep.RunAction
            if (step == null) {
                executeUiPlan(plan)
                return@launch
            }
            val result = when (step.actionId) {
                "send_whatsapp" -> {
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        ActionExecution.NeedsPermission("accessibility")
                    } else {
                        container.whatsAppSender.send(
                            contactQuery = step.arguments["contact"]?.toString(),
                            message = step.arguments["message"]?.toString(),
                            gateway = AndroidAccessibilityGateway(service),
                        )
                    }
                }
                "play_media" -> {
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        ActionExecution.NeedsPermission("accessibility")
                    } else {
                        container.youTubePlayer.play(
                            query = step.arguments["query"]?.toString(),
                            service = step.arguments["service"]?.toString(),
                            gateway = AndroidAccessibilityGateway(service),
                        )
                    }
                }
                else -> container.actionRegistry.execute(step.actionId, step.arguments)
            }
            when (result) {
                is ActionExecution.Success -> {
                    coordinator.complete()
                    activeTraceId?.let {
                        container.traceRecorder.step(
                            it, 0, step.toString(), null, "success", 0,
                        )
                        container.traceRecorder.finish(it, plan.command, "action", null, "success")
                    }
                    activeTraceId = null
                    setStatus(result.message, speak = true)
                }
                is ActionExecution.NeedsPermission -> {
                    coordinator.fail("Missing permission: ${result.permission}")
                    activeTraceId?.let {
                        container.traceRecorder.finish(
                            it, plan.command, "action", null, "needs_permission", result.permission,
                        )
                    }
                    activeTraceId = null
                    setStatus("Missing permission: ${result.permission}", speak = true)
                }
                is ActionExecution.NeedsChoice -> {
                    val names = result.candidates.joinToString(", ") { it.name }
                    coordinator.fail("Multiple contacts: $names")
                    activeTraceId?.let {
                        container.traceRecorder.finish(
                            it, plan.command, "action", null, "needs_choice", names,
                        )
                    }
                    activeTraceId = null
                    setStatus("Multiple contacts: $names", speak = true)
                }
                is ActionExecution.Failed -> {
                    coordinator.fail(result.reason)
                    activeTraceId?.let { traceId ->
                        captureFailure(traceId)
                    }
                    activeTraceId?.let {
                        container.traceRecorder.finish(
                            it, plan.command, "action", null, "failed", result.reason,
                        )
                    }
                    activeTraceId = null
                    setStatus(result.reason, speak = true)
                }
            }
        }
    }

    fun cancelPlan() {
        coordinator.cancel()
        activeTraceId?.let {
            container.traceRecorder.finish(it, "", "action", null, "cancelled")
        }
        activeTraceId = null
        setStatus("Plan cancelled", speak = true)
    }

    fun replan(correction: String) {
        val pending = planState.value as? PlanState.Pending ?: return
        if (correction.isBlank()) return
        viewModelScope.launch {
            val replanned = PlanReplanner { prompt -> router.route(prompt) }
                .replan(pending.plan, correction)
            if (replanned == null) {
                setStatus("Could not re-plan; approve or cancel the current plan", speak = true)
            } else {
                coordinator.cancel()
                coordinator.offer(replanned)
                setStatus("Plan updated; approve it again", speak = true)
            }
        }
    }

    fun updatePlan(transform: (Plan) -> Plan) {
        coordinator.update(transform)
        setStatus("Plan updated; approve it again")
    }

    fun downloadModel() {
        container.modelRepository.enqueueDownload()
    }

    fun retryModel() {
        container.modelRepository.enqueueDownload()
    }

    private suspend fun executeUiPlan(plan: Plan) {
        val service = if (accessibilityAvailable) AgentAccessibilityService.instance else null
        if (service == null) {
            coordinator.fail("Accessibility service is not enabled")
            setStatus("Accessibility service is not enabled", speak = true)
            return
        }
        val context = getApplication<Application>()
        ExecutionOverlay.start(context, "Executing plan")
        delay(1_200)
        val executor = PlanExecutor(
            gateway = AndroidAccessibilityGateway(service),
            safety = container.safetyPolicy,
            stopRequested = { ExecutionOverlay.stopRequested },
        )
        val result = executor.execute(plan) { index, step ->
            ExecutionOverlay.update(context, "Step ${index + 1}: ${step}")
            activeTraceId?.let {
                container.traceRecorder.step(it, index, step.toString(), null, "running", 0)
            }
        }
        ExecutionOverlay.stop(context)
        result.fold(
            onSuccess = {
                coordinator.complete()
                activeTraceId?.let {
                    container.traceRecorder.finish(it, plan.command, "ui_plan", plan.targetPackage, "success")
                }
                activeTraceId = null
                setStatus("Plan complete", speak = true)
            },
            onFailure = { error ->
                coordinator.fail(error.message ?: "UI plan failed")
                setStatus(error.message ?: "UI plan failed", speak = true)
                activeTraceId?.let {
                    captureFailure(it)
                    container.traceRecorder.finish(
                        it, plan.command, "ui_plan", plan.targetPackage, "failed", error.message,
                    )
                }
                activeTraceId = null
            },
        )
    }

    private fun captureFailure(traceId: String) {
        val foreground = container.foregroundApp.currentPackage()
        if (foreground != null && container.safetyPolicy.checkPackage(foreground).blocked) {
            return
        }
        if (!accessibilityAvailable) return
        val service = AgentAccessibilityService.instance ?: return
        ScreenshotTaker(service, container.screenshotStore)
            .capture("$traceId.png")
    }

    private fun setStatus(message: String, speak: Boolean = false) {
        _uiState.update { it.copy(status = message) }
        if (speak) container.ttsSpeaker.speak(message)
    }

    companion object {
        fun statusText(state: ModelState): String = when (state) {
            ModelState.Absent -> "Model not downloaded"
            is ModelState.Downloading -> {
                val percent = if (state.total > 0) state.bytes * 100 / state.total else 0
                "Downloading model ${percent}%"
            }
            ModelState.Verifying -> "Verifying model"
            ModelState.Ready -> "Model ready"
            is ModelState.Failed -> "Model failed: ${state.message}"
        }
    }
}
