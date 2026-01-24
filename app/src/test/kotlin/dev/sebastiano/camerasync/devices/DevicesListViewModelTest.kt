package dev.sebastiano.camerasync.devices

import android.content.Context
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.fakes.FakeBatteryOptimizationChecker
import dev.sebastiano.camerasync.fakes.FakeBluetoothBondingChecker
import dev.sebastiano.camerasync.fakes.FakeIssueReporter
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeLocationRepository
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakeVendorRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesListViewModelTest {

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var bluetoothBondingChecker: FakeBluetoothBondingChecker
    private lateinit var batteryOptimizationChecker: FakeBatteryOptimizationChecker
    private lateinit var issueReporter: FakeIssueReporter
    private lateinit var viewModel: DevicesListViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // Set up test dispatcher for Main
        Dispatchers.setMain(testDispatcher)

        // Initialize Khronicle with fake logger for tests
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        locationRepository = FakeLocationRepository()
        vendorRegistry = FakeVendorRegistry()
        bluetoothBondingChecker = FakeBluetoothBondingChecker()
        batteryOptimizationChecker = FakeBatteryOptimizationChecker()
        issueReporter = FakeIssueReporter()

        viewModel =
            DevicesListViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                locationRepository = locationRepository,
                bindingContextProvider = { mockContext() },
                vendorRegistry = vendorRegistry,
                bluetoothBondingChecker = bluetoothBondingChecker,
                batteryOptimizationChecker = batteryOptimizationChecker,
                issueReporter = issueReporter,
                ioDispatcher = testDispatcher, // Inject test dispatcher for IO operations
            )
    }

    @After
    fun tearDown() {
        // Reset Main dispatcher
        Dispatchers.resetMain()
    }

    @Test
    fun `computeDeviceDisplayInfo returns correct make and model for single device`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "GR IIIx",
                vendorId = "fake",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(
            "State should be HasDevices, but was: $state",
            state is DevicesListState.HasDevices,
        )
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(
            "Display info should not be null for device ${device.macAddress}",
            displayInfo,
        )
        assertEquals("Fake Camera", displayInfo!!.make)
        assertEquals("GR IIIx", displayInfo.model)
        assertFalse(displayInfo.showPairingName)
    }

    @Test
    fun `computeDeviceDisplayInfo shows pairing name when multiple devices have same make and model`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]

            assertNotNull(displayInfo1)
            assertNotNull(displayInfo2)

            // Both should show pairing name since they have the same make/model
            assertTrue(displayInfo1!!.showPairingName)
            assertTrue(displayInfo2!!.showPairingName)
            assertEquals("GR IIIx", displayInfo1.model)
            assertEquals("GR IIIx", displayInfo2.model)
        }

    @Test
    fun `computeDeviceDisplayInfo does not show pairing name when devices have different models`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "GR III",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]

            assertNotNull(displayInfo1)
            assertNotNull(displayInfo2)

            // Should not show pairing name since models are different
            assertFalse(displayInfo1!!.showPairingName)
            assertFalse(displayInfo2!!.showPairingName)
            assertEquals("GR IIIx", displayInfo1.model)
            assertEquals("GR III", displayInfo2.model)
        }

    @Test
    fun `computeDeviceDisplayInfo handles unknown vendor gracefully`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Unknown Camera",
                vendorId = "unknown_vendor",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DevicesListState.HasDevices)
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(displayInfo)
        // Should capitalize the vendorId when vendor is not found
        assertEquals("Unknown_vendor", displayInfo!!.make)
        assertEquals("Unknown Camera", displayInfo.model)
    }

    @Test
    fun `computeDeviceDisplayInfo handles null device name`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = null,
                vendorId = "fake",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DevicesListState.HasDevices)
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(displayInfo)
        assertEquals("Fake Camera", displayInfo!!.make)
        assertEquals("Unknown", displayInfo.model)
        assertNull(displayInfo.pairingName)
    }

    @Test
    fun `computeDeviceDisplayInfo shows pairing name only in collapsed state when duplicates exist`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "My Camera 1",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "My Camera 2",
                    vendorId = "fake",
                    isEnabled = true,
                )
            // Both have same model name but different pairing names
            // This simulates the case where user renamed cameras but they're the same model
            val device3 =
                PairedDevice(
                    macAddress = "77:88:99:AA:BB:CC",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            pairedDevicesRepository.addTestDevice(device3)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]
            val displayInfo3 = hasDevicesState.displayInfoMap[device3.macAddress]

            // device1 and device2 have same model (from pairing name), so should show pairing name
            // device3 has different model, so should not show pairing name
            assertTrue(displayInfo1!!.showPairingName)
            assertTrue(displayInfo2!!.showPairingName)
            assertFalse(displayInfo3!!.showPairingName)
        }

    @Test
    fun `battery optimization status is included in HasDevices state when ignoring optimizations`() =
        runTest {
            batteryOptimizationChecker.setIgnoringBatteryOptimizations(true)

            val device =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            assertTrue(
                "Should be ignoring battery optimizations",
                hasDevicesState.isIgnoringBatteryOptimizations,
            )
        }

    @Test
    fun `battery optimization status is included in HasDevices state when not ignoring optimizations`() =
        runTest {
            // Need to recreate the ViewModel after changing the battery optimization status
            // because the status is captured during initialization
            batteryOptimizationChecker.setIgnoringBatteryOptimizations(false)

            val testViewModel =
                DevicesListViewModel(
                    pairedDevicesRepository = pairedDevicesRepository,
                    locationRepository = locationRepository,
                    bindingContextProvider = { mockContext() },
                    vendorRegistry = vendorRegistry,
                    bluetoothBondingChecker = bluetoothBondingChecker,
                    batteryOptimizationChecker = batteryOptimizationChecker,
                    ioDispatcher = testDispatcher,
                )

            val device =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device)
            advanceUntilIdle()

            val state = testViewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            assertFalse(
                "Should not be ignoring battery optimizations",
                hasDevicesState.isIgnoringBatteryOptimizations,
            )
        }

    @Test
    fun `checkBatteryOptimizationStatus updates state when battery optimization changes`() =
        runTest {
            // Start with battery optimization disabled (ignoring = true)
            batteryOptimizationChecker.setIgnoringBatteryOptimizations(true)

            val device =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device)
            advanceUntilIdle()

            val initialState = viewModel.state.value as DevicesListState.HasDevices
            assertTrue(
                "Initially should be ignoring battery optimizations",
                initialState.isIgnoringBatteryOptimizations,
            )

            // Simulate user disabling the exemption (turning battery optimization back on)
            batteryOptimizationChecker.setIgnoringBatteryOptimizations(false)
            viewModel.checkBatteryOptimizationStatus()
            advanceUntilIdle()

            val updatedState = viewModel.state.value as DevicesListState.HasDevices
            assertFalse(
                "After check, should not be ignoring battery optimizations",
                updatedState.isIgnoringBatteryOptimizations,
            )

            // Simulate user enabling the exemption again (disabling battery optimization)
            batteryOptimizationChecker.setIgnoringBatteryOptimizations(true)
            viewModel.checkBatteryOptimizationStatus()
            advanceUntilIdle()

            val finalState = viewModel.state.value as DevicesListState.HasDevices
            assertTrue(
                "After second check, should be ignoring battery optimizations again",
                finalState.isIgnoringBatteryOptimizations,
            )
        }

    @Test
    fun `sendFeedback calls issueReporter`() = runTest {
        viewModel.sendFeedback()
        advanceUntilIdle()

        assertTrue("Issue report should have been sent", issueReporter.sendIssueReportCalled)
    }

    private fun mockContext(): Context {
        // Return a relaxed mock context that won't crash
        // The service binding will fail, but that's fine for these tests
        // since we're only testing display info computation
        return mockk<Context>(relaxed = true) {
            every { applicationContext } returns this
            every { packageName } returns "test.package"
            every {
                bindService(
                    any<android.content.Intent>(),
                    any<android.content.ServiceConnection>(),
                    any<Int>(),
                )
            } returns false
            every {
                bindService(
                    any<android.content.Intent>(),
                    any<android.content.ServiceConnection>(),
                    any<android.content.Context.BindServiceFlags>(),
                )
            } returns false
        }
    }
}
