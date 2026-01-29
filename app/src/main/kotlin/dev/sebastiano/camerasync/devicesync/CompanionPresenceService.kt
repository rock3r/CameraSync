package dev.sebastiano.camerasync.devicesync

// import android.companion.DevicePresenceEvent // Uncomment when SDK platform includes this class
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

    override fun onCreate() {
        super.onCreate()
        Log.info(tag = TAG) {
            "CompanionPresenceService created. Android should bind this service when devices appear."
        }
    }

    // Android 16+ (API 36+): New API - automatically called when device presence changes
    // The service is automatically bound/unbound based on device presence
    // NOTE: Uncomment when SDK platform includes DevicePresenceEvent class
    /*
    override fun onDeviceEvent(event: DevicePresenceEvent) {
        val associationInfo = event.associationInfo
        val macAddress = associationInfo.deviceMacAddress?.toString()?.uppercase()
        val eventType = event.eventType

        Log.info(tag = TAG) {
            "onDeviceEvent called by Android CDM (API 36+): " +
                "macAddress=$macAddress, eventType=$eventType"
        }

        when (eventType) {
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            DevicePresenceEvent.EVENT_BT_CONNECTED -> {
                Log.info(tag = TAG) {
                    "Device appeared (eventType=$eventType): $macAddress"
                }
                handlePresenceChange(associationInfo, isPresent = true)
            }
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> {
                Log.info(tag = TAG) {
                    "Device disappeared (eventType=$eventType): $macAddress"
                }
                handlePresenceChange(associationInfo, isPresent = false)
            }
            DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED -> {
                Log.info(tag = TAG) {
                    "Association removed (eventType=$eventType): $macAddress"
                }
                handlePresenceChange(associationInfo, isPresent = false)
            }
            else -> {
                Log.warn(tag = TAG) {
                    "Unknown device presence event type: $eventType for $macAddress"
                }
            }
        }
    }
    */

    private fun handlePresenceChange(associationInfo: AssociationInfo, isPresent: Boolean) {
        val macAddress = associationInfo.deviceMacAddress?.toString()?.uppercase()
        if (macAddress == null) {
            Log.warn(tag = TAG) {
                "Received presence event but deviceMacAddress is null: $associationInfo"
            }
            return
        }
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
                            Log.info(tag = TAG) {
                                "Starting service for presence callback: $deviceAddress"
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
                            Log.info(tag = TAG) {
                                "Updating service for presence callback: $deviceAddress"
                            }
                            // Use startForegroundService even for disappear events to avoid
                            // BackgroundServiceStartNotAllowedException if app is in background
                            try {
                                ContextCompat.startForegroundService(
                                    appContext,
                                    MultiDeviceSyncService.createPresenceIntent(
                                        appContext,
                                        deviceAddress,
                                        false,
                                    ),
                                )
                            } catch (e: Exception) {
                                // If service isn't running, this is fine - just log and continue
                                Log.debug(tag = TAG) {
                                    "Could not update service for disappeared device (service may not be running): $deviceAddress"
                                }
                            }
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
        val device = repository.getDevice(macAddress)
        if (device == null) {
            Log.warn(tag = TAG) { "Received presence callback for unpaired device: $macAddress" }
            return
        }

        val syncEnabled = repository.isSyncEnabled.first()
        Log.info(tag = TAG) {
            "Processing presence for $macAddress: isPresent=$isPresent, syncEnabled=$syncEnabled, deviceEnabled=${device.isEnabled}"
        }

        if (!syncEnabled || !device.isEnabled) {
            Log.info(tag = TAG) {
                "Ignoring presence for $macAddress (syncEnabled=$syncEnabled, deviceEnabled=${device.isEnabled})"
            }
            return
        }

        if (isPresent) {
            Log.info(tag = TAG) { "Device appeared, starting sync service for $macAddress" }
            startPresenceSync(macAddress, true)
        } else if (isServiceRunning()) {
            Log.info(tag = TAG) { "Device disappeared, updating service for $macAddress" }
            startPresenceSync(macAddress, false)
        }
    }
}

private const val TAG = "CompanionPresenceService"
