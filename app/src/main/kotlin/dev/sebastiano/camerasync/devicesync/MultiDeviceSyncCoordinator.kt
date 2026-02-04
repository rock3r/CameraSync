package dev.sebastiano.camerasync.devicesync

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ble.ScanReceiver
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.LocationSyncInfo
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.toCamera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.util.DeviceNameProvider
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private const val TAG = "MultiDeviceSyncCoordinator"
private const val PERIODIC_CHECK_INTERVAL_MS = 30_000L
private const val CONNECTION_TIMEOUT_MS = 90_000L
private const val PASSIVE_SCAN_REQUEST_CODE = 999

/**
 * Coordinates synchronization with multiple camera devices simultaneously.
 *
 * This coordinator manages:
 * - Multiple concurrent camera connections
 * - Centralized location collection shared across all devices
 * - Per-device connection state
 * - Broadcasting location updates to all connected devices
 */
class MultiDeviceSyncCoordinator
@AssistedInject
constructor(
    private val context: Context,
    private val cameraRepository: CameraRepository,
    private val vendorRegistry: CameraVendorRegistry,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val pendingIntentFactory: PendingIntentFactory,
    private val connectionManager: DeviceConnectionManager,
    private val firmwareManager: DeviceFirmwareManager,
    private val deviceNameProvider: DeviceNameProvider,
    @Assisted private val locationCollector: LocationCollectionCoordinator,
    @Assisted private val coroutineScope: CoroutineScope,
) {

    /** Factory for creating [MultiDeviceSyncCoordinator] with assisted injection. */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [MultiDeviceSyncCoordinator].
         *
         * @param locationCollector The coordinator for centralized location collection.
         * @param coroutineScope The scope in which to run synchronization jobs.
         */
        fun create(
            @Assisted locationCollector: LocationCollectionCoordinator,
            @Assisted coroutineScope: CoroutineScope,
        ): MultiDeviceSyncCoordinator
    }

    private val _deviceStates = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())

    /** A flow of the current connection states for all devices, keyed by MAC address. */
    val deviceStates: StateFlow<Map<String, DeviceConnectionState>> = _deviceStates.asStateFlow()

    private val _isScanning = MutableStateFlow(false)

    /**
     * A flow that emits true when the coordinator is actively scanning for or connecting to
     * devices.
     */
    val isScanning: StateFlow<Boolean> =
        combine(_isScanning, _deviceStates) { scanning, states ->
                scanning ||
                    states.values.any {
                        it is DeviceConnectionState.Connecting ||
                            it is DeviceConnectionState.Searching
                    }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val _presentDevices = MutableStateFlow<Set<String>>(emptySet())

    /** A flow of MAC addresses of devices that have been recently seen or are connected. */
    val presentDevices: StateFlow<Set<String>> = _presentDevices.asStateFlow()

    private val scanMutex = Mutex()
    private val connectionMutex = Mutex()

    private var backgroundMonitoringJob: Job? = null
    private var periodicCheckJob: Job? = null
    private var locationSyncJob: Job? = null
    private val enabledDevicesFlow = MutableStateFlow<List<PairedDevice>>(emptyList())

    /**
     * Starts monitoring the provided [enabledDevices] flow.
     *
     * This will automatically attempt to connect to devices as they are enabled and seen, and will
     * disconnect from devices that are disabled.
     *
     * @param enabledDevices A flow of currently enabled paired devices.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun startBackgroundMonitoring(enabledDevices: Flow<List<PairedDevice>>) {
        if (backgroundMonitoringJob != null) return

        backgroundMonitoringJob =
            coroutineScope.launch {
                var initialRefreshDone = false
                enabledDevices.collect { devices ->
                    enabledDevicesFlow.value = devices
                    val shouldIgnorePresence = !initialRefreshDone && devices.isNotEmpty()
                    if (shouldIgnorePresence) {
                        Log.info(tag = TAG) {
                            "Performing initial proactive refresh for ${devices.size} devices"
                        }
                    }
                    checkAndConnectEnabledDevices(ignorePresence = shouldIgnorePresence)
                    if (devices.isNotEmpty()) {
                        initialRefreshDone = true
                        startPeriodicCheck()
                    } else {
                        stopPeriodicCheck()
                    }
                }
            }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun startPeriodicCheck() {
        if (periodicCheckJob != null) return
        periodicCheckJob =
            coroutineScope.launch {
                while (true) {
                    delay(PERIODIC_CHECK_INTERVAL_MS)
                    val enabledDevices = enabledDevicesFlow.value
                    if (enabledDevices.isEmpty()) {
                        stopPeriodicCheck()
                        return@launch
                    }

                    val disconnectedDevices =
                        enabledDevices.filter { device ->
                            val state = getDeviceState(device.macAddress)
                            state is DeviceConnectionState.Disconnected ||
                                state is DeviceConnectionState.Unreachable ||
                                (state is DeviceConnectionState.Error && state.isRecoverable)
                        }

                    if (disconnectedDevices.isNotEmpty()) {
                        Log.info(tag = TAG) {
                            "Periodic check: Found ${disconnectedDevices.size} disconnected devices, attempting reconnect"
                        }
                        checkAndConnectEnabledDevices(ignorePresence = true)
                    }
                }
            }
    }

    private fun stopPeriodicCheck() {
        periodicCheckJob?.cancel()
        periodicCheckJob = null
    }

    /**
     * Manually triggers a refresh of all enabled device connections.
     *
     * This will proactively attempt to reconnect to all enabled devices, ignoring their current
     * presence status.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun refreshConnections() {
        Log.info(tag = TAG) { "Manual refresh requested" }
        coroutineScope.launch {
            if (enabledDevicesFlow.value.isEmpty()) {
                enabledDevicesFlow.value = pairedDevicesRepository.enabledDevices.first()
            }
            checkAndConnectEnabledDevices(ignorePresence = true)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun checkAndConnectEnabledDevices(ignorePresence: Boolean = false) {
        if (enabledDevicesFlow.value.isEmpty()) {
            enabledDevicesFlow.value = pairedDevicesRepository.enabledDevices.first()
        }

        _isScanning.value = true
        try {
            scanMutex.withLock {
                val enabledDevices = enabledDevicesFlow.value
                val enabledMacs = enabledDevices.map { it.macAddress.uppercase() }.toSet()
                val presentMacs = if (ignorePresence) enabledMacs else _presentDevices.value
                val allowUnknownPresence = !ignorePresence && presentMacs.isEmpty()

                disconnectIneligibleDevices(enabledMacs)
                connectEligibleDevices(
                    enabledDevices,
                    presentMacs,
                    allowUnknownPresence,
                    ignorePresence,
                )
            }
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun disconnectIneligibleDevices(enabledMacs: Set<String>) {
        val devicesToDisconnect =
            connectionManager.getConnections().keys.filter { mac ->
                !enabledMacs.contains(mac) && isDeviceSyncingOrConnecting(mac)
            }

        devicesToDisconnect.forEach { mac ->
            Log.info(tag = TAG) { "Device $mac is no longer eligible for sync, disconnecting..." }
            stopDeviceSync(mac)
        }
    }

    private fun isDeviceSyncingOrConnecting(macAddress: String): Boolean {
        val state = getDeviceState(macAddress)
        return state is DeviceConnectionState.Connected ||
            state is DeviceConnectionState.Syncing ||
            state is DeviceConnectionState.Connecting ||
            state is DeviceConnectionState.Searching
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun connectEligibleDevices(
        enabledDevices: List<PairedDevice>,
        presentMacs: Set<String>,
        allowUnknownPresence: Boolean,
        ignorePresence: Boolean,
    ) {
        enabledDevices.forEach { device ->
            val macAddress = device.macAddress.uppercase()
            val state = getDeviceState(macAddress)
            val isPresent =
                presentMacs.contains(macAddress) ||
                    (allowUnknownPresence && state is DeviceConnectionState.Disconnected)

            val eligibleState =
                state is DeviceConnectionState.Disconnected ||
                    state is DeviceConnectionState.Unreachable ||
                    (state is DeviceConnectionState.Error && state.isRecoverable)

            if (isPresent && eligibleState) {
                Log.debug(tag = TAG) {
                    "Device $macAddress is enabled but not connected, attempting sync..."
                }
                startDeviceSync(device)
            } else if (!isPresent && !ignorePresence) {
                Log.debug(tag = TAG) {
                    "Device $macAddress is enabled but not present, skipping connect"
                }
            }
        }
    }

    /**
     * Initiates synchronization for a specific [device].
     *
     * This includes:
     * - Scanning for the device if not already found
     * - Connecting to the device
     * - Performing initial setup (date/time, device name, geo-tagging)
     * - Reading firmware version
     * - Registering for location updates
     *
     * @param device The paired device to sync with.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun startDeviceSync(device: PairedDevice) {
        coroutineScope.launch {
            val macAddress = device.macAddress.uppercase()

            connectionMutex.withLock {
                if (isDeviceSyncingOrConnecting(macAddress)) {
                    Log.debug(tag = TAG) {
                        "Device $macAddress is already syncing or connecting, skipping"
                    }
                    return@launch
                }
                updateDeviceState(macAddress, DeviceConnectionState.Searching)
            }

            try {
                val vendor =
                    vendorRegistry.getVendorById(device.vendorId)
                        ?: run {
                            val errorMessage = context.getString(R.string.error_unknown_vendor)
                            updateDeviceState(
                                macAddress,
                                DeviceConnectionState.Error(errorMessage, isRecoverable = false),
                            )
                            return@launch
                        }

                updateDeviceState(macAddress, DeviceConnectionState.Connecting)

                val connection =
                    withTimeout(CONNECTION_TIMEOUT_MS) {
                        cameraRepository.connect(
                            device.toCamera(vendor),
                            onFound = {
                                updateDeviceState(macAddress, DeviceConnectionState.Connecting)
                            },
                        )
                    }

                val firmwareVersion = performInitialSetup(connection, device)

                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Syncing(firmwareVersion = firmwareVersion),
                )
                _presentDevices.update { it + macAddress }

                val syncJob =
                    currentCoroutineContext()[Job] ?: coroutineScope.launch {}.also { it.cancel() }
                connectionManager.addConnection(macAddress, connection, syncJob)
                locationCollector.registerDevice(macAddress)

                // Ensure location sync is running for all connected devices
                ensureLocationSyncRunning()

                firmwareManager.checkAndNotifyFirmwareUpdate(macAddress, firmwareVersion)

                connection.isConnected.filter { !it }.first()
                Log.info(tag = TAG) { "Device $macAddress disconnected" }
            } catch (e: TimeoutCancellationException) {
                Log.warn(tag = TAG, throwable = e) { "Timeout connecting to $macAddress" }
                updateDeviceState(macAddress, DeviceConnectionState.Unreachable)
            } catch (e: CancellationException) {
                Log.debug(tag = TAG, throwable = e) { "Cancelled connection to $macAddress" }
                updateDeviceState(macAddress, DeviceConnectionState.Disconnected)
                throw e
            } catch (e: IOException) {
                Log.error(tag = TAG, throwable = e) { "Error connecting to $macAddress" }
                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Error(e.message ?: "Unknown error", isRecoverable = true),
                )
            } catch (e: IllegalStateException) {
                Log.error(tag = TAG, throwable = e) { "Error connecting to $macAddress" }
                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Error(e.message ?: "Unknown error", isRecoverable = true),
                )
            } catch (e: IllegalArgumentException) {
                Log.error(tag = TAG, throwable = e) { "Error connecting to $macAddress" }
                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Error(e.message ?: "Unknown error", isRecoverable = true),
                )
            } catch (e: SecurityException) {
                Log.error(tag = TAG, throwable = e) { "Error connecting to $macAddress" }
                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Error(e.message ?: "Unknown error", isRecoverable = true),
                )
            } finally {
                locationCollector.unregisterDevice(macAddress)
                val (connection, _) = connectionManager.removeConnection(macAddress)
                // We don't cancel the job here as this finally block runs IN that job
                // or if the job was cancelled externally.
                // The connection removal ensures future lookups don't find it.
                _presentDevices.update { it - macAddress }

                val state = getDeviceState(macAddress)
                if (
                    state is DeviceConnectionState.Connected ||
                        state is DeviceConnectionState.Syncing
                ) {
                    updateDeviceState(macAddress, DeviceConnectionState.Disconnected)
                }
            }
        }
    }

    private suspend fun performInitialSetup(
        connection: CameraConnection,
        device: PairedDevice,
    ): String? {
        Log.info(tag = TAG) { "Performing initial setup for ${device.macAddress}" }

        val capabilities = connection.camera.vendor.getCapabilities()

        if (capabilities.supportsDeviceName) {
            try {
                val deviceName = connection.camera.vendor.getPairedDeviceName(deviceNameProvider)
                connection.setPairedDeviceName(deviceName)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to set device name for ${device.macAddress}"
                }
            }
        }

        if (capabilities.supportsDateTimeSync) {
            try {
                connection.syncDateTime(ZonedDateTime.now())
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to sync date time for ${device.macAddress}"
                }
            }
        }

        if (capabilities.supportsGeoTagging) {
            try {
                connection.setGeoTaggingEnabled(true)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to enable geo tagging for ${device.macAddress}"
                }
            }
        }

        var firmwareVersion: String? = null
        if (capabilities.supportsFirmwareVersion) {
            try {
                firmwareVersion = connection.readFirmwareVersion()
                pairedDevicesRepository.updateFirmwareVersion(device.macAddress, firmwareVersion)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to read firmware version for ${device.macAddress}"
                }
            }
        }

        return firmwareVersion
    }

    /**
     * Stops synchronization for a specific device.
     *
     * @param macAddress The MAC address of the device to stop syncing.
     */
    suspend fun stopDeviceSync(macAddress: String) {
        val upperMacAddress = macAddress.uppercase()

        // Stop location updates first
        locationCollector.unregisterDevice(upperMacAddress)

        // Disconnect camera connection and cancel job
        val (connection, job) = connectionManager.removeConnection(upperMacAddress)
        connection?.disconnect()
        job?.cancel()

        updateDeviceState(upperMacAddress, DeviceConnectionState.Disconnected)
    }

    /** Stops synchronization for all devices and cancels all background jobs. */
    suspend fun stopAllDevices() {
        backgroundMonitoringJob?.cancel()
        backgroundMonitoringJob = null
        periodicCheckJob?.cancel()
        periodicCheckJob = null
        locationSyncJob?.cancel()
        locationSyncJob = null

        val connections = connectionManager.getConnections()
        connections.keys.forEach { macAddress -> stopDeviceSync(macAddress) }
        _presentDevices.value = emptySet()
    }

    /**
     * Returns the current connection state for a specific device.
     *
     * @param macAddress The MAC address of the device.
     * @return The current [DeviceConnectionState].
     */
    fun getDeviceState(macAddress: String): DeviceConnectionState {
        val upperMacAddress = macAddress.uppercase()
        return _deviceStates.value[upperMacAddress] ?: DeviceConnectionState.Disconnected
    }

    /**
     * Returns the number of devices currently connected and syncing.
     *
     * @return The count of connected/syncing devices.
     */
    fun getConnectedDeviceCount(): Int =
        _deviceStates.value.values.count { state ->
            state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
        }

    /**
     * Returns true if the device with the given [macAddress] is currently connected or syncing.
     *
     * @param macAddress The MAC address of the device.
     * @return true if connected or syncing.
     */
    fun isDeviceConnected(macAddress: String): Boolean {
        val state = getDeviceState(macAddress)
        return state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
    }

    private fun updateDeviceState(macAddress: String, state: DeviceConnectionState) {
        _deviceStates.update { currentStates ->
            currentStates.toMutableMap().apply { put(macAddress, state) }
        }
    }

    /**
     * Starts a passive BLE scan to discover cameras in the background.
     *
     * When a camera is discovered, the [ScanReceiver] will be triggered, which in turn starts the
     * synchronization process.
     */
    fun startPassiveScan() {
        Log.info(tag = TAG) { "Starting passive scan" }
        val pendingIntent = createScanPendingIntent()
        cameraRepository.startPassiveScan(pendingIntent)
    }

    /** Stops the background passive BLE scan. */
    fun stopPassiveScan() {
        Log.info(tag = TAG) { "Stopping passive scan" }
        val pendingIntent = createScanPendingIntent()
        cameraRepository.stopPassiveScan(pendingIntent)
    }

    private fun createScanPendingIntent(): PendingIntent {
        val intent = Intent(context, ScanReceiver::class.java)
        return pendingIntentFactory.createBroadcastPendingIntent(
            context = context,
            requestCode = PASSIVE_SCAN_REQUEST_CODE,
            intent = intent,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Ensures location sync is running for all connected devices. Starts a coroutine that collects
     * location updates and syncs them to all connected devices.
     */
    private fun ensureLocationSyncRunning() {
        if (locationSyncJob != null) return

        locationSyncJob =
            coroutineScope.launch {
                locationCollector.locationUpdates.filterNotNull().collect { location ->
                    syncLocationToConnectedDevices(location)
                }
            }
    }

    /** Syncs location to all connected devices that support location sync. */
    private suspend fun syncLocationToConnectedDevices(location: GpsLocation) {
        val connections = connectionManager.getConnections()
        connections.forEach { (macAddress, connection) ->
            syncLocationToDevice(macAddress, connection, location)
        }
    }

    private suspend fun syncLocationToDevice(
        macAddress: String,
        connection: CameraConnection,
        location: GpsLocation,
    ) {
        if (!connection.camera.vendor.getCapabilities().supportsLocationSync) return
        try {
            connection.syncLocation(location)
            val now = System.currentTimeMillis()
            pairedDevicesRepository.updateLastSyncedAt(macAddress, now)
            val currentState = getDeviceState(macAddress)
            val newState = newSyncingStateWithLocation(currentState, location)
            if (newState != currentState) {
                updateDeviceState(macAddress, newState)
            }
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Failed to sync location to $macAddress" }
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) { "Failed to sync location to $macAddress" }
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) { "Failed to sync location to $macAddress" }
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) { "Failed to sync location to $macAddress" }
        }
    }

    private fun newSyncingStateWithLocation(
        currentState: DeviceConnectionState,
        location: GpsLocation,
    ): DeviceConnectionState {
        val syncInfo = LocationSyncInfo(syncTime = ZonedDateTime.now(), location = location)
        return when (currentState) {
            is DeviceConnectionState.Syncing -> currentState.copy(lastSyncInfo = syncInfo)
            is DeviceConnectionState.Connected ->
                DeviceConnectionState.Syncing(
                    firmwareVersion = currentState.firmwareVersion,
                    lastSyncInfo = syncInfo,
                )
            else -> currentState
        }
    }
}
