package com.localglasses.app.agent.automation

import android.graphics.Rect
import com.localglasses.app.agent.approval.Direction
import com.localglasses.app.agent.approval.NodeSelector
import java.security.MessageDigest

data class ObservedNode(
    val index: Int,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
)

data class ScreenObservation(
    val packageName: String?,
    val nodes: List<ObservedNode>,
) {
    val signature: String by lazy {
        val source = nodes.joinToString("|") {
            listOf(
                it.viewId,
                it.text,
                it.contentDescription,
                it.className,
                it.bounds?.toShortString(),
            ).joinToString(":")
        }
        MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun readableText(): String = nodes.joinToString(" ") { node ->
        node.contentDescription ?: node.text ?: ""
    }
}

data class NodeSnapshot(
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
)

object ObservationComposer {
    const val MAX_NODES = 80
    const val MAX_TEXT = 80

    fun compose(packageName: String?, nodes: List<NodeSnapshot>): ScreenObservation =
        ScreenObservation(
            packageName = packageName,
            nodes = nodes.take(MAX_NODES).mapIndexed { index, node ->
                ObservedNode(
                    index = index,
                    viewId = node.viewId,
                    text = if (node.password) null else node.text?.take(MAX_TEXT),
                    contentDescription = if (node.password) {
                        null
                    } else {
                        node.contentDescription?.take(MAX_TEXT)
                    },
                    className = node.className,
                    bounds = node.bounds,
                    clickable = node.clickable,
                    editable = node.editable,
                    scrollable = node.scrollable,
                    password = node.password,
                )
            },
        )
}

interface AccessibilityGateway {
    fun observe(): ScreenObservation
    fun tap(target: NodeSelector): Boolean
    fun type(target: NodeSelector, text: String): Boolean
    fun scroll(direction: Direction): Boolean
    fun launch(packageName: String): Boolean
    fun launchUri(uri: String): Boolean
}
