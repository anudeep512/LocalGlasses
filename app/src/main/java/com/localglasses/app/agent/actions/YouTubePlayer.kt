package com.localglasses.app.agent.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.automation.AccessibilityGateway
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
            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
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

        delay(3_000)
        val tapped = gateway.tap(NodeSelector(contentDescriptionContains = "play video")) ||
            gateway.tap(NodeSelector(viewId = "com.google.android.youtube:id/thumbnail")) ||
            gateway.tap(NodeSelector(viewId = "com.google.android.apps.youtube.music:id/thumbnail"))
        return if (tapped) {
            ActionExecution.Success("Playing $query on ${if (music) "YouTube Music" else "YouTube"}")
        } else {
            ActionExecution.Failed("YouTube result not found")
        }
    }

    private fun installed(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)
}
