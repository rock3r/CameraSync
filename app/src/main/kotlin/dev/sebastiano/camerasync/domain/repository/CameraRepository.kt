package dev.sebastiano.camerasync.domain.repository

import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for camera BLE operations.
 *
 * Abstracts the BLE communication layer to allow for easier testing. Supports cameras from multiple
 * vendors (Ricoh, Canon, Nikon, etc.).
 */
interface CameraRepository {

    /** Flow of discovered cameras during scanning. */
    val discoveredCameras: Flow<Camera>

    /** Starts scanning for supported cameras from all registered vendors. */
    fun startScanning()

    /** Stops scanning for cameras. */
    fun stopScanning()

    /**
     * Scans for a specific camera by MAC address and returns it when found.
     *
     * @return Flow that emits the camera when found.
     */
    fun findCameraByMacAddress(macAddress: String): Flow<Camera>

    /**
     * Connects to the specified camera.
     *
     * @param camera The camera to connect to.
     * @param onFound Optional callback called when the camera is found during scanning, before
     *   connecting.
     * @return A [CameraConnection] for interacting with the connected camera.
     */
    suspend fun connect(camera: Camera, onFound: (() -> Unit)? = null): CameraConnection
}

/**
 * Represents an active connection to a camera.
 *
 * This interface provides methods to interact with a connected camera. The actual capabilities
 * available depend on the camera vendor.
 */
interface CameraConnection {

    /** The camera this connection is for. */
    val camera: Camera

    /** Flow of the connection state. Emits true when connected, false when disconnected. */
    val isConnected: Flow<Boolean>

    /**
     * Performs vendor-specific pairing initialization.
     *
     * Some camera vendors (like Sony) require a specific BLE command to be written after OS-level
     * bonding to complete the pairing process. This method should be called when the camera is in
     * pairing mode.
     *
     * @return true if pairing was successful or not required, false if pairing failed.
     */
    suspend fun initializePairing(): Boolean

    /** Reads the camera's firmware version. */
    suspend fun readFirmwareVersion(): String

    /** Reads the camera's hardware revision. */
    suspend fun readHardwareRevision(): String

    /** Sets the paired device name on the camera. */
    suspend fun setPairedDeviceName(name: String)

    /** Syncs the current date/time to the camera. */
    suspend fun syncDateTime(dateTime: ZonedDateTime)

    /** Reads the current date/time from the camera (for debugging). */
    suspend fun readDateTime(): ByteArray

    /** Enables or disables geo-tagging on the camera. */
    suspend fun setGeoTaggingEnabled(enabled: Boolean)

    /** Reads whether geo-tagging is currently enabled. */
    suspend fun isGeoTaggingEnabled(): Boolean

    /** Syncs a GPS location to the camera. */
    suspend fun syncLocation(location: GpsLocation)

    /** Disconnects from the camera. */
    suspend fun disconnect()
}
