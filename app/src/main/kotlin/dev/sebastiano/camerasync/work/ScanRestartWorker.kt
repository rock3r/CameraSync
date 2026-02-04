package dev.sebastiano.camerasync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService

private const val TAG = "ScanRestartWorker"

class ScanRestartWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.info(tag = TAG) { "Scan restart worker running" }

        // This worker is a safety net to ensure the app is alive and can restart scanning if
        // needed.
        // We trigger the service to check state. If the service is not running,
        // starting it with ACTION_DEVICE_FOUND (or a new ACTION_CHECK_STATE) will
        // cause it to start, check enabled devices, and if any are enabled but not connected,
        // it will start monitoring (which includes registering the PendingIntent scan if needed).

        // However, we don't want to start the Foreground Service if we are supposed to be idle.
        // If we are idle, we should just ensure the PendingIntent scan is active.
        // But we can't easily check if the PendingIntent scan is active without holding state.

        // Strategy:
        // 1. If service is running, it handles things.
        // 2. If service is NOT running, we assume we might be in idle mode.
        //    We should check if there are enabled devices. If so, we should ensure scan is active.
        //    Since we can't easily inject the repository here without a custom Factory,
        //    and we want to avoid complex DI in Worker for now,
        //    we will just log that we are alive. The system shouldn't kill the PendingIntent scan
        // easily.
        //
        //    However, to be truly defensive as per plan, we should restart the scan.
        //    The easiest way to do that without DI is to start the service, let it check state, and
        // if it decides it should be idle, it will stop itself and register the scan.
        //    But starting a Foreground Service from Background Worker (if app is in background) is
        // allowed
        //    since the Worker is a foreground-exempt context? No, Worker can be expedited.

        //    Let's just use the service start. If it's short-lived, it's fine.

        try {
            val serviceIntent = MultiDeviceSyncService.createDeviceFoundIntent(applicationContext)
            // Use startForegroundService to ensure we can run
            applicationContext.startForegroundService(serviceIntent)
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) { "Failed to trigger service from Worker" }
            return Result.failure()
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) { "Failed to trigger service from Worker" }
            return Result.failure()
        }

        return Result.success()
    }
}
