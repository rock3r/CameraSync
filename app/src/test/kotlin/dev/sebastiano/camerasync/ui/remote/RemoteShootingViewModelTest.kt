package dev.sebastiano.camerasync.ui.remote

import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.devicesync.DeviceConnectionManager
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.ThrowingRemoteControlDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression tests for [RemoteShootingViewModel] remote control exception handling. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteShootingViewModelTest {

    private val macAddress = "11:22:33:44:55:66"
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var connectionManager: DeviceConnectionManager
    private lateinit var viewModel: RemoteShootingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        connectionManager = DeviceConnectionManager()

        val vendor =
            mockk<dev.sebastiano.camerasync.domain.vendor.CameraVendor>(relaxed = true) {
                every { vendorId } returns "test"
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
            }
        val camera =
            Camera(
                identifier = "test-id",
                name = "Test Camera",
                macAddress = macAddress,
                vendor = vendor,
            )

        runTest(testDispatcher) { pairedDevicesRepository.addDevice(camera, enabled = true) }

        val connection = FakeCameraConnection(camera)
        connection.setRemoteControlDelegate(ThrowingRemoteControlDelegate())
        connectionManager.addConnection(macAddress, connection, Job())

        viewModel =
            RemoteShootingViewModel(
                deviceConnectionManager = connectionManager,
                pairedDevicesRepository = pairedDevicesRepository,
                ioDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDevice reaches Ready state when device and connection exist`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is RemoteShootingUiState.Ready)
            assertNotNull((state as RemoteShootingUiState.Ready).delegate)
        }

    @Test
    fun `triggerCapture does not throw when delegate throws`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)

            viewModel.triggerCapture()
            advanceUntilIdle()
            // No exception propagates; state remains Ready
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)
        }

    @Test
    fun `disconnectWifi does not throw when delegate throws`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)

            viewModel.disconnectWifi()
            advanceUntilIdle()
            // No exception propagates; state remains Ready
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)
        }
}
