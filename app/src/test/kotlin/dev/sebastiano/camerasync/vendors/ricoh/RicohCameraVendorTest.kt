package dev.sebastiano.camerasync.vendors.ricoh

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class RicohCameraVendorTest {

    @Test
    fun `vendorId is ricoh`() {
        assertEquals("ricoh", RicohCameraVendor.vendorId)
    }

    @Test
    fun `vendorName is Ricoh`() {
        assertEquals("Ricoh", RicohCameraVendor.vendorName)
    }

    @Test
    fun `recognizes device with Ricoh service UUID`() {
        val serviceUuids = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID)
        assertTrue(RicohCameraVendor.recognizesDevice("GR IIIx", serviceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device with Ricoh service UUID regardless of device name`() {
        val serviceUuids = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID)
        assertTrue(RicohCameraVendor.recognizesDevice(null, serviceUuids, emptyMap()))
        assertTrue(RicohCameraVendor.recognizesDevice("", serviceUuids, emptyMap()))
        assertTrue(RicohCameraVendor.recognizesDevice("Unknown Camera", serviceUuids, emptyMap()))
    }

    @Test
    fun `recognizes device by GR name prefix even without service UUID`() {
        assertTrue(RicohCameraVendor.recognizesDevice("GR IIIx", emptyList(), emptyMap()))
        assertTrue(RicohCameraVendor.recognizesDevice("GR III", emptyList(), emptyMap()))
        assertTrue(RicohCameraVendor.recognizesDevice("GR II", emptyList(), emptyMap()))
        assertTrue(
            RicohCameraVendor.recognizesDevice("gr iiix", emptyList(), emptyMap())
        ) // case-insensitive
    }

    @Test
    fun `recognizes device by RICOH name prefix even without service UUID`() {
        assertTrue(RicohCameraVendor.recognizesDevice("RICOH GR IIIx", emptyList(), emptyMap()))
        assertTrue(RicohCameraVendor.recognizesDevice("RICOH Camera", emptyList(), emptyMap()))
        assertTrue(
            RicohCameraVendor.recognizesDevice("ricoh test", emptyList(), emptyMap())
        ) // case-insensitive
    }

    @Test
    fun `does not recognize device without service UUID or recognized name`() {
        assertFalse(RicohCameraVendor.recognizesDevice("Unknown Camera", emptyList(), emptyMap()))
        assertFalse(RicohCameraVendor.recognizesDevice("Sony Camera", emptyList(), emptyMap()))
        assertFalse(RicohCameraVendor.recognizesDevice(null, emptyList(), emptyMap()))
    }

    @Test
    fun `recognizes device with non-Ricoh service UUID and Ricoh name`() {
        val otherServiceUuids = listOf(Uuid.parse("00001800-0000-1000-8000-00805f9b34fb"))
        assertTrue(RicohCameraVendor.recognizesDevice("GR IIIx", otherServiceUuids, emptyMap()))
    }

    @Test
    fun `capabilities indicate firmware version support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsFirmwareVersion)
    }

    @Test
    fun `capabilities indicate device name support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsDeviceName)
    }

    @Test
    fun `capabilities indicate date time sync support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsDateTimeSync)
    }

    @Test
    fun `capabilities indicate geo tagging support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsGeoTagging)
    }

    @Test
    fun `capabilities indicate location sync support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsLocationSync)
    }

    @Test
    fun `capabilities indicate hardware revision support`() {
        assertTrue(RicohCameraVendor.getCapabilities().supportsHardwareRevision)
    }

    @Test
    fun `gattSpec is RicohGattSpec`() {
        assertEquals(RicohGattSpec, RicohCameraVendor.gattSpec)
    }

    @Test
    fun `protocol is RicohProtocol`() {
        assertEquals(RicohProtocol, RicohCameraVendor.protocol)
    }

    // Model extraction tests

    @Test
    fun `extractModelFromPairingName extracts GR IIIx`() {
        assertEquals("GR IIIx", RicohCameraVendor.extractModelFromPairingName("GR IIIx"))
        assertEquals(
            "GR IIIx",
            RicohCameraVendor.extractModelFromPairingName("gr iiix"),
        ) // case-insensitive
        assertEquals(
            "GR IIIx",
            RicohCameraVendor.extractModelFromPairingName("RICOH GR IIIx Camera"),
        )
        assertEquals(
            "GR IIIx",
            RicohCameraVendor.extractModelFromPairingName("  GR IIIx  "),
        ) // with whitespace
    }

    @Test
    fun `extractModelFromPairingName extracts GR III but not GR IIIx`() {
        assertEquals("GR III", RicohCameraVendor.extractModelFromPairingName("GR III"))
        assertEquals(
            "GR III",
            RicohCameraVendor.extractModelFromPairingName("gr iii"),
        ) // case-insensitive
        assertEquals("GR III", RicohCameraVendor.extractModelFromPairingName("RICOH GR III"))
    }

    @Test
    fun `extractModelFromPairingName extracts other GR models`() {
        assertEquals("GR 2", RicohCameraVendor.extractModelFromPairingName("GR 2"))
        assertEquals("GR 1", RicohCameraVendor.extractModelFromPairingName("GR 1"))
        assertEquals("GR 10", RicohCameraVendor.extractModelFromPairingName("GR 10"))
        assertEquals("GR 3x", RicohCameraVendor.extractModelFromPairingName("GR 3x"))
    }

    @Test
    fun `extractModelFromPairingName returns pairing name for GR or RICOH prefix`() {
        assertEquals(
            "GR Custom Model",
            RicohCameraVendor.extractModelFromPairingName("GR Custom Model"),
        )
        assertEquals("RICOH Test", RicohCameraVendor.extractModelFromPairingName("RICOH Test"))
    }

    @Test
    fun `extractModelFromPairingName returns name as-is for non-Ricoh names`() {
        assertEquals("Sony Camera", RicohCameraVendor.extractModelFromPairingName("Sony Camera"))
        assertEquals(
            "Unknown Device",
            RicohCameraVendor.extractModelFromPairingName("Unknown Device"),
        )
    }

    @Test
    fun `extractModelFromPairingName returns Unknown for null`() {
        assertEquals("Unknown", RicohCameraVendor.extractModelFromPairingName(null))
    }

    @Test
    fun `extractModelFromPairingName returns empty string for empty input`() {
        // The implementation returns the trimmed name as-is when it doesn't match any pattern
        // For empty strings, this results in an empty string being returned
        assertEquals("", RicohCameraVendor.extractModelFromPairingName(""))
        assertEquals("", RicohCameraVendor.extractModelFromPairingName("   "))
    }
}
