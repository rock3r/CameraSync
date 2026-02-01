package dev.sebastiano.camerasync

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.Log
import com.juul.khronicle.Logger
import dev.sebastiano.camerasync.di.AppGraph
import dev.sebastiano.camerasync.widget.LockScreenSyncWidgetReceiver
import dev.sebastiano.camerasync.widget.LauncherSyncWidgetReceiver
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

        // Start observing state for widget updates
        appGraph.widgetUpdateManager().start(applicationScope)

        // Generate widget preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @OptIn(DelicateCoroutinesApi::class) // Fine for this case
            GlobalScope.launch {
                Log.info("CameraSyncApp") { "Generating widget preview" }
                val glanceManager = GlanceAppWidgetManager(this@CameraSyncApp)
                glanceManager.setWidgetPreviews(LauncherSyncWidgetReceiver::class)
                glanceManager.setWidgetPreviews(LockScreenSyncWidgetReceiver::class)
            }
        }
    }

    companion object {
        private const val TAG = "CameraSyncApp"

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
