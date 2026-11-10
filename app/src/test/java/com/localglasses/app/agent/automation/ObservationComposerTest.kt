package com.localglasses.app.agent.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationComposerTest {

    private fun node(
        text: String? = null,
        password: Boolean = false,
    ) = NodeSnapshot(
        viewId = "id",
        text = text,
        contentDescription = text,
        className = "TextView",
        bounds = null,
        clickable = true,
        editable = false,
        scrollable = false,
        password = password,
    )

    @Test
    fun capsNodesAtEighty() {
        val observation = ObservationComposer.compose("com.example", List(100) { node("n$it") })
        assertEquals(80, observation.nodes.size)
    }

    @Test
    fun dropsPasswordText() {
        val observation = ObservationComposer.compose("com.example", listOf(node("secret", password = true)))
        assertNull(observation.nodes.single().text)
        assertNull(observation.nodes.single().contentDescription)
        assertTrue(observation.nodes.single().password)
    }

    @Test
    fun truncatesLongText() {
        val observation = ObservationComposer.compose("com.example", listOf(node("x".repeat(200))))
        assertEquals(80, observation.nodes.single().text?.length)
    }

    @Test
    fun signatureIsStableForSameNodes() {
        val nodes = List(3) { node("n$it") }
        assertEquals(
            ObservationComposer.compose("com.example", nodes).signature,
            ObservationComposer.compose("com.example", nodes).signature,
        )
        assertFalse(
            ObservationComposer.compose("com.example", nodes).signature ==
                ObservationComposer.compose("com.example", listOf(node("other"))).signature,
        )
    }
}
