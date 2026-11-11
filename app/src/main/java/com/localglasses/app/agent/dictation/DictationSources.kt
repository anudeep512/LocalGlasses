package com.localglasses.app.agent.dictation

import android.content.Context

/**
 * Resolves the configured dictation source.
 *
 * Both sources are created fresh per utterance; the glasses source opens and closes a DAT
 * session around the capture, so no camera stream outlives a dictation.
 */
object DictationSources {

    fun resolve(
        context: Context,
        preference: DictationSourcePreference,
        glassesSupported: Boolean = true,
    ): AudioSource = when (preference.source) {
        AudioSourceKind.PHONE -> PhoneAudioSource.from(context)
        AudioSourceKind.GLASSES -> GlassesAudioSource(
            sessionOpener = { DatGlassesAudio.open() },
            supported = { glassesSupported },
        )
    }
}
