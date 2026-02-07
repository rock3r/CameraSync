package dev.sebastiano.camerasync.domain.vendor

import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.CustomButton
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.LiveViewFrame
import dev.sebastiano.camerasync.domain.model.RecordingStatus
import dev.sebastiano.camerasync.domain.model.ShutterStatus
import dev.sebastiano.camerasync.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the contract for vendor-specific remote control implementations.
 *
 * This delegate handles the actual communication with the camera for shooting and monitoring
 * features. It adapts its behavior based on the active [ShootingConnectionMode].
 *
 * Implementations should respect the capabilities declared in [RemoteControlCapabilities]. Methods
 * corresponding to unsupported capabilities should be no-ops or throw
 * [UnsupportedOperationException]. Methods requiring Wi-Fi should handle the case where Wi-Fi is
 * not connected (e.g., by throwing [IllegalStateException] or returning empty flows).
 */
interface RemoteControlDelegate {

    /**
     * The current connection mode (BLE-only or Full Wi-Fi).
     *
     * This state flow allows the UI to react to changes in connectivity, enabling/disabling
     * features dynamically.
     */
    val connectionMode: StateFlow<ShootingConnectionMode>

    // --- Shutter Control (Basic) ---

    /**
     * Triggers the shutter release.
     *
     * In BLE-only mode, this sends the appropriate BLE command. In Full mode, this may use PTP/IP
     * or HTTP depending on the vendor.
     */
    suspend fun triggerCapture()

    /**
     * Starts a Bulb/Time exposure (long exposure).
     *
     * Relevant for cameras supporting [RemoteCaptureCapabilities.supportsBulbMode].
     */
    suspend fun startBulbExposure()

    /** Stops a Bulb/Time exposure. */
    suspend fun stopBulbExposure()

    // --- Status Monitoring (BLE-only or Full) ---

    /**
     * Observes the camera's battery level.
     *
     * Should work in BLE-only mode for most vendors.
     */
    fun observeBatteryLevel(): Flow<BatteryInfo>

    /**
     * Observes the camera's storage status (capacity, card presence).
     *
     * Should work in BLE-only mode for most vendors.
     */
    fun observeStorageStatus(): Flow<StorageInfo>

    /** Observes the camera's current operation mode (Still, Movie, Playback, etc.). */
    fun observeCameraMode(): Flow<CameraMode>

    /** Observes the capture status (Idle, Capturing, Countdown). */
    fun observeCaptureStatus(): Flow<CaptureStatus>

    /**
     * Observes the current exposure mode (P, A, S, M, etc.).
     *
     * For Sony, this typically requires Wi-Fi (Full mode). For Ricoh, this works over BLE.
     */
    fun observeExposureMode(): Flow<ExposureMode>

    /** Observes the current drive mode (Single, Continuous, Self-timer). */
    fun observeDriveMode(): Flow<DriveMode>

    // --- Wi-Fi Connection Management ---

    /**
     * Initiates the Wi-Fi connection process to upgrade from BLE-only to Full mode.
     *
     * This typically involves:
     * 1. Commanding the camera via BLE to enable Wi-Fi.
     * 2. Retrieving credentials.
     * 3. Connecting the Android device to the camera AP.
     * 4. Establishing the data channel (PTP/IP or HTTP).
     */
    suspend fun connectWifi()

    /** Disconnects Wi-Fi and downgrades the connection to BLE-only mode. */
    suspend fun disconnectWifi()

    // --- Advanced Shooting Controls (Sony BLE / Wi-Fi) ---

    /**
     * Performs a half-press of the shutter button (Autofocus).
     *
     * Primarily for Sony cameras (BLE FF01 or PTP/IP).
     */
    suspend fun halfPressAF() {
        throw UnsupportedOperationException("Half-press AF is not supported by this device.")
    }

    /** Releases the half-press AF state. */
    suspend fun releaseAF() {
        throw UnsupportedOperationException("Release AF is not supported by this device.")
    }

    /**
     * Adjusts manual focus towards "Near".
     *
     * @param speed Speed of adjustment (vendor-specific range, typically 0-127). 0 usually means
     *   "stop".
     */
    suspend fun focusNear(speed: Int = 0x20) {
        throw UnsupportedOperationException("Manual focus control is not supported by this device.")
    }

    /**
     * Adjusts manual focus towards "Far".
     *
     * @param speed Speed of adjustment.
     */
    suspend fun focusFar(speed: Int = 0x20) {
        throw UnsupportedOperationException("Manual focus control is not supported by this device.")
    }

    /**
     * Zooms in (Tele).
     *
     * @param speed Speed of zoom.
     */
    suspend fun zoomIn(speed: Int = 0x20) {
        throw UnsupportedOperationException("Zoom control is not supported by this device.")
    }

    /**
     * Zooms out (Wide).
     *
     * @param speed Speed of zoom.
     */
    suspend fun zoomOut(speed: Int = 0x20) {
        throw UnsupportedOperationException("Zoom control is not supported by this device.")
    }

    /** Presses a custom button on the camera. */
    suspend fun pressCustomButton(button: CustomButton) {
        throw UnsupportedOperationException("Custom button press is not supported by this device.")
    }

    /** Releases a custom button. */
    suspend fun releaseCustomButton(button: CustomButton) {
        throw UnsupportedOperationException(
            "Custom button release is not supported by this device."
        )
    }

    /**
     * Observes the focus status (Locked, Searching, Lost).
     *
     * Returns null if unsupported by the vendor or current connection mode.
     */
    fun observeFocusStatus(): Flow<FocusStatus>? = null

    /**
     * Observes the shutter status (Ready, Active).
     *
     * Returns null if unsupported.
     */
    fun observeShutterStatus(): Flow<ShutterStatus>? = null

    // --- Wi-Fi Only Extensions ---

    /**
     * Performs Touch AF at the specified relative coordinates.
     *
     * @param x Horizontal position (0.0 - 1.0).
     * @param y Vertical position (0.0 - 1.0).
     */
    suspend fun touchAF(x: Float, y: Float) {
        throw UnsupportedOperationException("Touch AF is not supported by this device.")
    }

    /** Toggles Auto Exposure Lock. */
    suspend fun toggleAELock() {
        throw UnsupportedOperationException("AE Lock is not supported by this device.")
    }

    /** Toggles Flash Exposure Lock. */
    suspend fun toggleFELock() {
        throw UnsupportedOperationException("FE Lock is not supported by this device.")
    }

    /** Toggles Auto White Balance Lock. */
    suspend fun toggleAWBLock() {
        throw UnsupportedOperationException("AWB Lock is not supported by this device.")
    }

    /**
     * Observes the Live View stream.
     *
     * Only available in Full mode for supported vendors (e.g., Sony).
     */
    fun observeLiveView(): Flow<LiveViewFrame>? = null

    /** Observes the current zoom level. */
    fun observeZoomLevel(): Flow<Float>? = null

    // --- Video Recording ---

    /** Toggles video recording start/stop. */
    suspend fun toggleVideoRecording() {
        throw UnsupportedOperationException("Video recording is not supported by this device.")
    }

    /** Observes video recording status. */
    fun observeRecordingStatus(): Flow<RecordingStatus>? = null
}

/** Represents the active connection tier for remote control. */
enum class ShootingConnectionMode {
    /**
     * Connected via BLE only.
     *
     * Base remote control features (shutter, basic monitoring) are available. Power consumption is
     * low.
     */
    BLE_ONLY,

    /**
     * Connected via BLE and Wi-Fi.
     *
     * Full remote control features (Live View, Image Transfer, advanced settings) are available.
     * Power consumption is higher.
     */
    FULL,
}
