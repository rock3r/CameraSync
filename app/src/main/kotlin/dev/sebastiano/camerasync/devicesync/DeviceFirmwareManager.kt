package dev.sebastiano.camerasync.devicesync

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.zacsweers.metro.Inject
import java.io.IOException

private const val TAG = "DeviceFirmwareManager"

/**
 * Manages firmware update checks and notifications for camera devices.
 *
 * This manager is responsible for checking if a newer firmware version is available for a device
 * and showing a system notification to the user if an update is found and hasn't been notified yet.
 */
@Inject
class DeviceFirmwareManager(
    private val context: Context,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val pendingIntentFactory: PendingIntentFactory,
    private val intentFactory: IntentFactory,
    private val notificationBuilder: NotificationBuilder,
) {

    /**
     * Checks for firmware updates for the given device and shows a notification if available.
     *
     * The check compares the current [firmwareVersion] with the latest version known to the
     * [pairedDevicesRepository]. If a newer version is found and no notification has been shown yet
     * for this update, a system notification is displayed.
     *
     * @param macAddress The MAC address of the device to check.
     * @param firmwareVersion The current firmware version reported by the device.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun checkAndNotifyFirmwareUpdate(macAddress: String, firmwareVersion: String?) {
        if (firmwareVersion == null) {
            Log.debug(tag = TAG) {
                "Skipping firmware update check for $macAddress: firmware version is null"
            }
            return
        }

        val device =
            pairedDevicesRepository.getDevice(macAddress)
                ?: run {
                    Log.warn(tag = TAG) {
                        "Cannot check firmware update for $macAddress: device not found"
                    }
                    return
                }

        val latestVersion =
            device.latestFirmwareVersion
                ?: run {
                    Log.debug(tag = TAG) {
                        "No firmware update available for $macAddress (current: $firmwareVersion)"
                    }
                    return
                }

        if (device.firmwareUpdateNotificationShown) {
            Log.debug(tag = TAG) {
                "Firmware update notification already shown for $macAddress ($firmwareVersion -> $latestVersion)"
            }
            return
        }

        try {
            val deviceName = device.name ?: context.getString(R.string.label_unknown)
            Log.info(tag = TAG) {
                "Showing firmware update notification for $macAddress ($deviceName): $firmwareVersion -> $latestVersion"
            }

            val notification =
                createFirmwareUpdateNotification(
                    context = context,
                    notificationBuilder = notificationBuilder,
                    pendingIntentFactory = pendingIntentFactory,
                    intentFactory = intentFactory,
                    params =
                        FirmwareUpdateNotificationParams(
                            deviceName = deviceName,
                            currentVersion = firmwareVersion,
                            latestVersion = latestVersion,
                        ),
                )

            val notificationId = "$macAddress:$firmwareVersion".hashCode()
            NotificationManagerCompat.from(context).notify(notificationId, notification)

            pairedDevicesRepository.setFirmwareUpdateNotificationShown(macAddress)
            Log.info(tag = TAG) {
                "Successfully showed firmware update notification for $macAddress"
            }
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) {
                "Failed to show firmware update notification for $macAddress"
            }
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) {
                "Failed to show firmware update notification for $macAddress"
            }
        }
    }
}
