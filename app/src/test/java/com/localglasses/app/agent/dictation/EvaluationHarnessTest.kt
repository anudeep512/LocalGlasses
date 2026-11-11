package com.localglasses.app.agent.dictation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvaluationHarnessTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun writeWav(file: File, milliseconds: Int) {
        val samples = DICTATION_SAMPLE_RATE * milliseconds / 1000
        val dataBytes = samples * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(DICTATION_SAMPLE_RATE)
        buffer.putInt(DICTATION_SAMPLE_RATE * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
        repeat(samples) { buffer.putShort(1000) }
        file.writeBytes(buffer.array())
    }

    private fun recognizer(text: String) = object : SpeechRecognizer {
        override suspend fun transcribe(samples: FloatArray) = RecognitionResult(
            text = text,
            audioDurationMs = PcmCodec.durationMs(samples.size.toLong()),
            recognitionLatencyMs = 250,
        )

        override fun close() = Unit
    }

    @Test
    fun scoresEveryWavTextPairAndReportsTheNumbers() = runBlocking {
        val directory = temp.newFolder("corpus")
        writeWav(File(directory, "a.wav"), 2_000)
        File(directory, "a.txt").writeText("call Amma")
        writeWav(File(directory, "b.wav"), 1_000)
        File(directory, "b.txt").writeText("play music")

        val report = EvaluationHarness(directory, recognizer("call Amma")).run()

        assertEquals(2, report.cases.size)
        assertEquals(0.0, report.cases.first { it.name == "a" }.wer, 0.0001)
        assertEquals(1.0, report.cases.first { it.name == "b" }.wer, 0.0001)
        assertEquals(0.5, report.meanWer, 0.0001)

        val text = report.text()
        assertTrue(text.contains("mean WER: 0.500"))
        assertTrue(text.contains("- a: WER 0.000"))
        assertTrue(text.contains("reference: call Amma"))
    }

    @Test
    fun ignoresAudioWithoutAReference() = runBlocking {
        val directory = temp.newFolder("corpus")
        writeWav(File(directory, "orphan.wav"), 500)

        val report = EvaluationHarness(directory, recognizer("anything")).run()

        assertTrue(report.cases.isEmpty())
        assertEquals(0.0, report.meanWer, 0.0001)
    }

    @Test
    fun rejectsAudioAtTheWrongSampleRate() = runBlocking {
        val directory = temp.newFolder("corpus")
        val file = File(directory, "wrong.wav")
        val buffer = ByteBuffer.allocate(44 + 8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + 8)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(44_100)
        buffer.putInt(88_200)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(8)
        buffer.putShort(1)
        buffer.putShort(1)
        file.writeBytes(buffer.array())
        File(directory, "wrong.txt").writeText("x")

        val error = runCatching { EvaluationHarness(directory, recognizer("x")).run() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
