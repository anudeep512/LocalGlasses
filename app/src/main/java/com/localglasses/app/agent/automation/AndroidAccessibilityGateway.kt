package com.localglasses.app.agent.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.localglasses.app.agent.approval.Direction
import com.localglasses.app.agent.approval.NodeSelector

class AndroidAccessibilityGateway(
    private val service: AccessibilityService,
) : AccessibilityGateway {

    override fun observe(): ScreenObservation {
        val root = service.rootInActiveWindow ?: return ScreenObservation(null, emptyList())
        val nodes = mutableListOf<ObservedNode>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && nodes.size < MAX_NODES) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                nodes.add(
                    ObservedNode(
                        index = nodes.size,
                        viewId = node.viewIdResourceName,
                        text = if (node.isPassword) null else node.text?.toString()?.take(MAX_TEXT),
                        contentDescription = if (node.isPassword) {
                            null
                        } else {
                            node.contentDescription?.toString()?.take(MAX_TEXT)
                        },
                        className = node.className?.toString(),
                        bounds = Rect().also { node.getBoundsInScreen(it) },
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        password = node.isPassword,
                    ),
                )
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return ScreenObservation(root.packageName?.toString(), nodes)
    }

    override fun tap(target: NodeSelector): Boolean {
        val node = find(target) ?: return false
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) {
            clickable = clickable.parent
        }
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override fun type(target: NodeSelector, text: String): Boolean {
        val node = find(target) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun scroll(direction: Direction): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        val action = if (direction == Direction.FORWARD) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    override fun launch(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            service.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun find(selector: NodeSelector): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (matches(node, selector)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }

    private fun matches(node: AccessibilityNodeInfo, selector: NodeSelector): Boolean {
        if (selector.viewId != null && node.viewIdResourceName != selector.viewId) return false
        if (selector.contentDescription != null &&
            node.contentDescription?.toString() != selector.contentDescription
        ) {
            return false
        }
        if (selector.text != null && node.text?.toString() != selector.text) return false
        if (selector.textContains != null &&
            node.text?.toString()?.contains(selector.textContains) != true
        ) {
            return false
        }
        return true
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                findScrollable(child)?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val MAX_NODES = 80
        const val MAX_TEXT = 80
    }
}
