package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.juul.khronicle.Log
import java.util.concurrent.TimeUnit

private const val TAG = "FirmwareUpdateScheduler"
private const val WORK_NAME = "firmware_update_check"

/**
 * Schedules periodic firmware update checks using WorkManager.
 *
 * The check runs once per day when network is available.
 */
object FirmwareUpdateScheduler {

    /**
     * Schedules a periodic firmware update check that runs once per day.
     *
     * This should be called when the app starts (e.g., in MainActivity.onCreate()).
     *
     * @throws IllegalStateException if WorkManager is not initialized (shouldn't happen if
     *   Application implements Configuration.Provider)
     */
    fun scheduleDailyCheck(context: Context) {
        try {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val workRequest =
                PeriodicWorkRequest.Builder(FirmwareUpdateCheckWorker::class.java, 1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)

            Log.info(tag = TAG) { "Scheduled daily firmware update check" }
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) {
                "Failed to schedule firmware update check: WorkManager not initialized"
            }
            throw e // Re-throw to allow caller to handle
        }
    }

    /**
     * Cancels the scheduled firmware update check.
     *
     * This can be called if firmware update checking should be disabled.
     */
    fun cancelCheck(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.info(tag = TAG) { "Cancelled firmware update check" }
        } catch (e: IllegalStateException) {
            Log.warn(tag = TAG, throwable = e) {
                "Failed to cancel firmware update check: WorkManager not initialized"
            }
            // Don't re-throw for cancel operations - it's okay if WorkManager isn't initialized
        }
    }
}
