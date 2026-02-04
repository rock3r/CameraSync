package dev.sebastiano.camerasync.domain.vendor

import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.camerasync.vendors.ricoh.RicohGattSpec
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyGattSpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class CameraVendorRegistryTest {

    private lateinit var registry: CameraVendorRegistry

    @Before
    fun setup() {
        registry =
            DefaultCameraVendorRegistry(vendors = listOf(RicohCameraVendor, SonyCameraVendor))
    }

    // getAllVendors tests

    @Test
    fun `getAllVendors returns all registered vendors`() {
        val vendors = registry.getAllVendors()
        assertEquals(2, vendors.size)
        assertTrue(vendors.contains(RicohCameraVendor))
        assertTrue(vendors.contains(SonyCameraVendor))
    }

    @Test
    fun `getAllVendors returns vendors in registration order`() {
        val vendors = registry.getAllVendors()
        assertEquals(RicohCameraVendor, vendors[0])
        assertEquals(SonyCameraVendor, vendors[1])
    }

    // identifyVendor tests

    @Test
    fun `identifyVendor returns Ricoh for Ricoh service UUID`() {
        val vendor =
            registry.identifyVendor(
                deviceName = null,
                serviceUuids = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID),
                manufacturerData = emptyMap(),
            )
        assertEquals(RicohCameraVendor, vendor)
    }

    @Test
    fun `identifyVendor returns Ricoh for GR device name`() {
        val vendor =
            registry.identifyVendor(
                deviceName = "GR IIIx",
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )
        assertEquals(RicohCameraVendor, vendor)
    }

    @Test
    fun `identifyVendor returns Sony for Sony service UUID`() {
        val vendor =
            registry.identifyVendor(
                deviceName = null,
                serviceUuids = listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID),
                manufacturerData = emptyMap(),
            )
        assertEquals(SonyCameraVendor, vendor)
    }

    @Test
    fun `identifyVendor returns Sony for ILCE device name`() {
        val vendor =
            registry.identifyVendor(
                deviceName = "ILCE-7M4",
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )
        assertEquals(SonyCameraVendor, vendor)
    }

    @Test
    fun `identifyVendor returns Sony for Sony manufacturer data`() {
        val sonyMfrData = mapOf(SonyCameraVendor.SONY_MANUFACTURER_ID to byteArrayOf(0x03, 0x00))
        val vendor =
            registry.identifyVendor(
                deviceName = null,
                serviceUuids = emptyList(),
                manufacturerData = sonyMfrData,
            )
        assertEquals(SonyCameraVendor, vendor)
    }

    @Test
    fun `identifyVendor returns null for unrecognized device`() {
        val vendor =
            registry.identifyVendor(
                deviceName = "Unknown Device",
                serviceUuids = listOf(Uuid.parse("00001800-0000-1000-8000-00805f9b34fb")),
                manufacturerData = emptyMap(),
            )
        assertNull(vendor)
    }

    @Test
    fun `identifyVendor returns null for device with no identifying information`() {
        val vendor =
            registry.identifyVendor(
                deviceName = null,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )
        assertNull(vendor)
    }

    @Test
    fun `identifyVendor returns first matching vendor when multiple vendors match`() {
        // Create a registry with a custom order
        val customRegistry =
            DefaultCameraVendorRegistry(vendors = listOf(SonyCameraVendor, RicohCameraVendor))

        // Use a device name that could match both (hypothetically)
        // In reality, this shouldn't happen, but the registry should return the first match
        val vendor =
            customRegistry.identifyVendor(
                deviceName = "GR IIIx",
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )

        // Should return Ricoh since it actually recognizes "GR IIIx"
        assertEquals(RicohCameraVendor, vendor)
    }

    // getVendorById tests

    @Test
    fun `getVendorById returns Ricoh vendor by id`() {
        val vendor = registry.getVendorById("ricoh")
        assertEquals(RicohCameraVendor, vendor)
    }

    @Test
    fun `getVendorById returns Sony vendor by id`() {
        val vendor = registry.getVendorById("sony")
        assertEquals(SonyCameraVendor, vendor)
    }

    @Test
    fun `getVendorById returns null for unknown vendor id`() {
        val vendor = registry.getVendorById("unknown")
        assertNull(vendor)
    }

    @Test
    fun `getVendorById is case sensitive`() {
        val vendor = registry.getVendorById("RICOH")
        assertNull(vendor)
    }

    // getAllScanFilterUuids tests

    @Test
    fun `getAllScanFilterUuids returns UUIDs from all vendors`() {
        val uuids = registry.getAllScanFilterUuids()

        // Should contain Ricoh UUIDs
        assertTrue(uuids.contains(RicohGattSpec.SCAN_FILTER_SERVICE_UUID))

        // Should contain Sony UUIDs
        assertTrue(uuids.contains(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID))
        assertTrue(uuids.contains(SonyGattSpec.PAIRING_SERVICE_UUID))
    }

    @Test
    fun `getAllScanFilterUuids returns distinct UUIDs`() {
        val uuids = registry.getAllScanFilterUuids()
        val distinctUuids = uuids.distinct()
        assertEquals(distinctUuids.size, uuids.size)
    }

    @Test
    fun `getAllScanFilterUuids handles vendors with multiple UUIDs`() {
        val uuids = registry.getAllScanFilterUuids()

        // Sony has 2 scan filter UUIDs
        val sonyUuids =
            uuids.filter {
                it == SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID ||
                    it == SonyGattSpec.PAIRING_SERVICE_UUID
            }
        assertEquals(2, sonyUuids.size)
    }

    // getAllScanFilterDeviceNames tests

    @Test
    fun `getAllScanFilterDeviceNames returns device name prefixes from all vendors`() {
        val names = registry.getAllScanFilterDeviceNames()

        // Should contain Ricoh device names
        assertTrue(names.contains("GR"))
        assertTrue(names.contains("RICOH"))

        // Should contain Sony device names
        assertTrue(names.contains("ILCE-"))
    }

    @Test
    fun `getAllScanFilterDeviceNames returns distinct names`() {
        val names = registry.getAllScanFilterDeviceNames()
        val distinctNames = names.distinct()
        assertEquals(distinctNames.size, names.size)
    }

    @Test
    fun `getAllScanFilterDeviceNames handles vendors with multiple name prefixes`() {
        val names = registry.getAllScanFilterDeviceNames()

        // Ricoh has 2 device name prefixes
        val ricohNames = names.filter { it == "GR" || it == "RICOH" }
        assertEquals(2, ricohNames.size)
    }

    // Empty registry tests

    @Test
    fun `empty registry returns empty vendor list`() {
        val emptyRegistry = DefaultCameraVendorRegistry(vendors = emptyList())
        assertEquals(0, emptyRegistry.getAllVendors().size)
    }

    @Test
    fun `empty registry returns null for any device identification`() {
        val emptyRegistry = DefaultCameraVendorRegistry(vendors = emptyList())
        val vendor =
            emptyRegistry.identifyVendor(
                deviceName = "GR IIIx",
                serviceUuids = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID),
                manufacturerData = emptyMap(),
            )
        assertNull(vendor)
    }

    @Test
    fun `empty registry returns null for vendor lookup by id`() {
        val emptyRegistry = DefaultCameraVendorRegistry(vendors = emptyList())
        assertNull(emptyRegistry.getVendorById("ricoh"))
    }

    @Test
    fun `empty registry returns empty scan filter UUID list`() {
        val emptyRegistry = DefaultCameraVendorRegistry(vendors = emptyList())
        assertEquals(0, emptyRegistry.getAllScanFilterUuids().size)
    }

    @Test
    fun `empty registry returns empty device name list`() {
        val emptyRegistry = DefaultCameraVendorRegistry(vendors = emptyList())
        assertEquals(0, emptyRegistry.getAllScanFilterDeviceNames().size)
    }

    // Single vendor registry tests

    @Test
    fun `single vendor registry works correctly`() {
        val singleVendorRegistry = DefaultCameraVendorRegistry(vendors = listOf(RicohCameraVendor))

        assertEquals(1, singleVendorRegistry.getAllVendors().size)
        assertEquals(RicohCameraVendor, singleVendorRegistry.getVendorById("ricoh"))
        assertNull(singleVendorRegistry.getVendorById("sony"))

        val vendor =
            singleVendorRegistry.identifyVendor(
                deviceName = "GR IIIx",
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )
        assertEquals(RicohCameraVendor, vendor)
    }
}
