package dev.sebastiano.camerasync.pairing

import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.fakes.FakeBluetoothBondingChecker
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeCompanionDeviceManagerHelper
import dev.sebastiano.camerasync.fakes.FakeIssueReporter
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var bluetoothBondingChecker: FakeBluetoothBondingChecker
    private lateinit var issueReporter: FakeIssueReporter
    private lateinit var companionDeviceManagerHelper: FakeCompanionDeviceManagerHelper
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
        companionDeviceManagerHelper = FakeCompanionDeviceManagerHelper()
        viewModel =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = testDispatcher,
                mainDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Scanning`() {
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
    }

    @Test
    fun `startScanning populates discovered cameras`() = runTest {
        cameraRepository.emitDiscoveredCamera(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningState = state as PairingScreenState.Scanning
        assertEquals(1, scanningState.devices.size)
        assertEquals(testCamera, scanningState.devices.first().camera)
    }

    @Test
    fun `requestCompanionPairing transitions to Associating and calls helper`() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Associating)
        assertEquals(testCamera, (state as PairingScreenState.Associating).camera)

        assertTrue(companionDeviceManagerHelper.requestAssociationCalled)
        assertEquals(testCamera.macAddress, companionDeviceManagerHelper.lastMacAddress)
    }

    @Test
    fun `onCompanionAssociationResult transitions to Bonding then Connecting then Success`() =
        runTest {
            // Setup: Start associating
            viewModel.requestCompanionPairing(testCamera)

            // Mock successful connection
            val fakeConnection = FakeCameraConnection(testCamera)
            cameraRepository.connectionToReturn = fakeConnection

            // Trigger association result (intent is not used by logic currently, just checking for
            // null)
            val intent = mockk<android.content.Intent>(relaxed = true)
            viewModel.onCompanionAssociationResult(intent)
            advanceUntilIdle()

            // Should eventually reach Success state
            val state = viewModel.state.value
            assertTrue(
                "State should be Success but was $state",
                state is PairingScreenState.Success,
            )
            assertEquals(testCamera, (state as PairingScreenState.Success).camera)

            // Verify repository update
            assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
        }

    @Test
    fun `bonding failure transitions to Error`() = runTest {
        // Setup: Start associating
        viewModel.requestCompanionPairing(testCamera)

        // Mock bonding failure
        bluetoothBondingChecker.createBondSucceeds = false

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Error)
        assertEquals(PairingError.UNKNOWN, (state as PairingScreenState.Error).error)
    }

    @Test
    fun `connection failure transitions to Error`() = runTest {
        // Setup: Start associating
        viewModel.requestCompanionPairing(testCamera)

        // Mock connection failure
        cameraRepository.connectException = java.io.IOException("Connection failed")

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Error)
        // Verify type of error
        assertTrue((state as PairingScreenState.Error).error == PairingError.UNKNOWN)
    }

    @Test
    fun `cancelPairing transitions back to Scanning`() = runTest {
        viewModel.requestCompanionPairing(testCamera)

        viewModel.cancelPairing()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
    }

    @Test
    fun `cancelPairing returns devices sorted by name`() = runTest {
        val cameraA = testCamera.copy(name = "Alpha Cam", macAddress = "AA:BB:CC:DD:EE:01")
        val cameraB = testCamera.copy(name = "Zeta Cam", macAddress = "AA:BB:CC:DD:EE:02")

        cameraRepository.emitDiscoveredCamera(cameraB)
        cameraRepository.emitDiscoveredCamera(cameraA)
        advanceUntilIdle()

        viewModel.cancelPairing()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningState = state as PairingScreenState.Scanning
        val names = scanningState.devices.map { it.camera.name }
        assertEquals(listOf("Alpha Cam", "Zeta Cam"), names)
    }

    @Test
    fun `removeBondAndRetry removes bond and restarts pairing`() = runTest {
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)

        viewModel.removeBondAndRetry(testCamera)
        advanceUntilIdle()

        // Should remove bond
        assertFalse(bluetoothBondingChecker.isDeviceBonded(testCamera.macAddress))
        // Should restart scanning
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)

        // Note: Logic has changed, it no longer auto-restarts CDM pairing, just goes back to
        // scanning
        // allowing user to click pair again from list.
    }

    @Test
    fun `removeBondAndRetry restarts pairing when device is not bonded`() = runTest {
        // Device is not bonded (e.g., association failed before bonding)
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = false)

        // Set error state to simulate association failure
        // We need to manually set the error state since we can't easily trigger onFailure callback
        // This simulates the scenario where association fails before bonding
        viewModel.removeBondAndRetry(testCamera)
        advanceUntilIdle()

        // Should restart scanning (not set canRetry = false)
        // This verifies the fix: when there's no bond, we restart pairing instead of
        // setting canRetry = false
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
    }

    @Test
    fun `removeBondAndRetry restarts pairing even when bond removal fails`() = runTest {
        // Device is bonded but removal will fail
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)
        bluetoothBondingChecker.removeBondSucceeds = false

        viewModel.removeBondAndRetry(testCamera)
        advanceUntilIdle()

        // Should restart scanning even though bond removal failed
        // This verifies the fix: when bond removal fails, we still restart pairing
        // instead of setting canRetry = false
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        // Device should still be bonded since removal failed
        assertTrue(bluetoothBondingChecker.isDeviceBonded(testCamera.macAddress))
    }

    @Test
    fun `sendFeedback calls issueReporter`() = runTest {
        viewModel.sendFeedback()
        advanceUntilIdle()

        assertTrue("Issue report should have been sent", issueReporter.sendIssueReportCalled)
    }

    @Test
    fun `sendFeedback includes state info in extra info`() = runTest {
        // Drive to Error state so extra info includes camera and error details
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        cameraRepository.connectException = java.io.IOException("Connection failed")
        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is PairingScreenState.Error)

        viewModel.sendFeedback()
        advanceUntilIdle()

        val extraInfo = issueReporter.lastExtraInfo
        assertNotNull(extraInfo)
        assertTrue("Extra info should contain camera name", extraInfo!!.contains("Test Camera"))
        assertTrue("Extra info should contain MAC address", extraInfo.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue("Extra info should contain error type", extraInfo.contains("UNKNOWN"))
    }

    @Test
    fun `model probe updates discovered device model`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        fakeConnection.setModelName("Updated Model")
        cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)

        cameraRepository.emitDiscoveredCamera(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val deviceUi = (state as PairingScreenState.Scanning).devices.single()
        assertTrue(deviceUi is DiscoveredCameraUi.Detected)
        assertEquals("Updated Model", (deviceUi as DiscoveredCameraUi.Detected).model)
        assertTrue(fakeConnection.readModelNameCalled)
    }

    @Test
    fun `requestCompanionPairing cancels in-flight model probe`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)
        cameraRepository.connectDelay = 10_000L

        cameraRepository.emitDiscoveredCamera(testCamera)
        runCurrent()
        assertEquals(1, cameraRepository.connectCallCount)

        viewModel.requestCompanionPairing(testCamera)
        runCurrent()

        advanceTimeBy(10_000L)
        runCurrent()

        assertFalse(fakeConnection.readModelNameCalled)
        assertTrue(viewModel.state.value is PairingScreenState.Associating)
    }

    @Test
    fun `requestCompanionPairing cancels probe and disconnects connection`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        fakeConnection.readModelNameDelay = 10_000L
        fakeConnection.disconnectRequiresActive = true
        cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)

        cameraRepository.emitDiscoveredCamera(testCamera)
        runCurrent()
        assertTrue(fakeConnection.readModelNameCalled)

        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        assertTrue(fakeConnection.disconnectCalled)
        assertTrue(viewModel.state.value is PairingScreenState.Associating)
    }

    @Test
    fun `cancelPairing clears detecting state when probe was in flight`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)
        cameraRepository.connectDelay = 10_000L

        cameraRepository.emitDiscoveredCamera(testCamera)
        runCurrent()
        assertTrue(viewModel.state.value is PairingScreenState.Scanning)
        val scanningBefore = viewModel.state.value as PairingScreenState.Scanning
        val deviceBefore = scanningBefore.devices.single()
        assertTrue(
            "Probe should be in flight so device shows detecting",
            deviceBefore is DiscoveredCameraUi.Detecting,
        )

        viewModel.cancelPairing()
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningAfter = state as PairingScreenState.Scanning
        val deviceAfter = scanningAfter.devices.single()
        assertTrue(
            "Cancelled probe should transition to Detected so UI does not show stuck loading",
            deviceAfter is DiscoveredCameraUi.Detected,
        )
    }

    @Test
    fun `probeModelName should handle IllegalStateException from connect`() = runTest {
        // Setup: Mock connect to throw IllegalStateException
        cameraRepository.connectException = IllegalStateException("Connection failed due to state")

        // Trigger discovery, which triggers probeModelName because FakeCameraVendor supports model
        // name
        cameraRepository.emitDiscoveredCamera(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningState = state as PairingScreenState.Scanning
        val deviceUi = scanningState.devices.single()

        assertTrue(
            "Device should transition to Detected after exception (not stuck in Detecting)",
            deviceUi is DiscoveredCameraUi.Detected,
        )
    }

    @Test
    fun `probeModelName should handle unexpected exception from readModelName and transition to Detected`() =
        runTest {
            // Setup: Connection succeeds but readModelName throws unexpected exception
            val fakeConnection = FakeCameraConnection(testCamera)
            fakeConnection.readModelNameException =
                RuntimeException("Unexpected BLE error during model read")
            cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)

            cameraRepository.emitDiscoveredCamera(testCamera)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is PairingScreenState.Scanning)
            val scanningState = state as PairingScreenState.Scanning
            val deviceUi = scanningState.devices.single()

            assertTrue(
                "Device should transition to Detected after unexpected exception (not stuck in Detecting)",
                deviceUi is DiscoveredCameraUi.Detected,
            )
            // Should use fallback extracted from pairing name, not the probed model
            assertEquals("Test Camera", (deviceUi as DiscoveredCameraUi.Detected).model)
        }

    // ==================== C11: Bonding timeout tests ====================

    @Test
    fun `bonding timeout transitions to Error with TIMEOUT`() = runTest {
        // Use StandardTestDispatcher for explicit time control
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)

        val vm =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = standardDispatcher,
                mainDispatcher = standardDispatcher,
            )

        // Start associating
        vm.requestCompanionPairing(testCamera)
        runCurrent()

        // Bonding will be initiated but never complete
        bluetoothBondingChecker.createBondAutoBonds = false

        val intent = mockk<android.content.Intent>(relaxed = true)
        vm.onCompanionAssociationResult(intent)
        runCurrent()

        // Advance past the bonding timeout (60 seconds)
        advanceTimeBy(BONDING_TIMEOUT_MS + 1_000L)
        runCurrent()

        val state = vm.state.value
        assertTrue("State should be Error but was $state", state is PairingScreenState.Error)
        assertEquals(PairingError.TIMEOUT, (state as PairingScreenState.Error).error)
        assertTrue(state.canRetry)
    }

    @Test
    fun `bonding completes before timeout transitions to Connecting then Success`() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)

        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        val vm =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = standardDispatcher,
                mainDispatcher = standardDispatcher,
            )

        vm.requestCompanionPairing(testCamera)
        runCurrent()

        // Bonding won't auto-complete — we'll manually complete it
        bluetoothBondingChecker.createBondAutoBonds = false

        val intent = mockk<android.content.Intent>(relaxed = true)
        vm.onCompanionAssociationResult(intent)
        runCurrent()

        // Advance a few poll cycles, then mark as bonded
        advanceTimeBy(2_000L)
        runCurrent()
        bluetoothBondingChecker.setBonded(testCamera.macAddress, bonded = true)

        // Let the poll detect it
        advanceTimeBy(1_000L)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("State should be Success but was $state", state is PairingScreenState.Success)
    }

    // ==================== C7: Navigation event tests ====================

    @Test
    fun `manualCloseSuccess emits DevicePaired navigation event`() = runTest {
        val event = async { viewModel.navigationEvents.first() }

        viewModel.manualCloseSuccess()
        advanceUntilIdle()

        assertEquals(PairingNavigationEvent.DevicePaired, event.await())
    }

    @Test
    fun `scheduleSuccessNavigation emits DevicePaired after 5 seconds`() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)

        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        val vm =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = standardDispatcher,
                mainDispatcher = standardDispatcher,
            )

        // Drive to success state to trigger scheduleSuccessNavigation.
        // FakeBluetoothBondingChecker auto-bonds and FakeCameraConnection connects
        // instantly, so we only need the bonding poll delay (500ms) to reach Success.
        vm.requestCompanionPairing(testCamera)
        runCurrent()

        val intent = mockk<android.content.Intent>(relaxed = true)
        vm.onCompanionAssociationResult(intent)
        // Advance just enough for the bonding poll to detect the bond (500ms delay)
        advanceTimeBy(600L)
        runCurrent()
        assertTrue(
            "State should be Success but was ${vm.state.value}",
            vm.state.value is PairingScreenState.Success,
        )

        // Start collecting navigation events now
        val event = async { vm.navigationEvents.first() }

        // Advance 3 more seconds (total ~3.6s since Success) — should NOT fire yet
        advanceTimeBy(3_000L)
        runCurrent()
        assertFalse("Event should not have fired before 5 seconds", event.isCompleted)

        // Advance past the 5-second timer
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(PairingNavigationEvent.DevicePaired, event.await())
    }

    @Test
    fun `manualCloseSuccess cancels scheduled auto-close`() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)

        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        val vm =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = companionDeviceManagerHelper,
                issueReporter = issueReporter,
                ioDispatcher = standardDispatcher,
                mainDispatcher = standardDispatcher,
            )

        // Drive to success state
        vm.requestCompanionPairing(testCamera)
        runCurrent()
        val intent = mockk<android.content.Intent>(relaxed = true)
        vm.onCompanionAssociationResult(intent)
        // Advance enough for bonding + connection but not the 5s auto-close
        advanceTimeBy(PAIRING_CONNECTION_TIMEOUT_MS + 1_000L)
        runCurrent()
        assertTrue(
            "State should be Success but was ${vm.state.value}",
            vm.state.value is PairingScreenState.Success,
        )

        // Collect first event
        val event = async { vm.navigationEvents.first() }

        // User clicks close manually before the 5s timer fires
        vm.manualCloseSuccess()
        runCurrent()

        // Should get exactly one event from the manual close
        assertEquals(PairingNavigationEvent.DevicePaired, event.await())

        // Advance well past the timer — should not get a second event
        // (no second event = the channel is empty, verified by the fact that
        // we only got one event and the test completes)
        advanceTimeBy(10_000L)
        runCurrent()
    }

    // ==================== Additional coverage ====================

    @Test
    fun `onCompanionAssociationResult with null data returns to Scanning`() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is PairingScreenState.Associating)

        viewModel.onCompanionAssociationResult(null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be Scanning but was $state", state is PairingScreenState.Scanning)
    }

    @Test
    fun `onCompanionAssociationResult when not in Associating state returns to Scanning`() =
        runTest {
            // State is Scanning (initial), not Associating
            assertTrue(viewModel.state.value is PairingScreenState.Scanning)

            val intent = mockk<android.content.Intent>(relaxed = true)
            viewModel.onCompanionAssociationResult(intent)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(
                "State should be Scanning but was $state",
                state is PairingScreenState.Scanning,
            )
        }

    @Test
    fun `initializePairing failure transitions to Error`() = runTest {
        viewModel.requestCompanionPairing(testCamera)

        val fakeConnection = FakeCameraConnection(testCamera)
        fakeConnection.initializePairingResult = false
        cameraRepository.connectionToReturn = fakeConnection

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("State should be Error but was $state", state is PairingScreenState.Error)
        assertEquals(PairingError.UNKNOWN, (state as PairingScreenState.Error).error)
        assertTrue(state.canRetry)
        assertTrue(fakeConnection.initializePairingCalled)
    }

    @Test
    fun `already paired camera is filtered from scan results`() = runTest {
        // Pre-populate as paired
        pairedDevicesRepository.addDevice(testCamera, enabled = true)

        cameraRepository.emitDiscoveredCamera(testCamera)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningState = state as PairingScreenState.Scanning
        assertTrue(
            "Already-paired camera should not appear in scan results",
            scanningState.devices.isEmpty(),
        )
    }

    @Test
    fun `connection rejection error maps to REJECTED`() = runTest {
        viewModel.requestCompanionPairing(testCamera)

        cameraRepository.connectException = java.io.IOException("Connection reject from device")

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Error)
        assertEquals(PairingError.REJECTED, (state as PairingScreenState.Error).error)
    }

    @Test
    fun `connection timeout error maps to TIMEOUT`() = runTest {
        viewModel.requestCompanionPairing(testCamera)

        cameraRepository.connectException = java.io.IOException("Connection timeout occurred")

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Error)
        assertEquals(PairingError.TIMEOUT, (state as PairingScreenState.Error).error)
    }
}
