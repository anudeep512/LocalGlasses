package com.localglasses.app.agent.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmCodecTest {

    @Test
    fun `converts full scale shorts to unit floats`() {
        val floats = PcmCodec.pcm16ToFloat(shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, -16384))

        assertEquals(0f, floats[0], 0.0001f)
        assertTrue(floats[1] > 0.999f)
        assertEquals(-1f, floats[2], 0.0001f)
        assertEquals(-0.5f, floats[3], 0.0001f)
    }

    @Test
    fun `honours a partial read count`() {
        val floats = PcmCodec.pcm16ToFloat(shortArrayOf(100, 200, 300, 400), count = 2)

        assertEquals(2, floats.size)
        assertEquals(100f / 32768f, floats[0], 0.0001f)
    }

    @Test
    fun `empty and non positive counts produce no samples`() {
        assertEquals(0, PcmCodec.pcm16ToFloat(ShortArray(0)).size)
        assertEquals(0, PcmCodec.pcm16ToFloat(shortArrayOf(1, 2), count = 0).size)
        assertEquals(0, PcmCodec.pcm16ToFloat(shortArrayOf(1, 2), count = -5).size)
    }

    @Test
    fun `interprets little endian bytes`() {
        // 0x0100 = 256 little endian, then the most negative value 0x8000.
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x80.toByte())

        val floats = PcmCodec.littleEndianToFloat(bytes)

        assertEquals(2, floats.size)
        assertEquals(256f / 32768f, floats[0], 0.0001f)
        assertEquals(-1f, floats[1], 0.0001f)
    }

    @Test
    fun `ignores a trailing odd byte`() {
        assertEquals(1, PcmCodec.littleEndianToFloat(byteArrayOf(0x00, 0x01, 0x7F)).size)
    }

    @Test
    fun `computes duration from sample count`() {
        assertEquals(0, PcmCodec.durationMs(0))
        assertEquals(1000, PcmCodec.durationMs(16_000))
        assertEquals(32, PcmCodec.durationMs(512))
    }
}
