package dev.sebastiano.camerasync.vendors.ricoh

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import dev.sebastiano.camerasync.testutils.WriteRecorder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [RicohRemoteControlDelegate] BLE flows using Mockk. */
@OptIn(ExperimentalUuidApi::class)
class RicohRemoteControlDelegateTest {

    private fun createCamera(): Camera =
        Camera(
            identifier = "ricoh-ble-id",
            name = "GR IIIx",
            macAddress = "11:22:33:44:55:66",
            vendor = RicohCameraVendor,
        )

    private fun createPeripheral(
        observeFlow: kotlinx.coroutines.flow.Flow<ByteArray>,
        writeRecorder: WriteRecorder? = null,
    ): Peripheral =
        mockk<Peripheral>(relaxed = true) {
            every { observe(any<Characteristic>(), any()) } returns observeFlow
            if (writeRecorder != null) {
                coEvery { write(any<Characteristic>(), any(), any()) } answers
                    {
                        writeRecorder.record(firstArg(), secondArg(), thirdArg())
                    }
            } else {
                coEvery { write(any<Characteristic>(), any(), any()) } returns Unit
            }
        }

    private val operationRequestUuid = RicohGattSpec.Shooting.OPERATION_REQUEST_CHARACTERISTIC_UUID

    // ==================== triggerCapture ====================

    @Test
    fun `triggerCapture writes Operation Request Start then Stop with WithResponse`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.triggerCapture()

