package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository

/**
 * Factory for creating [FirmwareUpdateCheckWorker] instances with dependencies.
 *
 * This factory is registered with WorkManager to allow dependency injection into workers.
 */
class FirmwareUpdateCheckWorkerFactory(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val firmwareUpdateCheckers: List<FirmwareUpdateChecker>,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            FirmwareUpdateCheckWorker::class.java.name ->
                FirmwareUpdateCheckWorker(
                    appContext,
                    workerParameters,
                    pairedDevicesRepository,
                    firmwareUpdateCheckers,
                )
            else -> null
        }
    }
}
