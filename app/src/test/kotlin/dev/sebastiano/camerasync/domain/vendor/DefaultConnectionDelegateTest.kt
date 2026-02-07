package dev.sebastiano.camerasync.domain.vendor

import com.juul.kable.Characteristic
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class DefaultConnectionDelegateTest {

    private val delegate = DefaultConnectionDelegate()

    private val locationServiceUuid = Uuid.parse("00001000-0000-1000-8000-00805f9b34fb")
    private val locationCharUuid = Uuid.parse("00001001-0000-1000-8000-00805f9b34fb")
    private val dateTimeServiceUuid = Uuid.parse("00002000-0000-1000-8000-00805f9b34fb")
    private val dateTimeCharUuid = Uuid.parse("00002001-0000-1000-8000-00805f9b34fb")

    private val locationData = byteArrayOf(0x01, 0x02, 0x03)
    private val dateTimeData = byteArrayOf(0x04, 0x05, 0x06)

    private val gattSpec =
        mockk<CameraGattSpec> {
            every { locationServiceUuid } returns
                this@DefaultConnectionDelegateTest.locationServiceUuid
            every { locationCharacteristicUuid } returns locationCharUuid
            every { dateTimeServiceUuid } returns
                this@DefaultConnectionDelegateTest.dateTimeServiceUuid
            every { dateTimeCharacteristicUuid } returns dateTimeCharUuid
        }

    private val protocol =
        mockk<CameraProtocol> {
            every { encodeLocation(any()) } returns locationData
            every { encodeDateTime(any()) } returns dateTimeData
        }

    private val vendor =
        mockk<CameraVendor> {
            every { this@mockk.gattSpec } returns this@DefaultConnectionDelegateTest.gattSpec
            every { this@mockk.protocol } returns this@DefaultConnectionDelegateTest.protocol
            every { vendorName } returns "TestVendor"
        }

    private val camera =
        Camera(
            identifier = "test-id",
            name = "Test Camera",
            macAddress = "AA:BB:CC:DD:EE:FF",
            vendor = vendor,
        )

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
    fun `default MTU is null`() {
        assertNull(delegate.mtu)
    }

    // ==================== syncLocation Tests ====================

    @Test
    fun `syncLocation writes encoded data to correct characteristic`() = runTest {
        val dataSlot = slot<ByteArray>()
        val charSlot = slot<Characteristic>()
        val (peripheral, _) = createMockPeripheral()
        coEvery { peripheral.write(capture(charSlot), capture(dataSlot), any()) } returns Unit

        delegate.syncLocation(peripheral, camera, location)

        assertArrayEquals(locationData, dataSlot.captured)
        assertEquals(locationCharUuid, charSlot.captured.characteristicUuid)
    }

    @Test
    fun `syncLocation uses WriteType WithResponse`() = runTest {
        val writeTypeSlot = slot<WriteType>()
        val (peripheral, _) = createMockPeripheral()
        coEvery { peripheral.write(any<Characteristic>(), any(), capture(writeTypeSlot)) } returns
            Unit

        delegate.syncLocation(peripheral, camera, location)

        assertEquals(WriteType.WithResponse, writeTypeSlot.captured)
    }

    @Test
    fun `syncLocation uses vendor protocol to encode location`() = runTest {
        val (peripheral, _) = createMockPeripheral()

        delegate.syncLocation(peripheral, camera, location)

        coVerify { protocol.encodeLocation(location) }
    }

    @Test(expected = IllegalStateException::class)
    fun `syncLocation throws when location service UUID is null`() = runTest {
        every { gattSpec.locationServiceUuid } returns null
        val (peripheral, _) = createMockPeripheral()

        delegate.syncLocation(peripheral, camera, location)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncLocation throws when location characteristic UUID is null`() = runTest {
        every { gattSpec.locationCharacteristicUuid } returns null
        val (peripheral, _) = createMockPeripheral()

        delegate.syncLocation(peripheral, camera, location)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncLocation throws when location service not found on peripheral`() = runTest {
        val (peripheral, _) = createMockPeripheral(includeLocationService = false)

        delegate.syncLocation(peripheral, camera, location)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncLocation throws when location characteristic not found on service`() = runTest {
        val (peripheral, _) = createMockPeripheral(includeLocationCharacteristic = false)

        delegate.syncLocation(peripheral, camera, location)
    }

    // ==================== syncDateTime Tests ====================

    @Test
    fun `syncDateTime writes encoded data to correct characteristic`() = runTest {
        val dataSlot = slot<ByteArray>()
        val charSlot = slot<Characteristic>()
        val (peripheral, _) = createMockPeripheral()
        coEvery { peripheral.write(capture(charSlot), capture(dataSlot), any()) } returns Unit

        delegate.syncDateTime(peripheral, camera, dateTime)

        assertArrayEquals(dateTimeData, dataSlot.captured)
        assertEquals(dateTimeCharUuid, charSlot.captured.characteristicUuid)
    }

    @Test
    fun `syncDateTime uses WriteType WithResponse`() = runTest {
        val writeTypeSlot = slot<WriteType>()
        val (peripheral, _) = createMockPeripheral()
        coEvery { peripheral.write(any<Characteristic>(), any(), capture(writeTypeSlot)) } returns
            Unit

        delegate.syncDateTime(peripheral, camera, dateTime)

        assertEquals(WriteType.WithResponse, writeTypeSlot.captured)
    }

    @Test
    fun `syncDateTime uses vendor protocol to encode dateTime`() = runTest {
        val (peripheral, _) = createMockPeripheral()

        delegate.syncDateTime(peripheral, camera, dateTime)

        coVerify { protocol.encodeDateTime(dateTime) }
    }

    @Test(expected = IllegalStateException::class)
    fun `syncDateTime throws when dateTime service UUID is null`() = runTest {
        every { gattSpec.dateTimeServiceUuid } returns null
        val (peripheral, _) = createMockPeripheral()

        delegate.syncDateTime(peripheral, camera, dateTime)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncDateTime throws when dateTime characteristic UUID is null`() = runTest {
        every { gattSpec.dateTimeCharacteristicUuid } returns null
        val (peripheral, _) = createMockPeripheral()

        delegate.syncDateTime(peripheral, camera, dateTime)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncDateTime throws when dateTime service not found on peripheral`() = runTest {
        val (peripheral, _) = createMockPeripheral(includeDateTimeService = false)

        delegate.syncDateTime(peripheral, camera, dateTime)
    }

    @Test(expected = IllegalStateException::class)
    fun `syncDateTime throws when dateTime characteristic not found on service`() = runTest {
        val (peripheral, _) = createMockPeripheral(includeDateTimeCharacteristic = false)

        delegate.syncDateTime(peripheral, camera, dateTime)
    }

    // ==================== Helpers ====================

    private fun createMockPeripheral(
        includeLocationService: Boolean = true,
        includeLocationCharacteristic: Boolean = true,
        includeDateTimeService: Boolean = true,
        includeDateTimeCharacteristic: Boolean = true,
    ): Pair<Peripheral, List<DiscoveredService>> {
        val locationChar =
            mockk<DiscoveredCharacteristic>(relaxed = true) {
                every { characteristicUuid } returns locationCharUuid
                every { serviceUuid } returns locationServiceUuid
            }

        val dateTimeChar =
            mockk<DiscoveredCharacteristic>(relaxed = true) {
                every { characteristicUuid } returns dateTimeCharUuid
                every { serviceUuid } returns dateTimeServiceUuid
            }

        val locationService =
            mockk<DiscoveredService> {
                every { serviceUuid } returns locationServiceUuid
                every { characteristics } returns
                    if (includeLocationCharacteristic) listOf(locationChar) else emptyList()
            }

        val dateTimeService =
            mockk<DiscoveredService> {
                every { serviceUuid } returns dateTimeServiceUuid
                every { characteristics } returns
                    if (includeDateTimeCharacteristic) listOf(dateTimeChar) else emptyList()
            }

        val services = buildList {
            if (includeLocationService) add(locationService)
            if (includeDateTimeService) add(dateTimeService)
        }

        val peripheral =
            mockk<Peripheral> {
                every { this@mockk.services } returns MutableStateFlow(services)
                coEvery { write(any<Characteristic>(), any(), any()) } returns Unit
            }

        return peripheral to services
    }
}
