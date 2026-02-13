package dev.sebastiano.camerasync.ui.remote

import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.devicesync.DeviceConnectionManager
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.SyncCapabilities
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeIntentFactory
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakeRemoteControlDelegate
import dev.sebastiano.camerasync.fakes.ThrowingRemoteControlDelegate
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
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
    private lateinit var intentFactory: FakeIntentFactory
    private lateinit var viewModel: RemoteShootingViewModel
    private lateinit var connection: FakeCameraConnection

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        connectionManager = DeviceConnectionManager()
        intentFactory = FakeIntentFactory()

        val vendor =
            mockk<CameraVendor>(relaxed = true) {
                every { vendorId } returns "test"
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                every { getSyncCapabilities() } returns SyncCapabilities()
            }
        val camera =
            Camera(
                identifier = "test-id",
                name = "Test Camera",
                macAddress = macAddress,
                vendor = vendor,
            )

        runTest(testDispatcher) { pairedDevicesRepository.addDevice(camera, enabled = true) }

        connection = FakeCameraConnection(camera)
        connection.setRemoteControlDelegate(ThrowingRemoteControlDelegate())
        connectionManager.addConnection(macAddress, connection, Job())

        viewModel =
            RemoteShootingViewModel(
                deviceConnectionManager = connectionManager,
                pairedDevicesRepository = pairedDevicesRepository,
                intentFactory = intentFactory,
                context = mockk(relaxed = true),
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
            val state = viewModel.uiState.value as RemoteShootingUiState.Ready
            val actionState = state.actionState
            assertTrue(actionState is RemoteShootingActionState.Error)
            actionState as RemoteShootingActionState.Error
            assertTrue(actionState.canRetry)
            assertTrue(actionState.canReset)
        }

    @Test
    fun `disconnectWifi does not throw when delegate throws`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)

            viewModel.disconnectWifi()
            advanceUntilIdle()
            val state = viewModel.uiState.value as RemoteShootingUiState.Ready
            val actionState = state.actionState
            assertTrue(actionState is RemoteShootingActionState.Error)
            actionState as RemoteShootingActionState.Error
            assertTrue(actionState.canRetry)
            assertTrue(actionState.canReset)
        }

    @Test
    fun `retryAction reattempts failed capture and clears error`() =
        runTest(testDispatcher) {
            val delegate = FlakyRemoteControlDelegate()
            connection.setRemoteControlDelegate(delegate)

            viewModel.loadDevice(macAddress)
            advanceUntilIdle()

            viewModel.triggerCapture()
            advanceUntilIdle()

            val errorState = viewModel.uiState.value as RemoteShootingUiState.Ready
            assertTrue(errorState.actionState is RemoteShootingActionState.Error)

            viewModel.retryAction(RemoteShootingAction.TriggerCapture)
            advanceUntilIdle()

            val readyState = viewModel.uiState.value as RemoteShootingUiState.Ready
            assertTrue(readyState.actionState is RemoteShootingActionState.Idle)
            assertTrue(delegate.calls == 2)
        }

    @Test
    fun `resetRemoteShooting requests refresh intent for device`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()

            viewModel.resetRemoteShooting()
            advanceUntilIdle()

            assertNotNull(intentFactory.lastRefreshDeviceIntent)
            assertTrue(intentFactory.lastRefreshDeviceMacAddress == macAddress)
        }

    @Test
    fun `after BLE reconnect state is refreshed with new delegate`() =
        runTest(testDispatcher) {
            viewModel.loadDevice(macAddress)
            advanceUntilIdle()
            val firstReady = viewModel.uiState.value as RemoteShootingUiState.Ready
            val firstDelegate = firstReady.delegate

            // Simulate BLE disconnect then reconnect (new connection instance)
            connectionManager.removeConnection(macAddress)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Error)

            val vendor2 =
                mockk<CameraVendor>(relaxed = true) {
                    every { vendorId } returns "test"
                    every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                    every { getSyncCapabilities() } returns SyncCapabilities()
                }
            val camera2 =
                Camera(
                    identifier = "test-id-2",
                    name = "Test Camera",
                    macAddress = macAddress,
                    vendor = vendor2,
                )
            val connection2 = FakeCameraConnection(camera2)
            connection2.setRemoteControlDelegate(FakeRemoteControlDelegate())
            connectionManager.addConnection(macAddress, connection2, Job())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RemoteShootingUiState.Ready)
            val secondReady = viewModel.uiState.value as RemoteShootingUiState.Ready
            assertTrue(
                "Delegate should be the new instance after reconnect",
                secondReady.delegate !== firstDelegate,
            )
        }

    private class FlakyRemoteControlDelegate :
        RemoteControlDelegate by FakeRemoteControlDelegate() {
        var calls = 0

        override suspend fun triggerCapture() {
            calls += 1
            if (calls == 1) {
                throw IOException("BLE write failed")
            }
        }
    }
}
