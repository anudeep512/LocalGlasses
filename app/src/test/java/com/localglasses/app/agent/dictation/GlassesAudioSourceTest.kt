package com.localglasses.app.agent.dictation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesAudioSourceTest {

    private class FakeSession(frames: List<AudioFrame>) : GlassesAudioSession {
        override val frames: Flow<AudioFrame> = flow { frames.forEach { emit(it) } }
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private val frame = AudioFrame(floatArrayOf(0.1f, -0.1f))

    @Test
    fun `unsupported build reports the source as unavailable`() {
        val source = GlassesAudioSource(sessionOpener = { Result.failure(IllegalStateException()) }) {
            false
        }

        val unavailable = source.availability()

        assertTrue(unavailable is SourceAvailability.Unavailable)
        assertEquals(
            GlassesAudioSource.UNAVAILABLE_IN_BUILD,
            (unavailable as SourceAvailability.Unavailable).reason,
        )
        assertEquals(AudioSourceKind.GLASSES, source.kind)
    }

    @Test
    fun `frames flow and the session closes when capture ends`() = runBlocking {
        val session = FakeSession(listOf(frame, frame))
        val source = GlassesAudioSource(sessionOpener = { Result.success(session) })

        val frames = source.open().toList()

        assertEquals(2, frames.size)
        assertTrue(session.closed)
    }

    @Test
    fun `session closes when the collector stops early`() = runBlocking {
        val session = FakeSession(listOf(frame, frame, frame))
        val source = GlassesAudioSource(sessionOpener = { Result.success(session) })

        val frames = source.open().take(1).toList()

        assertEquals(1, frames.size)
        assertTrue(session.closed)
    }

    @Test
    fun `an unavailable opener raises a typed capture error`() = runBlocking {
        val source = GlassesAudioSource(
            sessionOpener = { Result.failure(IllegalStateException("glasses disconnected")) },
        )

        val error = runCatching { source.open().toList() }.exceptionOrNull()

        assertTrue(error is AudioCaptureException)
        assertEquals("glasses disconnected", error!!.message)
    }

    @Test
    fun `capture error passes through unchanged`() = runBlocking {
        val original = AudioCaptureException("session never started")
        val source = GlassesAudioSource(sessionOpener = { Result.failure(original) })

        val error = runCatching { source.open().toList() }.exceptionOrNull()

        assertTrue(error === original)
        assertFalse(error is IllegalStateException && error.message == null)
    }

    @Test
    fun `converts dat audio buffers to float samples`() {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(16_384)
        buffer.putShort((-16_384).toShort())
        buffer.flip()

        val floats = PcmCodec.littleEndianToFloat(buffer)

        assertEquals(2, floats.size)
        assertEquals(0.5f, floats[0], 0.001f)
        assertEquals(-0.5f, floats[1], 0.001f)
        assertEquals(0, buffer.position())
    }
}
