package com.localglasses.app.agent.automation

data class ProgressState(
    val stopped: Boolean = false,
    val reason: String? = null,
)

class ProgressTracker(
    private val maxConsecutiveFailures: Int = 3,
) {
    private var lastSignature: String? = null
    private var consecutiveFailures = 0
    var state: ProgressState = ProgressState()
        private set

    fun record(signature: String, success: Boolean): ProgressState {
        if (success) {
            consecutiveFailures = 0
        } else {
            consecutiveFailures += 1
            if (signature == lastSignature) {
                state = ProgressState(true, "The screen did not change")
                return state
            }
        }
        lastSignature = signature
        if (consecutiveFailures >= maxConsecutiveFailures) {
            state = ProgressState(true, "$maxConsecutiveFailures steps failed")
        }
        return state
    }

    fun stop(reason: String = "Stopped by user"): ProgressState {
        state = ProgressState(true, reason)
        return state
    }
}
