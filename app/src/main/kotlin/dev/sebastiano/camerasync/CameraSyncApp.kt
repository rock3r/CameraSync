package dev.sebastiano.camerasync

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.Log
import com.juul.khronicle.Logger
import dev.sebastiano.camerasync.devicesync.registerNotificationChannel
import dev.sebastiano.camerasync.di.AppGraph
import dev.sebastiano.camerasync.firmware.FirmwareUpdateCheckWorkerFactory
import dev.sebastiano.camerasync.firmware.FirmwareUpdateScheduler
import dev.sebastiano.camerasync.widget.SyncWidgetReceiver
import dev.zacsweers.metro.createGraphFactory
import kotlin.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-level configuration and dependency creation for CameraSync. */
class CameraSyncApp : Application() {
    /**
     * Holder reference for the app graph for
     * [dev.sebastiano.camerasync.di.MetroAppComponentFactory].
     */
    val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(application = this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Initialize Khronicle logging early in application lifecycle
        initializeLogging()

        // Register notification channels early so they're available before any service tries to use
        // them
        registerNotificationChannel(this)

        // Initialize WorkManager with custom factory for firmware update checks
        try {
            val firmwareUpdateCheckers = appGraph.provideFirmwareUpdateCheckers()
            val workerFactory =
                FirmwareUpdateCheckWorkerFactory(
                    pairedDevicesRepository = appGraph.pairedDevicesRepository(),
                    firmwareUpdateCheckers = firmwareUpdateCheckers,
                )
            WorkManager.initialize(
                this,
                Configuration.Builder().setWorkerFactory(workerFactory).build(),
            )

            // Schedule daily firmware update checks
            FirmwareUpdateScheduler.scheduleDailyCheck(this)
        } catch (e: IllegalStateException) {
            // WorkManager already initialized (e.g., in tests) - just schedule the check
            Log.warn("CameraSyncApp") {
                "WorkManager already initialized, skipping custom factory setup"
            }
            FirmwareUpdateScheduler.scheduleDailyCheck(this)
        }

        // Start observing state for widget updates
        appGraph.widgetUpdateManager().start(applicationScope)

        // Generate widget preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @OptIn(DelicateCoroutinesApi::class) // Fine for this case
            GlobalScope.launch {
                Log.info("CameraSyncApp") { "Generating widget preview" }
                val glanceManager = GlanceAppWidgetManager(this@CameraSyncApp)
                glanceManager.setWidgetPreviews(SyncWidgetReceiver::class)
            }
        }
    }

    companion object {
        /**
         * Initializes Khronicle logging with the provided logger.
         *
         * @param logger The logger to use. Defaults to ConsoleLogger for production.
         */
        fun initializeLogging(logger: Logger = ConsoleLogger) {
            Log.dispatcher.install(logger)
        }
    }
}
