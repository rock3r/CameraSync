package dev.sebastiano.camerasync.pairing

import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.fakes.FakeBluetoothBondingChecker
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeIssueReporter
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakeVendorRegistry
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var bluetoothBondingChecker: FakeBluetoothBondingChecker
    private lateinit var issueReporter: FakeIssueReporter
    private lateinit var companionDeviceManagerHelper: CompanionDeviceManagerHelper
    private lateinit var viewModel: PairingViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testCamera =
        Camera(
            identifier = "AA:BB:CC:DD:EE:FF",
            name = "Test Camera",
            macAddress = "AA:BB:CC:DD:EE:FF",
            vendor = FakeCameraVendor,
        )

    @Before
    fun setUp() {
        // Set up test dispatchers
        Dispatchers.setMain(testDispatcher)

        // Initialize Khronicle with fake logger for tests
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        cameraRepository = FakeCameraRepository()
        bluetoothBondingChecker = FakeBluetoothBondingChecker()
        issueReporter = FakeIssueReporter()
        val vendorRegistry = FakeVendorRegistry()
        companionDeviceManagerHelper = mockk(relaxed = true)
        viewModel =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                vendorRegistry = vendorRegistry,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = testDispatcher, // Inject test dispatcher
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `pairDevice adds device to repository`() = runTest {
        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `pairDevice enables sync and observes presence`() = runTest {
        pairedDevicesRepository.setSyncEnabled(false)

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        assertTrue(pairedDevicesRepository.isSyncEnabled.first())
        verify { companionDeviceManagerHelper.startObservingDevicePresence(testCamera.macAddress) }
    }

    @Test
    fun `pairDevice emits DevicePaired navigation event on success`() = runTest {
        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background
        val job = launch { viewModel.navigationEvents.take(1).collect { events.add(it) } }

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        job.join()

        assertEquals(1, events.size)
        assertEquals(PairingNavigationEvent.DevicePaired, events.first())
    }

    @Test
    fun `pairDevice establishes BLE connection before adding device to repository`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        assertFalse(pairedDevicesRepository.addDeviceCalled)
        assertEquals(0, cameraRepository.connectCallCount)

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        // Verify BLE connection was established
        assertEquals(1, cameraRepository.connectCallCount)

        // Verify device was added to repository after successful connection
        assertTrue(pairedDevicesRepository.addDeviceCalled)
        assertEquals(testCamera, pairedDevicesRepository.lastAddedCamera)
        assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))

        // Verify connection was disconnected after pairing
        assertTrue(fakeConnection.disconnectCalled)
    }

    @Test
    fun `pairDevice sets REJECTED error when BLE connection throws rejection exception`() =
        runTest {
            // Make connect throw an exception with "reject" in the message
            val exception = Exception("Device rejected pairing request")
            cameraRepository.connectException = exception

            viewModel.pairDevice(testCamera)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is PairingScreenState.Pairing)
            val pairingState = state as PairingScreenState.Pairing
            assertEquals(PairingError.REJECTED, pairingState.error)

            // Device should NOT be added to repository when pairing fails
            assertFalse(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
        }

    @Test
    fun `pairDevice sets TIMEOUT error when BLE connection throws timeout exception`() = runTest {
        // Make connect throw an exception with "timeout" in the message
        val exception = Exception("Connection timeout")
        cameraRepository.connectException = exception

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.TIMEOUT, pairingState.error)

        // Device should NOT be added to repository when pairing fails
        assertFalse(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `pairDevice sets TIMEOUT error when BLE connection exceeds timeout`() = runTest {
        cameraRepository.connectDelay = PAIRING_CONNECTION_TIMEOUT_MS + 1

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.TIMEOUT, pairingState.error)

        // Device should NOT be added to repository when pairing fails
        assertFalse(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `pairDevice sets UNKNOWN error when BLE connection throws other exception`() = runTest {
        // Make connect throw an exception without "reject" or "timeout"
        val exception = Exception("Unexpected error occurred")
        cameraRepository.connectException = exception

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.UNKNOWN, pairingState.error)

        // Device should NOT be added to repository when pairing fails
        assertFalse(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `pairDevice does not emit navigation event on error`() = runTest {
        // Make BLE connection fail
        val exception = Exception("Pairing failed")
        cameraRepository.connectException = exception

        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background with timeout
        val job = launch {
            try {
                viewModel.navigationEvents.take(1).collect { events.add(it) }
            } catch (_: Exception) {
                // Ignore timeout
            }
        }

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        job.cancel()

        // No events should be emitted on error
        assertTrue(events.isEmpty())

        // Verify state has an error
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        assertTrue((state as PairingScreenState.Pairing).error != null)
    }

    @Test
    fun `cancelPairing transitions back to Idle state`() = runTest {
        viewModel.pairDevice(testCamera)

        // Verify we're in Pairing state
        assertTrue(viewModel.state.value is PairingScreenState.Pairing)

        viewModel.cancelPairing()

        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `cancelPairing cancels ongoing pairing job`() = runTest {
        // Add a delay to the repository to simulate a long-running operation
        pairedDevicesRepository.addDeviceDelay = 1000L

        viewModel.pairDevice(testCamera)

        // Cancel before it completes
        viewModel.cancelPairing()

        advanceUntilIdle()

        // State should be Idle, not Pairing
        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `pairDevice transitions to AlreadyBonded when device is bonded at system level`() =
        runTest {
            bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)

            viewModel.pairDevice(testCamera)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is PairingScreenState.AlreadyBonded)
            assertEquals(testCamera, (state as PairingScreenState.AlreadyBonded).camera)
            assertFalse(state.removeFailed)
        }

    @Test
    fun `removeBondAndRetry removes bond and restarts system pairing`() = runTest {
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        // Should be in AlreadyBonded state
        assertTrue(viewModel.state.value is PairingScreenState.AlreadyBonded)

        // Remove bond and retry
        viewModel.removeBondAndRetry(testCamera)
        advanceUntilIdle()

        verify { companionDeviceManagerHelper.requestAssociation(any()) }
        assertEquals(PairingScreenState.Idle, viewModel.state.value)
        assertFalse(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `removeBondAndRetry shows removeFailed when bond removal fails`() = runTest {
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        // Manually remove the bond to simulate removal failure
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = false)

        // Now removeBondAndRetry will fail because device is no longer bonded
        viewModel.removeBondAndRetry(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.AlreadyBonded)
        assertTrue((state as PairingScreenState.AlreadyBonded).removeFailed)
    }

    @Test
    fun `multiple pairDevice calls emit navigation event for each successful pairing`() = runTest {
        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background
        val job = launch { viewModel.navigationEvents.collect { events.add(it) } }

        // First pairing
        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        // Ensure first event was received before starting second pairing
        assertEquals("First pairing should emit an event", 1, events.size)

        // Second pairing - device is already paired in repository
        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        job.cancel()

        // Both should emit events
        assertEquals(2, events.size)
        assertEquals(PairingNavigationEvent.DevicePaired, events[0])
        assertEquals(PairingNavigationEvent.DevicePaired, events[1])

        // Device should be paired
        assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `sendFeedback calls issueReporter`() = runTest {
        viewModel.sendFeedback()
        advanceUntilIdle()

        assertTrue("Issue report should have been sent", issueReporter.sendIssueReportCalled)
    }
}
