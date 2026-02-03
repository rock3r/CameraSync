package dev.sebastiano.camerasync.firmware

import dev.sebastiano.camerasync.domain.model.PairedDevice

/** Result of checking for firmware updates for a device. */
sealed interface FirmwareUpdateCheckResult {
    /** No update is available - device is on the latest version. */
    data object NoUpdateAvailable : FirmwareUpdateCheckResult

    /** An update is available. */
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val modelName: String,
    ) : FirmwareUpdateCheckResult

    /** Check failed - device doesn't support firmware updates or error occurred. */
    data class CheckFailed(val reason: String) : FirmwareUpdateCheckResult
}

/**
 * Interface for checking firmware update availability for cameras.
 *
 * Each vendor implements this interface to provide vendor-specific firmware update checking logic.
 */
interface FirmwareUpdateChecker {
    /**
     * Checks if a firmware update is available for the given device.
     *
     * @param device The paired device to check for updates.
     * @param currentFirmwareVersion The current firmware version on the device, or null if unknown.
     * @return Result indicating whether an update is available.
     */
    suspend fun checkForUpdate(
        device: PairedDevice,
        currentFirmwareVersion: String?,
    ): FirmwareUpdateCheckResult

    /** Returns true if this checker supports the given vendor ID. */
    fun supportsVendor(vendorId: String): Boolean
}
