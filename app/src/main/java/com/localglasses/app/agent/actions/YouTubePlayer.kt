package com.localglasses.app.agent.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.automation.AccessibilityGateway
import com.localglasses.app.agent.automation.ObservedNode
import com.localglasses.app.agent.automation.ScreenObservation
import kotlinx.coroutines.delay

class YouTubePlayer(private val context: Context) {

    suspend fun play(
        query: String?,
        service: String?,
        gateway: AccessibilityGateway,
    ): ActionExecution {
        if (query.isNullOrBlank()) return ActionExecution.Failed("Search text is required")
        val music = service == "youtube_music"
        val packageName = if (music) {
            "com.google.android.apps.youtube.music"
        } else {
            "com.google.android.youtube"
        }
        if (!installed(packageName)) return ActionExecution.Failed("$packageName is not installed")

        val uri = if (music) {
            Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
        } else {
            val sortByViews = prefersViewCount(query)
            val suffix = if (sortByViews) "&sp=CAMSAhAB" else ""
            Uri.parse(
                "https://www.youtube.com/results?search_query=${Uri.encode(query)}$suffix",
            )
        }
        val launched = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!launched) return ActionExecution.Failed("Could not open YouTube")
        if (!waitForPackage(gateway, packageName, RESULT_TIMEOUT_MS)) {
            return ActionExecution.Failed("YouTube did not open")
        }

        val selectors = listOf(
            NodeSelector(viewId = "com.google.android.youtube:id/thumbnail"),
            NodeSelector(viewId = "com.google.android.apps.youtube.music:id/thumbnail"),
            NodeSelector(contentDescriptionContains = "play video"),
        )
        val deadline = System.currentTimeMillis() + RESULT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (selectors.any(gateway::tap)) {
                delay(1_500)
                val post = gateway.observe()
                return if (post.packageName == packageName && isPlaybackVisible(post)) {
                    ActionExecution.Success(
                        "Playing $query on ${if (music) "YouTube Music" else "YouTube"}",
                    )
                } else {
                    ActionExecution.Success(
                        "Selected a YouTube result; playback is not confirmed",
                    )
                }
            }
            delay(1_000)
        }
        return ActionExecution.Failed("YouTube result not found; open YouTube and tap a result")
    }

    private suspend fun waitForPackage(
        gateway: AccessibilityGateway,
        packageName: String,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (gateway.observe().packageName == packageName) return true
            delay(500)
        }
        return false
    }

    private fun isPlaybackVisible(observation: ScreenObservation): Boolean =
        observation.nodes.any(::isPlaybackNode)

    private fun isPlaybackNode(node: ObservedNode): Boolean {
        val text = "${node.text.orEmpty()} ${node.contentDescription.orEmpty()}".lowercase()
        return node.className?.contains("SeekBar", ignoreCase = true) == true ||
            "pause" in text ||
            "seek" in text
    }

    private fun prefersViewCount(query: String): Boolean =
        listOf("most viewed", "most-viewed", "most popular", "top viewed")
            .any { query.contains(it, ignoreCase = true) }

    private companion object {
        const val RESULT_TIMEOUT_MS = 12_000L
    }

    private fun installed(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)
}
