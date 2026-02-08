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
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
        companionDeviceManagerHelper = mockk(relaxed = true)
        viewModel =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
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

        verify { companionDeviceManagerHelper.requestAssociation(any(), testCamera.macAddress) }
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
    fun `sendFeedback calls issueReporter`() = runTest {
        viewModel.sendFeedback()
        advanceUntilIdle()

        assertTrue("Issue report should have been sent", issueReporter.sendIssueReportCalled)
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
        assertEquals("Updated Model", deviceUi.model)
        assertFalse(deviceUi.isProbingModel)
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
    fun `cancelPairing clears isProbingModel when probe was in flight`() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.setConnectionForMac(testCamera.macAddress, fakeConnection)
        cameraRepository.connectDelay = 10_000L

        cameraRepository.emitDiscoveredCamera(testCamera)
        runCurrent()
        assertTrue(viewModel.state.value is PairingScreenState.Scanning)
        val scanningBefore = viewModel.state.value as PairingScreenState.Scanning
        assertTrue(
            "Probe should be in flight so device shows probing",
            scanningBefore.devices.single().isProbingModel,
        )

        viewModel.cancelPairing()
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Scanning)
        val scanningAfter = state as PairingScreenState.Scanning
        assertFalse(
            "Cancelled probe should clear isProbingModel so UI does not show stuck loading",
            scanningAfter.devices.single().isProbingModel,
        )
    }
}
