package dev.sebastiano.camerasync.devicesync

import android.content.Context
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeLocationCollector
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakePendingIntentFactory
import dev.sebastiano.camerasync.fakes.FakeVendorRegistry
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiDeviceSyncCoordinatorTest {

    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var locationCollector: FakeLocationCollector
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var pendingIntentFactory: FakePendingIntentFactory
    private lateinit var companionDeviceManagerHelper: CompanionDeviceManagerHelper
    private lateinit var context: Context
    private lateinit var testScope: TestScope
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
        companionDeviceManagerHelper = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(R.string.error_unknown_vendor) } returns "Unknown camera vendor"

        testScope = TestScope(UnconfinedTestDispatcher())

        coordinator =
            MultiDeviceSyncCoordinator(
                context = context,
                cameraRepository = cameraRepository,
                locationCollector = locationCollector,
                vendorRegistry = vendorRegistry,
                pairedDevicesRepository = pairedDevicesRepository,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                pendingIntentFactory = pendingIntentFactory,
                coroutineScope = testScope.backgroundScope,
                deviceNameProvider = { "Test Device CameraSync" },
            )
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
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

            assertEquals(
                DeviceConnectionState.Searching,
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
    fun `background monitoring connects only when device is present`() =
        testScope.runTest {
            pairedDevicesRepository.setTestDevices(listOf(testDevice1))
            cameraRepository.connectionToReturn = FakeCameraConnection(testDevice1.toTestCamera())

            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // We now expect 1 connection attempt even without presence because of the
            // proactive startup connect logic.
            assertEquals(1, cameraRepository.connectCallCount)

            coordinator.setDevicePresence(testDevice1.macAddress, true)
            advanceUntilIdle()

            // Still 1, as it was already connected
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
        val timeoutCoordinator =
            MultiDeviceSyncCoordinator(
                context = context,
                cameraRepository = cameraRepository,
                locationCollector = locationCollector,
                vendorRegistry = vendorRegistry,
                pairedDevicesRepository = pairedDevicesRepository,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                pendingIntentFactory = pendingIntentFactory,
                coroutineScope = timeoutTestScope,
            )

        runTest(testDispatcher) {
            // Set up a connection that will take longer than the 30s timeout
            // The connectDelay will cause delay() to be called, which with StandardTestDispatcher
            // requires time advancement. The withTimeout(30_000L) should trigger before the delay
            // completes.
            cameraRepository.connectDelay = 60_000L // Longer than 30s timeout
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            timeoutCoordinator.startDeviceSync(testDevice1)
            // Run only immediately scheduled work, don't advance virtual time
            runCurrent()

            // Verify we're in Searching or Connecting state initially
            // Note: With StandardTestDispatcher, if we don't advance time, the delay won't start
            // and the timeout won't trigger yet, so we should be in Searching or Connecting
            val initialState = timeoutCoordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(
                "Expected Searching or Connecting state initially, but got: $initialState",
                initialState is DeviceConnectionState.Searching ||
                    initialState is DeviceConnectionState.Connecting,
            )

            // Advance time to just before the timeout (29s) - should still be connecting
            advanceTimeBy(29_000L)
            advanceUntilIdle()

            // Now advance past the 30s timeout threshold to trigger the
            // TimeoutCancellationException
            // The withTimeout(30_000L) will timeout after 30 seconds total
            advanceTimeBy(2_000L)
            advanceUntilIdle()

            // Verify the state is Unreachable after timeout
            // Note: The timeout should have triggered by now, setting the state to Unreachable
            val state = timeoutCoordinator.getDeviceState(testDevice1.macAddress)

            // If the state is not Unreachable, it might still be Connecting if the timeout hasn't
            // triggered yet
            // This can happen if the delay hasn't started when we advance time, or if withTimeout
            // doesn't work as expected with StandardTestDispatcher. Let's check what we actually
            // got.
            if (state !is DeviceConnectionState.Unreachable) {
                // The timeout might not have triggered yet - let's advance a bit more and check
                // again
                advanceTimeBy(5_000L)
                advanceUntilIdle()
                val finalState = timeoutCoordinator.getDeviceState(testDevice1.macAddress)
                assertTrue(
                    "Expected Unreachable state after timeout, but got: $finalState (initial: $initialState, after first advance: $state)",
                    finalState is DeviceConnectionState.Unreachable,
                )
            }
        }
    }

    @Test
    fun `connection error updates state to Error`() =
        testScope.runTest {
            cameraRepository.connectException = RuntimeException("Connection failed")

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            val state = coordinator.getDeviceState(testDevice1.macAddress)
            assertTrue(state is DeviceConnectionState.Error)
            assertTrue((state as DeviceConnectionState.Error).message.contains("Connection failed"))
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

            coordinator.startDeviceSync(testDevice1)
            coordinator.startDeviceSync(testDevice1) // Should be ignored
            advanceUntilIdle()

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
    fun `clearDeviceState removes device from states`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            coordinator.stopDeviceSync(testDevice1.macAddress)
            advanceUntilIdle()

            // State should still exist as Disconnected
            assertEquals(
                DeviceConnectionState.Disconnected,
                coordinator.getDeviceState(testDevice1.macAddress),
            )

            // Clear the state
            coordinator.clearDeviceState(testDevice1.macAddress)

            // Now it should be gone (returns Disconnected as default)
            assertFalse(coordinator.deviceStates.value.containsKey(testDevice1.macAddress))
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
    fun `connection is established before initial setup to prevent GATT write errors`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            // Start with connection not established
            connection.setConnected(false)
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(testDevice1)

            // Connection should be waiting for establishment
            // After a short delay, establish the connection
            advanceUntilIdle()
            connection.setConnected(true)
            advanceUntilIdle()

            // Verify initial setup was performed after connection was established
            assertTrue(connection.readFirmwareVersionCalled)
            assertEquals("Test Device CameraSync", connection.pairedDeviceName)
            assertTrue(
                coordinator.getDeviceState(testDevice1.macAddress) is DeviceConnectionState.Syncing
            )
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

            coordinator.setDevicePresence(testDevice1.macAddress, true)
            advanceUntilIdle()

            // Start background monitoring
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Disable the device - this will trigger the enabledDevices flow to emit
            // The background monitoring collector should automatically call
            // checkAndConnectEnabledDevices()
            // when the flow emits, so we don't need to call refreshConnections() explicitly
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, false)
            advanceUntilIdle()

            // The collector should have processed the update and called
            // checkAndConnectEnabledDevices(), which calls stopDeviceSync
            // stopDeviceSync calls job.join() which waits for cleanup to complete
            // Give it time to complete the cleanup
            advanceUntilIdle()

            // Wait a bit more to ensure cleanup has updated the state
            advanceUntilIdle()

            // Device should be disconnected
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

            // Start with NO devices to avoid proactive connect at startup
            pairedDevicesRepository.setTestDevices(emptyList())
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Setup connections in the repository BEFORE adding devices
            cameraRepository.setConnectionForMac(testDevice1.macAddress, connection1)
            cameraRepository.setConnectionForMac(testDevice2.macAddress, connection2)

            // Now add both devices as enabled
            pairedDevicesRepository.addTestDevice(testDevice1)
            pairedDevicesRepository.addTestDevice(testDevice2)
            advanceUntilIdle()

            // Connect device1 via presence
            coordinator.setDevicePresence(testDevice1.macAddress, true)
            advanceUntilIdle()

            // Connect device2 via presence
            coordinator.setDevicePresence(testDevice2.macAddress, true)
            advanceUntilIdle()

            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))

            // Disable device1
            pairedDevicesRepository.setDeviceEnabled(testDevice1.macAddress, false)
            advanceUntilIdle()

            // Device1 should be disconnected, device2 should remain connected
            assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))
            assertTrue(connection1.disconnectCalled)
            assertFalse(connection2.disconnectCalled)
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

            // Start background monitoring
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection1
            coordinator.setDevicePresence(testDevice1.macAddress, true)
            advanceUntilIdle()

            cameraRepository.connectionToReturn = connection2
            coordinator.setDevicePresence(testDevice2.macAddress, true)
            advanceUntilIdle()

            // Both should be connected
            assertEquals(2, coordinator.getConnectedDeviceCount())
            assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
            assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))

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

            // Make connection fail when no connection is set
            cameraRepository.failIfConnectionNull = true
            cameraRepository.connectionToReturn = null

            coordinator.setDevicePresence(testDevice1.macAddress, true)
            coordinator.setDevicePresence(testDevice2.macAddress, true)
            advanceUntilIdle()

            // Start background monitoring to track enabled devices
            coordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)
            advanceUntilIdle()

            // Initially both enabled, but connection fails so they end up in Error state
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
            // Can't check intent extras easily as we mock intent creation but can check request code/flags
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

    private fun PairedDevice.toTestCamera() =
        dev.sebastiano.camerasync.domain.model.Camera(
            identifier = macAddress,
            name = name,
            macAddress = macAddress,
            vendor = FakeCameraVendor,
        )
}
