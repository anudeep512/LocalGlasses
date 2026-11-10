package com.localglasses.app

import android.app.Application
import android.util.Log
import com.localglasses.app.agent.AgentContainer
import com.meta.wearable.dat.core.Wearables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the DAT initialization state.
 *
 * Important: `Wearables.initialize()` must run **after** the runtime `BLUETOOTH_CONNECT`
 * permission is granted on Android 12+. Initializing earlier makes the SDK's bonded-device
 * scan throw `SecurityException`, and because DAT only re-checks on registration changes it
 * then reports "No eligible device available" forever, even with the glasses connected.
 */
class LocalGlassesApp : Application() {

    lateinit var agentContainer: AgentContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        agentContainer = AgentContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::agentContainer.isInitialized &&
            agentContainer.memoryGuard.shouldUnloadOnTrim(level)
        ) {
            appScope.launch { agentContainer.engineHolder.close() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::agentContainer.isInitialized) {
            appScope.launch { agentContainer.engineHolder.close() }
        }
    }

    private val _datReady = MutableStateFlow(false)
    val datReady: StateFlow<Boolean> = _datReady.asStateFlow()

    private val _datError = MutableStateFlow<String?>(null)
    val datError: StateFlow<String?> = _datError.asStateFlow()

    /** Idempotent. Call only once Bluetooth permissions are granted. */
    fun initializeDat(): Boolean {
        if (_datReady.value) return true

        return Wearables.initialize(this).fold(
            onSuccess = {
                _datError.value = null
                _datReady.value = true
                true
            },
            onFailure = { error, _ ->
                Log.e(TAG, "Failed to initialize DAT: ${error.description}")
                _datError.value = error.description
                false
            },
        )
    }

    private companion object {
        const val TAG = "DATWearables"
    }
}
