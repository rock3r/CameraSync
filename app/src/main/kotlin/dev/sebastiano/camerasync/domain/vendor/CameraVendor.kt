package dev.sebastiano.camerasync.domain.vendor

import android.companion.DeviceFilter
import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.util.DeviceNameProvider
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a camera vendor (manufacturer/model family).
 *
 * This interface defines the vendor-specific BLE protocol details that enable CameraSync to
 * communicate with cameras from different manufacturers.
 *
 * Each vendor implementation encapsulates:
 * - BLE GATT service and characteristic UUIDs
 * - Binary protocol encoding/decoding
 * - Device identification and scanning
 * - Vendor-specific capabilities
 */
interface CameraVendor {

    /** Unique identifier for this vendor (e.g., "ricoh", "canon", "nikon"). */
    val vendorId: String

    /** Human-readable vendor name (e.g., "Ricoh", "Canon", "Nikon"). */
    val vendorName: String

    /** GATT specification for this vendor's cameras. */
    val gattSpec: CameraGattSpec

    /** Protocol encoder/decoder for this vendor. */
    val protocol: CameraProtocol

    /**
     * Determines if a discovered BLE device belongs to this vendor.
     *
     * @param deviceName The advertised device name, or null if not available.
     * @param serviceUuids The list of advertised service UUIDs.
     * @param manufacturerData Map of manufacturer ID to data bytes from the advertisement.
     * @return true if this vendor recognizes the device.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun recognizesDevice(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
    ): Boolean

    /**
     * Parses vendor-specific metadata from BLE advertisement data.
     *
     * @param manufacturerData Map of manufacturer ID to data bytes from the advertisement.
     * @return A map of metadata keys to values (e.g., protocol version).
     */
    fun parseAdvertisementMetadata(manufacturerData: Map<Int, ByteArray>): Map<String, Any> =
        emptyMap()

    /**
     * Creates a connection delegate for this vendor.
     *
     * @return A new instance of [VendorConnectionDelegate].
     */
    fun createConnectionDelegate(): VendorConnectionDelegate

    /**
     * Returns the remote control capabilities for this vendor.
     *
     * Defines what remote shooting, monitoring, and transfer features are supported.
     */
    fun getRemoteControlCapabilities(): RemoteControlCapabilities

    /**
     * Returns the background sync capabilities for this vendor.
     *
     * Defines support for firmware, device name, date/time, geo-tagging, location, pairing, etc.
     */
    fun getSyncCapabilities(): SyncCapabilities

    /**
     * Returns the name to set on the camera for this paired device (e.g. phone name).
     *
     * Used when the camera supports displaying or storing the connected device name.
     *
     * @param deviceNameProvider Provider for the current device name.
     * @return The name to write to the camera.
     */
    fun getPairedDeviceName(deviceNameProvider: DeviceNameProvider): String =
        deviceNameProvider.getDeviceName()

    /**
     * Extracts the camera model from a pairing name.
     *
     * This method attempts to identify the actual camera model from the pairing name, which may
     * have been customized by the user. For example, if a user renamed their "GR IIIx" to "My
     * Camera", this method should still return "GR IIIx" as the model.
     *
     * @param pairingName The pairing name (may be user-customized).
     * @return The extracted model name, or the pairing name itself if the model cannot be
     *   determined.
     */
    fun extractModelFromPairingName(pairingName: String?): String

    /**
     * Returns a list of [DeviceFilter]s used for Companion Device Manager association.
     *
     * @return List of filters to match this vendor's devices, or empty list if not supported.
     */
    fun getCompanionDeviceFilters(): List<DeviceFilter<*>> = emptyList()

    /**
     * Creates a remote control delegate for this vendor.
     *
     * @param peripheral The connected BLE peripheral.
     * @param camera The camera domain object.
     * @return A new instance of [RemoteControlDelegate].
     */
    fun createRemoteControlDelegate(peripheral: Peripheral, camera: Camera): RemoteControlDelegate
}

