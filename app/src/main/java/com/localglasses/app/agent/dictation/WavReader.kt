package com.localglasses.app.agent.dictation

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal RIFF/PCM reader for 16 kHz mono 16-bit fixtures.
 *
 * Android-free so both the evaluation harness and unit tests can use it.
 */
object WavReader {

    data class Wav(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    fun read(stream: InputStream): Wav {
        val input = DataInputStream(stream)
        val header = ByteArray(12)
        input.readFully(header)
        require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") {
            "not a RIFF/WAVE file"
        }

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var data: ByteArray? = null

        while (true) {
            val chunkHeader = ByteArray(8)
            if (input.read(chunkHeader) < 8) break
            val id = String(chunkHeader, 0, 4)
            val size = ByteBuffer.wrap(chunkHeader, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            if (size < 0) break
            val body = ByteArray(size)
            input.readFully(body)

            when (id) {
                "fmt " -> {
                    val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.short
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.int
                    buffer.short
                    bitsPerSample = buffer.short.toInt()
                }
                "data" -> data = body
            }
        }

        require(bitsPerSample == 16) { "expected 16-bit PCM, got $bitsPerSample" }
        require(channels == 1) { "expected mono, got $channels" }
        return Wav(
            samples = PcmCodec.littleEndianToFloat(requireNotNull(data) { "no data chunk" }),
            sampleRate = sampleRate,
            channels = channels,
        )
    }
}
