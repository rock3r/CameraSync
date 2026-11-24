package dev.sebastiano.camerasync.vendors.sony

import android.bluetooth.le.ScanFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.DeviceFilter
import android.os.ParcelUuid
import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.AdvancedShootingCapabilities
import dev.sebastiano.camerasync.domain.vendor.AutofocusCapabilities
import dev.sebastiano.camerasync.domain.vendor.BatteryMonitoringCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.ConnectionModeSupport
import dev.sebastiano.camerasync.domain.vendor.ImageBrowsingCapabilities
import dev.sebastiano.camerasync.domain.vendor.ImageControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.LiveViewCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteCaptureCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.StorageMonitoringCapabilities
import dev.sebastiano.camerasync.domain.vendor.SyncCapabilities
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.VideoRecordingCapabilities
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
        val hasSonyName =
            deviceName?.let { name ->
                SonyGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        return hasSonyName
    }

    override fun parseAdvertisementMetadata(
        manufacturerData: Map<Int, ByteArray>
    ): Map<String, Any> {
        val version = parseProtocolVersion(manufacturerData)
        return if (version != null) {
            mapOf("bleProtocolVersion" to version)
        } else {
            emptyMap()
        }
    }

    override fun createConnectionDelegate(): VendorConnectionDelegate = SonyConnectionDelegate()

    override fun createRemoteControlDelegate(
        peripheral: Peripheral,
        camera: Camera,
    ): RemoteControlDelegate = SonyRemoteControlDelegate(peripheral, camera)

    /** Checks if the manufacturer data indicates a Sony camera. */
    private fun isSonyCamera(manufacturerData: Map<Int, ByteArray>): Boolean {
        val sonyData = manufacturerData[SONY_MANUFACTURER_ID] ?: return false

        // Need at least 2 bytes for device type
        if (sonyData.size < 2) return false

        // Device type is in the first 2 bytes (little-endian in raw BLE advertisement)
        val deviceType = ((sonyData[1].toInt() and 0xFF) shl 8) or (sonyData[0].toInt() and 0xFF)
        return deviceType.toShort() == DEVICE_TYPE_CAMERA
    }

    /** Parses the BLE protocol version from Sony manufacturer data. */
    fun parseProtocolVersion(manufacturerData: Map<Int, ByteArray>): Int? {
        val sonyData = manufacturerData[SONY_MANUFACTURER_ID] ?: return null

        // Need at least 4 bytes for device type + protocol version
        if (sonyData.size < 4) return null

        // Protocol version is at bytes 2-3
        return sonyData[2].toInt() and 0xFF
    }

    /** Minimum protocol version that requires DD30/DD31 unlock sequence. */
    const val PROTOCOL_VERSION_REQUIRES_UNLOCK = 65

    override fun getRemoteControlCapabilities(): RemoteControlCapabilities {
        return RemoteControlCapabilities(
            connectionModeSupport =
                ConnectionModeSupport(bleOnlyShootingSupported = true, wifiAddsFeatures = true),
            batteryMonitoring =
                BatteryMonitoringCapabilities(
                    supported = true,
                    supportsMultiplePacks = true,
                    supportsPowerSourceDetection = true,
                ),
            storageMonitoring =
                StorageMonitoringCapabilities(
                    supported = true,
                    supportsMultipleSlots = true,
                    supportsVideoCapacity = true,
                ),
            remoteCapture =
                RemoteCaptureCapabilities(
                    supported = true,
                    requiresWifi = false, // Works via BLE FF01
                    supportsHalfPressAF = true,
                    supportsTouchAF = true, // Wi-Fi only
                    supportsBulbMode = true,
                    supportsManualFocus = true,
                    supportsZoom = true,
                    supportsAELock = true, // Wi-Fi only
                    supportsFELock = true, // Wi-Fi only
                    supportsAWBLock = true, // Wi-Fi only
                    supportsCustomButtons = true,
                ),
            advancedShooting =
                AdvancedShootingCapabilities(
                    supported = true,
                    requiresWifi = true, // PTP/IP properties
                    supportsExposureModeReading = true,
                    supportsDriveModeReading = true,
                    supportsSelfTimer = true,
                    supportsProgramShift = true,
                    supportsExposureCompensation = true,
                ),
            videoRecording =
                VideoRecordingCapabilities(
                    supported = true,
                    requiresWifi = false, // BLE toggle
                ),
            liveView =
                LiveViewCapabilities(
                    supported = true,
                    requiresWifi = true,
                    supportsPostView = true,
                ),
            autofocus = AutofocusCapabilities(supported = true, supportsFocusStatusReading = true),
            imageControl = ImageControlCapabilities(supported = false), // Not supported
            imageBrowsing =
                ImageBrowsingCapabilities(
                    supported = true,
                    supportsThumbnails = true,
                    supportsPreview = true,
                    supportsFullDownload = true,
                    supportsExifReading = true,
                    supportsPushTransfer = true,
                    supportsDownloadResume = true,
                ),
        )
    }

    override fun getSyncCapabilities(): SyncCapabilities {
        return SyncCapabilities(
            supportsFirmwareVersion = true,
            supportsDeviceName = false,
            supportsDateTimeSync = true,
            supportsGeoTagging = false,
            supportsLocationSync = true,
            requiresVendorPairing = true,
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
        val serviceFilter =
            BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    ScanFilter.Builder()
                        .setServiceUuid(
                            ParcelUuid(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID.toJavaUuid())
                        )
                        .build()
                )
                .build()

        val nameFilter =
            BluetoothLeDeviceFilter.Builder().setNamePattern(Pattern.compile("ILCE-.*")).build()

        return listOf(serviceFilter, nameFilter)
    }
}
