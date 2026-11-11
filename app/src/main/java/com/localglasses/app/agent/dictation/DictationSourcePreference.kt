package com.localglasses.app.agent.dictation

import android.content.Context
import androidx.core.content.edit

/** Persisted choice of microphone for dictation. Phone is the default. */
class DictationSourcePreference(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var source: AudioSourceKind
        get() = when (prefs.getString(KEY_SOURCE, AudioSourceKind.PHONE.name)) {
            AudioSourceKind.GLASSES.name -> AudioSourceKind.GLASSES
            else -> AudioSourceKind.PHONE
        }
        set(value) {
            prefs.edit { putString(KEY_SOURCE, value.name) }
        }

    private companion object {
        const val FILE_NAME = "dictation"
        const val KEY_SOURCE = "source"
    }
}
