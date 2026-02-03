package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.flow.first

private const val TAG = "FirmwareUpdateCheckWorker"

/**
 * WorkManager worker that checks for firmware updates for all paired devices.
 *
 * This worker runs periodically (once per day) to check if firmware updates are available for any
 * paired cameras. When an update is found, it updates the device's firmware update availability
 * flag in the repository, which triggers UI updates and notifications.
 */
class FirmwareUpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val firmwareUpdateCheckers: List<FirmwareUpdateChecker>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.info(tag = TAG) { "Starting firmware update check" }

        return try {
            val devices = pairedDevicesRepository.pairedDevices.first()

            if (devices.isEmpty()) {
                Log.debug(tag = TAG) { "No paired devices, skipping firmware check" }
                return Result.success()
            }

            var checkedCount = 0
            var updateFoundCount = 0

            devices.forEach { device ->
                val checker = firmwareUpdateCheckers.find { it.supportsVendor(device.vendorId) }
                if (checker == null) {
                    Log.debug(tag = TAG) {
                        "No firmware checker available for vendor: ${device.vendorId}"
                    }
                    return@forEach
                }

                checkedCount++

                val result = checker.checkForUpdate(device, device.firmwareVersion)

                when (result) {
                    is FirmwareUpdateCheckResult.UpdateAvailable -> {
                        Log.info(tag = TAG) {
                            "Firmware update available for ${device.macAddress}: " +
                                "${result.currentVersion} -> ${result.latestVersion}"
                        }
                        pairedDevicesRepository.setFirmwareUpdateInfo(
                            device.macAddress,
                            result.latestVersion,
                        )
                        updateFoundCount++
                    }
                    is FirmwareUpdateCheckResult.NoUpdateAvailable -> {
                        Log.debug(tag = TAG) {
                            "No firmware update available for ${device.macAddress}"
                        }
                        // Clear update info if it was previously set
                        pairedDevicesRepository.setFirmwareUpdateInfo(device.macAddress, null)
                    }
                    is FirmwareUpdateCheckResult.CheckFailed -> {
                        Log.warn(tag = TAG) {
                            "Firmware check failed for ${device.macAddress}: ${result.reason}"
                        }
                        // Don't change the update flag on failure - keep existing state
                    }
                }
            }

            Log.info(tag = TAG) {
                "Firmware update check completed: $checkedCount devices checked, " +
                    "$updateFoundCount updates found"
            }

            Result.success()
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Error during firmware update check" }
            // Retry on failure
            Result.retry()
        }
    }
}
