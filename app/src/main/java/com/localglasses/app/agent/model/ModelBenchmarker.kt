package com.localglasses.app.agent.model

import com.google.ai.edge.litertlm.tool
import com.localglasses.app.agent.commands.CommandRouter
import com.localglasses.app.agent.commands.RouterTools
import com.localglasses.app.agent.commands.ToolCallParser

data class BenchmarkRun(
    val prompt: String,
    val repetition: Int,
    val decision: String,
    val latencyMs: Long,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
    val timeToFirstTokenSeconds: Double?,
)

data class BenchmarkSummary(
    val prompt: String,
    val runs: Int,
    val successes: Int,
    val minMs: Long,
    val maxMs: Long,
    val medianMs: Double,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
)

class ModelBenchmarker(private val engine: InferenceEngine) {

    suspend fun run(
        prompts: List<String>,
        repetitions: Int = 3,
        onRun: (BenchmarkRun) -> Unit = {},
    ): List<BenchmarkSummary> {
        val runs = mutableListOf<BenchmarkRun>()
        prompts.forEach { prompt ->
            repeat(repetitions) { repetition ->
                val start = System.nanoTime()
                val reply = engine.conversation(
                    systemPrompt = CommandRouter.SYSTEM_PROMPT,
                    tools = listOf(tool(RouterTools())),
                    automaticToolCalling = false,
                ).use { conversation ->
                    conversation.send(prompt)
                }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                val decision = ToolCallParser.parse(reply.toolCalls)
                val run = BenchmarkRun(
                    prompt = prompt,
                    repetition = repetition,
                    decision = decision::class.simpleName ?: "Unknown",
                    latencyMs = elapsedMs,
                    prefillTokensPerSecond = reply.benchmarkInfo?.lastPrefillTokensPerSecond,
                    decodeTokensPerSecond = reply.benchmarkInfo?.lastDecodeTokensPerSecond,
                    timeToFirstTokenSeconds = reply.benchmarkInfo?.timeToFirstTokenInSecond,
                )
                runs += run
                onRun(run)
            }
        }

        return prompts.map { prompt ->
            val promptRuns = runs.filter { it.prompt == prompt }
            val sorted = promptRuns.map { it.latencyMs }.sorted()
            BenchmarkSummary(
                prompt = prompt,
                runs = promptRuns.size,
                successes = promptRuns.count { it.decision != "Failed" },
                minMs = sorted.firstOrNull() ?: 0,
                maxMs = sorted.lastOrNull() ?: 0,
                medianMs = if (sorted.isEmpty()) 0.0 else sorted[sorted.size / 2].toDouble(),
                prefillTokensPerSecond = promptRuns.mapNotNull { it.prefillTokensPerSecond }.averageOrNull(),
                decodeTokensPerSecond = promptRuns.mapNotNull { it.decodeTokensPerSecond }.averageOrNull(),
            )
        }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
