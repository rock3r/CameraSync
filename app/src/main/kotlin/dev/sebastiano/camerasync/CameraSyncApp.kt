package dev.sebastiano.camerasync

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Configuration
import androidx.work.Configuration.Provider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-level configuration and dependency creation for CameraSync. */
class CameraSyncApp : Application(), Provider {
    /**
     * Holder reference for the app graph for
     * [dev.sebastiano.camerasync.di.MetroAppComponentFactory].
     */
    val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(application = this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Suppress("TooGenericExceptionCaught")
    override fun onCreate() {
        super.onCreate()
        // Initialize Khronicle logging early in application lifecycle
        initializeLogging()

        Log.info(javaClass.simpleName) {
            "-------------------- CAMERASYNC STARTED --------------------"
        }

        // Register notification channels early so they're available before any service tries to use
        // them
        registerNotificationChannel(this)

        // Heavy initialization and WorkManager scheduling are moved to the background to avoid
        // main thread jank during startup.
        applicationScope.launch(Dispatchers.IO) {
            // Accessing appGraph for the first time builds it. Doing it here prevents
            // the main thread from blocking.
            try {
                val widgetUpdateManager = appGraph.widgetUpdateManager()
                // Pass applicationScope explicitly to maintain lifecycle semantics - the widget
                // update manager launches a long-running coroutine that should be tied to the
                // application scope, not the initialization coroutine.
                widgetUpdateManager.start(applicationScope)
            } catch (e: Exception) {
                Log.error("CameraSyncApp", throwable = e) {
                    "Failed to initialize widget update manager"
                }
            }

            try {
                // Schedule daily firmware update checks
                FirmwareUpdateScheduler.scheduleDailyCheck(this@CameraSyncApp)
            } catch (e: Exception) {
                // Catch all exceptions to ensure app doesn't crash during background initialization
                Log.error("CameraSyncApp", throwable = e) {
                    "Failed to schedule daily firmware update checks"
                }
            }
        }

        // Generate widget preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applicationScope.launch {
                Log.info("CameraSyncApp") { "Generating widget preview" }
                val glanceManager = GlanceAppWidgetManager(this@CameraSyncApp)
                glanceManager.setWidgetPreviews(SyncWidgetReceiver::class)
            }
        }
    }

    /**
     * Provides WorkManager configuration with custom factory for firmware update checks.
     *
     * This property is accessed automatically by WorkManager when it needs to initialize, avoiding
     * initialization conflicts and ensuring our custom factory is always used. Uses a lazy getter
     * to ensure appGraph is initialized before accessing it.
     */
    @Suppress("TooGenericExceptionCaught")
    override val workManagerConfiguration: Configuration
        get() =
            try {
                val firmwareUpdateCheckers = appGraph.provideFirmwareUpdateCheckers()
                val workerFactory =
                    FirmwareUpdateCheckWorkerFactory(
                        pairedDevicesRepository = appGraph.pairedDevicesRepository(),
                        firmwareUpdateCheckers = firmwareUpdateCheckers,
                    )
                Configuration.Builder().setWorkerFactory(workerFactory).build()
            } catch (e: Exception) {
                // Fallback to default configuration if graph initialization fails, ensuring
                // WorkManager
                // doesn't crash the app during on-demand initialization.
                Log.error("CameraSyncApp", throwable = e) {
                    "Failed to initialize custom WorkManager configuration"
                }
                Configuration.Builder().build()
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
