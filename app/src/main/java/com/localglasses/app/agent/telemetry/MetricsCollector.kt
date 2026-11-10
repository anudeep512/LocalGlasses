package com.localglasses.app.agent.telemetry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

interface MetricsSource {
    fun sample(): DeviceSnapshot
}

/**
 * Foreground-only sampler. The stream is cold so the visible screen owns its
 * lifecycle: leaving the screen cancels collection and therefore stops readings.
 */
class MetricsCollector(
    private val source: MetricsSource,
    private val intervalMs: Long = 1_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun snapshot(): DeviceSnapshot = withContext(ioDispatcher) { source.sample() }

    suspend fun beginRequest(): RequestMetricsSession =
        RequestMetricsSession(snapshot())

    fun stream(): Flow<DeviceSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(withContext(ioDispatcher) { source.sample() })
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)
}
