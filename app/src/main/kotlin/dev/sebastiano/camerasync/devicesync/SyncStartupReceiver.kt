package dev.sebastiano.camerasync.devicesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sebastiano.camerasync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.camerasync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository =
                    DataStorePairedDevicesRepository(appContext.pairedDevicesDataStoreV2)
                val handler =
                    SyncStartupHandler(
                        repository = repository,
                        startObserving = { macAddress ->
                            val deviceManager =
                                appContext.getSystemService(Context.COMPANION_DEVICE_SERVICE)
                                    as android.companion.CompanionDeviceManager
                            deviceManager.startObservingDevicePresence(macAddress)
                        },
                    )
                handler.registerPresenceForEnabledDevices()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal class SyncStartupHandler(
    private val repository: PairedDevicesRepository,
    private val startObserving: (String) -> Unit,
) {
    suspend fun registerPresenceForEnabledDevices() {
        val enabledDevices = repository.enabledDevices.first()
        val isSyncEnabled = repository.isSyncEnabled.first()
        if (enabledDevices.isEmpty() || !isSyncEnabled) return

        enabledDevices.forEach { device ->
            try {
                startObserving(device.macAddress)
            } catch (_: Exception) {
                // Ignore observation errors at boot; will retry when app opens.
            }
        }
    }
}
