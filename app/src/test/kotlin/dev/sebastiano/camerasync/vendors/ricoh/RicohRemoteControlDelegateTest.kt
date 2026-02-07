package dev.sebastiano.camerasync.vendors.ricoh

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
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

    private class WriteRecorder {
        data class Record(val charUuid: Uuid, val data: ByteArray, val writeType: WriteType)

        private val writes = mutableListOf<Record>()

        fun record(char: Characteristic, data: ByteArray, writeType: WriteType) {
            writes.add(Record(char.characteristicUuid, data.copyOf(), writeType))
        }

        fun dataFor(charUuid: Uuid): List<ByteArray> =
            writes.filter { it.charUuid == charUuid }.map { it.data }

        fun writeTypesFor(charUuid: Uuid): List<WriteType> =
            writes.filter { it.charUuid == charUuid }.map { it.writeType }

        fun count(): Int = writes.size
    }

    private val commandUuid = RicohGattSpec.CameraControl.COMMAND_CHARACTERISTIC_UUID

    // ==================== triggerCapture ====================

    @Test
    fun `triggerCapture writes 0x01 to command characteristic with WithResponse`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.triggerCapture()

        val data = recorder.dataFor(commandUuid)
        assertEquals(1, data.size)
        assertArrayEquals(byteArrayOf(0x01), data[0])
        val types = recorder.writeTypesFor(commandUuid)
        assertEquals(1, types.size)
        assertEquals(WriteType.WithResponse, types[0])
    }

    // ==================== startBulbExposure / stopBulbExposure ====================

    @Test
    fun `startBulbExposure writes 0x01 to command characteristic`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.startBulbExposure()

        val data = recorder.dataFor(commandUuid)
        assertEquals(1, data.size)
        assertArrayEquals(byteArrayOf(0x01), data[0])
    }

    @Test
    fun `stopBulbExposure writes 0x01 to command characteristic`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.stopBulbExposure()

        val data = recorder.dataFor(commandUuid)
        assertEquals(1, data.size)
        assertArrayEquals(byteArrayOf(0x01), data[0])
    }

    @Test
    fun `bulb and trigger use WithResponse`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.startBulbExposure()
        delegate.stopBulbExposure()

        val types = recorder.writeTypesFor(commandUuid)
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
    fun `observeCameraMode maps 1 to MOVIE`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(1)))
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
    fun `observeExposureMode decodes single exposure byte from characteristic`() = runTest {
        // EXPOSURE_MODE_CHARACTERISTIC is 1 byte: 0=P, 1=Av, 2=Tv, 3=M, ...
        val peripheral = createPeripheral(flowOf(byteArrayOf(1)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeExposureMode().first()

        assertEquals(ExposureMode.APERTURE_PRIORITY, mode)
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
    fun `connectionMode is BLE_ONLY by default`() = runTest {
        val peripheral = createPeripheral(flowOf(ByteArray(0)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        assertEquals(ShootingConnectionMode.BLE_ONLY, delegate.connectionMode.value)
    }

    @Test
    fun `observeCaptureStatus returns empty flow`() = runTest {
        val peripheral = createPeripheral(flowOf(byteArrayOf(1)))
        val delegate = RicohRemoteControlDelegate(peripheral, camera = createCamera())

        val count = delegate.observeCaptureStatus().take(1).count()

        assertEquals(0, count)
    }
}
