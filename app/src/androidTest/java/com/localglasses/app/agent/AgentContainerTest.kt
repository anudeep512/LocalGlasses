package com.localglasses.app.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localglasses.app.LocalGlassesApp
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentContainerTest {

    @Test
    fun applicationOwnsAgentContainer() {
        val app = ApplicationProvider.getApplicationContext<LocalGlassesApp>()
        assertNotNull(app.agentContainer)
    }
}
