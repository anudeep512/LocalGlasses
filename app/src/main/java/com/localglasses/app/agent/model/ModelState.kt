package com.localglasses.app.agent.model

sealed interface ModelState {
    data object Absent : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}
