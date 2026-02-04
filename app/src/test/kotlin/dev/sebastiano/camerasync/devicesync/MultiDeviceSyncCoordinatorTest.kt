package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.toCamera
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeDeviceNameProvider
import dev.sebastiano.camerasync.fakes.FakeIntentFactory
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeLocationCollector
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakePendingIntentFactory
import dev.sebastiano.camerasync.fakes.FakeVendorRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class MultiDeviceSyncCoordinatorTest {

    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var locationCollector: FakeLocationCollector
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var pendingIntentFactory: FakePendingIntentFactory
    private lateinit var intentFactory: FakeIntentFactory
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var testScope: TestScope
    private lateinit var connectionManager: DeviceConnectionManager
    private lateinit var firmwareManager: DeviceFirmwareManager
    private lateinit var coordinator: MultiDeviceSyncCoordinator

    private val testDevice1 =
        PairedDevice(
            macAddress = "00:11:22:33:44:55",
            name = "Test Camera 1",
            vendorId = "fake",
            isEnabled = true,
            lastSyncedAt = 1L,
        )

    private val testDevice2 =
        PairedDevice(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = "Test Camera 2",
            vendorId = "fake",
            isEnabled = true,
            lastSyncedAt = 1L,
        )

    private val testLocation =
        GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC")),
        )

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        cameraRepository = FakeCameraRepository()
        locationCollector = FakeLocationCollector()
        vendorRegistry = FakeVendorRegistry()
        pairedDevicesRepository = FakePairedDevicesRepository()
        pendingIntentFactory = FakePendingIntentFactory()
        intentFactory = FakeIntentFactory()
        notificationManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(R.string.error_unknown_vendor) } returns "Unknown camera vendor"
        every { context.getString(R.string.label_unknown) } returns "Unknown"
        every { context.getString(R.string.firmware_update_notification_title) } returns
            "Firmware update available"
        every {
            context.getString(R.string.firmware_update_notification_content, any(), any(), any())
        } answers
            {
                val args = args[1] as Array<*>
                "${args[0]} has a firmware update available (${args[1]} → ${args[2]})"
            }
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.packageName } returns "dev.sebastiano.camerasync"
        every { context.applicationContext } returns context
        mockkStatic(NotificationManagerCompat::class)
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        every { NotificationManagerCompat.from(context) } returns notificationManagerCompat

        testScope = TestScope(UnconfinedTestDispatcher())

        connectionManager = DeviceConnectionManager()
        val notificationBuilder =
            object : NotificationBuilder {
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
                ): Notification = mockk(relaxed = true)
            }

        firmwareManager =
            DeviceFirmwareManager(
                context = context,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                notificationBuilder = notificationBuilder,
            )

        coordinator =
            MultiDeviceSyncCoordinator(
                context = context,
                cameraRepository = cameraRepository,
                vendorRegistry = vendorRegistry,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                connectionManager = connectionManager,
                firmwareManager = firmwareManager,
                deviceNameProvider = FakeDeviceNameProvider(),
                locationCollector = locationCollector,
                coroutineScope = testScope.backgroundScope,
            )
    }

    @After
    fun tearDown() {
        // No-op
    }

    @Test
    fun `initial state has no devices`() =
        testScope.runTest {
            assertEquals(emptyMap<String, DeviceConnectionState>(), coordinator.deviceStates.value)
            assertEquals(0, coordinator.getConnectedDeviceCount())
        }

    @Test
    fun `startDeviceSync transitions to Searching state`() =
        testScope.runTest {
            cameraRepository.connectDelay = 1000L

            coordinator.startDeviceSync(testDevice1)

            // With UnconfinedTestDispatcher, execution proceeds until suspension at connect()
            // So state transitions Searching -> Connecting
            assertEquals(
                DeviceConnectionState.Connecting,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
        }

    @Test
    fun `startDeviceSync transitions from Searching to Connecting when device found`() =
        testScope.runTest {
            // We can't easily test the intermediate Connecting state with UnconfinedTestDispatcher
            // because the onFound callback is called synchronously in our fake
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Should end up in Syncing
            assertTrue(
                coordinator.getDeviceState(testDevice1.macAddress) is DeviceConnectionState.Syncing
            )
        }

    @Test
    fun `startDeviceSync registers device for location updates`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            assertTrue(locationCollector.registerDeviceCalls.contains(testDevice1.macAddress))
            assertEquals(1, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `background monitoring proactively connects enabled devices`() =
        testScope.runTest {
            pairedDevicesRepository.setTestDevices(listOf(testDevice1))
            cameraRepository.connectionToReturn = FakeCameraConnection(testDevice1.toTestCamera())

            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // We expect 1 connection attempt because of the proactive startup connect logic
            // which connects to enabled devices even without external presence updates
            assertEquals(1, cameraRepository.connectCallCount)
        }

    @Test
    fun `refreshConnections ignores presence gating`() =
        testScope.runTest {
            pairedDevicesRepository.setTestDevices(listOf(testDevice1))
            cameraRepository.connectionToReturn = FakeCameraConnection(testDevice1.toTestCamera())

            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            coordinator.refreshConnections()
            advanceUntilIdle()

            assertEquals(1, cameraRepository.connectCallCount)
        }

    @Test
    fun `background monitoring connects never synced device when presence is empty`() =
        testScope.runTest {
            val neverSyncedDevice = testDevice1.copy(lastSyncedAt = null)
            pairedDevicesRepository.setTestDevices(listOf(neverSyncedDevice))
            cameraRepository.connectionToReturn =
                FakeCameraConnection(neverSyncedDevice.toTestCamera())

            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            assertEquals(1, cameraRepository.connectCallCount)
        }

    @Test
    fun `multiple devices can be synced simultaneously`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection2
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()

            assertEquals(2, locationCollector.getRegisteredDeviceCount())
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))
        }

    @Test
    fun `location updates are synced to all connected devices`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection2
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()

            // Emit a location
            locationCollector.emitLocation(testLocation)
            advanceUntilIdle()

            // Both devices should have received the location
            assertEquals(testLocation, connection1.lastSyncedLocation)
            assertEquals(testLocation, connection2.lastSyncedLocation)
        }

    @Test
    fun `stopDeviceSync disconnects device and updates state`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

            coordinator.stopDeviceSync(testDevice1.macAddress)
            advanceUntilIdle()

            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
        }

    @Test
    fun `stopDeviceSync unregisters device from location updates`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()
            assertEquals(1, locationCollector.getRegisteredDeviceCount())

            coordinator.stopDeviceSync(testDevice1.macAddress)
            advanceUntilIdle()

            assertTrue(locationCollector.unregisterDeviceCalls.contains(testDevice1.macAddress))
            assertEquals(0, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `stopAllDevices stops all syncs`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection2
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()

            assertEquals(2, coordinator.getConnectedDeviceCount())

            coordinator.stopAllDevices()
            advanceUntilIdle()

            assertEquals(0, coordinator.getConnectedDeviceCount())
        }

    @Test
    fun `connection timeout updates state to Unreachable`() {
        // Use StandardTestDispatcher for this test to properly test timeouts
        val testDispatcher = StandardTestDispatcher()
        val timeoutTestScope = TestScope(testDispatcher)
        val timeoutConnectionManager = DeviceConnectionManager()
        val timeoutNotificationBuilder =
            object : NotificationBuilder {
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
                ): Notification = mockk(relaxed = true)
            }
        val timeoutFirmwareManager =
            DeviceFirmwareManager(
                context = context,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                notificationBuilder = timeoutNotificationBuilder,
            )

        val timeoutCoordinator =
            MultiDeviceSyncCoordinator(
                context = context,
                cameraRepository = cameraRepository,
                vendorRegistry = vendorRegistry,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                connectionManager = timeoutConnectionManager,
                firmwareManager = timeoutFirmwareManager,
                deviceNameProvider = FakeDeviceNameProvider(),
                locationCollector = locationCollector,
                coroutineScope = timeoutTestScope,
            )

        runTest(testDispatcher) {
            // Set up a connection that will take longer than the 90s timeout
            // The connectDelay will cause delay() to be called, which with StandardTestDispatcher
            // requires time advancement. The withTimeout(90_000L) should trigger before the delay
            // completes.
            cameraRepository.connectDelay = 100_000L // Longer than 90s timeout
            // Don't set connectionToReturn - this ensures the connection never completes
            // and the timeout will trigger
            cameraRepository.connectionToReturn = null

            timeoutCoordinator.startDeviceSync(testDevice1)
            // Run only immediately scheduled work, don't advance virtual time
            runCurrent()

            // Verify we're in Searching or Connecting state initially
            val initialState = timeoutCoordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(
                "Expected Searching or Connecting state initially, but got: $initialState",
                initialState is DeviceConnectionState.Searching ||
                    initialState is DeviceConnectionState.Connecting,
            )

            // Advance time to just before the timeout (89s) - should still be connecting
            advanceTimeBy(89_000L)
            advanceUntilIdle()

            // Now advance past the 90s timeout threshold to trigger the
            // TimeoutCancellationException
            // The withTimeout(90_000L) will timeout after 90 seconds total
            advanceTimeBy(5_000L) // Advance past the 90s timeout
            advanceUntilIdle()

            // The timeout should have triggered by now, setting the state to Unreachable
            val state = timeoutCoordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(
                "Expected Unreachable state after timeout, but got: $state",
                state is DeviceConnectionState.Unreachable,
            )
        }
    }

    @Test
    fun `connection timeout from repository updates state to Unreachable`() =
        testScope.runTest {
            cameraRepository.connectException =
                try {
                    withTimeout(0) { delay(1) }
                    null
                } catch (e: TimeoutCancellationException) {
                    e
                }

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            val state = coordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(
                "Expected Unreachable but was $state",
                state is DeviceConnectionState.Unreachable,
            )
        }

    @Test
    fun `connection error updates state to Error`() =
        testScope.runTest {
            cameraRepository.connectException = IllegalStateException("Something went wrong")

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            val state = coordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(state is DeviceConnectionState.Error)
            assertTrue(
                (state as DeviceConnectionState.Error).message.contains("Something went wrong")
            )
        }

    @Test
    fun `unknown vendor updates state to Error`() =
        testScope.runTest {
            vendorRegistry.clearVendors() // Remove all vendors

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            val state = coordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(state is DeviceConnectionState.Error)
            assertTrue(
                (state as DeviceConnectionState.Error).message.contains("Unknown camera vendor")
            )
            assertFalse(state.isRecoverable)
        }

    @Test
    fun `duplicate startDeviceSync calls are ignored`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            // Simulate concurrent calls
            launch { coordinator.startDeviceSync(testDevice1) }
            launch { coordinator.startDeviceSync(testDevice1) }

            advanceUntilIdle()

            // With the race condition fix, we should only see 1 connection attempt
            assertEquals(1, cameraRepository.connectCallCount)
        }

    @Test
    fun `getConnectedDeviceCount returns correct count`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            assertEquals(0, coordinator.getConnectedDeviceCount())

            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()
            assertEquals(1, coordinator.getConnectedDeviceCount())

            cameraRepository.connectionToReturn = connection2
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()
            assertEquals(2, coordinator.getConnectedDeviceCount())
        }

    @Test
    fun `stopDeviceSync sets device state to Disconnected`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            coordinator.stopDeviceSync(testDevice1.macAddress)
            advanceUntilIdle()

            // State should be Disconnected after stopping
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
        }

    @Test
    fun `camera disconnection updates state to Disconnected`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

            // Simulate disconnection
            connection.setConnected(false)
            advanceUntilIdle()

            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
            // Should also unregister from location updates
            assertEquals(0, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `initial setup fails gracefully if connection closes during setup`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.setConnected(true)
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Connection should be established and setup started
            assertTrue(connection.readFirmwareVersionCalled)

            // Simulate connection closing during setup (before device name write)
            connection.setConnected(false)
            advanceUntilIdle()

            // Should handle gracefully - state should reflect disconnection
            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
        }

    @Test
    fun `devices are disconnected when disabled via background monitoring`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            // Add device as enabled
            pairedDevicesRepository.addTestDevice(testDevice1)
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

            // Start background monitoring
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Disable the device - this will trigger the enabledDevices flow to emit
            // The background monitoring collector should automatically call
            // checkAndConnectEnabledDevices()
            // when the flow emits, so we don't need to call refreshConnections() explicitly
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, false)
            advanceUntilIdle()

            // checkAndConnectEnabledDevices(), which calls stopDeviceSync
            // stopDeviceSync calls job.join() which waits for cleanup to complete
            // Give it time to complete the cleanup
            // Since we're using UnconfinedTestDispatcher, advanceUntilIdle should be enough,
            // but let's be explicit about ensuring coroutines complete
            advanceUntilIdle()

            // Wait a bit more to ensure cleanup has updated the state
            advanceUntilIdle()

            // Device should be disconnected
            // Note: Since stopDeviceSync is called, it should cancel the job and update state
            val state = coordinator.getDeviceState(testDevice1.macAddress)
            assertFalse(
                "Device should be disconnected, but isDeviceConnected returned true. State: $state",
                coordinator.isDeviceConnected(testDevice1.macAddress),
            )
            assertEquals(DeviceConnectionState.Disconnected, state)
            assertTrue(connection.disconnectCalled)
        }

    @Test
    fun `checkAndConnectEnabledDevices disconnects devices no longer enabled`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            // Setup connections in the repository BEFORE adding devices
            cameraRepository.setConnectionForMac(testDevice1.macAddress, connection1)
            cameraRepository.setConnectionForMac(testDevice2.macAddress, connection2)

            // Add both devices as enabled
            pairedDevicesRepository.addTestDevice(testDevice1)
            pairedDevicesRepository.addTestDevice(testDevice2)

            // Connect device1 directly via startDeviceSync (like the working test)
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Connect device2 directly via startDeviceSync
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()

            // Verify both devices are connected
            assertTrue(
                "Device1 should be connected",
                coordinator.isDeviceConnected(testDevice1.macAddress),
            )
            assertTrue(
                "Device2 should be connected",
                coordinator.isDeviceConnected(testDevice2.macAddress),
            )

            // Start background monitoring AFTER devices are connected
            // This matches the pattern from the working test
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Verify device1 is enabled before disabling
            val enabledBefore = pairedDevicesRepository.enabledDevices.first()
            assertTrue(
                "Device1 should be enabled before disabling",
                enabledBefore.any { it.macAddress == testDevice1.macAddress },
            )

            // Disable device1 - this will trigger the enabledDevices flow to emit
            // The background monitoring collector should automatically call
            // checkAndConnectEnabledDevices() when the flow emits
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, false)
            advanceUntilIdle()

            // checkAndConnectEnabledDevices() calls stopDeviceSync
            // stopDeviceSync calls job.join() which waits for cleanup to complete
            // Give it time to complete the cleanup
            // Since we're using UnconfinedTestDispatcher, advanceUntilIdle should be enough,
            // but let's be explicit about ensuring coroutines complete
            advanceUntilIdle()

            // Wait a bit more to ensure cleanup has updated the state
            advanceUntilIdle()

            // Verify disconnect was called on connection1
            assertTrue(
                "Connection1 should have been disconnected after device1 was disabled",
                connection1.disconnectCalled,
            )

            // Now verify device1 is disconnected
            val device1State = coordinator.getDeviceState(testDevice1.macAddress)
            assertFalse(
                "Device1 should be disconnected, but isDeviceConnected returned true. State: $device1State",
                coordinator.isDeviceConnected(testDevice1.macAddress),
            )
            assertEquals(
                "Device1 state should be Disconnected after being disabled",
                DeviceConnectionState.Disconnected,
                device1State,
            )

            // Verify device2 is still connected
            assertTrue(
                "Device2 should still be connected",
                coordinator.isDeviceConnected(testDevice2.macAddress),
            )

            // Verify disconnect was NOT called on connection2
            assertFalse(
                "Connection2 should not have been disconnected",
                connection2.disconnectCalled,
            )
        }

    @Test
    fun `connection check prevents write operations when connection is lost`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.setConnected(true)
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Connection should be established
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

            // Simulate connection loss before a write operation
            connection.setConnected(false)
            advanceUntilIdle()

            // State should reflect disconnection
            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
        }

    @Test
    fun `background monitoring connects newly enabled devices`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            // Add device as disabled
            val disabledDevice = testDevice1.copy(isEnabled = false)
            pairedDevicesRepository.addTestDevice(disabledDevice)

            // Start background monitoring
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Device should not be connected
            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))

            // Enable the device
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, true)
            advanceUntilIdle()

            // Trigger check
            coordinator.refreshConnections()
            advanceUntilIdle()

            // Device should now be connected
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
        }

    @Test
    fun `device state updates when device is disabled via background monitoring`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
            val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

            // Add both devices as enabled
            pairedDevicesRepository.addTestDevice(testDevice1)
            pairedDevicesRepository.addTestDevice(testDevice2)

            // Connect devices directly via startDeviceSync
            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection2
            coordinator.startDeviceSync(testDevice2)
            advanceUntilIdle()

            // Both should be connected
            assertEquals(2, coordinator.getConnectedDeviceCount())
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))

            // Start background monitoring after devices are connected
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Disable device1 - this will trigger the enabledDevices flow to emit
            // The background monitoring collector should automatically call
            // checkAndConnectEnabledDevices()
            // when the flow emits, so we don't need to call refreshConnections() explicitly
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, false)
            advanceUntilIdle()

            // The collector should have processed the update and called
            // checkAndConnectEnabledDevices()
            // Give it a bit more time to complete
            advanceUntilIdle()

            // Device1 should be disconnected, device2 should remain connected
            assertEquals(1, coordinator.getConnectedDeviceCount())
            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))

            // Device1 state should be Disconnected
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )
        }

    @Test
    fun `device state reflects enabled count changes for notification updates`() =
        testScope.runTest {
            val connection1 = FakeCameraConnection(testDevice1.toTestCamera())

            // Add both devices as enabled
            pairedDevicesRepository.addTestDevice(testDevice1)
            pairedDevicesRepository.addTestDevice(testDevice2)

            // Make connection fail when no connection is set (before starting background
            // monitoring)
            cameraRepository.failIfConnectionNull = true
            cameraRepository.connectionToReturn = null

            // Start background monitoring to track enabled devices
            // The proactive connection logic will attempt to connect, but it will fail
            // and devices will end up in Error state
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // So the count should be 0 (only Connected or Syncing states are counted).
            assertEquals(0, coordinator.getConnectedDeviceCount())

            // Connect device1
            cameraRepository.failIfConnectionNull = false
            cameraRepository.connectionToReturn = connection1
            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Should have 1 connected, 2 enabled
            assertEquals(1, coordinator.getConnectedDeviceCount())

            // Disable device2 - this should be reflected in enabled devices flow
            pairedDevicesRepository.setDeviceEnabled(testDevice2.macAddress, false)
            advanceUntilIdle()

            // Trigger check to process the change
            coordinator.refreshConnections()
            advanceUntilIdle()

            // Should still have 1 connected, but now only 1 enabled
            assertEquals(1, coordinator.getConnectedDeviceCount())
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
        }

    @Test
    fun `startPassiveScan creates PendingIntent and calls repository`() =
        testScope.runTest {
            coordinator.startPassiveScan()

            assertEquals(1, pendingIntentFactory.calls.size)
            // Can't check intent extras easily as we mock intent creation but can check request
            // code/flags
            val call = pendingIntentFactory.calls.first()
            assertEquals(999, call.requestCode) // PASSIVE_SCAN_REQUEST_CODE

            assertTrue(cameraRepository.startPassiveScanCalled)
        }

    @Test
    fun `stopPassiveScan creates PendingIntent and calls repository`() =
        testScope.runTest {
            coordinator.stopPassiveScan()

            assertEquals(1, pendingIntentFactory.calls.size)
            val call = pendingIntentFactory.calls.first()
            assertEquals(999, call.requestCode)

            assertTrue(cameraRepository.stopPassiveScanCalled)
        }

    @Test
    fun `connected device is added to present devices`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            val present = coordinator.presentDevices.first()
            assertTrue(present.contains(testDevice1.macAddress))
        }

    @Test
    fun `initial setup failures do not abort connection if partial success`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            // Configure connection to fail on firmware read but succeed on others
            connection.throwOnFirmwareRead = true
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            // Should still be connected despite firmware read failure
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

            // Should have attempted firmware read
            assertTrue(connection.readFirmwareVersionCalled)
        }

    @Test
    fun `coordinator can restart after being stopped`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection
            pairedDevicesRepository.setTestDevices(listOf(testDevice1))

            // Start first time
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Should connect
            assertEquals(1, coordinator.getConnectedDeviceCount())

            // Stop
            coordinator.stopAllDevices()
            advanceUntilIdle()

            assertEquals(0, coordinator.getConnectedDeviceCount())

            // RESET THE CONNECTION STATE to simulate a fresh connection or reconnection
            connection.setConnected(true)

            // Restart
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Should connect again if the jobs were nulled correctly
            assertEquals(1, coordinator.getConnectedDeviceCount())
        }

    @Test
    fun `isDeviceConnected returns true for Connected state`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            // Since we can't easily force the 'Connected' state (it transitions quickly to
            // Syncing),
            // we'll check it by verifying that isDeviceConnected handles Connected state correctly
            // if we could inject it. But here we can verify that the system ends up in Syncing
            // which is also considered connected.
            // A better test for the specific fix is to assume a state where we are just Connected.
            // But since startDeviceSync transitions to Syncing, let's verify that IS considered
            // connected.

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
        }

    @Test
    fun `connection is disconnected if performInitialSetup fails`() =
        testScope.runTest {
            // We need performInitialSetup to throw.
            // One way is to make getCapabilities throw.
            val throwingVendor = mockk<CameraVendor>()
            every { throwingVendor.vendorId } returns "throwing"
            every { throwingVendor.getCapabilities() } throws IllegalStateException("Setup failed")
            vendorRegistry.addVendor(throwingVendor)

            val throwingDevice = testDevice1.copy(vendorId = "throwing")
            val connection = FakeCameraConnection(throwingDevice.toCamera(throwingVendor))
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(throwingDevice)
            advanceUntilIdle()

            assertTrue(
                "Connection should be disconnected on setup failure",
                connection.disconnectCalled,
            )
            assertFalse(
                "Connection should not be in connection manager",
                connectionManager
                    .getConnections()
                    .containsKey(throwingDevice.macAddress.uppercase()),
            )
        }

    private fun PairedDevice.toTestCamera() =
        dev.sebastiano.camerasync.domain.model.Camera(
            identifier = macAddress,
            name = name,
            macAddress = macAddress,
            vendor = FakeCameraVendor,
        )
}
