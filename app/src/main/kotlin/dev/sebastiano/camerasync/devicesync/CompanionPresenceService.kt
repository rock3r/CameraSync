package dev.sebastiano.camerasync.devicesync

import android.Manifest
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Inject
class CompanionPresenceService(private val pairedDevicesRepository: PairedDevicesRepository) :
    CompanionDeviceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Deprecated("Deprecated in platform API", level = DeprecationLevel.WARNING)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        handlePresenceChange(associationInfo, isPresent = true)
    }

    @Deprecated("Deprecated in platform API", level = DeprecationLevel.WARNING)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        handlePresenceChange(associationInfo, isPresent = false)
    }

    private fun handlePresenceChange(associationInfo: AssociationInfo, isPresent: Boolean) {
        val macAddress = associationInfo.deviceMacAddress?.toString()?.uppercase() ?: return
        Log.info(tag = TAG) { "Companion presence event for $macAddress: isPresent=$isPresent" }
        val appContext = applicationContext
        scope.launch {
            val handler =
                PresenceSyncHandler(
                    repository = pairedDevicesRepository,
                    isServiceRunning = { MultiDeviceSyncService.isRunning.value },
                    startPresenceSync = { deviceAddress, shouldStart ->
                        if (shouldStart) {
                            val missingPermission = findMissingRequiredPermission(appContext)
                            if (missingPermission != null) {
                                Log.warn(tag = TAG) {
                                    "Skipping foreground start; missing permission: $missingPermission"
                                }
                                return@PresenceSyncHandler
                            }
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

    private fun findMissingRequiredPermission(context: Context): String? {
        val requiredPermissions =
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        return requiredPermissions.firstOrNull { permission ->
            PermissionChecker.checkSelfPermission(context, permission) !=
                PermissionChecker.PERMISSION_GRANTED
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
        if (!syncEnabled || !device.isEnabled) {
            Log.info(tag = TAG) {
                "Ignoring presence for $macAddress (syncEnabled=$syncEnabled, deviceEnabled=${device.isEnabled})"
            }
            return
        }

        if (isPresent) {
            startPresenceSync(macAddress, true)
        } else if (isServiceRunning()) {
            startPresenceSync(macAddress, false)
        }
    }
}

private const val TAG = "CompanionPresenceService"
