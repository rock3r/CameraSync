package dev.sebastiano.camerasync.domain.vendor

/**
 * Defines the comprehensive set of remote control capabilities supported by a camera.
 *
 * This structured model allows for granular declaration of supported features across different
 * functional groups (shooting, monitoring, transfer, etc.).
 *
 * The capabilities are static declarations of what the vendor implementation *can* support. Runtime
 * availability may depend on the active [ShootingConnectionMode].
 */
data class RemoteControlCapabilities(
    val connectionModeSupport: ConnectionModeSupport = ConnectionModeSupport(),

    // --- Status display (typically BLE-only for both vendors) ---
    val batteryMonitoring: BatteryMonitoringCapabilities = BatteryMonitoringCapabilities(),
    val storageMonitoring: StorageMonitoringCapabilities = StorageMonitoringCapabilities(),

    // --- Shooting control ---
    val remoteCapture: RemoteCaptureCapabilities = RemoteCaptureCapabilities(),
    val advancedShooting: AdvancedShootingCapabilities = AdvancedShootingCapabilities(),
    val videoRecording: VideoRecordingCapabilities = VideoRecordingCapabilities(),
    val liveView: LiveViewCapabilities = LiveViewCapabilities(),
    val autofocus: AutofocusCapabilities = AutofocusCapabilities(),
    val imageControl: ImageControlCapabilities = ImageControlCapabilities(),

    // --- File Transfer ---
    val imageBrowsing: ImageBrowsingCapabilities = ImageBrowsingCapabilities(),
)

/** Defines which connection modes the camera supports for remote control. */
data class ConnectionModeSupport(
    /** Whether remote shooting features are available over BLE only. */
    val bleOnlyShootingSupported: Boolean = false,
    /** Whether connecting via Wi-Fi adds additional features (e.g., live view, image transfer). */
    val wifiAddsFeatures: Boolean = false,
)

/** Capabilities related to battery status monitoring. */
data class BatteryMonitoringCapabilities(
    val supported: Boolean = false,
    /** Whether the camera reports battery level for multiple packs (e.g. grip). */
    val supportsMultiplePacks: Boolean = false,
    /** Whether the camera reports power source (e.g. USB vs Battery). */
    val supportsPowerSourceDetection: Boolean = false,
)

/** Capabilities related to storage status monitoring. */
data class StorageMonitoringCapabilities(
    val supported: Boolean = false,
    /** Whether the camera reports status for multiple storage slots. */
    val supportsMultipleSlots: Boolean = false,
    /** Whether the camera reports remaining recording time for video. */
    val supportsVideoCapacity: Boolean = false,
)

/** Capabilities related to basic remote shutter control. */
data class RemoteCaptureCapabilities(
    val supported: Boolean = false,
    /** Whether Wi-Fi is required for basic shutter control. */
    val requiresWifi: Boolean = false,
    /** Whether the camera supports a distinct half-press AF state/command. */
    val supportsHalfPressAF: Boolean = false,
    /** Whether the camera supports touch-to-focus (requires coordinate input). */
    val supportsTouchAF: Boolean = false,
    /** Whether the camera supports Bulb/Time exposure control. */
    val supportsBulbMode: Boolean = false,
    /** Whether the camera supports manual focus commands. */
    val supportsManualFocus: Boolean = false,
    /** Whether the camera supports zoom commands. */
    val supportsZoom: Boolean = false,
    /** Whether the camera supports AE lock toggle. */
    val supportsAELock: Boolean = false,
    /** Whether the camera supports FE lock toggle. */
    val supportsFELock: Boolean = false,
    /** Whether the camera supports AWB lock toggle. */
    val supportsAWBLock: Boolean = false,
    /** Whether the camera supports triggering custom buttons. */
    val supportsCustomButtons: Boolean = false,
)

/** Capabilities related to advanced shooting settings (Exposure, Drive, etc.). */
data class AdvancedShootingCapabilities(
    val supported: Boolean = false,
    /** Whether these features require Wi-Fi to function. */
    val requiresWifi: Boolean = false,
    val supportsExposureModeReading: Boolean = false,
    val supportsDriveModeReading: Boolean = false,
    val supportsSelfTimer: Boolean = false,
    /** Whether the camera supports user-defined modes (U1/U2/U3). */
    val supportsUserModes: Boolean = false,
    /** Whether the camera supports Program Shift. */
    val supportsProgramShift: Boolean = false,
    val supportsExposureCompensation: Boolean = false,
)

/** Capabilities related to video recording. */
data class VideoRecordingCapabilities(
    val supported: Boolean = false,
    /** Whether Wi-Fi is required to toggle recording. */
    val requiresWifi: Boolean = false,
)

/** Capabilities related to Live View / Viewfinder. */
data class LiveViewCapabilities(
    val supported: Boolean = false,
    /** Whether Wi-Fi is required for live view stream. */
    val requiresWifi: Boolean = true,
    /** Whether the camera supports post-capture image review. */
    val supportsPostView: Boolean = false,
)

/** Capabilities related to Autofocus control. */
data class AutofocusCapabilities(
    val supported: Boolean = false,
    val supportsFocusStatusReading: Boolean = false,
)

/** Capabilities related to Image Control / Picture Profiles. */
data class ImageControlCapabilities(
    val supported: Boolean = false,
    /** Whether Wi-Fi is required to manage image controls. */
    val requiresWifi: Boolean = true,
    /** Whether the camera supports custom preset slots. */
    val supportsCustomPresets: Boolean = false,
    /** Whether the camera supports fine-tuning preset parameters. */
    val supportsParameterAdjustment: Boolean = false,
)

/** Capabilities related to image browsing and transfer. */
data class ImageBrowsingCapabilities(
    val supported: Boolean = false,
    val supportsThumbnails: Boolean = false,
    val supportsPreview: Boolean = false,
    val supportsFullDownload: Boolean = false,
    val supportsExifReading: Boolean = false,
    /** Whether the camera can initiate a push transfer to the phone. */
    val supportsPushTransfer: Boolean = false,
    val supportsDownloadResume: Boolean = false,
)

/** Background sync capabilities (firmware, device name, date/time, geo-tagging, location). */
data class SyncCapabilities(
    val supportsFirmwareVersion: Boolean = false,
    val supportsDeviceName: Boolean = false,
    val supportsDateTimeSync: Boolean = false,
    val supportsGeoTagging: Boolean = false,
    val supportsLocationSync: Boolean = false,
    val requiresVendorPairing: Boolean = false,
    val supportsHardwareRevision: Boolean = false,
)
