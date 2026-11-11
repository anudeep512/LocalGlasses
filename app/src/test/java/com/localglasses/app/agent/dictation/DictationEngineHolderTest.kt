package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.model.LowMemoryException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationEngineHolderTest {

    private class FakeRecognizer : SpeechRecognizer {
        var closed = false
        override suspend fun transcribe(samples: FloatArray) =
            RecognitionResult("call Amma", 1_000, 500)

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `loads lazily and reuses the instance`() = runBlocking {
        var created = 0
        val holder = DictationEngineHolder(factory = { created++; FakeRecognizer() })

        assertFalse(holder.isLoaded)
        val first = holder.engine().getOrThrow()
        val second = holder.engine().getOrThrow()

        assertEquals(1, created)
        assertSame(first, second)
        assertTrue(holder.isLoaded)
    }

    @Test
    fun `refuses to load without memory headroom`() = runBlocking {
        var created = 0
        val holder = DictationEngineHolder(
            factory = { created++; FakeRecognizer() },
            canLoad = { false },
        )

        val result = holder.engine()

        assertTrue(result.exceptionOrNull() is LowMemoryException)
        assertEquals(0, created)
        assertFalse(holder.isLoaded)
    }

    @Test
    fun `reports a factory failure and stays unloaded`() = runBlocking {
        val holder = DictationEngineHolder(factory = { error("native library missing") })

        val result = holder.engine()

        assertEquals("native library missing", result.exceptionOrNull()?.message)
        assertFalse(holder.isLoaded)
    }

    @Test
    fun `release closes the recogniser and allows a reload`() = runBlocking {
        val created = mutableListOf<FakeRecognizer>()
        val holder = DictationEngineHolder(factory = { FakeRecognizer().also { created += it } })

        holder.engine().getOrThrow()
        holder.release()

        assertFalse(holder.isLoaded)
        assertTrue(created.single().closed)

        holder.engine().getOrThrow()
        assertEquals(2, created.size)
    }

    @Test
    fun `release before load is a no-op`() {
        val holder = DictationEngineHolder(factory = { FakeRecognizer() })

        holder.release()

        assertFalse(holder.isLoaded)
    }
}
