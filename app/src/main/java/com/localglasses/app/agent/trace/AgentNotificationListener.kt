package com.localglasses.app.agent.trace

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localglasses.app.LocalGlassesApp

class AgentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val app = application as? LocalGlassesApp ?: return
        runCatching {
            app.agentContainer.traceDatabase.recordNotification(
                packageName = sbn.packageName,
                postedAt = sbn.postTime,
                channel = sbn.notification.channelId,
                groupKey = sbn.groupKey,
            )
        }
    }
}
