package dev.sebastiano.camerasync.devicesync

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import androidx.core.content.ContextCompat
import dev.sebastiano.camerasync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.camerasync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CompanionPresenceService : CompanionDeviceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        handlePresenceChange(associationInfo, isPresent = true)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        handlePresenceChange(associationInfo, isPresent = false)
    }

    private fun handlePresenceChange(associationInfo: AssociationInfo, isPresent: Boolean) {
        val macAddress = associationInfo.deviceMacAddress?.toString() ?: return
        val appContext = applicationContext
        scope.launch {
            val repository = DataStorePairedDevicesRepository(appContext.pairedDevicesDataStoreV2)
            val handler =
                PresenceSyncHandler(
                    repository = repository,
                    isServiceRunning = { MultiDeviceSyncService.isRunning.value },
                    startPresenceSync = { deviceAddress, shouldStart ->
                        if (shouldStart) {
                            ContextCompat.startForegroundService(
                                appContext,
                                MultiDeviceSyncService.createPresenceIntent(
                                    appContext,
                                    deviceAddress,
                                    true,
                                ),
                            )
                        } else {
                            appContext.startService(
                                MultiDeviceSyncService.createPresenceIntent(
                                    appContext,
                                    deviceAddress,
                                    false,
                                )
                            )
                        }
                    },
                )
            handler.handlePresence(macAddress, isPresent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

internal class PresenceSyncHandler(
    private val repository: PairedDevicesRepository,
    private val isServiceRunning: () -> Boolean,
    private val startPresenceSync: (String, Boolean) -> Unit,
) {
    suspend fun handlePresence(macAddress: String, isPresent: Boolean) {
        val device = repository.getDevice(macAddress) ?: return
        val syncEnabled = repository.isSyncEnabled.first()
        if (!syncEnabled || !device.isEnabled) return

        if (isPresent) {
            startPresenceSync(macAddress, true)
        } else if (isServiceRunning()) {
            startPresenceSync(macAddress, false)
        }
    }
}
