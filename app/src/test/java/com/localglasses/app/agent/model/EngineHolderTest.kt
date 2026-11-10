package com.localglasses.app.agent.model

import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineHolderTest {

    @Test
    fun loadsOnceAndReusesTheEngine() = runTest {
        val fake = FakeEngine()
        val holder = EngineHolder({ fake }, UnconfinedTestDispatcher(testScheduler))

        val first = holder.ensureLoaded().getOrThrow()
        val second = holder.ensureLoaded().getOrThrow()

        assertSame(first, second)
        assertEquals(1, fake.loads)
    }

    @Test
    fun concurrentCallsLoadOnce() = runTest {
        val fake = FakeEngine(loadDelayMs = 50)
        val holder = EngineHolder({ fake }, Dispatchers.IO)

        coroutineScope {
            launch { holder.ensureLoaded() }
            launch { holder.ensureLoaded() }
        }

        assertEquals(1, fake.loads)
    }

    @Test
    fun closesAndReloads() = runTest {
        val fake = FakeEngine()
        val holder = EngineHolder({ fake }, UnconfinedTestDispatcher(testScheduler))
        holder.ensureLoaded().getOrThrow()

        holder.close()
        assertFalse(holder.isLoaded)

        holder.reload().getOrThrow()
        assertTrue(holder.isLoaded)
        assertEquals(2, fake.loads)
        assertEquals(1, fake.closes)
    }

    @Test
    fun failedLoadCanBeRetried() = runTest {
        val fake = FakeEngine(failFirstLoad = true)
        val holder = EngineHolder({ fake }, UnconfinedTestDispatcher(testScheduler))

        assertTrue(holder.ensureLoaded().isFailure)
        assertTrue(holder.ensureLoaded().isSuccess)
        assertEquals(2, fake.loads)
    }

    @Test
    fun refusesLoadWhenMemoryIsLow() = runTest {
        val fake = FakeEngine()
        val holder = EngineHolder(
            factory = { fake },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            canLoad = { false },
        )

        val result = holder.ensureLoaded()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LowMemoryException)
        assertEquals(0, fake.loads)
    }

    @Test
    fun loadDoesNotRunOnCallerThread() = runTest {
        var loadThread = ""
        val fake = object : InferenceEngine {
            private var loaded = false
            override val isLoaded: Boolean get() = loaded
        override val modelId: String = "test-model"
        override val backendName: String = "CPU"
            override fun load() {
                loadThread = Thread.currentThread().name
                loaded = true
            }
            override fun close() {
                loaded = false
            }
            override fun conversation(
                systemPrompt: String,
                tools: List<ToolProvider>,
                automaticToolCalling: Boolean,
            ): InferenceConversation = error("unused")
        }
        val holder = EngineHolder({ fake }, Dispatchers.IO)

        holder.ensureLoaded().getOrThrow()

        assertNotEquals(Thread.currentThread().name, loadThread)
    }

    private class FakeEngine(
        private val loadDelayMs: Long = 0,
        private val failFirstLoad: Boolean = false,
    ) : InferenceEngine {
        var loads = 0
        var closes = 0
        private var loaded = false

        override val isLoaded: Boolean get() = loaded
        override val modelId: String = "test-model"
        override val backendName: String = "CPU"

        override fun load() {
            loads += 1
            if (loadDelayMs > 0) Thread.sleep(loadDelayMs)
            if (failFirstLoad && loads == 1) error("simulated load failure")
            loaded = true
        }

        override fun close() {
            closes += 1
            loaded = false
        }

        override fun conversation(
            systemPrompt: String,
            tools: List<ToolProvider>,
            automaticToolCalling: Boolean,
        ): InferenceConversation = error("unused")
    }
}
