package com.localglasses.app.agent.dictation

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/PCM reader for the bundled recognition fixture. */
object WavFixture {

    data class Wav(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    fun read(context: Context, assetName: String): Wav =
        context.assets.open(assetName).use { read(it) }

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
            val read = input.read(chunkHeader)
            if (read < 8) break
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
                    buffer.short // audio format
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.int // byte rate
                    buffer.short // block align
                    bitsPerSample = buffer.short.toInt()
                }
                "data" -> data = body
            }
        }

        require(bitsPerSample == 16) { "expected 16-bit PCM, got $bitsPerSample" }
        require(channels == 1) { "expected mono, got $channels" }
        val bytes = requireNotNull(data) { "no data chunk" }
        return Wav(
            samples = PcmCodec.littleEndianToFloat(bytes),
            sampleRate = sampleRate,
            channels = channels,
        )
    }
}