/** Defines the BLE GATT service and characteristic UUIDs for a camera vendor. */
@OptIn(ExperimentalUuidApi::class)
interface CameraGattSpec {

    /** Manufacturer data scan filter for BLE advertisements. */
    data class ManufacturerDataFilter(
        val manufacturerId: Int,
        val data: ByteArray,
        val mask: ByteArray? = null,
    )

    /** Service UUID(s) used for scanning and filtering camera advertisements. */
    val scanFilterServiceUuids: List<Uuid>

    /** Device name prefix(es) used for scanning and filtering camera advertisements. */
    val scanFilterDeviceNames: List<String>
        get() = emptyList()

    /** Manufacturer data filters used for scanning and filtering camera advertisements. */
    val scanFilterManufacturerData: List<ManufacturerDataFilter>
        get() = emptyList()

    /** Firmware version service UUID, or null if not supported. */
    val firmwareServiceUuid: Uuid?

    /** Firmware version characteristic UUID, or null if not supported. */
    val firmwareVersionCharacteristicUuid: Uuid?

    /** Device name service UUID, or null if not supported. */
    val deviceNameServiceUuid: Uuid?

    /** Device name characteristic UUID, or null if not supported. */
    val deviceNameCharacteristicUuid: Uuid?

    /** Date/time service UUID, or null if not supported. */
    val dateTimeServiceUuid: Uuid?

    /** Date/time characteristic UUID, or null if not supported. */
    val dateTimeCharacteristicUuid: Uuid?

    /** Geo-tagging enable/disable characteristic UUID, or null if not supported. */
    val geoTaggingCharacteristicUuid: Uuid?

    /** Location sync service UUID, or null if not supported. */
    val locationServiceUuid: Uuid?

    /** Location sync characteristic UUID, or null if not supported. */
    val locationCharacteristicUuid: Uuid?

    /** Pairing service UUID, or null if vendor-specific pairing is not required. */
    val pairingServiceUuid: Uuid?
        get() = null

    /** Pairing characteristic UUID, or null if vendor-specific pairing is not required. */
    val pairingCharacteristicUuid: Uuid?
        get() = null

    /**
     * Hardware revision service UUID, or null if not supported. Defaults to standard Device
     * Information Service.
     */
    val hardwareRevisionServiceUuid: Uuid?
        get() = Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb")

    /**
     * Hardware revision characteristic UUID, or null if not supported. Defaults to standard
     * Hardware Revision String.
     */
    val hardwareRevisionCharacteristicUuid: Uuid?
        get() = Uuid.parse("00002a27-0000-1000-8000-00805f9b34fb")
}

/** Handles encoding and decoding of data for a camera vendor's BLE protocol. */
interface CameraProtocol {

    /**
     * Encodes a date/time value to the vendor's binary format.
     *
     * @return Encoded byte array ready to be written to the camera.
     */
    fun encodeDateTime(dateTime: ZonedDateTime): ByteArray

    /**
     * Decodes a date/time value from the vendor's binary format.
     *
     * @return Human-readable string representation of the decoded date/time.
     */
    fun decodeDateTime(bytes: ByteArray): String

    /**
     * Encodes a GPS location to the vendor's binary format.
     *
     * @param location The GPS location to encode.
     * @return Encoded byte array ready to be written to the camera.
     */
    fun encodeLocation(location: GpsLocation): ByteArray

    /**
     * Decodes a GPS location from the vendor's binary format.
     *
     * @return Human-readable string representation of the decoded location.
     */
    fun decodeLocation(bytes: ByteArray): String

    /**
     * Encodes the geo-tagging enabled/disabled state.
     *
     * @return Encoded byte array.
     */
    fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray

    /**
     * Decodes the geo-tagging enabled/disabled state.
     *
     * @return true if geo-tagging is enabled.
     */
    fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean

    /**
     * Returns the data to write for vendor-specific pairing initialization.
     *
     * @return The pairing initialization data, or null if vendor-specific pairing is not required.
     */
    fun getPairingInitData(): ByteArray? = null
}
