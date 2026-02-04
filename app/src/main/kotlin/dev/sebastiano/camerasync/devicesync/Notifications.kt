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
internal const val FIRMWARE_UPDATE_NOTIFICATION_CHANNEL = "FIRMWARE_UPDATE_NOTIFICATION_CHANNEL"

/**
 * Formats the elapsed time since the last sync in a human-readable format.
 *
 * Requirements:
 * - Relative timestamp if in the last 24 hours (e.g., seconds ago, 10 minutes ago, 2 hours ago)
 * - "Yesterday at 13:33" if between 24 and 48 hours ago (roughly "yesterday")
 * - Date and time if further in the past (e.g., "Jan 1, 2026, 13:33")
 */
internal fun formatElapsedTimeSince(context: Context, lastSyncTime: ZonedDateTime?): String {
    if (lastSyncTime == null) return context.getString(R.string.time_never)
    return formatElapsedTimeSince(context, lastSyncTime.toInstant().toEpochMilli())
}

/** Formats the elapsed time since the last sync in a human-readable format. */
internal fun formatElapsedTimeSince(context: Context, lastSyncTimestamp: Long?): String {
    if (lastSyncTimestamp == null) return context.getString(R.string.time_never)

    val now = Instant.now()
    val then = Instant.ofEpochMilli(lastSyncTimestamp)
    val diffSeconds = ChronoUnit.SECONDS.between(then, now)

    if (diffSeconds < 0)
        return context.getString(R.string.time_just_now) // Should not happen with real clocks

    return when {
        diffSeconds < 60 -> context.getString(R.string.time_seconds_ago)
        diffSeconds < 3600 ->
            context.resources.getQuantityString(
                R.plurals.time_minutes_ago,
                (diffSeconds / 60).toInt(),
                (diffSeconds / 60).toInt(),
            )
        diffSeconds < 24 * 3600 ->
            context.resources.getQuantityString(
                R.plurals.time_hours_ago,
                (diffSeconds / 3600).toInt(),
                (diffSeconds / 3600).toInt(),
            )
        else -> {
            val thenDateTime = LocalDateTime.ofInstant(then, ZoneId.systemDefault())
            val nowDateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault())

            val yesterday = nowDateTime.minusDays(1).toLocalDate()
            if (thenDateTime.toLocalDate() == yesterday) {
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                context.getString(R.string.time_yesterday_at, thenDateTime.format(timeFormatter))
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
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val syncChannel =
        NotificationChannel(
            /* id = */ NOTIFICATION_CHANNEL,
            /* name = */ context.getString(R.string.notification_channel_name),
            /* importance = */ NotificationManager.IMPORTANCE_MIN,
        )
    notificationManager.createNotificationChannel(syncChannel)

    val firmwareUpdateChannel =
        NotificationChannel(
            /* id = */ FIRMWARE_UPDATE_NOTIFICATION_CHANNEL,
            /* name = */ context.getString(R.string.firmware_update_notification_channel_name),
            /* importance = */ NotificationManager.IMPORTANCE_DEFAULT,
        )
    notificationManager.createNotificationChannel(firmwareUpdateChannel)
}

// --- Multi-device notification functions ---

/** Parameters for creating a multi-device sync notification. */
internal data class MultiDeviceNotificationParams(
    val connectedCount: Int,
    val totalEnabled: Int,
    val lastSyncTime: ZonedDateTime?,
)

/** Creates a notification for multi-device sync showing connection status. */
internal fun createMultiDeviceNotification(
    context: Context,
    notificationBuilder: NotificationBuilder,
    pendingIntentFactory: PendingIntentFactory,
    intentFactory: IntentFactory,
    params: MultiDeviceNotificationParams,
): Notification {
    val title =
        when {
            params.totalEnabled == 0 -> context.getString(R.string.notification_no_devices)
            params.connectedCount == 0 ->
                context.resources.getQuantityString(
                    R.plurals.notification_searching,
                    params.totalEnabled,
                    params.totalEnabled,
                )
            params.connectedCount == params.totalEnabled -> {
                context.resources.getQuantityString(
                    R.plurals.notification_syncing,
                    params.connectedCount,
                    params.connectedCount,
                )
            }
            else ->
                context.getString(
                    R.string.notification_syncing_partial,
                    params.connectedCount,
                    params.totalEnabled,
                )
        }

    val content =
        when {
            params.totalEnabled == 0 -> context.getString(R.string.notification_enable_to_start)
            params.connectedCount == 0 -> context.getString(R.string.notification_will_connect)
            params.connectedCount < params.totalEnabled -> {
                val missing = params.totalEnabled - params.connectedCount
                val syncText =
                    if (params.lastSyncTime != null) {
                        context.getString(
                            R.string.notification_last_sync,
                            formatElapsedTimeSince(context, params.lastSyncTime),
                        )
                    } else {
                        context.getString(R.string.notification_connected_syncing)
                    }
                context.getString(R.string.notification_sync_waiting, syncText, missing)
            }
            params.lastSyncTime != null ->
                context.getString(
                    R.string.notification_last_sync,
                    formatElapsedTimeSince(context, params.lastSyncTime),
                )
            else -> context.getString(R.string.notification_connected_syncing)
        }

    val icon =
        when {
            params.connectedCount == 0 -> R.drawable.ic_sync_disabled
            else -> R.drawable.ic_sync
        }

    val actions =
        listOf(
            NotificationAction(
                icon = 0,
                title = context.getString(R.string.notification_action_refresh),
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
                title = context.getString(R.string.notification_action_stop),
                pendingIntent =
                    pendingIntentFactory.createServicePendingIntent(
                        context,
                        MultiDeviceSyncService.STOP_REQUEST_CODE,
                        intentFactory.createStopIntent(context),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
            ),
        )

    val contentIntent =
        pendingIntentFactory.createActivityPendingIntent(
            context,
            MultiDeviceSyncService.MAIN_ACTIVITY_REQUEST_CODE,
            intentFactory.createMainActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
        contentIntent = contentIntent,
    )
}

/** Parameters for creating a firmware update notification. */
internal data class FirmwareUpdateNotificationParams(
    val deviceName: String,
    val currentVersion: String,
    val latestVersion: String,
)

/** Creates a notification indicating that a firmware update is available for a connected device. */
internal fun createFirmwareUpdateNotification(
    context: Context,
    notificationBuilder: NotificationBuilder,
    pendingIntentFactory: PendingIntentFactory,
    intentFactory: IntentFactory,
    params: FirmwareUpdateNotificationParams,
): Notification {
    val title = context.getString(R.string.firmware_update_notification_title)
    val content =
        context.getString(
            R.string.firmware_update_notification_content,
            params.deviceName,
            params.currentVersion,
            params.latestVersion,
        )

    val contentIntent =
        pendingIntentFactory.createActivityPendingIntent(
            context,
            MultiDeviceSyncService.MAIN_ACTIVITY_REQUEST_CODE,
            intentFactory.createMainActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    return notificationBuilder.build(
        channelId = FIRMWARE_UPDATE_NOTIFICATION_CHANNEL,
        title = title,
        content = content,
        icon = R.drawable.ic_sync,
        isOngoing = false,
        priority = PRIORITY_HIGH,
        category = Notification.CATEGORY_STATUS,
        isSilent = false,
        actions = emptyList(),
        contentIntent = contentIntent,
    )
}
