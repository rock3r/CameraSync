package dev.sebastiano.camerasync.domain.vendor

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Registry for managing available camera vendors.
 *
 * This registry maintains a list of supported camera vendors and provides methods to identify which
 * vendor a discovered BLE device belongs to.
 */
@OptIn(ExperimentalUuidApi::class)
interface CameraVendorRegistry {

    /** Returns all registered camera vendors. */
    fun getAllVendors(): List<CameraVendor>

    /**
     * Identifies the vendor for a discovered BLE device.
     *
     * @param deviceName The advertised device name, or null if not available.
     * @param serviceUuids The list of advertised service UUIDs.
     * @param manufacturerData Map of manufacturer ID to data bytes from the advertisement.
     * @return The matching CameraVendor, or null if no vendor recognizes the device.
     */
    fun identifyVendor(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
    ): CameraVendor?

    /**
     * Gets a specific vendor by its ID.
     *
     * @param vendorId The unique vendor identifier.
     * @return The CameraVendor, or null if not found.
     */
    fun getVendorById(vendorId: String): CameraVendor?

    /**
     * Returns all scan filter service UUIDs from all registered vendors.
     *
     * This is useful for configuring BLE scanners to discover all supported cameras.
     */
    fun getAllScanFilterUuids(): List<Uuid>

    /**
     * Returns all scan filter device name prefixes from all registered vendors.
     *
     * This is useful for configuring BLE scanners to discover all supported cameras.
     */
    fun getAllScanFilterDeviceNames(): List<String>
}

/**
 * Default implementation of CameraVendorRegistry.
 *
 * This implementation uses a simple list to store registered vendors. Vendors are checked in
 * registration order when identifying devices.
 */
@OptIn(ExperimentalUuidApi::class)
class DefaultCameraVendorRegistry(private val vendors: List<CameraVendor>) : CameraVendorRegistry {

    override fun getAllVendors(): List<CameraVendor> = vendors

    override fun identifyVendor(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray>,
    ): CameraVendor? {
        return vendors.firstOrNull { vendor ->
            vendor.recognizesDevice(deviceName, serviceUuids, manufacturerData)
        }
    }

    override fun getVendorById(vendorId: String): CameraVendor? =
        vendors.find { it.vendorId == vendorId }

    override fun getAllScanFilterUuids(): List<Uuid> =
        vendors.flatMap { it.gattSpec.scanFilterServiceUuids }.distinct()

    override fun getAllScanFilterDeviceNames(): List<String> =
        vendors.flatMap { it.gattSpec.scanFilterDeviceNames }.distinct()
}
