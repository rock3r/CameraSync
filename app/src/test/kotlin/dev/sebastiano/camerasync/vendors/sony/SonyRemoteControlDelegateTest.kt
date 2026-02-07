package dev.sebastiano.camerasync.vendors.sony

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.RecordingStatus
import dev.sebastiano.camerasync.domain.model.ShutterStatus
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [SonyRemoteControlDelegate] BLE flows using Mockk. */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class SonyRemoteControlDelegateTest {

    private fun createCamera(): Camera =
        Camera(
            identifier = "sony-ble-id",
            name = "ILCE-7M4",
            macAddress = "AA:BB:CC:DD:EE:FF",
            vendor = SonyCameraVendor,
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

    // ==================== triggerCapture (BLE) — event-driven ====================

    /** FF02 payloads per BLE_STATE_MONITORING.md: [0x02, type, value]. */
    private fun focusAcquiredPayload(): ByteArray = byteArrayOf(0x02, 0x3F, 0x20)

    private fun shutterActivePayload(): ByteArray = byteArrayOf(0x02, 0xA0.toByte(), 0x20)

    @Test
    fun `triggerCapture follows event-driven sequence per doc half down then wait focus then full down then wait shutter then release`() =
        runTest {
            val ff02Flow = flowOf(focusAcquiredPayload(), shutterActivePayload())
            val recorder = WriteRecorder()
            val peripheral = createPeripheral(ff02Flow, recorder)
            val delegate = SonyRemoteControlDelegate(peripheral, createCamera())

            delegate.triggerCapture()

            val finalWrites = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
            assertEquals(4, finalWrites.size)
            assertArrayEquals(
                SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_PRESS),
                finalWrites[0],
            )
            assertArrayEquals(
                SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_PRESS),
                finalWrites[1],
            )
            assertArrayEquals(
                SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_RELEASE),
                finalWrites[2],
            )
            assertArrayEquals(
                SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_RELEASE),
                finalWrites[3],
            )
        }

    @Test
    fun `triggerCapture uses WithoutResponse for all writes`() = runTest {
        val ff02Flow = flowOf(focusAcquiredPayload(), shutterActivePayload())
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(ff02Flow, recorder)
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.triggerCapture()

        val types = recorder.writeTypesFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
        assertEquals(4, types.size)
        assertTrue(types.all { it == WriteType.WithoutResponse })
    }

    /**
     * Regression: FF02 shared flow must use replay=1 so notifications emitted between the BLE write
     * and the filter+first subscriber becoming active are not dropped. With replay=0, a fast camera
     * response in that window would be lost and we'd fall back to timeouts.
     */
    @Test
    fun `triggerCapture receives FF02 notifications that arrive before subscriber with replay`() =
        runTest {
            val ff02Flow = flow {
                emit(focusAcquiredPayload())
                delay(100)
                emit(shutterActivePayload())
            }
            val recorder = WriteRecorder()
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val peripheral = createPeripheral(ff02Flow, recorder)
            val delegate =
                SonyRemoteControlDelegate(peripheral, createCamera(), captureScope = scope)

            val startTime = testScheduler.currentTime
            launch { delegate.triggerCapture() }
            advanceTimeBy(150)
            advanceUntilIdle()
            val elapsed = testScheduler.currentTime - startTime

            assertEquals(
                "Full sequence (half → focus → full → shutter → release) must complete",
                4,
                recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID).size,
            )
            assertTrue(
                "Must complete without relying on 3s+5s timeouts (replay=1 delivers early emissions); elapsed=${elapsed}ms",
                elapsed < 2_000L,
            )
        }

    @Test
    fun `triggerCapture proceeds after focus and shutter timeouts when no FF02 events`() = runTest {
        val ff02Flow = flowOf<ByteArray>()
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(ff02Flow, recorder)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val delegate = SonyRemoteControlDelegate(peripheral, createCamera(), captureScope = scope)

        val captureJob = launch { delegate.triggerCapture() }
        runCurrent()
        val step1 = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID).size
        assertEquals(
            "After half-press, delegate waits for focus (1 write so far, got $step1)",
            1,
            step1,
        )

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(
            "After focus timeout (3s), full press is written",
            2,
            recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID).size,
        )

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(
            "After shutter timeout (5s), release sequence is written",
            4,
            recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID).size,
        )
        advanceUntilIdle()
        captureJob.join()
    }

    // ==================== startBulbExposure / stopBulbExposure ====================

    @Test
    fun `startBulbExposure writes shutter full press`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.startBulbExposure()

        val ff01Data = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
        assertEquals(1, ff01Data.size)
        assertArrayEquals(
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_PRESS),
            ff01Data[0],
        )
    }

    @Test
    fun `stopBulbExposure writes shutter full release`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.stopBulbExposure()

        val ff01Data = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
        assertEquals(1, ff01Data.size)
        assertArrayEquals(
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_RELEASE),
            ff01Data[0],
        )
    }

    // ==================== observe flows — FF02 and CC09 ====================

    @Test
    fun `observeCaptureStatus maps FF02 shutter 0xA0 0x20 to Capturing`() = runTest {
        val ff02Payload = byteArrayOf(0x02, 0xA0.toByte(), 0x20)
        val peripheral = createPeripheral(flowOf(ff02Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val status = delegate.observeCaptureStatus().first()

        assertEquals(CaptureStatus.Capturing, status)
    }

    @Test
    fun `observeCaptureStatus maps FF02 shutter 0xA0 0x00 to Idle`() = runTest {
        val ff02Payload = byteArrayOf(0x02, 0xA0.toByte(), 0x00)
        val peripheral = createPeripheral(flowOf(ff02Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val status = delegate.observeCaptureStatus().first()

        assertEquals(CaptureStatus.Idle, status)
    }

    @Test
    fun `observeCameraMode maps CC09 tag 0x0008 value 1 to MOVIE`() = runTest {
        val cc09Payload = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x01)
        val peripheral = createPeripheral(flowOf(cc09Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(dev.sebastiano.camerasync.domain.model.CameraMode.MOVIE, mode)
    }

    @Test
    fun `observeCameraMode maps CC09 tag 0x0008 value 0 to STILL_IMAGE`() = runTest {
        val cc09Payload = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x00)
        val peripheral = createPeripheral(flowOf(cc09Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val mode = delegate.observeCameraMode().first()

        assertEquals(dev.sebastiano.camerasync.domain.model.CameraMode.STILL_IMAGE, mode)
    }

    @Test
    fun `observeFocusStatus maps FF02 focus 0x3F 0x20 to LOCKED`() = runTest {
        val ff02Payload = byteArrayOf(0x02, 0x3F, 0x20.toByte())
        val peripheral = createPeripheral(flowOf(ff02Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val flow = delegate.observeFocusStatus()
        assertNotNull(flow)
        val status = flow!!.first()

        assertEquals(FocusStatus.LOCKED, status)
    }

    @Test
    fun `observeShutterStatus maps FF02 0xA0 0x20 to ACTIVE`() = runTest {
        val ff02Payload = byteArrayOf(0x02, 0xA0.toByte(), 0x20)
        val peripheral = createPeripheral(flowOf(ff02Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val flow = delegate.observeShutterStatus()
        assertNotNull(flow)
        val status = flow!!.first()

        assertEquals(ShutterStatus.ACTIVE, status)
    }

    @Test
    fun `observeRecordingStatus maps FF02 0xD5 0x20 to RECORDING`() = runTest {
        val ff02Payload = byteArrayOf(0x02, 0xD5.toByte(), 0x20)
        val peripheral = createPeripheral(flowOf(ff02Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val flow = delegate.observeRecordingStatus()
        assertNotNull(flow)
        val status = flow!!.first()

        assertEquals(RecordingStatus.RECORDING, status)
    }

    @Test
    fun `observeBatteryLevel decodes CC10 payload`() = runTest {
        // CC10: enable, support, position, status, then 4-byte big-endian percentage
        val cc10Payload = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 75)
        val peripheral = createPeripheral(flowOf(cc10Payload))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        val info = delegate.observeBatteryLevel().first()

        assertEquals(75, info.levelPercentage)
    }

    @Test
    fun `connectionMode is BLE_ONLY by default`() = runTest {
        val peripheral = createPeripheral(flowOf(ByteArray(0)))
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        assertEquals(ShootingConnectionMode.BLE_ONLY, delegate.connectionMode.value)
    }

    @Test
    fun `halfPressAF writes RC_SHUTTER_HALF_PRESS`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.halfPressAF()

        val data = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
        assertEquals(1, data.size)
        assertArrayEquals(
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_PRESS),
            data[0],
        )
    }

    @Test
    fun `toggleVideoRecording writes RC_VIDEO_REC`() = runTest {
        val recorder = WriteRecorder()
        val peripheral = createPeripheral(flowOf(ByteArray(0)), recorder)
        val delegate = SonyRemoteControlDelegate(peripheral, camera = createCamera())

        delegate.toggleVideoRecording()

        val data = recorder.dataFor(SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID)
        assertEquals(1, data.size)
        assertArrayEquals(
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_VIDEO_REC),
            data[0],
        )
    }
}
