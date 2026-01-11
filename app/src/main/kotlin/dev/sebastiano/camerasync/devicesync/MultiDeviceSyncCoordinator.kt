package dev.sebastiano.camerasync.devicesync

import android.os.Build
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.LocationSyncInfo
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.toCamera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
private const val PERIODIC_SCAN_INTERVAL_MS = 60_000L // 1 minute

/**
 * Coordinates synchronization with multiple camera devices simultaneously.
 *
 * This coordinator manages:
 * - Multiple concurrent camera connections
 * - Centralized location collection shared across all devices
 * - Per-device connection state
 * - Broadcasting location updates to all connected devices
 *
 * @param cameraRepository Repository for BLE camera operations.
 * @param locationCollector Centralized location collector.
 * @param vendorRegistry Registry for resolving camera vendors.
 * @param pairedDevicesRepository Repository for managing paired devices.
 * @param coroutineScope Scope for launching coroutines.
 * @param deviceNameProvider Provider for the device name to set on cameras.
 */
class MultiDeviceSyncCoordinator(
    private val cameraRepository: CameraRepository,
    private val locationCollector: LocationCollectionCoordinator,
    private val vendorRegistry: CameraVendorRegistry,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val companionDeviceManagerHelper: dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper,
    private val coroutineScope: CoroutineScope,
    private val deviceNameProvider: () -> String = { "${Build.MODEL} CameraSync" },
) {
    private val _deviceStates = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())

    /** Flow of connection states for all managed devices. Key is the device MAC address. */
    val deviceStates: StateFlow<Map<String, DeviceConnectionState>> = _deviceStates.asStateFlow()

    private val _isScanning = MutableStateFlow(false)

    /** Flow that emits true when a scan/discovery pass is in progress. */
    val isScanning: StateFlow<Boolean> =
        combine(_isScanning, _deviceStates) { scanning, states ->
                scanning ||
                    states.values.any {
                        it is DeviceConnectionState.Connecting ||
                            it is DeviceConnectionState.Searching
                    }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val deviceJobs = mutableMapOf<String, Job>()
    private val deviceConnections = mutableMapOf<String, CameraConnection>()
    private val jobsMutex = Mutex()
    private val scanMutex = Mutex()

    private var locationSyncJob: Job? = null
    private var backgroundMonitoringJob: Job? = null
    private val enabledDevicesFlow = MutableStateFlow<List<PairedDevice>>(emptyList())

    /**
     * Starts background monitoring of enabled devices. This will periodically scan for and connect
     * to devices that are enabled but not connected.
     *
     * @param enabledDevices Flow of enabled devices from the repository.
     */
    fun startBackgroundMonitoring(enabledDevices: Flow<List<PairedDevice>>) {
        if (backgroundMonitoringJob != null) return

        backgroundMonitoringJob =
            coroutineScope.launch {
                // Job for observing enabled devices
                launch {
                    enabledDevices.collect { devices ->
                        enabledDevicesFlow.value = devices
                        checkAndConnectEnabledDevices()
                    }
                }

                // Job for periodic scanning
                launch {
                    while (true) {
                        delay(PERIODIC_SCAN_INTERVAL_MS)
                        Log.debug(tag = TAG) { "Running periodic scan for enabled devices..." }
                        checkAndConnectEnabledDevices()
                    }
                }
            }
    }

    /** Manually triggers a scan for all enabled but disconnected devices. */
    fun refreshConnections() {
        Log.info(tag = TAG) { "Manual refresh requested" }
        coroutineScope.launch { checkAndConnectEnabledDevices() }
    }

    private suspend fun checkAndConnectEnabledDevices() {
        _isScanning.value = true
        try {
            scanMutex.withLock {
                val enabledDevices = enabledDevicesFlow.value
                val enabledMacAddresses = enabledDevices.map { it.macAddress }.toSet()

                // First, collect devices to disconnect while holding the mutex
                // We must NOT call stopDeviceSync while holding jobsMutex to avoid deadlock
                // (stopDeviceSync calls job.join() which eventually calls cleanup() which
                // also needs jobsMutex)
                val devicesToDisconnect =
                    jobsMutex.withLock {
                        deviceConnections.keys
                            .filter { macAddress ->
                                if (!enabledMacAddresses.contains(macAddress)) {
                                    val state = getDeviceState(macAddress)
                                    state is DeviceConnectionState.Connected ||
                                        state is DeviceConnectionState.Syncing ||
                                        state is DeviceConnectionState.Connecting ||
                                        state is DeviceConnectionState.Searching
                                } else {
                                    false
                                }
                            }
                            .toList()
                    }

                // Now disconnect outside the mutex to avoid deadlock
                devicesToDisconnect.forEach { macAddress ->
                    Log.info(tag = TAG) {
                        "Device $macAddress is connected but no longer enabled, disconnecting..."
                    }
                    stopDeviceSync(macAddress)
                }

                // Then, connect devices that are enabled but not connected
                enabledDevices.forEach { device ->
                    val macAddress = device.macAddress
                    val state = getDeviceState(macAddress)

                    if (
                        state is DeviceConnectionState.Disconnected ||
                            state is DeviceConnectionState.Unreachable ||
                            (state is DeviceConnectionState.Error && state.isRecoverable)
                    ) {
                        Log.debug(tag = TAG) {
                            "Device $macAddress is enabled but not connected (state: $state), attempting sync..."
                        }
                        startDeviceSync(device)
                    }
                }
            }
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Starts syncing with a paired device.
     *
     * This will:
     * 1. Connect to the camera
     * 2. Perform initial setup (firmware read, device name, datetime, geo-tagging)
     * 3. Register for location updates
     * 4. Continuously sync location to the camera
     *
     * @param device The paired device to connect to.
     */
    fun startDeviceSync(device: PairedDevice) {
        val macAddress = device.macAddress

        val vendor = vendorRegistry.getVendorById(device.vendorId)
        if (vendor == null) {
            Log.error(tag = TAG) { "Unknown vendor ${device.vendorId} for device $macAddress" }
            updateDeviceState(
                macAddress,
                DeviceConnectionState.Error(
                    message = "Unknown camera vendor",
                    isRecoverable = false,
                ),
            )
            return
        }

        val camera = device.toCamera(vendor)

        synchronized(deviceJobs) {
            if (deviceJobs.containsKey(macAddress)) {
                Log.warn(tag = TAG) { "Device $macAddress already syncing, ignoring" }
                return
            }

            // Set state to searching immediately before launching the job
            updateDeviceState(macAddress, DeviceConnectionState.Searching)

            // Ensure we are observing device presence for better background performance
            companionDeviceManagerHelper.startObservingDevicePresence(macAddress)

            val job =
                coroutineScope.launch {
                    try {
                        // Register for location updates early, as soon as we start connecting
                        // This starts location collection so it's ready by the time we connect
                        locationCollector.registerDevice(macAddress)

                        Log.debug(tag = TAG) { "Starting connection attempt for $macAddress..." }
                        val connection =
                            withTimeout(30_000L) {
                                connectToCamera(
                                    camera,
                                    onFound = {
                                        updateDeviceState(
                                            macAddress,
                                            DeviceConnectionState.Connecting,
                                        )
                                    },
                                )
                            }

                        jobsMutex.withLock { deviceConnections[macAddress] = connection }

                        // Wait until the connection is fully established before performing setup
                        connection.isConnected.filter { it }.first()
                        Log.info(tag = TAG) {
                            "Device $macAddress connection established, performing initial setup..."
                        }

                        val firmwareVersion = performInitialSetup(connection)

                        updateDeviceState(
                            macAddress,
                            DeviceConnectionState.Syncing(firmwareVersion = firmwareVersion),
                        )

                        // Start the global location sync if not already running
                        ensureLocationSyncRunning()

                        // Wait until the connection is lost or the job is cancelled
                        connection.isConnected.filter { !it }.first()

                        Log.info(tag = TAG) { "Connection lost for $macAddress" }
                        cleanup(macAddress, preserveErrorState = false)
                    } catch (e: TimeoutCancellationException) {
                        Log.error(tag = TAG) { "Connection timed out for $macAddress" }
                        updateDeviceState(macAddress, DeviceConnectionState.Unreachable)
                        cleanup(macAddress, preserveErrorState = true)
                    } catch (e: CancellationException) {
                        Log.info(tag = TAG) { "Sync cancelled for $macAddress" }
                        cleanup(macAddress, preserveErrorState = false)
                        throw e
                    } catch (e: Exception) {
                        Log.error(tag = TAG, throwable = e) { "Connection error for $macAddress" }
                        val errorMessage =
                            when {
                                e.message?.contains("pairing", ignoreCase = true) == true ->
                                    "Pairing rejected. Enable pairing on your camera."
                                e.message?.contains("timeout", ignoreCase = true) == true ->
                                    "Connection timed out. Is the camera nearby?"
                                else -> e.message ?: "Connection failed"
                            }
                        updateDeviceState(
                            macAddress,
                            DeviceConnectionState.Error(message = errorMessage),
                        )
                        cleanup(macAddress, preserveErrorState = true)
                    }
                }
            deviceJobs[macAddress] = job
        }
    }

    private suspend fun connectToCamera(
        camera: Camera,
        onFound: (() -> Unit)? = null,
    ): CameraConnection {
        Log.info(tag = TAG) { "Connecting to ${camera.name ?: camera.macAddress}..." }
        return cameraRepository.connect(camera, onFound)
    }

    private suspend fun performInitialSetup(connection: CameraConnection): String {
        val capabilities = connection.camera.vendor.getCapabilities()

        // Verify connection is still active before proceeding
        if (!connection.isConnected.first()) {
            throw IllegalStateException("Connection lost before initial setup")
        }

        // Read firmware version if supported
        val firmwareVersion =
            if (capabilities.supportsFirmwareVersion) {
                try {
                    if (!connection.isConnected.first()) {
                        throw IllegalStateException("Connection lost during firmware read")
                    }
                    connection.readFirmwareVersion()
                } catch (e: Exception) {
                    Log.warn(tag = TAG, throwable = e) { "Failed to read firmware version" }
                    null
                }
            } else null

        // Set paired device name if supported
        if (capabilities.supportsDeviceName) {
            try {
                if (!connection.isConnected.first()) {
                    throw IllegalStateException("Connection lost before setting device name")
                }
                connection.setPairedDeviceName(deviceNameProvider())
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to set paired device name" }
            }
        }

        // Sync date/time if supported
        if (capabilities.supportsDateTimeSync) {
            try {
                if (!connection.isConnected.first()) {
                    throw IllegalStateException("Connection lost before date/time sync")
                }
                connection.syncDateTime(ZonedDateTime.now())
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to sync date/time" }
            }
        }

        // Enable geo-tagging if supported
        if (capabilities.supportsGeoTagging) {
            try {
                if (!connection.isConnected.first()) {
                    throw IllegalStateException("Connection lost before enabling geo-tagging")
                }
                connection.setGeoTaggingEnabled(true)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to enable geo-tagging" }
            }
        }

        return firmwareVersion ?: "Unknown"
    }

    /**
     * Ensures the global location sync job is running. This job broadcasts location updates to all
     * connected devices.
     */
    private fun ensureLocationSyncRunning() {
        if (locationSyncJob != null) return

        locationSyncJob =
            coroutineScope.launch {
                locationCollector.locationUpdates.filterNotNull().collect { location ->
                    syncLocationToAllDevices(location)
                }
            }
    }

    /** Syncs a location update to all connected devices. */
    private suspend fun syncLocationToAllDevices(location: GpsLocation) {
        val connections = jobsMutex.withLock { deviceConnections.toMap() }

        connections.forEach { (macAddress, connection) ->
            try {
                if (connection.camera.vendor.getCapabilities().supportsLocationSync) {
                    connection.syncLocation(location)

                    // Update persistent last sync timestamp
                    val now = System.currentTimeMillis()
                    pairedDevicesRepository.updateLastSyncedAt(macAddress, now)

                    // Update state with sync info
                    updateDeviceState(macAddress) { currentState ->
                        when (currentState) {
                            is DeviceConnectionState.Syncing ->
                                currentState.copy(
                                    lastSyncInfo =
                                        LocationSyncInfo(
                                            syncTime = ZonedDateTime.now(),
                                            location = location,
                                        )
                                )
                            is DeviceConnectionState.Connected ->
                                DeviceConnectionState.Syncing(
                                    firmwareVersion = currentState.firmwareVersion,
                                    lastSyncInfo =
                                        LocationSyncInfo(
                                            syncTime = ZonedDateTime.now(),
                                            location = location,
                                        ),
                                )
                            else -> currentState
                        }
                    }
                }
            } catch (e: Exception) {
                Log.error(tag = TAG, throwable = e) { "Failed to sync location to $macAddress" }
                // Don't update state to error for sync failures - device is still connected
            }
        }
    }

    /**
     * Stops syncing with a specific device.
     *
     * @param macAddress The MAC address of the device to stop.
     */
    suspend fun stopDeviceSync(macAddress: String) {
        Log.info(tag = TAG) { "Stopping sync for $macAddress" }

        val job = synchronized(deviceJobs) { deviceJobs[macAddress] }

        if (job != null) {
            job.cancel()
            job.join() // Wait for the finally block to complete
        }
    }

    /** Stops syncing with all devices and stops background monitoring. */
    suspend fun stopAllDevices() {
        Log.info(tag = TAG) { "Stopping all device syncs" }

        backgroundMonitoringJob?.cancel()
        backgroundMonitoringJob = null

        val jobs = synchronized(deviceJobs) { deviceJobs.values.toList() }

        // Cancel all jobs
        jobs.forEach { it.cancel() }
        // Wait for all to complete
        jobs.forEach { it.join() }

        locationSyncJob?.cancel()
        locationSyncJob = null
    }

    /**
     * Cleans up resources for a device.
     *
     * @param macAddress The MAC address of the device to clean up.
     * @param preserveErrorState If true, won't update state to Disconnected if currently in Error
     *   state.
     */
    private suspend fun cleanup(macAddress: String, preserveErrorState: Boolean = false) {
        // Remove job from tracking
        synchronized(deviceJobs) { deviceJobs.remove(macAddress) }

        // Disconnect and remove connection
        jobsMutex.withLock {
            deviceConnections[macAddress]?.let { connection ->
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.warn(tag = TAG, throwable = e) { "Error disconnecting from $macAddress" }
                }
            }
            deviceConnections.remove(macAddress)
        }

        // Unregister from location updates
        locationCollector.unregisterDevice(macAddress)

        // Stop location sync if no more devices
        if (locationCollector.getRegisteredDeviceCount() == 0) {
            locationSyncJob?.cancel()
            locationSyncJob = null
        }

        // Update state to disconnected (unless we want to preserve error/unreachable state)
        val currentState = getDeviceState(macAddress)
        val shouldPreserve =
            preserveErrorState &&
                (currentState is DeviceConnectionState.Error ||
                    currentState is DeviceConnectionState.Unreachable)
        if (!shouldPreserve) {
            updateDeviceState(macAddress, DeviceConnectionState.Disconnected)
        }
    }

    /** Gets the current connection state for a device. */
    fun getDeviceState(macAddress: String): DeviceConnectionState {
        return _deviceStates.value[macAddress] ?: DeviceConnectionState.Disconnected
    }

    /** Checks if a device is currently connected. */
    fun isDeviceConnected(macAddress: String): Boolean {
        val state = getDeviceState(macAddress)
        return state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
    }

    /** Gets the count of currently connected devices. */
    fun getConnectedDeviceCount(): Int {
        return _deviceStates.value.count { (_, state) ->
            state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
        }
    }

    private fun updateDeviceState(macAddress: String, state: DeviceConnectionState) {
        _deviceStates.update { currentStates -> currentStates + (macAddress to state) }
    }

    private inline fun updateDeviceState(
        macAddress: String,
        transform: (DeviceConnectionState) -> DeviceConnectionState,
    ) {
        _deviceStates.update { currentStates ->
            val currentState = currentStates[macAddress] ?: DeviceConnectionState.Disconnected
            currentStates + (macAddress to transform(currentState))
        }
    }

    /** Removes a device state entry (used when device is unpaired). */
    fun clearDeviceState(macAddress: String) {
        _deviceStates.update { currentStates -> currentStates - macAddress }
    }

    /** Attempts to reconnect to a device that failed. */
    fun retryDeviceConnection(device: PairedDevice) {
        val macAddress = device.macAddress
        val currentState = getDeviceState(macAddress)

        if (
            currentState is DeviceConnectionState.Unreachable ||
                (currentState is DeviceConnectionState.Error && currentState.isRecoverable)
        ) {
            Log.info(tag = TAG) { "Retrying connection for $macAddress" }
            startDeviceSync(device)
        }
    }

    companion object {
        /** Suspends until cancellation. Used to keep a coroutine alive. */
        private suspend fun awaitCancellation(): Nothing {
            while (true) {
                delay(Long.MAX_VALUE)
            }
        }
    }
}
