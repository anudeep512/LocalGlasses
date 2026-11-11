package com.localglasses.app.agent.model

import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackEngineTest {

    private class FakeEngine(
        override val backendName: String,
        private val failOnLoad: Throwable? = null,
    ) : InferenceEngine {
        override val modelId = "gemma-test"
        var loaded = false
        var closed = false
        var loadCalls = 0

        override val isLoaded: Boolean
            get() = loaded

        override fun load() {
            loadCalls++
            failOnLoad?.let { throw it }
            loaded = true
        }

        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation = object : InferenceConversation {
            override fun send(command: String) = InferenceReply("from $backendName", emptyList(), null as BenchmarkInfo?)

            override fun close() = Unit
        }

        override fun close() {
            closed = true
            loaded = false
        }
    }

    @Test
    fun usesThePreferredBackendWhenItLoads() {
        val gpu = FakeEngine("GPU")
        val cpu = FakeEngine("CPU")
        val engine = FallbackEngine(primary = { gpu }, secondary = { cpu })

        engine.load()

        assertEquals("GPU", engine.backendName)
        assertTrue(engine.isLoaded)
        assertEquals(0, cpu.loadCalls)
        assertEquals(false, gpu.closed)
    }

    @Test
    fun fallsBackToTheSecondBackendWhenThePreferredOneFails() {
        val gpu = FakeEngine("GPU", failOnLoad = IllegalStateException("no OpenCL"))
        val cpu = FakeEngine("CPU")
        var reported: Throwable? = null
        val engine = FallbackEngine(
            primary = { gpu },
            secondary = { cpu },
            onFallback = { reported = it },
        )

        engine.load()

        assertEquals("CPU", engine.backendName)
        assertTrue(engine.isLoaded)
        assertEquals(1, cpu.loadCalls)
        assertTrue("the failed GPU engine must be closed", gpu.closed)
        assertEquals("no OpenCL", reported?.message)
    }

    @Test
    fun neverClaimsGpuAfterFallingBack() {
        val engine = FallbackEngine(
            primary = { FakeEngine("GPU", failOnLoad = IllegalStateException("boom")) },
            secondary = { FakeEngine("CPU") },
        )

        engine.load()

        assertEquals("CPU", engine.backendName)
    }

    @Test
    fun delegatesConversationsToTheLoadedBackend() {
        val engine = FallbackEngine(
            primary = { FakeEngine("GPU", failOnLoad = IllegalStateException("boom")) },
            secondary = { FakeEngine("CPU") },
        )
        engine.load()

        val reply = engine.conversation("prompt", emptyList()).send("hi")

        assertEquals("from CPU", reply.text)
    }

    @Test
    fun reportsThePreferredNameBeforeLoading() {
        val engine = FallbackEngine(primary = { FakeEngine("GPU") }, secondary = { FakeEngine("CPU") })

        assertEquals(FallbackEngine.PREFERRED_NAME, engine.backendName)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun closeReleasesTheActiveBackend() {
        val cpu = FakeEngine("CPU")
        val engine = FallbackEngine(
            primary = { FakeEngine("GPU", failOnLoad = IllegalStateException("boom")) },
            secondary = { cpu },
        )
        engine.load()

        engine.close()

        assertTrue(cpu.closed)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun aConversationBeforeLoadFailsLoudly() {
        val engine = FallbackEngine(primary = { FakeEngine("GPU") }, secondary = { FakeEngine("CPU") })

        val error = runCatching { engine.conversation("p", emptyList()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
