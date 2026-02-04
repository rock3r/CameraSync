package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

/**
 * Interface for building notifications, allowing testability by providing a fake implementation.
 */
interface NotificationBuilder {
    /**
     * Builds a system [Notification].
     *
     * @param channelId The ID of the notification channel.
     * @param title The title of the notification.
     * @param content The main body text of the notification.
     * @param icon The resource ID of the notification icon.
     * @param isOngoing Whether the notification is ongoing (not dismissible).
     * @param priority The notification priority.
     * @param category The notification category (e.g., [NotificationCompat.CATEGORY_SERVICE]).
     * @param isSilent Whether the notification should be silent.
     * @param actions A list of [NotificationAction]s to add to the notification.
     * @param contentIntent The [PendingIntent] to trigger when the notification is clicked.
     * @return The built [Notification].
     */
    fun build(
        channelId: String,
        title: String,
        content: String,
        icon: Int,
        isOngoing: Boolean = false,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        category: String? = null,
        isSilent: Boolean = false,
        actions: List<NotificationAction> = emptyList(),
        contentIntent: PendingIntent? = null,
    ): Notification
}

/**
 * Represents an action button in a notification.
 *
 * @property icon The resource ID of the action icon.
 * @property title The title of the action button.
 * @property pendingIntent The [PendingIntent] to trigger when the action button is clicked.
 */
data class NotificationAction(val icon: Int, val title: String, val pendingIntent: PendingIntent)
