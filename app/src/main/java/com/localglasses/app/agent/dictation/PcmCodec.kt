package com.localglasses.app.agent.dictation

/**
 * Conversions between the capture formats the sources produce and the float samples the
 * recogniser and VAD consume.
 *
 * Kept free of Android types so the arithmetic is unit-testable on the JVM.
 */
object PcmCodec {

    /** Converts signed 16-bit samples to floats in the range -1.0 to 1.0. */
    fun pcm16ToFloat(samples: ShortArray, count: Int = samples.size): FloatArray {
        if (count <= 0) return FloatArray(0)
        val safe = count.coerceAtMost(samples.size)
        val out = FloatArray(safe)
        for (i in 0 until safe) {
            out[i] = samples[i] / SHORT_SCALE
        }
        return out
    }

    /** Interprets little-endian 16-bit PCM bytes, as delivered by DAT audio frames. */
    fun littleEndianToFloat(bytes: ByteArray, length: Int = bytes.size): FloatArray {
        if (length <= 0) return FloatArray(0)
        val safe = length.coerceAtMost(bytes.size)
        val sampleCount = safe / 2
        if (sampleCount == 0) return FloatArray(0)
        val out = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = bytes[2 * i].toInt() and 0xFF
            val high = bytes[2 * i + 1].toInt()
            val value = (high shl 8) or low
            out[i] = value.toShort() / SHORT_SCALE
        }
        return out
    }

    /** Interprets the remaining bytes of a little-endian 16-bit PCM buffer. */
    fun littleEndianToFloat(buffer: java.nio.ByteBuffer): FloatArray {
        val duplicate = buffer.duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return littleEndianToFloat(bytes)
    }

    /** Duration of [sampleCount] mono samples at [DICTATION_SAMPLE_RATE], in milliseconds. */
    fun durationMs(sampleCount: Long): Long =
        (sampleCount * 1000L) / DICTATION_SAMPLE_RATE

    private const val SHORT_SCALE = 32768.0f
}
