package dev.sebastiano.camerasync.vendors.sony

import android.bluetooth.le.ScanFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.DeviceFilter
import android.os.ParcelUuid
import dev.sebastiano.camerasync.domain.vendor.CameraCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import java.util.regex.Pattern
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Sony camera vendor implementation.
 *
 * Supports Sony Alpha cameras using the DI Remote Control protocol.
 */
@OptIn(ExperimentalUuidApi::class)
object SonyCameraVendor : CameraVendor {

    /** Sony's Bluetooth manufacturer ID (0x012D = 301 decimal). */
    const val SONY_MANUFACTURER_ID = 0x012D

    /** Device type ID for cameras in Sony's manufacturer data. */
    private const val DEVICE_TYPE_CAMERA: Short = 0x0003

    override val vendorId: String = "sony"

    override val vendorName: String = "Sony"

    override val gattSpec: CameraGattSpec = SonyGattSpec

    override val protocol: CameraProtocol = SonyProtocol

    override fun recognizesDevice(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray>,
    ): Boolean {
        // Check 1: Sony manufacturer data with camera device type
        // According to PROTOCOL_EN.md, Sony cameras advertise with manufacturer ID 0x012D
        // and device type 0x0003 in the first bytes of manufacturer data
        if (isSonyCamera(manufacturerData)) {
            return true
        }

        // Check 2: Sony-specific service UUIDs (Remote Control or Pairing service)
        val hasSonyService =
            serviceUuids.any { uuid -> SonyGattSpec.scanFilterServiceUuids.contains(uuid) }
        if (hasSonyService) {
            return true
        }

        // Check 3: Device name pattern matching (ILCE- prefix for Alpha cameras, DSC- for others)
        // This is a fallback when neither manufacturer data nor service UUIDs are available
        val hasSonyName =
            deviceName?.let { name ->
                SonyGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        return hasSonyName
    }

    /**
     * Checks if the manufacturer data indicates a Sony camera.
     *
     * According to PROTOCOL_EN.md, Sony camera manufacturer data format:
     * - Bytes 0-1: Device Type ID (0x0003 = Camera, little-endian in raw BLE data)
     *
     * Note: The manufacturer ID (0x012D) is the key in the map, already parsed by the BLE stack.
     */
    private fun isSonyCamera(manufacturerData: Map<Int, ByteArray>): Boolean {
        val sonyData = manufacturerData[SONY_MANUFACTURER_ID] ?: return false

        // Need at least 2 bytes for device type
        if (sonyData.size < 2) return false

        // Device type is in the first 2 bytes (little-endian in raw BLE advertisement)
        val deviceType = ((sonyData[1].toInt() and 0xFF) shl 8) or (sonyData[0].toInt() and 0xFF)
        return deviceType.toShort() == DEVICE_TYPE_CAMERA
    }

    override fun getCapabilities(): CameraCapabilities {
        return CameraCapabilities(
            supportsFirmwareVersion = true, // Standard DIS
            supportsDeviceName = false, // Setting device name is not standard for Sony via BLE
            supportsDateTimeSync = true,
            supportsGeoTagging = false, // No separate toggle; location data includes time
            supportsLocationSync = true,
            requiresVendorPairing = true, // Sony requires writing to EE01 characteristic
            supportsHardwareRevision = true,
        )
    }

    override fun extractModelFromPairingName(pairingName: String?): String {
        if (pairingName == null) return "Unknown"

        val name = pairingName.trim()

        // Sony cameras typically use ILCE- prefix for Alpha cameras
        // Try to extract model from common patterns
        val ilcePattern = Regex("ILCE-?([0-9A-Z]+)", RegexOption.IGNORE_CASE)
        ilcePattern.find(name)?.let { match ->
            return "ILCE-${match.groupValues[1]}"
        }

        // If name starts with known Sony prefixes, assume it's a model
        if (
            name.startsWith("ILCE", ignoreCase = true) || name.startsWith("DSC-", ignoreCase = true)
        ) {
            return name
        }

        // Fallback: return the pairing name as-is
        return name
    }

    override fun getCompanionDeviceFilters(): List<DeviceFilter<*>> {
        val serviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID.toJavaUuid()))
                    .build()
            )
            .build()

        val nameFilter = BluetoothLeDeviceFilter.Builder()
             .setNamePattern(Pattern.compile("ILCE-.*"))
             .build()

        return listOf(serviceFilter, nameFilter)
    }
}
