package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import dev.sebastiano.camerasync.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal const val NOTIFICATION_CHANNEL = "SYNC_SERVICE_NOTIFICATION_CHANNEL"

/**
 * Formats the elapsed time since the last sync in a human-readable format.
 *
 * Requirements:
 * - Relative timestamp if in the last 24 hours (e.g., seconds ago, 10 minutes ago, 2 hours ago)
 * - "Yesterday at 13:33" if between 24 and 48 hours ago (roughly "yesterday")
 * - Date and time if further in the past (e.g., "Jan 1, 2026, 13:33")
 */
internal fun formatElapsedTimeSince(lastSyncTime: ZonedDateTime?): String {
    if (lastSyncTime == null) return "never"
    return formatElapsedTimeSince(lastSyncTime.toInstant().toEpochMilli())
}

/** Formats the elapsed time since the last sync in a human-readable format. */
internal fun formatElapsedTimeSince(lastSyncTimestamp: Long?): String {
    if (lastSyncTimestamp == null) return "never"

    val now = Instant.now()
    val then = Instant.ofEpochMilli(lastSyncTimestamp)
    val diffSeconds = ChronoUnit.SECONDS.between(then, now)

    if (diffSeconds < 0) return "just now" // Should not happen with real clocks

    return when {
        diffSeconds < 60 -> "seconds ago"
        diffSeconds < 3600 -> "${diffSeconds / 60} minutes ago"
        diffSeconds < 24 * 3600 -> "${diffSeconds / 3600} hours ago"
        else -> {
            val thenDateTime = LocalDateTime.ofInstant(then, ZoneId.systemDefault())
            val nowDateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault())

            val yesterday = nowDateTime.minusDays(1).toLocalDate()
            if (thenDateTime.toLocalDate() == yesterday) {
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                "yesterday at ${thenDateTime.format(timeFormatter)}"
            } else {
                val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm")
                thenDateTime.format(dateTimeFormatter)
            }
        }
    }
}

/** Creates a notification builder for error notifications. */
fun createErrorNotificationBuilder(context: Context): NotificationCompat.Builder =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_sync_error)
        .setPriority(PRIORITY_HIGH)
        .setCategory(Notification.CATEGORY_ERROR)

/**
 * Registers the notification channel for the sync service.
 *
 * Should be called during app initialization.
 */
fun registerNotificationChannel(context: Context) {
    val channel =
        NotificationChannel(
            /* id = */ NOTIFICATION_CHANNEL,
            /* name = */ "Camera connection",
            /* importance = */ NotificationManager.IMPORTANCE_MIN,
        )

    @Suppress("UNCHECKED_CAST")
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.createNotificationChannel(channel)
}

// --- Multi-device notification functions ---

/** Creates a notification for multi-device sync showing connection status. */
internal fun createMultiDeviceNotification(
    notificationBuilder: NotificationBuilder,
    pendingIntentFactory: PendingIntentFactory,
    intentFactory: IntentFactory,
    context: Context,
    connectedCount: Int,
    totalEnabled: Int,
    lastSyncTime: java.time.ZonedDateTime?,
): Notification {
    val title =
        when {
            totalEnabled == 0 -> "No devices enabled"
            connectedCount == 0 ->
                "Searching for $totalEnabled device${if (totalEnabled > 1) "s" else ""}..."
            connectedCount == totalEnabled -> {
                if (connectedCount == 1) {
                    "Syncing with 1 device"
                } else {
                    "Syncing with $connectedCount devices"
                }
            }
            else -> "Syncing with $connectedCount of $totalEnabled devices"
        }

    val content =
        when {
            totalEnabled == 0 -> "Enable devices to start syncing"
            connectedCount == 0 -> "Will connect when cameras are in range"
            connectedCount < totalEnabled -> {
                val missing = totalEnabled - connectedCount
                val syncText =
                    if (lastSyncTime != null) {
                        "Last sync: ${formatElapsedTimeSince(lastSyncTime)}"
                    } else {
                        "Connected and syncing"
                    }
                "$syncText • $missing waiting"
            }
            lastSyncTime != null -> "Last sync: ${formatElapsedTimeSince(lastSyncTime)}"
            else -> "Connected and syncing"
        }

    val icon =
        when {
            connectedCount == 0 -> R.drawable.ic_sync_disabled
            else -> R.drawable.ic_sync
        }

    val actions =
        listOf(
            NotificationAction(
                icon = 0,
                title = "Refresh",
                pendingIntent =
                    pendingIntentFactory.createServicePendingIntent(
                        context,
                        MultiDeviceSyncService.REFRESH_REQUEST_CODE,
                        intentFactory.createRefreshIntent(context),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
            ),
            NotificationAction(
                icon = 0,
                title = "Stop all",
                pendingIntent =
                    pendingIntentFactory.createServicePendingIntent(
                        context,
                        MultiDeviceSyncService.STOP_REQUEST_CODE,
                        intentFactory.createStopIntent(context),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
            ),
        )

    return notificationBuilder.build(
        channelId = NOTIFICATION_CHANNEL,
        title = title,
        content = content,
        icon = icon,
        isOngoing = true,
        priority = PRIORITY_LOW,
        category = Notification.CATEGORY_LOCATION_SHARING,
        isSilent = true,
        actions = actions,
    )
}
