package dev.sebastiano.camerasync

import android.app.Application
import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.Log
import com.juul.khronicle.Logger
import dev.sebastiano.camerasync.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import kotlin.getValue

/** Application-level configuration and dependency creation for CameraSync. */
class CameraSyncApp : Application() {
    /** Holder reference for the app graph for [MetroAppComponentFactory]. */
    val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(application = this) }

    override fun onCreate() {
        super.onCreate()
        // Initialize Khronicle logging early in application lifecycle
        initializeLogging()
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
