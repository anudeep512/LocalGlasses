package com.localglasses.app.agent.automation

import com.google.ai.edge.litertlm.tool
import com.localglasses.app.agent.approval.Direction
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.approval.PlanValidator
import com.localglasses.app.agent.model.EngineHolder

class UiPlanner(private val engineHolder: EngineHolder) {

    suspend fun plan(command: String, goal: String, app: String?): Result<Plan> {
        val engine = engineHolder.ensureLoaded().getOrElse { return Result.failure(it) }
        val reply = try {
            engine.conversation(
                systemPrompt = SYSTEM_PROMPT,
                tools = listOf(tool(SubmitPlanTool())),
                automaticToolCalling = false,
            ).use { conversation ->
                conversation.send(
                    "Command: $command\nGoal: $goal\nTarget package: ${app ?: "current app"}",
                )
            }
        } catch (error: Exception) {
            return Result.failure(error)
        }

        val call = reply.toolCalls.singleOrNull()
            ?: return Result.failure(IllegalStateException("Planner returned no plan"))
        val steps = parseSteps(call.arguments["steps"])
            ?: return Result.failure(IllegalStateException("Planner returned no valid steps"))
        return PlanValidator.validate(Plan(command = command, steps = steps, targetPackage = app))
    }

    private fun parseSteps(raw: Any?): List<PlanStep>? {
        val list = raw as? List<*> ?: return null
        val steps = list.mapNotNull { item ->
            (item as? Map<*, *>)?.let(::parseStep)
        }
        return steps.ifEmpty { null }
    }

    private fun parseStep(values: Map<*, *>): PlanStep? {
        val type = values["type"]?.toString()?.lowercase() ?: return null
        return when (type) {
            "open_app" -> values["package"]?.toString()?.let(PlanStep::OpenApp)
            "tap" -> PlanStep.Tap(selector(values))
            "type" -> {
                val text = values["text"]?.toString() ?: return null
                PlanStep.TypeText(selector(values), text)
            }
            "scroll" -> PlanStep.Scroll(
                if (values["direction"]?.toString() == "backward") {
                    Direction.BACKWARD
                } else {
                    Direction.FORWARD
                },
            )
            "wait" -> PlanStep.Wait((values["millis"] as? Number)?.toLong() ?: 500)
            else -> null
        }
    }

    private fun selector(values: Map<*, *>): NodeSelector = NodeSelector(
        viewId = values["viewId"]?.toString(),
        text = values["text"]?.toString(),
        textContains = values["textContains"]?.toString(),
        contentDescription = values["contentDescription"]?.toString(),
    )

    companion object {
        val SYSTEM_PROMPT = """
            You plan Android UI steps for LocalGlasses.
            Call submit_plan exactly once. Never answer in prose.
            Allowed steps: open_app(package), tap(selector), type(selector,text),
            scroll(direction), wait(millis).
            Open the target app first when it is not already open.
            Keep the plan minimal and do not include registry actions.
        """.trimIndent()
    }
}
