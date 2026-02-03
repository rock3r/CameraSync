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
     */
    fun scheduleDailyCheck(context: Context) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =
            PeriodicWorkRequest.Builder(FirmwareUpdateCheckWorker::class.java, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)

        Log.info(tag = TAG) { "Scheduled daily firmware update check" }
    }

    /**
     * Cancels the scheduled firmware update check.
     *
     * This can be called if firmware update checking should be disabled.
     */
    fun cancelCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.info(tag = TAG) { "Cancelled firmware update check" }
    }
}
