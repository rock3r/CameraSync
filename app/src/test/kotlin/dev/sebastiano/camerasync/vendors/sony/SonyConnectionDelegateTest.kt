package dev.sebastiano.camerasync.vendors.sony

import com.juul.kable.Characteristic
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SonyConnectionDelegate].
 *
 * Verifies the Sony-specific BLE connection lifecycle:
 * - MTU negotiation (158 bytes)
 * - DD30/DD31 locking/unlocking for location service
 * - Capability reading from DD21
 * - Location packet size (91 or 95 bytes based on timezone config)
 * - Retry logic for location writes
 * - Graceful disconnect cleanup
 * - Legacy camera support (no DD30/DD31)
 * - Strict order of operations (Lock -> Enable -> Write)
 */
@OptIn(ExperimentalUuidApi::class)
class SonyConnectionDelegateTest {

    private val location =
        GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
        )

    private val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)

    // ==================== MTU Tests ====================

    @Test
    fun `MTU is 158`() {
        val delegate = SonyConnectionDelegate()
        assertEquals(158, delegate.mtu)
    }

    // ==================== syncLocation Tests ====================

    @Test
    fun `syncLocation writes to DD11 characteristic`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(writeRecorder = recorder)

        delegate.syncLocation(peripheral, camera, location)

        assertTrue(
            "Expected a write to DD11",
            recorder.hasWriteTo(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID),
        )
    }

    @Test
    fun `syncLocation enables DD30 and DD31 before first write`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2, writeRecorder = recorder)

        delegate.syncLocation(peripheral, camera, location)

        // Verify DD30 (lock) was written with 0x01
        assertTrue(
            "DD30 (lock) should have been written",
            recorder.hasWriteToWithData(
                SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
                byteArrayOf(0x01),
            ),
        )

        // Verify DD31 (enable) was written with 0x01
        assertTrue(
            "DD31 (enable) should have been written",
            recorder.hasWriteToWithData(
                SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
                byteArrayOf(0x01),
            ),
        )
    }

    @Test
    fun `syncLocation enforces strict write order DD30 then DD31 then DD11`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2, writeRecorder = recorder)

        delegate.syncLocation(peripheral, camera, location)

        val writes = recorder.getAllWrites()
        assertTrue("Should have at least 3 writes", writes.size >= 3)

        // Filter for relevant writes to ignore potential reads/other ops if recorded (though mock
        // only records writes)
        val relevantWrites =
            writes.filter {
                it.first == SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID ||
                    it.first == SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID ||
                    it.first == SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID
            }

        // 1. Lock (DD30) -> 0x01
        assertEquals(
            "First op should be Lock (DD30)",
            SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
            relevantWrites[0].first,
        )
        assertEquals("Lock should be 0x01", 0x01.toByte(), relevantWrites[0].second[0])

        // 2. Enable (DD31) -> 0x01
        assertEquals(
            "Second op should be Enable (DD31)",
            SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
            relevantWrites[1].first,
        )
        assertEquals("Enable should be 0x01", 0x01.toByte(), relevantWrites[1].second[0])

        // 3. Data (DD11)
        assertEquals(
            "Third op should be Data (DD11)",
            SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
            relevantWrites[2].first,
        )
    }

    @Test
    fun `syncLocation subscribes to DD01 notifications`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2)

        delegate.syncLocation(peripheral, camera, location)

        // Verify we subscribed to DD01 notifications
        val slot = slot<Characteristic>()
        coVerify(exactly = 1) { peripheral.observe(capture(slot), any()) }
        assertEquals(
            SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID,
            slot.captured.characteristicUuid,
        )
    }

    @Test
    fun `syncLocation retries on IOException`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(hasDD30DD31 = false)

        var dd11WriteCount = 0
        coEvery { peripheral.write(any<Characteristic>(), any(), any()) } answers
            {
                val char = firstArg<Characteristic>()
                if (
                    char.characteristicUuid == SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID
                ) {
                    dd11WriteCount++
                    if (dd11WriteCount < 3) throw IOException("BLE write failed")
                }
            }

        delegate.syncLocation(peripheral, camera, location)

        assertEquals("Should have retried 3 times total", 3, dd11WriteCount)
    }

    @Test(expected = IOException::class)
    fun `syncLocation throws after max retries exhausted`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(hasDD30DD31 = false)

        coEvery { peripheral.write(any<Characteristic>(), any(), any()) } answers
            {
                val char = firstArg<Characteristic>()
                if (
                    char.characteristicUuid == SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID
                ) {
                    throw IOException("BLE write failed")
                }
            }

        delegate.syncLocation(peripheral, camera, location)
    }

    @Test
    fun `syncLocation produces correctly sized packet with timezone`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) =
            createSonySetup(
                hasDD30DD31 = false,
                configData = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x02, 0x00),
                writeRecorder = recorder,
            )

        delegate.syncLocation(peripheral, camera, location)

        val dd11Data = recorder.dataWrittenTo(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID)
        assertEquals("DD11 packet with timezone should be 95 bytes", 95, dd11Data?.size)
    }

    @Test
    fun `syncLocation produces correctly sized packet without timezone`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) =
            createSonySetup(
                hasDD30DD31 = false,
                configData = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x00, 0x00),
                writeRecorder = recorder,
            )

        delegate.syncLocation(peripheral, camera, location)

        val dd11Data = recorder.dataWrittenTo(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID)
        assertEquals("DD11 packet without timezone should be 91 bytes", 91, dd11Data?.size)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncLocation throws when location service not found`() = runTest {
        val delegate = SonyConnectionDelegate()
        val peripheral =
            mockk<Peripheral> { every { services } returns MutableStateFlow(emptyList()) }
        val camera = createCamera()

        delegate.syncLocation(peripheral, camera, location)
    }

    // ==================== syncDateTime Tests ====================

    @Test
    fun `syncDateTime writes to CC13 characteristic`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(writeRecorder = recorder)

        delegate.syncDateTime(peripheral, camera, dateTime)

        assertTrue(
            "Expected a write to CC13",
            recorder.hasWriteTo(SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID),
        )
    }

    @Test
    fun `syncDateTime produces 13-byte CC13 packet`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(writeRecorder = recorder)

        delegate.syncDateTime(peripheral, camera, dateTime)

        val cc13Data = recorder.dataWrittenTo(SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID)
        assertEquals("CC13 packet should be 13 bytes", 13, cc13Data?.size)
    }

    // ==================== onDisconnecting Tests ====================

    @Test
    fun `onDisconnecting disables DD31 then DD30`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2)

        // First sync to enable the service
        delegate.syncLocation(peripheral, camera, location)

        // Set up recorder for disconnect writes only
        val recorder = WriteRecorder()
        coEvery { peripheral.write(any<Characteristic>(), any(), any()) } answers
            {
                recorder.record(firstArg(), secondArg())
            }

        // Now disconnect
        delegate.onDisconnecting(peripheral)

        // Verify DD31 (enable) written with 0x00
        assertTrue(
            "DD31 should be disabled",
            recorder.hasWriteToWithData(
                SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
                byteArrayOf(0x00),
            ),
        )

        // Verify DD30 (lock) written with 0x00
        assertTrue(
            "DD30 should be unlocked",
            recorder.hasWriteToWithData(
                SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
                byteArrayOf(0x00),
            ),
        )
    }

    @Test
    fun `onDisconnecting enforces strict write order DD31 then DD30`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2)

        // First sync to enable the service
        delegate.syncLocation(peripheral, camera, location)

        // Set up recorder for disconnect writes only
        val recorder = WriteRecorder()
        coEvery { peripheral.write(any<Characteristic>(), any(), any()) } answers
            {
                recorder.record(firstArg(), secondArg())
            }

        // Now disconnect
        delegate.onDisconnecting(peripheral)

        val writes = recorder.getAllWrites()
        assertEquals("Should have exactly 2 writes", 2, writes.size)

        // 1. Enable (DD31) -> 0x00
        assertEquals(
            "First op should be Enable (DD31)",
            SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
            writes[0].first,
        )
        assertEquals("Enable should be 0x00", 0x00.toByte(), writes[0].second[0])

        // 2. Lock (DD30) -> 0x00
        assertEquals(
            "Second op should be Lock (DD30)",
            SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
            writes[1].first,
        )
        assertEquals("Lock should be 0x00", 0x00.toByte(), writes[1].second[0])
    }

    @Test
    fun `onDisconnecting is no-op when service was never enabled`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, _) = createSonySetup()

        // Disconnect without ever syncing
        delegate.onDisconnecting(peripheral)

        // Should not write anything
        coVerify(exactly = 0) { peripheral.write(any<Characteristic>(), any(), any()) }
    }

    @Test
    fun `onDisconnecting handles write failure gracefully`() = runTest {
        val delegate = SonyConnectionDelegate()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2)

        // First sync to enable the service
        delegate.syncLocation(peripheral, camera, location)

        // Make all writes during disconnect fail
        coEvery { peripheral.write(any<Characteristic>(), any(), any()) } throws
            IOException("BLE write failed during disconnect")

        // Should not throw — errors during disconnect are logged but swallowed
        delegate.onDisconnecting(peripheral)
    }

    // ==================== Legacy Protocol Tests ====================

    @Test
    fun `syncLocation works with legacy camera without DD30 and DD31`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) =
            createSonySetup(hasDD30DD31 = false, protocolVersion = null, writeRecorder = recorder)

        delegate.syncLocation(peripheral, camera, location)

        // Should NOT write to DD30/DD31
        assertTrue(
            "Should NOT write to DD30 for legacy camera",
            !recorder.hasWriteTo(SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID),
        )

        // Should still write location data
        assertTrue(
            "Should write location data to DD11",
            recorder.hasWriteTo(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID),
        )
    }

    // ==================== Multiple Syncs ====================

    @Test
    fun `syncLocation re-enables DD30 and DD31 on subsequent calls`() = runTest {
        val delegate = SonyConnectionDelegate()
        val recorder = WriteRecorder()
        val (peripheral, camera) = createSonySetup(protocolVersion = 2, writeRecorder = recorder)

        delegate.syncLocation(peripheral, camera, location)
        delegate.syncLocation(peripheral, camera, location)

        // DD30 should be written with 0x01 at least twice
        val dd30Enables =
            recorder.countWritesWithData(
                SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
                byteArrayOf(0x01),
            )
        assertTrue("DD30 should be enabled at least twice, was $dd30Enables", dd30Enables >= 2)

        // DD31 should be written with 0x01 at least twice
        val dd31Enables =
            recorder.countWritesWithData(
                SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
                byteArrayOf(0x01),
            )
        assertTrue("DD31 should be enabled at least twice, was $dd31Enables", dd31Enables >= 2)
    }

    // ==================== Helpers ====================

    /** Records BLE writes for later assertion. Avoids MockK's `match` limitations. */
    private class WriteRecorder {
        private val writes = mutableListOf<Pair<Uuid, ByteArray>>()

        fun record(char: Characteristic, data: ByteArray) {
            writes.add(char.characteristicUuid to data.copyOf())
        }

        fun hasWriteTo(charUuid: Uuid): Boolean = writes.any { it.first == charUuid }

        fun hasWriteToWithData(charUuid: Uuid, data: ByteArray): Boolean =
            writes.any { it.first == charUuid && it.second.contentEquals(data) }

        fun dataWrittenTo(charUuid: Uuid): ByteArray? =
            writes.lastOrNull { it.first == charUuid }?.second

        fun countWritesWithData(charUuid: Uuid, data: ByteArray): Int =
            writes.count { it.first == charUuid && it.second.contentEquals(data) }

        fun getAllWrites(): List<Pair<Uuid, ByteArray>> = writes.toList()
    }

    private fun createCamera(protocolVersion: Int? = null): Camera {
        val metadata = buildMap {
            if (protocolVersion != null) {
                put("bleProtocolVersion", protocolVersion)
            }
        }
        return Camera(
            identifier = "sony-test-id",
            name = "ILCE-7M4",
            macAddress = "11:22:33:44:55:66",
            vendor = SonyCameraVendor,
            vendorMetadata = metadata,
        )
    }

    private fun createSonySetup(
        protocolVersion: Int? = null,
        hasDD30DD31: Boolean = true,
        configData: ByteArray? = null,
        writeRecorder: WriteRecorder? = null,
    ): Pair<Peripheral, Camera> {
        val camera = createCamera(protocolVersion)

        val dd11Char =
            createMockCharacteristic(
                SonyGattSpec.LOCATION_SERVICE_UUID,
                SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
            )

        val dd21Char =
            createMockCharacteristic(
                SonyGattSpec.LOCATION_SERVICE_UUID,
                SonyGattSpec.LOCATION_CONFIG_READ_CHARACTERISTIC_UUID,
            )

        val dd01Char =
            createMockCharacteristic(
                SonyGattSpec.LOCATION_SERVICE_UUID,
                SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID,
            )

        val locationChars = mutableListOf(dd11Char, dd21Char, dd01Char)

        if (hasDD30DD31) {
            locationChars +=
                createMockCharacteristic(
                    SonyGattSpec.LOCATION_SERVICE_UUID,
                    SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
                )
            locationChars +=
                createMockCharacteristic(
                    SonyGattSpec.LOCATION_SERVICE_UUID,
                    SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
                )
        }

        val locationService =
            mockk<DiscoveredService> {
                every { serviceUuid } returns SonyGattSpec.LOCATION_SERVICE_UUID
                every { characteristics } returns locationChars
            }

        val cc13Char =
            createMockCharacteristic(
                SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
                SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID,
            )

        val cameraControlService =
            mockk<DiscoveredService> {
                every { serviceUuid } returns SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID
                every { characteristics } returns listOf(cc13Char)
            }

        val services = listOf(locationService, cameraControlService)

        val peripheral =
            mockk<Peripheral> {
                every { this@mockk.services } returns MutableStateFlow(services)
                if (writeRecorder != null) {
                    coEvery { write(any<Characteristic>(), any(), any()) } answers
                        {
                            writeRecorder.record(firstArg(), secondArg())
                        }
                } else {
                    coEvery { write(any<Characteristic>(), any(), any()) } returns Unit
                }
                coEvery { read(any<Characteristic>()) } returns
                    (configData ?: byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x02, 0x00))
                every { observe(any<Characteristic>(), any()) } returns emptyFlow()
            }

        return peripheral to camera
    }

    private fun createMockCharacteristic(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): DiscoveredCharacteristic =
        mockk(relaxed = true) {
            every { this@mockk.serviceUuid } returns serviceUuid
            every { characteristicUuid } returns charUuid
        }
}
