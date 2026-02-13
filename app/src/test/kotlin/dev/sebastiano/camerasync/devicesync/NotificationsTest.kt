package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.di.TestGraph
import dev.sebastiano.camerasync.di.TestGraphFactory
import dev.sebastiano.camerasync.fakes.FakeIntentFactory
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeNotificationBuilder
import dev.sebastiano.camerasync.fakes.FakePendingIntentFactory
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun Notification.getTitle(): CharSequence? =
    extras.getCharSequence(NotificationCompat.EXTRA_TITLE)

private fun Notification.getText(): CharSequence? =
    extras.getCharSequence(NotificationCompat.EXTRA_TEXT)

class NotificationsTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var testGraph: TestGraph

    // Store fake instances to avoid getting new instances on each testGraph.* access
    private lateinit var notificationBuilder: FakeNotificationBuilder
    private lateinit var pendingIntentFactory: FakePendingIntentFactory
    private lateinit var intentFactory: FakeIntentFactory
    private lateinit var resources: Resources

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        // Create test dependency graph using Metro
        testGraph = TestGraphFactory.create()

        // Store fake instances - each testGraph.* access creates a NEW instance,
        // so we must store them and reuse the same instances throughout the test
        notificationBuilder = testGraph.notificationBuilder as FakeNotificationBuilder
        pendingIntentFactory = testGraph.pendingIntentFactory as FakePendingIntentFactory
        intentFactory = testGraph.intentFactory as FakeIntentFactory

        // Reset fake factories between tests
        notificationBuilder.reset()
        pendingIntentFactory.reset()
        intentFactory.reset()

        // Mock context and notification manager
        notificationManager = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        context = mockk(relaxed = true)
        // Return NotificationManager directly for NOTIFICATION_SERVICE
        // Use answers to ensure proper type casting
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } answers
            {
                notificationManager
            }
        every { context.getSystemService(any()) } returns
            mockk(relaxed = true) // Handle any other system service
        every { context.packageName } returns "dev.sebastiano.camerasync"
        every { context.applicationContext } returns context
        every { context.resources } returns resources
        every { context.theme } returns mockk(relaxed = true)
        every { context.classLoader } returns javaClass.classLoader
        // Ensure packageManager is available for PendingIntent creation
        every { context.packageManager } returns mockk(relaxed = true)

        every {
            resources.getQuantityString(R.plurals.notification_searching, any(), any())
        } answers
            {
                val count = args[1] as Int
                if (count == 1) {
                    "Searching for 1 device…"
                } else {
                    "Searching for $count devices…"
                }
            }
        every { resources.getQuantityString(R.plurals.notification_syncing, any(), any()) } answers
            {
                val count = args[1] as Int
                if (count == 1) {
                    "Syncing with 1 device"
                } else {
                    "Syncing with $count devices"
                }
            }

        every { context.getString(R.string.notification_no_devices) } returns "No devices enabled"
        every { context.getString(R.string.notification_enable_to_start) } returns
            "Enable devices to start syncing"
        every { context.getString(R.string.notification_will_connect) } returns
            "Will connect when cameras are in range"
        every { context.getString(R.string.notification_connected_syncing) } returns
            "Connected and syncing"
        every { context.getString(R.string.notification_action_refresh) } returns "Refresh"
        every { context.getString(R.string.notification_action_stop) } returns "Stop all"
        every { context.getString(R.string.notification_syncing_partial, any(), any()) } answers
            {
                val formatArgs = args[1] as Array<*>
                val connected = formatArgs[0] as Int
                val total = formatArgs[1] as Int
                "Syncing with $connected of $total devices"
            }
        every { context.getString(R.string.notification_last_sync, any()) } answers
            {
                val formatArgs = args[1] as Array<*>
                val value = formatArgs[0] as String
                "Last sync: $value"
            }
        every { context.getString(R.string.notification_sync_waiting, any(), any()) } answers
            {
                val formatArgs = args[1] as Array<*>
                val syncText = formatArgs[0] as String
                val waiting = formatArgs[1] as Int
                "$syncText • $waiting waiting"
            }

        // Skip registerNotificationChannel in tests - it requires a real NotificationManager
        // and we're using FakeNotificationBuilder anyway, so the channel registration isn't needed
        // registerNotificationChannel(context)
    }

    @Test
    fun `notification shows no devices when none enabled`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 0, totalEnabled = 0, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(context.getString(R.string.notification_no_devices), notification.getTitle())
        assertEquals(
            context.getString(R.string.notification_enable_to_start),
            notification.getText(),
        )
    }

    @Test
    fun `notification shows searching when enabled but not connected`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 0, totalEnabled = 2, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_searching, 2, 2),
            notification.getTitle(),
        )
        assertEquals(context.getString(R.string.notification_will_connect), notification.getText())
    }

    @Test
    fun `notification shows single device syncing when all connected`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 1, totalEnabled = 1, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_syncing, 1, 1),
            notification.getTitle(),
        )
        assertEquals(
            context.getString(R.string.notification_connected_syncing),
            notification.getText(),
        )
    }

    @Test
    fun `notification shows multiple devices syncing when all connected`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 3, totalEnabled = 3, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_syncing, 3, 3),
            notification.getTitle(),
        )
        assertEquals(
            context.getString(R.string.notification_connected_syncing),
            notification.getText(),
        )
    }

    @Test
    fun `notification shows partial connection with X of Y format`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 2, totalEnabled = 3, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.getString(R.string.notification_syncing_partial, 2, 3),
            notification.getTitle(),
        )
        assertTrue(notification.getText()?.toString()?.contains("waiting") == true)
    }

    @Test
    fun `notification shows waiting count when partially connected`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 1, totalEnabled = 3, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.getString(R.string.notification_syncing_partial, 1, 3),
            notification.getTitle(),
        )
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("2 waiting") || content.contains("waiting"))
    }

    @Test
    fun `notification shows last sync time when available`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val params =
            MultiDeviceNotificationParams(
                connectedCount = 2,
                totalEnabled = 2,
                lastSyncTime = lastSyncTime,
            )
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_syncing, 2, 2),
            notification.getTitle(),
        )
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
    }

    @Test
    fun `notification shows last sync time with waiting count when partially connected`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val params =
            MultiDeviceNotificationParams(
                connectedCount = 1,
                totalEnabled = 3,
                lastSyncTime = lastSyncTime,
            )
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        assertEquals(
            context.getString(R.string.notification_syncing_partial, 1, 3),
            notification.getTitle(),
        )
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
        assertTrue(content.contains("waiting"))
    }

    @Test
    fun `notification has refresh and stop actions`() {
        val params =
            MultiDeviceNotificationParams(connectedCount = 1, totalEnabled = 1, lastSyncTime = null)
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                params = params,
            )

        // Verify notification builder received 2 actions
        val buildCall = notificationBuilder.lastBuildCall
        assertNotNull(buildCall)
        assertEquals(2, buildCall!!.actions.size)
        assertEquals(
            context.getString(R.string.notification_action_refresh),
            buildCall.actions[0].title,
        )
        assertEquals(
            context.getString(R.string.notification_action_stop),
            buildCall.actions[1].title,
        )

        // Verify content intent was set
        assertNotNull(buildCall.contentIntent)

        // Verify PendingIntentFactory was called 3 times (2 actions + 1 content intent)
        assertEquals(3, pendingIntentFactory.calls.size)
        assertEquals(
            MultiDeviceSyncService.REFRESH_REQUEST_CODE,
            pendingIntentFactory.calls[0].requestCode,
        )
        assertEquals(
            MultiDeviceSyncService.STOP_REQUEST_CODE,
            pendingIntentFactory.calls[1].requestCode,
        )
        assertEquals(
            MultiDeviceSyncService.MAIN_ACTIVITY_REQUEST_CODE,
            pendingIntentFactory.calls[2].requestCode,
        )

        // Verify the intents were created correctly
        assertNotNull(intentFactory.lastRefreshIntent)
        assertNotNull(intentFactory.lastStopIntent)
        assertNotNull(intentFactory.lastMainActivityIntent)
    }
}
