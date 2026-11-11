package com.localglasses.app.agent.dictation

import java.io.File
import java.util.Locale

/**
 * Scores the shipping model against a local corpus.
 *
 * The corpus is a directory of `name.wav` / `name.txt` pairs holding 16 kHz mono audio and the
 * reference transcript. It is deliberately outside the app's normal flow: nothing here runs
 * unless the harness is invoked explicitly, and the directory it reads is not committed.
 */
class EvaluationHarness(
    private val corpusDir: File,
    private val recognizer: SpeechRecognizer,
) {

    data class CaseResult(
        val name: String,
        val reference: String,
        val hypothesis: String,
        val wer: Double,
        val realTimeFactor: Double,
        val latencyMs: Long,
    )

    data class Report(val cases: List<CaseResult>) {
        val meanWer: Double
            get() = if (cases.isEmpty()) 0.0 else cases.sumOf { it.wer } / cases.size

        val meanRealTimeFactor: Double
            get() = if (cases.isEmpty()) 0.0 else cases.sumOf { it.realTimeFactor } / cases.size

        fun text(): String = buildString {
            appendLine("word_error_rate report")
            appendLine("cases: ${cases.size}")
            appendLine(String.format(Locale.US, "mean WER: %.3f", meanWer))
            appendLine(String.format(Locale.US, "mean RTF: %.3f", meanRealTimeFactor))
            cases.forEach { case ->
                appendLine(
                    String.format(
                        Locale.US,
                        "- %s: WER %.3f, RTF %.3f, %d ms",
                        case.name,
                        case.wer,
                        case.realTimeFactor,
                        case.latencyMs,
                    ),
                )
                appendLine("  reference: ${case.reference}")
                appendLine("  hypothesis: ${case.hypothesis}")
            }
        }
    }

    suspend fun run(): Report {
        val pairs = corpusDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
            ?.mapNotNull { wav ->
                val reference = File(corpusDir, wav.nameWithoutExtension + ".txt")
                if (reference.isFile) wav to reference else null
            }
            ?.sortedBy { it.first.name }
            .orEmpty()

        val results = pairs.map { (wav, referenceFile) ->
            val audio = wav.inputStream().use { WavReader.read(it) }
            require(audio.sampleRate == DICTATION_SAMPLE_RATE) {
                "${wav.name} must be ${DICTATION_SAMPLE_RATE} Hz"
            }
            val recognition = recognizer.transcribe(audio.samples)
            val reference = referenceFile.readText().trim()
            val score = WordErrorRate.score(reference, recognition.text)
            CaseResult(
                name = wav.nameWithoutExtension,
                reference = reference,
                hypothesis = recognition.text,
                wer = score.wer,
                realTimeFactor = if (recognition.audioDurationMs > 0) {
                    recognition.recognitionLatencyMs.toDouble() / recognition.audioDurationMs
                } else {
                    0.0
                },
                latencyMs = recognition.recognitionLatencyMs,
            )
        }
        return Report(results)
    }

    companion object {
        /** App-private corpus location; filled by adb or by a debug build, never committed. */
        const val DIRECTORY = "eval-corpus"
    }
}
