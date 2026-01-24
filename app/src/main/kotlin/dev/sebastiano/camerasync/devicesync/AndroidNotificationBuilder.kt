package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

/** Android implementation of [NotificationBuilder] using NotificationCompat. */
class AndroidNotificationBuilder(private val context: Context) : NotificationBuilder {
    override fun build(
        channelId: String,
        title: String,
        content: String,
        icon: Int,
        isOngoing: Boolean,
        priority: Int,
        category: String?,
        isSilent: Boolean,
        actions: List<NotificationAction>,
        contentIntent: PendingIntent?,
    ): Notification {
        val builder =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(icon)
                .setOngoing(isOngoing)
                .setPriority(priority)
                .setSilent(isSilent)

        if (category != null) {
            builder.setCategory(category)
        }

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        actions.forEach { action ->
            builder.addAction(
                NotificationCompat.Action.Builder(action.icon, action.title, action.pendingIntent)
                    .build()
            )
        }

        return builder.build()
    }
}
