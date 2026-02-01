package dev.sebastiano.camerasync.devicesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncStartupReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.info(javaClass.name) { "Received ${intent.action} intent" }

        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val pairedDevicesRepository = appGraph.pairedDevicesRepository()
        val widgetUpdateHelper = appGraph.widgetUpdateHelper()

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val isSyncEnabled = pairedDevicesRepository.isSyncEnabled.first()
                Log.debug(javaClass.name) { "Checking if sync is enabled: $isSyncEnabled" }

                if (isSyncEnabled) {
                    val hasEnabledDevices = pairedDevicesRepository.hasEnabledDevices()
                    if (hasEnabledDevices) {
                        Log.info(javaClass.name) { "Starting MultiDeviceSyncService..." }
                        val serviceIntent = MultiDeviceSyncService.createStartIntent(context)
                        context.startForegroundService(serviceIntent)
                    } else {
                        Log.info(javaClass.name) { "Sync enabled but no devices enabled, not starting service." }
                    }
                }

                Log.info(javaClass.name) { "Updating widgets..." }
                widgetUpdateHelper.updateWidgets()
            } catch (e: Exception) {
                Log.error(javaClass.name, throwable = e) { "Error during startup sync initialization" }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
