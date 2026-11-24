package dev.sebastiano.camerasync.vendors.ricoh

import android.bluetooth.le.ScanFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.DeviceFilter
import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.AdvancedShootingCapabilities
import dev.sebastiano.camerasync.domain.vendor.BatteryMonitoringCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.ConnectionModeSupport
import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.ImageBrowsingCapabilities
import dev.sebastiano.camerasync.domain.vendor.ImageControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteCaptureCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.StorageMonitoringCapabilities
import dev.sebastiano.camerasync.domain.vendor.SyncCapabilities
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.VideoRecordingCapabilities
import dev.sebastiano.camerasync.util.DeviceNameProvider
import java.util.regex.Pattern
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    private val ricohServiceUuids: Set<Uuid> =
        setOf(
            RicohGattSpec.Firmware.SERVICE_UUID,
            RicohGattSpec.DateTime.SERVICE_UUID,
            RicohGattSpec.Shooting.SERVICE_UUID,
            RicohGattSpec.WlanControl.SERVICE_UUID,
            RicohGattSpec.DeviceName.SERVICE_UUID,
            RicohGattSpec.CameraState.SERVICE_UUID,
            RicohGattSpec.Location.SERVICE_UUID,
        )

    override fun recognizesDevice(
        deviceName: String?,
        serviceUuids: List<Uuid>,
        manufacturerData: Map<Int, ByteArray>,
    ): Boolean {
        val manufacturerBytes = manufacturerData[RicohGattSpec.RICOH_MANUFACTURER_ID]
        val hasRicohManufacturerData = manufacturerBytes?.firstOrNull() == 0xDA.toByte()

        // Additional check: device name typically starts with "GR" or "RICOH"
        val hasRicohName =
            deviceName?.let { name ->
                RicohGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        val hasRicohService = serviceUuids.any { uuid -> uuid in ricohServiceUuids }

        // Accept device if it has Ricoh manufacturer data, a recognized name, or Ricoh services
        return hasRicohManufacturerData || hasRicohName || hasRicohService
    }

    override fun parseAdvertisementMetadata(
        manufacturerData: Map<Int, ByteArray>
    ): Map<String, Any> {
        val payload = manufacturerData[RicohGattSpec.RICOH_MANUFACTURER_ID] ?: return emptyMap()
        if (payload.isEmpty() || payload[0] != 0xDA.toByte()) return emptyMap()

        val metadata = mutableMapOf<String, Any>()
        // Format from def_adv.decrypted.yaml: [0xDA][Type][Len][Data]...
        var index = 1
        while (index + 1 < payload.size) {
            val type = payload[index].toInt() and 0xFF
            val length = payload[index + 1].toInt() and 0xFF
            val dataStart = index + 2
            val dataEnd = (dataStart + length).coerceAtMost(payload.size)
            if (dataStart >= payload.size) break
            when (type) {
                0x01 ->
                    metadata["modelCode"] = payload.getOrNull(dataStart)?.toInt()?.and(0xFF) ?: -1
                0x02 ->
                    if (dataEnd - dataStart >= 4) {
                        val serial =
                            payload.copyOfRange(dataStart, dataStart + 4).joinToString("") {
                                "%02x".format(it.toInt() and 0xFF)
                            }
                        metadata["serial"] = serial
                    }
                0x03 ->
                    metadata["cameraPower"] = payload.getOrNull(dataStart)?.toInt()?.and(0xFF) ?: -1
            }
            index = dataEnd
        }

        return metadata
    }

    override fun createConnectionDelegate(): VendorConnectionDelegate = DefaultConnectionDelegate()

    override fun createRemoteControlDelegate(
        peripheral: Peripheral,
        camera: Camera,
    ): RemoteControlDelegate = RicohRemoteControlDelegate(peripheral, camera)

    override fun getRemoteControlCapabilities(): RemoteControlCapabilities {
        return RemoteControlCapabilities(
            connectionModeSupport =
                ConnectionModeSupport(bleOnlyShootingSupported = true, wifiAddsFeatures = true),
            batteryMonitoring = BatteryMonitoringCapabilities(supported = true),
            storageMonitoring = StorageMonitoringCapabilities(supported = true),
            remoteCapture =
                RemoteCaptureCapabilities(
                    supported = true,
                    requiresWifi = false,
                    supportsBulbMode = true,
                ),
            advancedShooting =
                AdvancedShootingCapabilities(
                    supported = true,
                    requiresWifi = false,
                    supportsExposureModeReading = true,
                    supportsDriveModeReading = true,
                    supportsSelfTimer = true,
                    supportsUserModes = true,
                ),
            videoRecording =
                VideoRecordingCapabilities(
                    supported = false, // Not implemented yet
                    requiresWifi = false,
                ),
            imageControl =
                ImageControlCapabilities(
                    supported = true,
                    requiresWifi = true,
                    supportsCustomPresets = true,
                    supportsParameterAdjustment = true,
                ),
            imageBrowsing =
                ImageBrowsingCapabilities(
                    supported = true,
                    supportsThumbnails = true,
                    supportsPreview = true,
                    supportsFullDownload = true,
                    supportsExifReading = true,
                    supportsPushTransfer = true,
                ),
        )
    }

    override fun getSyncCapabilities(): SyncCapabilities {
        return SyncCapabilities(
            supportsFirmwareVersion = true,
            supportsDeviceName = true,
            supportsDateTimeSync = true,
            supportsGeoTagging = true,
            supportsLocationSync = true,
            supportsHardwareRevision = true,
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
        val manufacturerFilter =
            BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            RicohGattSpec.RICOH_MANUFACTURER_ID,
                            byteArrayOf(0xDA.toByte()),
                            byteArrayOf(0xFF.toByte()),
                        )
                        .build()
                )
                .build()

        val nameFilter =
            BluetoothLeDeviceFilter.Builder()
                .setNamePattern(Pattern.compile("(GR|RICOH).*"))
                .build()

        return listOf(manufacturerFilter, nameFilter)
    }

    override fun getPairedDeviceName(deviceNameProvider: DeviceNameProvider): String =
        "${deviceNameProvider.getDeviceName()} (CameraSync)"
}
