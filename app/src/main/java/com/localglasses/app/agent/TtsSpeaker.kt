package com.localglasses.app.agent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsSpeaker(context: Context) : AutoCloseable {

    @Volatile
    private var ready = false

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.US
            }
        }
    }

    val isReady: Boolean
        get() = ready

    fun speak(text: String) {
        if (text.isBlank() || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-status")
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
