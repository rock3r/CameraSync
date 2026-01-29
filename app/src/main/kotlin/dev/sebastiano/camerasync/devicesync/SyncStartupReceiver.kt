package dev.sebastiano.camerasync.devicesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.flow.first

class SyncStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        // Note: CDM presence observations removed - they don't work reliably.
        // The periodic check in MultiDeviceSyncCoordinator handles reconnection.
        // This receiver is kept for potential future use but doesn't do anything at boot.
        val pendingResult = goAsync()
        pendingResult.finish()
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
