package com.localglasses.app.agent.trace

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localglasses.app.LocalGlassesApp

class AgentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val app = application as? LocalGlassesApp ?: return
        runCatching {
            val metadata = NotificationMetadataMapper.from(sbn)
            app.agentContainer.traceDatabase.recordNotification(
                packageName = metadata.packageName,
                postedAt = metadata.postedAt,
                channel = metadata.channel,
                groupKey = metadata.groupKey,
            )
        }
    }
}
