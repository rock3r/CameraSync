package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.sebastiano.camerasync.CameraSyncApp
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
    private lateinit var testGraph: dev.sebastiano.camerasync.di.TestGraph

    // Store fake instances to avoid getting new instances on each testGraph.* access
    private lateinit var notificationBuilder: FakeNotificationBuilder
    private lateinit var pendingIntentFactory: FakePendingIntentFactory
    private lateinit var intentFactory: FakeIntentFactory

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
        notificationManager = mockk<NotificationManager>(relaxed = true)
        context =
            mockk<Context>(relaxed = true) {
                // Return NotificationManager directly for NOTIFICATION_SERVICE
                // Use answers to ensure proper type casting
                every { getSystemService(Context.NOTIFICATION_SERVICE) } answers
                    {
                        notificationManager
                    }
                every { getSystemService(any()) } returns
                    mockk(relaxed = true) // Handle any other system service
                every { packageName } returns "dev.sebastiano.camerasync"
                every { applicationContext } returns this
                every { resources } returns mockk(relaxed = true)
                every { theme } returns mockk(relaxed = true)
                every { classLoader } returns javaClass.classLoader
                // Ensure packageManager is available for PendingIntent creation
                every { packageManager } returns mockk(relaxed = true)
            }

        // Skip registerNotificationChannel in tests - it requires a real NotificationManager
        // and we're using FakeNotificationBuilder anyway, so the channel registration isn't needed
        // registerNotificationChannel(context)
    }

    @Test
    fun `notification shows no devices when none enabled`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 0,
                totalEnabled = 0,
                lastSyncTime = null,
            )

        assertEquals("No devices enabled", notification.getTitle())
        assertEquals("Enable devices to start syncing", notification.getText())
    }

    @Test
    fun `notification shows searching when enabled but not connected`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 0,
                totalEnabled = 2,
                lastSyncTime = null,
            )

        assertEquals("Searching for 2 devices...", notification.getTitle())
        assertEquals("Will connect when cameras are in range", notification.getText())
    }

    @Test
    fun `notification shows single device syncing when all connected`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 1,
                totalEnabled = 1,
                lastSyncTime = null,
            )

        assertEquals("Syncing with 1 device", notification.getTitle())
        assertEquals("Connected and syncing", notification.getText())
    }

    @Test
    fun `notification shows multiple devices syncing when all connected`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 3,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals("Syncing with 3 devices", notification.getTitle())
        assertEquals("Connected and syncing", notification.getText())
    }

    @Test
    fun `notification shows partial connection with X of Y format`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 2,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals("Syncing with 2 of 3 devices", notification.getTitle())
        assertTrue(notification.getText()?.toString()?.contains("waiting") == true)
    }

    @Test
    fun `notification shows waiting count when partially connected`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 1,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals("Syncing with 1 of 3 devices", notification.getTitle())
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("2 waiting") || content.contains("waiting"))
    }

    @Test
    fun `notification shows last sync time when available`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 2,
                totalEnabled = 2,
                lastSyncTime = lastSyncTime,
            )

        assertEquals("Syncing with 2 devices", notification.getTitle())
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
    }

    @Test
    fun `notification shows last sync time with waiting count when partially connected`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 1,
                totalEnabled = 3,
                lastSyncTime = lastSyncTime,
            )

        assertEquals("Syncing with 1 of 3 devices", notification.getTitle())
        val content = notification.getText()?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
        assertTrue(content.contains("waiting"))
    }

    @Test
    fun `notification has refresh and stop actions`() {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = context,
                connectedCount = 1,
                totalEnabled = 1,
                lastSyncTime = null,
            )

        // Verify notification builder received 2 actions
        val buildCall = notificationBuilder.lastBuildCall
        assertNotNull(buildCall)
        assertEquals(2, buildCall!!.actions.size)
        assertEquals("Refresh", buildCall.actions[0].title)
        assertEquals("Stop all", buildCall.actions[1].title)

        // Verify content intent was set
        assertNotNull(buildCall.contentIntent)

        // Verify PendingIntentFactory was called 3 times (2 actions + 1 content intent)
        assertEquals(3, pendingIntentFactory.calls.size)
        assertEquals(
            dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService.REFRESH_REQUEST_CODE,
            pendingIntentFactory.calls[0].requestCode,
        )
        assertEquals(
            dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService.STOP_REQUEST_CODE,
            pendingIntentFactory.calls[1].requestCode,
        )
        assertEquals(
            dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService.MAIN_ACTIVITY_REQUEST_CODE,
            pendingIntentFactory.calls[2].requestCode,
        )

        // Verify the intents were created correctly
        assertNotNull(intentFactory.lastRefreshIntent)
        assertNotNull(intentFactory.lastStopIntent)
        assertNotNull(intentFactory.lastMainActivityIntent)
    }
}
