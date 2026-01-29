package dev.sebastiano.camerasync

import android.app.Application
import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.Log
import com.juul.khronicle.Logger
import dev.sebastiano.camerasync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.camerasync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.camerasync.devicesync.PresenceObservationManager
import dev.sebastiano.camerasync.di.AppGraph
import dev.sebastiano.camerasync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.zacsweers.metro.createGraphFactory
import kotlin.getValue

/** Application-level configuration and dependency creation for CameraSync. */
class CameraSyncApp : Application() {
    /** Holder reference for the app graph for [MetroAppComponentFactory]. */
    val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(application = this) }

    private var presenceObservationManager: PresenceObservationManager? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize Khronicle logging early in application lifecycle
        initializeLogging()

        // Start presence observations for ALL paired devices as soon as app starts
        // This ensures CDM callbacks work even if the app was closed or service wasn't running
        startPresenceObservations()
    }

    private fun startPresenceObservations() {
        try {
            // Create dependencies directly (don't wait for appGraph to be fully initialized)
            val pairedDevicesRepository = DataStorePairedDevicesRepository(pairedDevicesDataStoreV2)
            val vendorRegistry =
                DefaultCameraVendorRegistry(vendors = listOf(RicohCameraVendor, SonyCameraVendor))
            val companionDeviceManagerHelper = CompanionDeviceManagerHelper(this, vendorRegistry)

            presenceObservationManager =
                PresenceObservationManager(
                    pairedDevicesRepository = pairedDevicesRepository,
                    companionDeviceManagerHelper = companionDeviceManagerHelper,
                )
            presenceObservationManager?.start()
            Log.info(tag = TAG) { "Started presence observation manager" }
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Failed to start presence observation manager" }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        presenceObservationManager?.stop()
        presenceObservationManager = null
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
