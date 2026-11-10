package com.localglasses.app.agent.model

import com.google.ai.edge.litertlm.ToolProvider

data class InferenceToolCall(
    val name: String,
    val arguments: Map<String, Any?>,
)

data class InferenceReply(
    val text: String,
    val toolCalls: List<InferenceToolCall>,
)

interface InferenceConversation : AutoCloseable {
    fun send(command: String): InferenceReply
}

interface InferenceEngine : AutoCloseable {
    val isLoaded: Boolean

    fun load()

    fun conversation(
        systemPrompt: String,
        tools: List<ToolProvider>,
        automaticToolCalling: Boolean = false,
    ): InferenceConversation
}
