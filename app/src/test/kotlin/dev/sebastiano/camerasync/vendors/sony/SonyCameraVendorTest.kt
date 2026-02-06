package dev.sebastiano.camerasync.vendors.sony

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class SonyCameraVendorTest {

    @Test
    fun `vendorId is sony`() {
        assertEquals("sony", SonyCameraVendor.vendorId)
    }

    @Test
    fun `vendorName is Sony`() {
        assertEquals("Sony", SonyCameraVendor.vendorName)
    }

    @Test
    fun `recognizes device with remote control service UUID`() {
        val serviceUuids = listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID)
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", serviceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device with pairing service UUID`() {
        val serviceUuids = listOf(SonyGattSpec.PAIRING_SERVICE_UUID)
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", serviceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device regardless of device name`() {
        val serviceUuids = listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID)
        assertTrue(SonyCameraVendor.recognizesDevice(null, serviceUuids, emptyMap()))
        assertTrue(SonyCameraVendor.recognizesDevice("", serviceUuids, emptyMap()))
        assertTrue(SonyCameraVendor.recognizesDevice("Unknown Camera", serviceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device by name pattern even with non-Sony service UUIDs`() {
        // Sony cameras with ILCE- prefix should be recognized even if other service UUIDs are
        // advertised
        val otherServiceUuids = listOf(Uuid.parse("00001800-0000-1000-8000-00805f9b34fb"))
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", otherServiceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device by name pattern even without service UUID`() {
        // Sony cameras with ILCE- prefix should be recognized even if service UUID isn't advertised
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", emptyList(), emptyMap()))
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M3", emptyList(), emptyMap()))
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-9", emptyList(), emptyMap()))
    }

    @Test
    fun `does not recognize device without service UUID or recognized name`() {
        assertFalse(SonyCameraVendor.recognizesDevice("Unknown Camera", emptyList(), emptyMap()))
        assertFalse(SonyCameraVendor.recognizesDevice(null, emptyList(), emptyMap()))
    }

    @Test
    fun `recognizes device by Sony manufacturer data`() {
        // Sony manufacturer ID 0x012D with camera device type 0x0003 (little-endian)
        val sonyMfrData = mapOf(SonyCameraVendor.SONY_MANUFACTURER_ID to byteArrayOf(0x03, 0x00))
        assertTrue(SonyCameraVendor.recognizesDevice(null, emptyList(), sonyMfrData))
        assertTrue(SonyCameraVendor.recognizesDevice("Unknown", emptyList(), sonyMfrData))
    }

    @Test
    fun `does not recognize device with Sony manufacturer data but wrong device type`() {
        // Sony manufacturer ID but not camera device type
        val sonyMfrData = mapOf(SonyCameraVendor.SONY_MANUFACTURER_ID to byteArrayOf(0x01, 0x00))
        assertFalse(SonyCameraVendor.recognizesDevice(null, emptyList(), sonyMfrData))
    }

    @Test
    fun `does not recognize device with non-Sony manufacturer data`() {
        // Different manufacturer ID
        val otherMfrData = mapOf(0x004C to byteArrayOf(0x03, 0x00)) // Apple's manufacturer ID
        assertFalse(SonyCameraVendor.recognizesDevice(null, emptyList(), otherMfrData))
    }

    @Test
    fun `capabilities indicate firmware version support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsFirmwareVersion)
    }

    @Test
    fun `capabilities indicate no device name support`() {
        assertFalse(SonyCameraVendor.getCapabilities().supportsDeviceName)
    }

    @Test
    fun `capabilities indicate date time sync support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsDateTimeSync)
    }

    @Test
    fun `capabilities indicate no geo tagging support`() {
        assertFalse(SonyCameraVendor.getCapabilities().supportsGeoTagging)
    }

    @Test
    fun `capabilities indicate location sync support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsLocationSync)
    }

    @Test
    fun `gattSpec is SonyGattSpec`() {
        assertEquals(SonyGattSpec, SonyCameraVendor.gattSpec)
    }

    @Test
    fun `protocol is SonyProtocol`() {
        assertEquals(SonyProtocol, SonyCameraVendor.protocol)
    }

    // Connection delegate tests

    @Test
    fun `createConnectionDelegate returns SonyConnectionDelegate`() {
        val delegate = SonyCameraVendor.createConnectionDelegate()
        assertTrue(
            "Sony should use SonyConnectionDelegate, got ${delegate::class.simpleName}",
            delegate is SonyConnectionDelegate,
        )
    }

    @Test
    fun `createConnectionDelegate returns a new instance each time`() {
        val delegate1 = SonyCameraVendor.createConnectionDelegate()
        val delegate2 = SonyCameraVendor.createConnectionDelegate()
        assertTrue("Each call should return a new instance", delegate1 !== delegate2)
    }

    @Test
    fun `createConnectionDelegate returns delegate with Sony MTU`() {
        val delegate = SonyCameraVendor.createConnectionDelegate()
        assertEquals("Sony delegate should request MTU of 158", 158, delegate.mtu)
    }
}
