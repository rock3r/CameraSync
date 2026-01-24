package dev.sebastiano.camerasync.devicesync

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "MultiDeviceSyncService"

/**
 * Foreground service that manages synchronization with multiple camera devices.
 *
 * This service handles:
 * - Running as a foreground service for background operation
 * - Managing connections to multiple enabled devices
 * - Centralized location collection shared across all devices
 * - Automatic reconnection attempts
 * - Notifications showing sync status
 *
 * The service starts automatically when there are enabled paired devices and stops when all devices
 * are disabled or disconnected.
 */
class MultiDeviceSyncService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + CoroutineName("MultiDeviceSyncService") + SupervisorJob()

    private val binder by lazy { MultiDeviceSyncServiceBinder() }

    @Inject lateinit var vendorRegistry: CameraVendorRegistry
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var cameraRepository: CameraRepository
    @Inject lateinit var pairedDevicesRepository: PairedDevicesRepository
    @Inject lateinit var notificationBuilder: NotificationBuilder
    @Inject lateinit var intentFactory: IntentFactory
    @Inject lateinit var pendingIntentFactory: PendingIntentFactory
    @Inject lateinit var companionDeviceManagerHelper: CompanionDeviceManagerHelper
    @Inject lateinit var locationCollectorFactory: DefaultLocationCollector.Factory

    private val locationCollector by lazy { locationCollectorFactory.create(this) }

    private val syncCoordinator by lazy {
        MultiDeviceSyncCoordinator(
            cameraRepository = cameraRepository,
            locationCollector = locationCollector,
            vendorRegistry = vendorRegistry,
            pairedDevicesRepository = pairedDevicesRepository,
            companionDeviceManagerHelper = companionDeviceManagerHelper,
            coroutineScope = this,
        )
    }

    private val vibrator by lazy { SyncErrorVibrator(applicationContext) }

    private val _serviceState =
        MutableStateFlow<MultiDeviceSyncServiceState>(MultiDeviceSyncServiceState.Starting)

    /** The current state of the sync service. */
    val serviceState: StateFlow<MultiDeviceSyncServiceState> = _serviceState.asStateFlow()

    /** Flow of device states from the coordinator. */
    val deviceStates: StateFlow<Map<String, DeviceConnectionState>>
        get() = syncCoordinator.deviceStates

    /** Flow that emits true when a scan/discovery pass is in progress. */
    val isScanning: StateFlow<Boolean>
        get() = syncCoordinator.isScanning

    private var stateCollectionJob: Job? = null
    private var deviceMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        (applicationContext as CameraSyncApp).appGraph.inject(this)
        _isRunning.value = true

        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        Log.debug(tag = TAG) { "App brought to foreground, stopping vibration" }
                        vibrator.stop()
                    }
                }
            )
    }

    override fun onBind(intent: Intent): IBinder {
        if (!checkPermissions()) {
            throw RuntimeException("Required permissions not granted")
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.info(tag = TAG) { "Received stop intent, stopping all syncs..." }
                launch {
                    pairedDevicesRepository.setSyncEnabled(false)
                    stopAllAndShutdown()
                }
            }
            ACTION_REFRESH -> {
                Log.info(tag = TAG) { "Received refresh intent, reconnecting to devices..." }
                if (!checkPermissions()) return START_NOT_STICKY
                startForegroundService()
                startDeviceMonitoring()
                launch { refreshConnections() }
            }
            ACTION_DEVICE_APPEARED -> {
                Log.info(tag = TAG) { "Device presence appeared, starting sync..." }
                if (!checkPermissions()) return START_NOT_STICKY
                startForegroundService()
                startDeviceMonitoring()
                intent.getStringExtra(EXTRA_DEVICE_ADDRESS)?.let { macAddress ->
                    syncCoordinator.setDevicePresence(macAddress, true)
                }
            }
            ACTION_DEVICE_DISAPPEARED -> {
                Log.info(tag = TAG) { "Device presence disappeared, stopping sync..." }
                if (!checkPermissions()) return START_NOT_STICKY
                startForegroundService()
                startDeviceMonitoring()
                intent.getStringExtra(EXTRA_DEVICE_ADDRESS)?.let { macAddress ->
                    syncCoordinator.setDevicePresence(macAddress, false)
                    launch { syncCoordinator.stopDeviceSync(macAddress) }
                }
            }
            else -> {
                if (!checkPermissions()) return START_NOT_STICKY
                startForegroundService()
                startDeviceMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createMultiDeviceNotification(
                    notificationBuilder = notificationBuilder,
                    pendingIntentFactory = pendingIntentFactory,
                    intentFactory = intentFactory,
                    context = this,
                    connectedCount = 0,
                    totalEnabled = 0,
                    lastSyncTime = null,
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            _serviceState.value =
                MultiDeviceSyncServiceState.Running(
                    connectedDeviceCount = 0,
                    enabledDeviceCount = 0,
                )
        } catch (e: Exception) {
            if (e is ForegroundServiceStartNotAllowedException) {
                Log.error(tag = TAG, throwable = e) { "Cannot start foreground service" }
            }
            _serviceState.value =
                MultiDeviceSyncServiceState.Error("Failed to start service: ${e.message}")
            vibrator.vibrate()
        }
    }

    /** Starts monitoring enabled devices and connecting to them. */
    private fun startDeviceMonitoring() {
        if (deviceMonitorJob != null) return

        // Start background monitoring in the coordinator
        syncCoordinator.startBackgroundMonitoring(pairedDevicesRepository.enabledDevices)

        // Still need to monitor enabled devices here to stop service when none are enabled
        deviceMonitorJob = launch {
            pairedDevicesRepository.enabledDevices.collect { enabledDevices ->
                if (enabledDevices.isEmpty()) {
                    Log.info(tag = TAG) { "No enabled devices, stopping service" }
                    stopAllAndShutdown()
                }
            }
        }

        // Start state collection for notification updates
        stateCollectionJob = launch {
            combine(
                    syncCoordinator.deviceStates,
                    pairedDevicesRepository.enabledDevices,
                    syncCoordinator.presentDevices,
                    syncCoordinator.isScanning,
                ) { deviceStates, enabledDevices, presentDevices, isScanning ->
                    val connectedCount =
                        deviceStates.count { (_, state) ->
                            state is DeviceConnectionState.Connected ||
                                state is DeviceConnectionState.Syncing
                        }
                    val presentCount = presentDevices.size

                    // Get last sync time from any syncing device
                    val lastSyncTime =
                        deviceStates.values
                            .filterIsInstance<DeviceConnectionState.Syncing>()
                            .mapNotNull { it.lastSyncInfo?.syncTime }
                            .maxOrNull()

                    // Trigger vibration if any device is in error state
                    if (deviceStates.values.any { it is DeviceConnectionState.Error }) {
                        vibrator.vibrate()
                    }

                    Quintuple(
                        connectedCount,
                        enabledDevices.size,
                        presentCount,
                        isScanning,
                        lastSyncTime,
                    )
                }
                .collect { (connectedCount, enabledCount, presentCount, isScanning, lastSyncTime) ->
                    if (presentCount == 0 && connectedCount == 0 && !isScanning) {
                        stopAllAndShutdown()
                        return@collect
                    }
                    updateNotification(connectedCount, enabledCount, lastSyncTime)
                    _serviceState.value =
                        MultiDeviceSyncServiceState.Running(
                            connectedDeviceCount = connectedCount,
                            enabledDeviceCount = enabledCount,
                        )
                }
        }
    }

    /**
     * Refreshes connections to all enabled devices. Disconnects and reconnects to trigger a fresh
     * scan.
     */
    private fun refreshConnections() {
        syncCoordinator.refreshConnections()
    }

    /** Connects to a specific device. */
    fun connectDevice(device: PairedDevice) {
        launch { syncCoordinator.startDeviceSync(device) }
    }

    /** Disconnects from a specific device without disabling it. */
    fun disconnectDevice(macAddress: String) {
        launch { syncCoordinator.stopDeviceSync(macAddress) }
    }

    private fun updateNotification(
        connectedCount: Int,
        enabledCount: Int,
        lastSyncTime: java.time.ZonedDateTime?,
    ) {
        val notification =
            createMultiDeviceNotification(
                notificationBuilder = notificationBuilder,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                context = this,
                connectedCount = connectedCount,
                totalEnabled = enabledCount,
                lastSyncTime = lastSyncTime,
            )

        if (
            PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PermissionChecker.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun stopAllAndShutdown() {
        syncCoordinator.stopAllDevices()
        deviceMonitorJob?.cancel()
        deviceMonitorJob = null
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        _serviceState.value = MultiDeviceSyncServiceState.Stopped
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions =
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        for (permission in requiredPermissions) {
            val result = PermissionChecker.checkSelfPermission(this, permission)
            if (result != PermissionChecker.PERMISSION_GRANTED) {
                stopAndNotifyMissingPermission(permission)
                return false
            }
        }
        return true
    }

    private fun stopAndNotifyMissingPermission(missingPermission: String) {
        stopSelf()

        val notification =
            createErrorNotificationBuilder(this)
                .setContentTitle("Missing permission")
                .setContentText("Cannot sync with cameras: $missingPermission is required")
                .build()

        val hasNotificationPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasNotificationPermission) {
            Log.error(tag = TAG) {
                "Cannot show missing permission notification: $missingPermission"
            }
            return
        }

        vibrator.vibrate()
        NotificationManagerCompat.from(this).notify(ERROR_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceMonitorJob?.cancel()
        stateCollectionJob?.cancel()
        _isRunning.value = false
    }

    inner class MultiDeviceSyncServiceBinder : Binder() {
        fun getService(): MultiDeviceSyncService = this@MultiDeviceSyncService
    }

    companion object {
        private const val NOTIFICATION_ID = 112
        private const val ERROR_NOTIFICATION_ID = 124
        private const val LOCATION_UPDATE_INTERVAL_SECONDS = 60L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        const val ACTION_STOP = "dev.sebastiano.camerasync.STOP_ALL_SYNC"
        const val ACTION_REFRESH = "dev.sebastiano.camerasync.REFRESH_CONNECTIONS"
        const val ACTION_DEVICE_APPEARED = "dev.sebastiano.camerasync.DEVICE_APPEARED"
        const val ACTION_DEVICE_DISAPPEARED = "dev.sebastiano.camerasync.DEVICE_DISAPPEARED"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        const val STOP_REQUEST_CODE = 667
        const val REFRESH_REQUEST_CODE = 668

        fun getInstanceFrom(binder: Binder): MultiDeviceSyncService =
            (binder as MultiDeviceSyncServiceBinder).getService()

        fun createStopIntent(context: Context): Intent =
            Intent(context, MultiDeviceSyncService::class.java).apply { action = ACTION_STOP }

        fun createRefreshIntent(context: Context): Intent =
            Intent(context, MultiDeviceSyncService::class.java).apply { action = ACTION_REFRESH }

        fun createPresenceIntent(context: Context, macAddress: String, isPresent: Boolean): Intent =
            Intent(context, MultiDeviceSyncService::class.java).apply {
                action = if (isPresent) ACTION_DEVICE_APPEARED else ACTION_DEVICE_DISAPPEARED
                putExtra(EXTRA_DEVICE_ADDRESS, macAddress)
            }

        fun createStartIntent(context: Context): Intent =
            Intent(context, MultiDeviceSyncService::class.java)
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)

/** Represents the state of the multi-device sync service. */
sealed interface MultiDeviceSyncServiceState {
    /** Service is starting up. */
    data object Starting : MultiDeviceSyncServiceState

    /** Service is running and managing devices. */
    data class Running(val connectedDeviceCount: Int, val enabledDeviceCount: Int) :
        MultiDeviceSyncServiceState

    /** Service encountered an error. */
    data class Error(val message: String) : MultiDeviceSyncServiceState

    /** Service has been stopped. */
    data object Stopped : MultiDeviceSyncServiceState
}
