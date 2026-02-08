package dev.sebastiano.camerasync.data.repository

import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.State
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.CameraCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class KableCameraConnectionTest {

    @Test
    fun `readModelName throws when vendor does not support model names`() = runTest {
        val vendor = TestVendor(capabilities = CameraCapabilities())
        val connection = createConnection(vendor = vendor)

        val error =
            try {
                connection.readModelName()
                null
            } catch (e: UnsupportedOperationException) {
                e
            }

        assertEquals(
            "${vendor.vendorName} cameras do not support model name reading",
            error?.message,
        )
    }

    @Test
    fun `readModelName throws when UUIDs are missing`() = runTest {
        val vendor =
            TestVendor(
                capabilities = CameraCapabilities(supportsModelName = true),
                gattSpec =
                    TestGattSpec(modelNameServiceUuid = null, modelNameCharacteristicUuid = null),
            )
        val connection = createConnection(vendor = vendor)

        val error =
            try {
                connection.readModelName()
                null
            } catch (e: UnsupportedOperationException) {
                e
            }

        assertEquals(
            "${vendor.vendorName} cameras claims to support model name reading but UUIDs are not configured",
            error?.message,
        )
    }

    @Test
    fun `readModelName reads and trims value`() = runTest {
        val modelServiceUuid = Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb")
        val modelCharUuid = Uuid.parse("00002a24-0000-1000-8000-00805f9b34fb")
        val vendor =
            TestVendor(
                capabilities = CameraCapabilities(supportsModelName = true),
                gattSpec = TestGattSpec(modelServiceUuid, modelCharUuid),
            )

        val characteristic =
            mockk<DiscoveredCharacteristic> {
                every { characteristicUuid } returns modelCharUuid
                every { serviceUuid } returns modelServiceUuid
            }

        val service =
            mockk<DiscoveredService> {
                every { serviceUuid } returns modelServiceUuid
                every { characteristics } returns listOf(characteristic)
            }

        val peripheral = mockk<Peripheral>()
        every { peripheral.state } returns MutableStateFlow(mockk<State>(relaxed = true))
        every { peripheral.services } returns
            MutableStateFlow<List<DiscoveredService>?>(listOf(service))
        coEvery { peripheral.read(characteristic) } returns "ILCE-7M4\u0000".encodeToByteArray()

        val connection =
            KableCameraConnection(
                camera = Camera("id", "Test", "AA:BB", vendor),
                peripheral = peripheral,
                connectionDelegate = DefaultConnectionDelegate(),
            )

        assertEquals("ILCE-7M4", connection.readModelName())
    }

    @OptIn(ExperimentalUuidApi::class)
    private class TestVendor(
        private val capabilities: CameraCapabilities,
        override val gattSpec: CameraGattSpec = TestGattSpec(),
    ) : CameraVendor {
        override val vendorId: String = "test"
        override val vendorName: String = "Test"
        override val protocol: CameraProtocol = TestProtocol()

        override fun recognizesDevice(
            deviceName: String?,
            serviceUuids: List<Uuid>,
            manufacturerData: Map<Int, ByteArray>,
        ): Boolean = false

        override fun createConnectionDelegate(): VendorConnectionDelegate =
            DefaultConnectionDelegate()

        override fun getCapabilities(): CameraCapabilities = capabilities

        override fun extractModelFromPairingName(pairingName: String?): String =
            pairingName ?: "Unknown"
    }

    private class TestProtocol : CameraProtocol {
        override fun encodeDateTime(dateTime: java.time.ZonedDateTime): ByteArray = byteArrayOf()

        override fun decodeDateTime(bytes: ByteArray): String = ""

        override fun encodeLocation(
            location: dev.sebastiano.camerasync.domain.model.GpsLocation
        ): ByteArray = byteArrayOf()

        override fun decodeLocation(bytes: ByteArray): String = ""

        override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray = byteArrayOf()

        override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean = false
    }

    @OptIn(ExperimentalUuidApi::class)
    private class TestGattSpec(
        override val modelNameServiceUuid: Uuid? = null,
        override val modelNameCharacteristicUuid: Uuid? = null,
    ) : CameraGattSpec {
        private val fakeUuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        override val scanFilterServiceUuids: List<Uuid> = listOf(fakeUuid)
        override val firmwareServiceUuid: Uuid = fakeUuid
        override val firmwareVersionCharacteristicUuid: Uuid = fakeUuid
        override val deviceNameServiceUuid: Uuid? = fakeUuid
        override val deviceNameCharacteristicUuid: Uuid? = fakeUuid
        override val dateTimeServiceUuid: Uuid = fakeUuid
        override val dateTimeCharacteristicUuid: Uuid = fakeUuid
        override val geoTaggingCharacteristicUuid: Uuid? = fakeUuid
        override val locationServiceUuid: Uuid = fakeUuid
        override val locationCharacteristicUuid: Uuid = fakeUuid
    }

    private fun createConnection(vendor: CameraVendor): KableCameraConnection {
        val peripheral = mockk<Peripheral>()
        every { peripheral.state } returns MutableStateFlow(mockk<State>(relaxed = true))
        every { peripheral.services } returns
            MutableStateFlow<List<DiscoveredService>?>(emptyList())
        return KableCameraConnection(
            camera = Camera("id", "Test", "AA:BB", vendor),
            peripheral = peripheral,
            connectionDelegate = DefaultConnectionDelegate(),
        )
    }
}
