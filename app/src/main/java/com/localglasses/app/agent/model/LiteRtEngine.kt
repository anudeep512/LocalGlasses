package com.localglasses.app.agent.model

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ToolProvider
import java.io.File

@OptIn(ExperimentalApi::class)
class LiteRtEngine(
    private val modelFile: File,
    private val cacheDir: File,
    private val backend: Backend = Backend.CPU(),
) : InferenceEngine {

    private var engine: Engine? = null

    override val isLoaded: Boolean
        get() = engine?.isInitialized() == true

    override fun load() {
        check(engine == null) { "engine is already loaded" }
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = backend,
            cacheDir = cacheDir.absolutePath,
        )
        engine = Engine(config).also { it.initialize() }
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    override fun conversation(
        systemPrompt: String,
        tools: List<ToolProvider>,
        automaticToolCalling: Boolean,
    ): InferenceConversation {
        val active = engine ?: error("engine is not loaded")
        val conversation = active.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                tools = tools,
                automaticToolCalling = automaticToolCalling,
            ),
        )
        return object : InferenceConversation {
            override fun send(command: String): InferenceReply {
                val message = conversation.sendMessage(command)
                return InferenceReply(
                    text = message.toString(),
                    toolCalls = message.toolCalls.map {
                        InferenceToolCall(it.name, it.arguments)
                    },
                    benchmarkInfo = runCatching { conversation.getBenchmarkInfo() }.getOrNull(),
                )
            }

            override fun close() {
                conversation.close()
            }
        }
    }
}
