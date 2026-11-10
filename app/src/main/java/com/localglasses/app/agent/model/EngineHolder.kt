package com.localglasses.app.agent.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialises engine load, close, and reload.
 *
 * Loading takes seconds, so it always runs on the supplied IO dispatcher; callers
 * suspend instead of blocking. A failed load leaves the holder empty so the next
 * command can retry.
 */
class EngineHolder(
    private val factory: () -> InferenceEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val canLoad: () -> Boolean = { true },
) {

    private val mutex = Mutex()

    @Volatile
    private var engine: InferenceEngine? = null

    val isLoaded: Boolean
        get() = engine?.isLoaded == true

    suspend fun ensureLoaded(): Result<InferenceEngine> = mutex.withLock {
        engine?.takeIf { it.isLoaded }?.let { return@withLock Result.success(it) }
        if (!canLoad()) {
            return@withLock Result.failure(LowMemoryException())
        }

        val result = withContext(ioDispatcher) {
            runCatching {
                factory().also { it.load() }
            }
        }
        result.onSuccess { engine = it }
        result
    }

    suspend fun close() {
        mutex.withLock {
            engine?.close()
            engine = null
        }
    }

    suspend fun reload(): Result<InferenceEngine> {
        mutex.withLock {
            engine?.close()
            engine = null
        }
        return ensureLoaded()
    }
}
