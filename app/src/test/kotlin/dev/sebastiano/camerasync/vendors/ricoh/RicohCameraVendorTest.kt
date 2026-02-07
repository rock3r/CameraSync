package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `recognizes device with Ricoh manufacturer data`() {
        val manufacturerData =
            mapOf(RicohGattSpec.RICOH_MANUFACTURER_ID to byteArrayOf(0xDA.toByte()))
        assertTrue(RicohCameraVendor.recognizesDevice("GR IIIx", emptyList(), manufacturerData))
    }

    @Test
    fun `recognizes device with Ricoh manufacturer data regardless of device name`() {
        val manufacturerData =
            mapOf(RicohGattSpec.RICOH_MANUFACTURER_ID to byteArrayOf(0xDA.toByte()))
        assertTrue(RicohCameraVendor.recognizesDevice(null, emptyList(), manufacturerData))
        assertTrue(RicohCameraVendor.recognizesDevice("", emptyList(), manufacturerData))
        assertTrue(
            RicohCameraVendor.recognizesDevice("Unknown Camera", emptyList(), manufacturerData)
        )
    }

    @Test
    fun `parseAdvertisementMetadata extracts model code, serial, and power`() {
        val payload =
            byteArrayOf(
                0xDA.toByte(),
                0x01,
                0x01,
                0x03, // model code
                0x02,
                0x04,
                0x01,
                0x02,
                0x03,
                0x04, // serial
                0x03,
                0x01,
                0x01, // power on
            )
        val metadata =
            RicohCameraVendor.parseAdvertisementMetadata(
                mapOf(RicohGattSpec.RICOH_MANUFACTURER_ID to payload)
            )
        assertEquals(3, metadata["modelCode"])
        assertEquals("01020304", metadata["serial"])
        assertEquals(1, metadata["cameraPower"])
    }

    @Test
    fun `parseAdvertisementMetadata handles unsigned bytes correctly`() {
        // Serial bytes above 0x7F must not be sign-extended when formatting hex
        val payload =
            byteArrayOf(
                0xDA.toByte(),
                0x01,
                0x01,
                0xAB.toByte(), // model code (high byte)
                0x02,
                0x04,
                0xDE.toByte(),
                0xAD.toByte(),
                0xBE.toByte(),
                0xEF.toByte(), // serial (high bytes)
                0x03,
                0x01,
                0xFF.toByte(), // power (high byte)
            )
        val metadata =
            RicohCameraVendor.parseAdvertisementMetadata(
                mapOf(RicohGattSpec.RICOH_MANUFACTURER_ID to payload)
            )
        assertEquals(0xAB, metadata["modelCode"])
        assertEquals("deadbeef", metadata["serial"])
        assertEquals(0xFF, metadata["cameraPower"])
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
    fun `recognizes device by Ricoh service UUID even without name or manufacturer data`() {
        val serviceUuids = listOf(RicohGattSpec.Firmware.SERVICE_UUID)
        assertTrue(RicohCameraVendor.recognizesDevice(null, serviceUuids, emptyMap()))
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
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsFirmwareVersion)
    }

    @Test
    fun `capabilities indicate device name support`() {
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsDeviceName)
    }

    @Test
    fun `capabilities indicate date time sync support`() {
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsDateTimeSync)
    }

    @Test
    fun `capabilities indicate geo tagging support`() {
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsGeoTagging)
    }

    @Test
    fun `capabilities indicate location sync support`() {
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsLocationSync)
    }

    @Test
    fun `capabilities indicate hardware revision support`() {
        assertTrue(RicohCameraVendor.getSyncCapabilities().supportsHardwareRevision)
    }

    @Test
    fun `gattSpec is RicohGattSpec`() {
        assertEquals(RicohGattSpec, RicohCameraVendor.gattSpec)
    }

    @Test
    fun `protocol is RicohProtocol`() {
        assertEquals(RicohProtocol, RicohCameraVendor.protocol)
    }

    // Connection delegate tests

    @Test
    fun `createConnectionDelegate returns DefaultConnectionDelegate`() {
        val delegate = RicohCameraVendor.createConnectionDelegate()
        assertTrue(
            "Ricoh should use DefaultConnectionDelegate, got ${delegate::class.simpleName}",
            delegate is DefaultConnectionDelegate,
        )
    }

    @Test
    fun `createConnectionDelegate returns a new instance each time`() {
        val delegate1 = RicohCameraVendor.createConnectionDelegate()
        val delegate2 = RicohCameraVendor.createConnectionDelegate()
        assertTrue("Each call should return a new instance", delegate1 !== delegate2)
    }

    @Test
    fun `createConnectionDelegate returns delegate with null MTU`() {
        val delegate = RicohCameraVendor.createConnectionDelegate()
        assertNull("Ricoh delegate should not request a specific MTU", delegate.mtu)
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
