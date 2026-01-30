package dev.sebastiano.camerasync.devicesync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ble.ScanReceiver
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

/**
 * Coordinates synchronization with multiple camera devices simultaneously.
 *
 * This coordinator manages:
 * - Multiple concurrent camera connections
 * - Centralized location collection shared across all devices
 * - Per-device connection state
 * - Broadcasting location updates to all connected devices
 *
 * @param context Application context for resource access.
 * @param cameraRepository Repository for BLE camera operations.
 * @param locationCollector Centralized location collector.
 * @param vendorRegistry Registry for resolving camera vendors.
 * @param pairedDevicesRepository Repository for managing paired devices.
 * @param coroutineScope Scope for launching coroutines.
 * @param deviceNameProvider Provider for the device name to set on cameras.
 */
class MultiDeviceSyncCoordinator(
    private val context: Context,
    private val cameraRepository: CameraRepository,
    private val locationCollector: LocationCollectionCoordinator,
    private val vendorRegistry: CameraVendorRegistry,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val companionDeviceManagerHelper:
        dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper,
    private val pendingIntentFactory: PendingIntentFactory,
    private val coroutineScope: CoroutineScope,
    private val deviceNameProvider: () -> String = {
        context.getString(R.string.default_device_name, Build.MODEL)
    },
) {
    private val _deviceStates = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())

    /** Flow of connection states for all managed devices. Key is the device MAC address. */
    val deviceStates: StateFlow<Map<String, DeviceConnectionState>> = _deviceStates.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    private val _presentDevices = MutableStateFlow<Set<String>>(emptySet())

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

    /** Flow of devices reported as present by Companion Device Manager. */
    val presentDevices: StateFlow<Set<String>> = _presentDevices.asStateFlow()

    private val deviceJobs = mutableMapOf<String, Job>()
    private val deviceConnections = mutableMapOf<String, CameraConnection>()
    private val jobsMutex = Mutex()
    private val scanMutex = Mutex()

    private var locationSyncJob: Job? = null
    private var backgroundMonitoringJob: Job? = null
    private var periodicCheckJob: Job? = null
    private val enabledDevicesFlow = MutableStateFlow<List<PairedDevice>>(emptyList())

    /**
     * Starts background monitoring of enabled devices. Connections are triggered by presence events
     * or explicit refresh requests.
     *
     * @param enabledDevices Flow of enabled devices from the repository.
     */
    fun startBackgroundMonitoring(enabledDevices: Flow<List<PairedDevice>>) {
        if (backgroundMonitoringJob != null) return

        backgroundMonitoringJob =
            coroutineScope.launch {
                var initialRefreshDone = false

                enabledDevices.collect { devices ->
                    enabledDevicesFlow.value = devices
                    // Note: Presence observations are managed by PresenceObservationManager
                    // We don't need to start them here - they're already active for all paired
                    // devices.

                    // We perform an initial refresh (ignoring presence) the first time we see
                    // enabled devices to ensure we try to connect to them even if Companion
                    // Device Manager hasn't reported them as present yet.
                    val shouldIgnorePresence = !initialRefreshDone && devices.isNotEmpty()
                    if (shouldIgnorePresence) {
                        Log.info(tag = TAG) {
                            "Performing initial proactive refresh for ${devices.size} devices"
                        }
                    }
                    checkAndConnectEnabledDevices(ignorePresence = shouldIgnorePresence)
                    if (devices.isNotEmpty()) {
                        initialRefreshDone = true
                        // Start periodic check when we have enabled devices
                        startPeriodicCheck()
                    } else {
                        // Stop periodic check when no devices are enabled
                        stopPeriodicCheck()
                    }
                }
            }
    }

    /** Starts a periodic check (every 30 seconds) for disconnected/unreachable enabled devices. */
    private fun startPeriodicCheck() {
        if (periodicCheckJob != null) return

        periodicCheckJob =
            coroutineScope.launch {
                while (true) {
                    delay(30_000L) // Check every 30 seconds

                    val enabledDevices = enabledDevicesFlow.value
                    if (enabledDevices.isEmpty()) {
                        // No enabled devices, stop periodic check
                        stopPeriodicCheck()
                        return@launch
                    }

                    Log.debug(tag = TAG) {
                        "Periodic check: ${enabledDevices.size} enabled devices, checking for disconnected ones"
                    }

                    // Check for disconnected/unreachable devices and attempt reconnect
                    val disconnectedDevices =
                        enabledDevices.filter { device ->
                            val state = getDeviceState(device.macAddress.uppercase())
                            state is DeviceConnectionState.Disconnected ||
                                state is DeviceConnectionState.Unreachable ||
                                (state is DeviceConnectionState.Error && state.isRecoverable)
                        }

                    if (disconnectedDevices.isEmpty()) {
                        Log.debug(tag = TAG) {
                            "Periodic check: All ${enabledDevices.size} devices are connected or in-progress"
                        }
                    } else {
                        Log.info(tag = TAG) {
                            "Periodic check: Found ${disconnectedDevices.size} disconnected devices (${disconnectedDevices.map { it.macAddress }}), attempting reconnect"
                        }
                        // Use ignorePresence=true to force connection attempt even if CDM hasn't reported presence
                        checkAndConnectEnabledDevices(ignorePresence = true)
                    }
                }
            }
    }

    /** Stops the periodic check. */
    private fun stopPeriodicCheck() {
        periodicCheckJob?.cancel()
        periodicCheckJob = null
    }

    /** Manually triggers a scan for all enabled but disconnected devices. */
    fun refreshConnections() {
        Log.info(tag = TAG) { "Manual refresh requested" }
        coroutineScope.launch {
            // Ensure we have loaded devices from the repository before refreshing
            if (enabledDevicesFlow.value.isEmpty()) {
                val devices = pairedDevicesRepository.enabledDevices.first()
                enabledDevicesFlow.value = devices
            }
            checkAndConnectEnabledDevices(ignorePresence = true)
        }
    }

    private suspend fun checkAndConnectEnabledDevices(ignorePresence: Boolean = false) {
        Log.debug(tag = TAG) { "Checking enabled devices (ignorePresence=$ignorePresence)" }

        // Ensure we have loaded devices from the repository before checking
        if (enabledDevicesFlow.value.isEmpty()) {
            Log.debug(tag = TAG) { "Enabled devices list empty, waiting for initial load..." }
            val devices = pairedDevicesRepository.enabledDevices.first()
            enabledDevicesFlow.value = devices
        }

        _isScanning.value = true
        try {
            scanMutex.withLock {
                val enabledDevices = enabledDevicesFlow.value
                val enabledMacAddresses = enabledDevices.map { it.macAddress.uppercase() }.toSet()
                val presentMacAddresses =
                    if (ignorePresence) enabledMacAddresses else _presentDevices.value
                val allowUnknownPresence = !ignorePresence && presentMacAddresses.isEmpty()

                if (allowUnknownPresence) {
                    Log.debug(tag = TAG) {
                        "Presence list empty; allowing initial connect for disconnected devices"
                    }
                }
                Log.debug(tag = TAG) {
                    "Enabled devices: ${enabledMacAddresses.size} ($enabledMacAddresses), present devices: ${presentMacAddresses.size} ($presentMacAddresses)"
                }

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
                        "Device $macAddress is no longer eligible for sync, disconnecting..."
                    }
                    stopDeviceSync(macAddress)
                }

                // Then, connect devices that are enabled but not connected
                enabledDevices.forEach { device ->
                    val macAddress = device.macAddress.uppercase()
                    val state = getDeviceState(macAddress)
                    val isPresent =
                        presentMacAddresses.contains(macAddress) ||
                            (allowUnknownPresence && state is DeviceConnectionState.Disconnected)

                    val eligibleState =
                        state is DeviceConnectionState.Disconnected ||
                            state is DeviceConnectionState.Unreachable ||
                            (state is DeviceConnectionState.Error && state.isRecoverable)

                    if (isPresent && eligibleState) {
                        Log.debug(tag = TAG) {
                            "Device $macAddress is enabled but not connected (state: $state), attempting sync..."
                        }
                        startDeviceSync(device)
                    } else if (!isPresent && !ignorePresence) {
                        Log.debug(tag = TAG) {
                            "Device $macAddress is enabled but not present, skipping connect"
                        }
                    } else if (!eligibleState) {
                        Log.debug(tag = TAG) {
                            "Device $macAddress not eligible for sync (state: $state)"
                        }
                    }
                }
            }
        } finally {
            _isScanning.value = false
        }
    }

    /** Updates whether a device is currently present (in range). */
    fun setDevicePresence(macAddress: String, isPresent: Boolean) {
        val normalizedMac = macAddress.uppercase()
        Log.info(tag = TAG) { "Presence update for $normalizedMac: isPresent=$isPresent" }
        _presentDevices.update { current ->
            if (isPresent) current + normalizedMac else current - normalizedMac
        }
        coroutineScope.launch {
            if (isPresent) {
                // When a device appears (e.g., camera turned on), give it time to start advertising
                // before attempting to connect. Cameras may need 10-15 seconds after power-on
                // before they're ready for BLE connections, especially if they were just turned on.
                val delayMs = 10_000L
                Log.info(tag = TAG) {
                    "Device $normalizedMac appeared, waiting ${delayMs}ms before attempting connection to allow camera to start advertising"
                }
                delay(delayMs)
            }
            checkAndConnectEnabledDevices()
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
        val macAddress = device.macAddress.uppercase()

        Log.info(tag = TAG) {
            "Starting sync for $macAddress (enabled=${device.isEnabled}, vendorId=${device.vendorId})"
        }
        val vendor = vendorRegistry.getVendorById(device.vendorId)
        if (vendor == null) {
            Log.error(tag = TAG) { "Unknown vendor ${device.vendorId} for device $macAddress" }
            updateDeviceState(
                macAddress,
                DeviceConnectionState.Error(
                    message = context.getString(R.string.error_unknown_vendor),
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

            // Note: Presence observations are managed by PresenceObservationManager
            // We don't need to start them here - they're already active for all paired devices.

            val job =
                coroutineScope.launch {
                    try {
                        // Register for location updates early, as soon as we start connecting
                        // This starts location collection so it's ready by the time we connect
                        locationCollector.registerDevice(macAddress)

                        Log.debug(tag = TAG) { "Starting connection attempt for $macAddress..." }
                        // Increased timeout to allow for retry logic in connect():
                        // 5s initial delay + 3 attempts × (20s timeout + 3s delay) ≈ 74s max
                        val connection =
                            withTimeout(90_000L) {
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

                        // Update presence state - device is clearly present since we connected
                        // This is important because CDM callbacks may not fire reliably
                        _presentDevices.update { current -> current + macAddress.uppercase() }
                        Log.debug(tag = TAG) {
                            "Marked device $macAddress as present (successfully connected)"
                        }

                        val firmwareVersion = performInitialSetup(connection)
                        Log.info(tag = TAG) {
                            "Initial setup complete for $macAddress (firmware=$firmwareVersion)"
                        }

                        updateDeviceState(
                            macAddress,
                            DeviceConnectionState.Syncing(firmwareVersion = firmwareVersion),
                        )

                        // Start the global location sync if not already running
                        ensureLocationSyncRunning()

                        // Wait until the connection is lost or the job is cancelled
                        connection.isConnected.filter { !it }.first()

                        Log.info(tag = TAG) { "Connection lost for $macAddress" }
                        // Update presence state - device is no longer present
                        _presentDevices.update { current -> current - macAddress.uppercase() }
                        Log.debug(tag = TAG) {
                            "Marked device $macAddress as not present (connection lost)"
                        }
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

                        // Check if this is an unreachable device scenario (timeout, not found,
                        // etc.)
                        val isUnreachable =
                            e is TimeoutCancellationException ||
                                (e is IllegalStateException &&
                                    e.cause is TimeoutCancellationException) ||
                                e.message?.contains("timeout", ignoreCase = true) == true ||
                                e.message?.contains("not found", ignoreCase = true) == true ||
                                e.message?.contains("unreachable", ignoreCase = true) == true ||
                                e.message?.contains("could not find device", ignoreCase = true) ==
                                    true

                        if (isUnreachable) {
                            Log.info(tag = TAG) {
                                "Device $macAddress is unreachable, setting state to Unreachable"
                            }
                            updateDeviceState(macAddress, DeviceConnectionState.Unreachable)
                            cleanup(macAddress, preserveErrorState = true)
                        } else {
                            // This is a real error (pairing rejected, etc.)
                            val errorMessage =
                                when {
                                    e.message?.contains("pairing", ignoreCase = true) == true ->
                                        context.getString(R.string.error_pairing_rejected_long)
                                    else ->
                                        e.message
                                            ?: context.getString(R.string.error_connection_failed)
                                }
                            updateDeviceState(
                                macAddress,
                                DeviceConnectionState.Error(message = errorMessage),
                            )
                            cleanup(macAddress, preserveErrorState = true)
                        }
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
        Log.debug(tag = TAG) {
            "Initial setup capabilities for ${connection.camera.macAddress}: $capabilities"
        }

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
                    Log.debug(tag = TAG) {
                        "Reading firmware version for ${connection.camera.macAddress}"
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
                Log.debug(tag = TAG) {
                    "Setting paired device name for ${connection.camera.macAddress}"
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
                Log.debug(tag = TAG) { "Syncing date/time for ${connection.camera.macAddress}" }
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
                Log.debug(tag = TAG) { "Enabling geo-tagging for ${connection.camera.macAddress}" }
                connection.setGeoTaggingEnabled(true)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to enable geo-tagging" }
            }
        }

        return firmwareVersion ?: context.getString(R.string.label_unknown)
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
        val normalizedMac = macAddress.uppercase()
        Log.info(tag = TAG) { "Stopping sync for $normalizedMac" }

        val job = synchronized(deviceJobs) { deviceJobs[normalizedMac] }

        if (job != null) {
            job.cancel()
            job.join() // Wait for the finally block to complete
        }
    }

    /** Stops syncing with all devices and stops background monitoring. */
    suspend fun stopAllDevices() {
        stopPeriodicCheck()
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
        return _deviceStates.value[macAddress.uppercase()] ?: DeviceConnectionState.Disconnected
    }

    /** Checks if a device is currently connected. */
    fun isDeviceConnected(macAddress: String): Boolean {
        val state = getDeviceState(macAddress.uppercase())
        return state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
    }

    /** Gets the count of currently connected devices. */
    fun getConnectedDeviceCount(): Int {
        return _deviceStates.value.count { (_, state) ->
            state is DeviceConnectionState.Connected || state is DeviceConnectionState.Syncing
        }
    }

    private fun updateDeviceState(macAddress: String, state: DeviceConnectionState) {
        val normalizedMac = macAddress.uppercase()
        _deviceStates.update { currentStates -> currentStates + (normalizedMac to state) }
    }

    private inline fun updateDeviceState(
        macAddress: String,
        transform: (DeviceConnectionState) -> DeviceConnectionState,
    ) {
        val normalizedMac = macAddress.uppercase()
        _deviceStates.update { currentStates ->
            val currentState = currentStates[normalizedMac] ?: DeviceConnectionState.Disconnected
            currentStates + (normalizedMac to transform(currentState))
        }
    }

    /** Removes a device state entry (used when device is unpaired). */
    fun clearDeviceState(macAddress: String) {
        val normalizedMac = macAddress.uppercase()
        _deviceStates.update { currentStates -> currentStates - normalizedMac }
    }

    /** Attempts to reconnect to a device that failed. */
    fun retryDeviceConnection(device: PairedDevice) {
        val macAddress = device.macAddress.uppercase()
        val currentState = getDeviceState(macAddress)

        if (
            currentState is DeviceConnectionState.Unreachable ||
                (currentState is DeviceConnectionState.Error && currentState.isRecoverable)
        ) {
            Log.info(tag = TAG) { "Retrying connection for $macAddress" }
            startDeviceSync(device)
        }
    }

    /** Starts the passive scan using PendingIntent. */
    fun startPassiveScan() {
        Log.info(tag = TAG) { "Starting passive scan" }
        val scanReceiverIntent = Intent(context, ScanReceiver::class.java)
        val pendingIntent =
            pendingIntentFactory.createBroadcastPendingIntent(
                context,
                PASSIVE_SCAN_REQUEST_CODE,
                scanReceiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        cameraRepository.startPassiveScan(pendingIntent)
    }

    /** Stops the passive scan. */
    fun stopPassiveScan() {
        Log.info(tag = TAG) { "Stopping passive scan" }
        val scanReceiverIntent = Intent(context, ScanReceiver::class.java)
        val pendingIntent =
            pendingIntentFactory.createBroadcastPendingIntent(
                context,
                PASSIVE_SCAN_REQUEST_CODE,
                scanReceiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        cameraRepository.stopPassiveScan(pendingIntent)
    }

    companion object {
        private const val PASSIVE_SCAN_REQUEST_CODE = 999

        /** Suspends until cancellation. Used to keep a coroutine alive. */
        private suspend fun awaitCancellation(): Nothing {
            while (true) {
                delay(Long.MAX_VALUE)
            }
        }
    }
}
