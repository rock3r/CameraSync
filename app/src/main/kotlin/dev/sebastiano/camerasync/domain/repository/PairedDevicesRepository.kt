package dev.sebastiano.camerasync.domain.repository

import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing paired camera devices.
 *
 * This repository handles persistence of paired devices and their enabled state. The actual BLE
 * connections are managed separately by the CameraRepository.
 */
interface PairedDevicesRepository {

    /**
     * Flow of all paired devices. Emits whenever the list changes (device added, removed, or
     * enabled state changed).
     */
    val pairedDevices: Flow<List<PairedDevice>>

    /**
     * Flow of enabled devices only. Useful for determining which devices should be auto-connected.
     */
    val enabledDevices: Flow<List<PairedDevice>>

    /**
     * Flow of the global sync enabled state. When true, the service can start automatically if
     * there are enabled devices. When false, the service should only start if explicitly requested
     * by the user.
     */
    val isSyncEnabled: Flow<Boolean>

    /**
     * Sets the global sync enabled state.
     *
     * @param enabled Whether global sync should be enabled.
     */
    suspend fun setSyncEnabled(enabled: Boolean)

    /**
     * Adds a newly paired device to persistent storage.
     *
     * @param camera The discovered camera to add.
     * @param enabled Whether the device should be initially enabled.
     */
    suspend fun addDevice(camera: Camera, enabled: Boolean = true)

    /**
     * Removes (unpairs) a device from persistent storage.
     *
     * @param macAddress The MAC address of the device to remove.
     */
    suspend fun removeDevice(macAddress: String)

    /**
     * Enables or disables a paired device.
     *
     * When enabled, the device will be auto-connected when in range. When disabled, any existing
     * connection should be closed.
     *
     * @param macAddress The MAC address of the device.
     * @param enabled Whether to enable or disable the device.
     */
    suspend fun setDeviceEnabled(macAddress: String, enabled: Boolean)

    /**
     * Updates the stored name for a device. Useful when the device advertises a different name.
     *
     * @param macAddress The MAC address of the device.
     * @param name The new name to store.
     */
    suspend fun updateDeviceName(macAddress: String, name: String?)

    /**
     * Updates the last successful sync timestamp for a device.
     *
     * @param macAddress The MAC address of the device.
     * @param timestamp The timestamp (millis since epoch).
     */
    suspend fun updateLastSyncedAt(macAddress: String, timestamp: Long)

    /**
     * Updates the firmware version for a device.
     *
     * @param macAddress The MAC address of the device.
     * @param firmwareVersion The firmware version string (e.g., "2.01"), or null to clear.
     */
    suspend fun updateFirmwareVersion(macAddress: String, firmwareVersion: String?)

    /**
     * Updates firmware update information for a device.
     *
     * @param macAddress The MAC address of the device.
     * @param latestVersion The latest firmware version available, or null to clear (no update
     *   available). When an update is found, this clears the notification shown flag so the user
     *   can be notified.
     */
    suspend fun setFirmwareUpdateInfo(macAddress: String, latestVersion: String?)

    /**
     * Marks that the firmware update notification has been shown to the user.
     *
     * @param macAddress The MAC address of the device.
     */
    suspend fun setFirmwareUpdateNotificationShown(macAddress: String)

    /**
     * Checks if a device with the given MAC address is already paired.
     *
     * @param macAddress The MAC address to check.
     * @return true if the device is already paired.
     */
    suspend fun isDevicePaired(macAddress: String): Boolean

    /**
     * Gets a single paired device by MAC address.
     *
     * @param macAddress The MAC address of the device.
     * @return The paired device, or null if not found.
     */
    suspend fun getDevice(macAddress: String): PairedDevice?

    /** Checks if there are any paired devices. */
    suspend fun hasAnyDevices(): Boolean

    /** Checks if there are any enabled devices. */
    suspend fun hasEnabledDevices(): Boolean
}
