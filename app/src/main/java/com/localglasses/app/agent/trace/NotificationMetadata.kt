package com.localglasses.app.agent.trace

import android.service.notification.StatusBarNotification

data class NotificationMetadata(
    val packageName: String,
    val postedAt: Long,
    val channel: String?,
    val groupKey: String?,
)

/**
 * Deliberately reads only routing metadata. Notification title and body are never
 * touched, so OTP and message text cannot reach the trace store.
 */
object NotificationMetadataMapper {
    fun from(sbn: StatusBarNotification): NotificationMetadata = NotificationMetadata(
        packageName = sbn.packageName,
        postedAt = sbn.postTime,
        channel = sbn.notification.channelId,
        groupKey = sbn.groupKey,
    )
}
