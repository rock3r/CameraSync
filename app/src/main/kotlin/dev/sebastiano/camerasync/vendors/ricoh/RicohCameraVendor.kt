package dev.sebastiano.camerasync.vendors.ricoh

import android.bluetooth.le.ScanFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.DeviceFilter
import android.os.ParcelUuid
import dev.sebastiano.camerasync.domain.vendor.CameraCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.util.DeviceNameProvider
import java.util.regex.Pattern
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Ricoh camera vendor implementation.
 *
 * Supports Ricoh cameras including:
 * - GR IIIx
 * - GR III (likely compatible, untested)
 * - Other Ricoh cameras using the same BLE protocol
 */
@OptIn(ExperimentalUuidApi::class)
object RicohCameraVendor : CameraVendor {

    override val vendorId: String = "ricoh"

    override val vendorName: String = "Ricoh"

    override val gattSpec: CameraGattSpec = RicohGattSpec

    override val protocol: CameraProtocol = RicohProtocol

    override fun recognizesDevice(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray>,
    ): Boolean {
        // Ricoh cameras advertise a specific service UUID
        val hasRicohService =
            serviceUuids.any { uuid -> RicohGattSpec.scanFilterServiceUuids.contains(uuid) }

        // Additional check: device name typically starts with "GR" or "RICOH"
        val hasRicohName =
            deviceName?.let { name ->
                RicohGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        // Accept device if it has the Ricoh service UUID or a recognized name
        return hasRicohService || hasRicohName
    }

    override fun createConnectionDelegate(): VendorConnectionDelegate = DefaultConnectionDelegate()

    override fun getCapabilities(): CameraCapabilities {
        return CameraCapabilities(
            supportsFirmwareVersion = true,
            supportsDeviceName = true,
            supportsDateTimeSync = true,
            supportsGeoTagging = true,
            supportsLocationSync = true,
            supportsHardwareRevision = true,
            supportsModelName = true,
        )
    }

    override fun extractModelFromPairingName(pairingName: String?): String {
        if (pairingName == null) return "Unknown"

        val name = pairingName.trim()

        // Try to extract known Ricoh GR model patterns
        // Check for "GR IIIx" first (more specific)
        if (name.contains("GR IIIx", ignoreCase = true)) {
            return "GR IIIx"
        }

        // Check for "GR III" (but not "GR IIIx" which we already handled)
        if (
            name.contains("GR III", ignoreCase = true) &&
                !name.contains("GR IIIx", ignoreCase = true)
        ) {
            return "GR III"
        }

        // Check for other GR models
        val grPattern = Regex("GR\\s+(\\d+[a-z]?)", RegexOption.IGNORE_CASE)
        grPattern.find(name)?.let { match ->
            return "GR ${match.groupValues[1]}"
        }

        // If name starts with "GR" or "RICOH", assume it might be a model name
        if (
            name.startsWith("GR", ignoreCase = true) || name.startsWith("RICOH", ignoreCase = true)
        ) {
            return name
        }

        // Fallback: return the pairing name as-is
        return name
    }

    override fun getCompanionDeviceFilters(): List<DeviceFilter<*>> {
        val serviceFilter =
            BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    ScanFilter.Builder()
                        .setServiceUuid(
                            ParcelUuid(RicohGattSpec.SCAN_FILTER_SERVICE_UUID.toJavaUuid())
                        )
                        .build()
                )
                .build()

        val nameFilter =
            BluetoothLeDeviceFilter.Builder()
                .setNamePattern(Pattern.compile("(GR|RICOH).*"))
                .build()

        return listOf(serviceFilter, nameFilter)
    }

    override fun getPairedDeviceName(deviceNameProvider: DeviceNameProvider): String =
        "${deviceNameProvider.getDeviceName()} (CameraSync)"
}
