package dev.sebastiano.camerasync.devicesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import java.io.IOException
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

        Log.info(javaClass.simpleName) { "Received ${intent.action} intent" }

        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val pairedDevicesRepository = appGraph.pairedDevicesRepository()
        val widgetUpdateHelper = appGraph.widgetUpdateHelper()

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val isSyncEnabled = pairedDevicesRepository.isSyncEnabled.first()
                Log.debug(javaClass.simpleName) { "Checking if sync is enabled: $isSyncEnabled" }

                if (isSyncEnabled) {
                    val hasEnabledDevices = pairedDevicesRepository.hasEnabledDevices()
                    if (hasEnabledDevices) {
                        Log.info(javaClass.simpleName) { "Starting MultiDeviceSyncService..." }
                        val serviceIntent = MultiDeviceSyncService.createStartIntent(context)
                        context.startForegroundService(serviceIntent)
                    } else {
                        Log.info(javaClass.simpleName) {
                            "Sync enabled but no devices enabled, not starting service."
                        }
                    }
                }

                Log.info(javaClass.simpleName) { "Updating widgets..." }
                widgetUpdateHelper.updateWidgets()
            } catch (e: IOException) {
                Log.error(javaClass.simpleName, throwable = e) {
                    "Error during startup sync initialization"
                }
            } catch (e: IllegalStateException) {
                Log.error(javaClass.simpleName, throwable = e) {
                    "Error during startup sync initialization"
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
