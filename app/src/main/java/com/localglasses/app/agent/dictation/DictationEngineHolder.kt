package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.model.LowMemoryException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the lazily created recogniser.
 *
 * Loading is refused when [canLoad] reports no headroom, mirroring the agent model's guard, and
 * [release] is safe to call repeatedly so a memory trim can drop the engine without a follow-up
 * request failing.
 */
class DictationEngineHolder(
    private val factory: () -> SpeechRecognizer,
    private val canLoad: () -> Boolean = { true },
) {

    private val mutex = Mutex()
    private var recognizer: SpeechRecognizer? = null

    val isLoaded: Boolean
        get() = recognizer != null

    suspend fun engine(): Result<SpeechRecognizer> = mutex.withLock {
        recognizer?.let { return Result.success(it) }
        if (!canLoad()) {
            return Result.failure(LowMemoryException())
        }
        val created = runCatching { factory() }
        created.onSuccess { recognizer = it }
        created
    }

    fun release() {
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
    }
}
