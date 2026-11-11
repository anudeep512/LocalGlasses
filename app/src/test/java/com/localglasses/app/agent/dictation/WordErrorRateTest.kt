package com.localglasses.app.agent.dictation

import org.junit.Assert.assertEquals
import org.junit.Test

class WordErrorRateTest {

    @Test
    fun identicalTextHasNoErrors() {
        val result = WordErrorRate.score("call Amma now", "call Amma now")

        assertEquals(0, result.errors)
        assertEquals(0.0, result.wer, 0.0001)
    }

    @Test
    fun casingAndPunctuationAreIgnored() {
        val result = WordErrorRate.score("Call Amma now.", "call amma now")

        assertEquals(0.0, result.wer, 0.0001)
    }

    @Test
    fun aSubstitutionCostsOneWord() {
        val result = WordErrorRate.score("call Amma now", "call Appa now")

        assertEquals(1, result.errors)
        assertEquals(1.0 / 3.0, result.wer, 0.0001)
    }

    @Test
    fun anInsertionCostsOneWord() {
        val result = WordErrorRate.score("call Amma", "call Amma please")

        assertEquals(1, result.errors)
        assertEquals(0.5, result.wer, 0.0001)
    }

    @Test
    fun aDeletionCostsOneWord() {
        val result = WordErrorRate.score("call Amma now", "call now")

        assertEquals(1, result.errors)
        assertEquals(1.0 / 3.0, result.wer, 0.0001)
    }

    @Test
    fun aCompletelyDifferentSentenceScoresOne() {
        val result = WordErrorRate.score("call Amma", "play music")

        assertEquals(2, result.errors)
        assertEquals(1.0, result.wer, 0.0001)
    }

    @Test
    fun anEmptyHypothesisScoresOne() {
        val result = WordErrorRate.score("call Amma", "")

        assertEquals(2, result.errors)
        assertEquals(1.0, result.wer, 0.0001)
    }

    @Test
    fun anEmptyReferenceWithNoOutputScoresZero() {
        val result = WordErrorRate.score("", "")

        assertEquals(0, result.errors)
        assertEquals(0.0, result.wer, 0.0001)
    }

    @Test
    fun anEmptyReferenceWithOutputScoresOne() {
        val result = WordErrorRate.score("", "anything")

        assertEquals(1, result.errors)
        assertEquals(1.0, result.wer, 0.0001)
    }
}
