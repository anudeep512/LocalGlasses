package com.localglasses.app.agent.model

/**
 * Tries a preferred engine (the GPU) and falls back to a second one (the CPU) if it cannot
 * start.
 *
 * LiteRT-LM's GPU backend depends on an OpenCL driver that some devices ship incompletely, so a
 * failed initialisation must degrade instead of making the agent unusable. The name of the
 * backend that actually loaded is what telemetry reports, so Stats for nerds never claims GPU
 * when the CPU is running. The failure is surfaced through [onFallback] rather than logged here,
 * which keeps this class free of Android types and unit-testable on the JVM.
 */
class FallbackEngine(
    private val primary: () -> InferenceEngine,
    private val secondary: () -> InferenceEngine,
    private val onFallback: (Throwable) -> Unit = {},
) : InferenceEngine {

    private var active: InferenceEngine? = null

    override val isLoaded: Boolean
        get() = active?.isLoaded == true

    override val modelId: String
        get() = active?.modelId ?: runCatching { primary().modelId }.getOrElse { "unknown" }

    override val backendName: String
        get() = active?.backendName ?: PREFERRED_NAME

    override fun load() {
        check(active == null) { "engine is already loaded" }

        val preferred = primary()
        val loaded = runCatching { preferred.load() }
        if (loaded.isSuccess) {
            active = preferred
            return
        }

        val error = loaded.exceptionOrNull() ?: IllegalStateException("GPU backend failed")
        runCatching { preferred.close() }
        onFallback(error)

        val fallback = secondary()
        fallback.load()
        active = fallback
    }

    override fun conversation(
        systemPrompt: String,
        tools: List<com.google.ai.edge.litertlm.ToolProvider>,
        automaticToolCalling: Boolean,
    ): InferenceConversation {
        val engine = active ?: error("engine is not loaded")
        return engine.conversation(systemPrompt, tools, automaticToolCalling)
    }

    override fun close() {
        active?.close()
        active = null
    }

    companion object {
        const val PREFERRED_NAME = "GPU"
    }
}