        val data = recorder.dataFor(operationRequestUuid)
        assertEquals(2, data.size)
        assertArrayEquals(
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_START,
                RicohProtocol.OP_REQ_PARAM_AF,
            ),
            data[0],
        )
        assertArrayEquals(
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_STOP,
                RicohProtocol.OP_REQ_PARAM_NO_AF,
            ),
            data[1],
        )
        val types = recorder.writeTypesFor(operationRequestUuid)
        assertEquals(2, types.size)
        assertTrue(types.all { it == WriteType.WithResponse })
    }

    // ==================== startBulbExposure / stopBulbExposure ====================

    @Test
    fun `startBulbExposure writes Operation Request Start NO_AF to operation request characteristic`() =
        runTest {
            val recorder = WriteRecorder()
            val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
            val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

            delegate.startBulbExposure()

            val data = recorder.dataFor(operationRequestUuid)
            assertEquals(1, data.size)
            assertArrayEquals(
                RicohProtocol.encodeOperationRequest(
                    RicohProtocol.OP_REQ_START,
                    RicohProtocol.OP_REQ_PARAM_NO_AF,
                ),
                data[0],
            )
        }

    @Test
    fun `startBulbExposure uses NO_AF not AF to avoid focus hunt in dark bulb conditions`() =
        runTest {
            val recorder = WriteRecorder()
            val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
            val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

            delegate.startBulbExposure()

            val data = recorder.dataFor(operationRequestUuid).single()
            assertEquals(
                "Parameter byte must be NO_AF (0), not AF (1); AF would hunt in dark and block exposure",
                RicohProtocol.OP_REQ_PARAM_NO_AF.toByte(),
                data[1],
            )
        }

    @Test
    fun `stopBulbExposure writes Operation Request Stop to operation request characteristic`() =
        runTest {
            val recorder = WriteRecorder()
            val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
            val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

            delegate.stopBulbExposure()

            val data = recorder.dataFor(operationRequestUuid)
            assertEquals(1, data.size)
            assertArrayEquals(
                RicohProtocol.encodeOperationRequest(
                    RicohProtocol.OP_REQ_STOP,
                    RicohProtocol.OP_REQ_PARAM_NO_AF,
                ),
                data[0],
            )
        }

    @Test
    fun `bulb and trigger use WithResponse`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.startBulbExposure()
        delegate.stopBulbExposure()

        val types = recorder.writeTypesFor(operationRequestUuid)
        assertEquals(2, types.size)
        assertTrue(types.all { it == WriteType.WithResponse })
    }

    // ==================== observe flows ====================

    @Test
    fun `observeBatteryLevel decodes first byte as level`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(75)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val info = delegate.observeBatteryLevel().first()

        assertEquals(75, info.levelPercentage)
    }

    @Test
    fun `observeCameraMode maps 0 to STILL_IMAGE`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(CameraMode.STILL_IMAGE, mode)
    }

    @Test
    fun `observeCameraMode maps 2 to MOVIE`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(2)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(CameraMode.MOVIE, mode)
    }

    @Test
    fun `observeCameraMode maps empty data to UNKNOWN`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf()))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(CameraMode.UNKNOWN, mode)
    }

    @Test
    fun `observeCameraMode maps unknown byte to UNKNOWN`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(0xFF.toByte())))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(CameraMode.UNKNOWN, mode)
    }

    @Test
    fun `observeCameraMode maps 1 to UNKNOWN`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(1)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(CameraMode.UNKNOWN, mode)
    }

    @Test
    fun `observeExposureMode decodes exposure from shooting mode characteristic`() = runTest {
        // SHOOTING_MODE_CHARACTERISTIC: [ShootingMode, ExposureMode]. Byte 0=Still/Movie, byte
        // 1=P/Av/Tv/M...
        val peripheral = createPeripheral(flowOf(byteArrayOf(0, 1)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeExposureMode().first()

        assertEquals(ExposureMode.APERTURE_PRIORITY, mode)
    }

    @Test
    fun `observeExposureMode reads exposure from byte 1 not byte 0`() = runTest {
        // Regression: byte 0=shooting mode (1=Movie), byte 1=exposure (0=P). Must decode byte 1.
        val peripheral = createPeripheral(flowOf(byteArrayOf(1, 0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeExposureMode().first()

        assertEquals(ExposureMode.PROGRAM_AUTO, mode)
    }

    @Test
    fun `observeExposureMode Movie and M decodes MANUAL from byte 1`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(1, 3)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeExposureMode().first()

        assertEquals(ExposureMode.MANUAL, mode)
    }

    @Test
    fun `observeExposureMode returns UNKNOWN for short or empty data`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeExposureMode().first()

        assertEquals(ExposureMode.UNKNOWN, mode)
    }

    @Test
    fun `observeDriveMode decodes drive byte`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeDriveMode().first()

        assertEquals(DriveMode.SINGLE_SHOOTING, mode)
    }

    @Test
    fun `observeDriveMode maps 3 to CONTINUOUS_SHOOTING`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(3)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeDriveMode().first()

        assertEquals(DriveMode.CONTINUOUS_SHOOTING, mode)
    }

    @Test
    fun `observeDriveMode uses distinctUntilChanged to avoid duplicate emissions`() = runTest {
        // Verify that distinctUntilChanged prevents duplicate drive mode values from being emitted
        val notifications =
            flowOf(
                byteArrayOf(3), // CONTINUOUS_SHOOTING
                byteArrayOf(3), // Duplicate - should be filtered by distinctUntilChanged
                byteArrayOf(2), // SELF_TIMER_2S
            )
        val peripheral = createPeripheral(notifications)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val modes = delegate.observeDriveMode().take(2).toList()

        // Should only receive 2 distinct values, not 3 (duplicate filtered)
        assertEquals(2, modes.size)
        assertEquals(DriveMode.CONTINUOUS_SHOOTING, modes[0])
        assertEquals(DriveMode.SELF_TIMER_2S, modes[1])
    }

    @Test
    fun `connectionMode is BLE_ONLY by default`() = runTest {
        val peripheral = createPeripheral(flowOf(ByteArray(0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        assertEquals(ShootingConnectionMode.BLE_ONLY, delegate.connectionMode.value)
    }

    @Test
    fun `observeCaptureStatus decodes capture status`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(1, 0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val status = delegate.observeCaptureStatus().first()

        assertEquals(CaptureStatus.Capturing, status)
    }
}
