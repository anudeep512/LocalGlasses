package com.localglasses.app.agent.dictation

/**
 * Word error rate over normalised tokens.
 *
 * Normalisation lowercases and strips punctuation so a difference in casing or a trailing
 * period is not counted as a word error.
 */
object WordErrorRate {

    fun score(reference: String, hypothesis: String): Result {
        val ref = tokens(reference)
        val hyp = tokens(hypothesis)
        val errors = editDistance(ref, hyp)
        val wer = if (ref.isEmpty()) {
            if (hyp.isEmpty()) 0.0 else 1.0
        } else {
            errors.toDouble() / ref.size
        }
        return Result(referenceWords = ref.size, errors = errors, wer = wer)
    }

    data class Result(val referenceWords: Int, val errors: Int, val wer: Double)

    fun tokens(text: String): List<String> = text
        .lowercase()
        .replace(PUNCTUATION, " ")
        .trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }

    private fun editDistance(reference: List<String>, hypothesis: List<String>): Int {
        if (reference.isEmpty()) return hypothesis.size
        if (hypothesis.isEmpty()) return reference.size

        var previous = IntArray(hypothesis.size + 1) { it }
        var current = IntArray(hypothesis.size + 1)

        for (i in 1..reference.size) {
            current[0] = i
            for (j in 1..hypothesis.size) {
                val substitution = previous[j - 1] + if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[hypothesis.size]
    }

    private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s']")
    private val WHITESPACE = Regex("\\s+")
}
