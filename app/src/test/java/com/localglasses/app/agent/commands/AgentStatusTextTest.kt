package com.localglasses.app.agent.commands

import com.localglasses.app.agent.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentStatusTextTest {

    @Test
    fun rendersEveryModelState() {
        assertEquals("Model not downloaded", AgentViewModel.statusText(ModelState.Absent))
        assertEquals(
            "Downloading model 50%",
            AgentViewModel.statusText(ModelState.Downloading(50, 100)),
        )
        assertEquals("Verifying model", AgentViewModel.statusText(ModelState.Verifying))
        assertEquals("Model ready", AgentViewModel.statusText(ModelState.Ready))
        assertEquals(
            "Model failed: checksum",
            AgentViewModel.statusText(ModelState.Failed("checksum")),
        )
    }
}
