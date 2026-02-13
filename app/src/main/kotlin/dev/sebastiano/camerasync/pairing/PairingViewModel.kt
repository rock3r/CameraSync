package dev.sebastiano.camerasync.pairing

import android.Manifest
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.di.IoDispatcher
import dev.sebastiano.camerasync.di.MainDispatcher
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.zacsweers.metro.Inject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "PairingViewModel"
internal const val PAIRING_CONNECTION_TIMEOUT_MS = 30_000L
internal const val BONDING_TIMEOUT_MS = 60_000L
private const val BOND_CHECK_DELAY_MS = 500L
private const val MODEL_PROBE_TIMEOUT_MS = 25_000L
private const val MODEL_PROBE_CONCURRENCY = 2

/**
 * UI model for a discovered camera during the pairing flow.
 *
 * Represents the state of camera discovery:
 * - [Detecting]: Camera discovered via BLE but make/model not yet identified
 * - [Detected]: Camera identified with make and model, ready for pairing
 */
sealed interface DiscoveredCameraUi {
    /** The discovered camera with at least name and MAC address. */
    val camera: Camera

    /** Camera is being detected/identified. Only has BLE device name and MAC address. */
    data class Detecting(override val camera: Camera) : DiscoveredCameraUi

    /** Camera is detected with make and model information. */
    data class Detected(override val camera: Camera, val make: String, val model: String) :
        DiscoveredCameraUi
}

/**
 * ViewModel for the pairing screen.
 *
 * Handles the companion device association flow and the subsequent camera pairing process.
 *
 * @param pairedDevicesRepository Repository for managing paired devices.
 * @param cameraRepository Repository for BLE camera communication.
 * @param bluetoothBondingChecker Utility for checking and managing Bluetooth bonding.
 * @param companionDeviceManagerHelper Helper for Companion Device Manager association.
 * @param issueReporter Utility for sending feedback reports.
 * @param ioDispatcher The dispatcher to use for IO operations. Can be overridden in tests to use a
 *   test dispatcher.
 * @param mainDispatcher The dispatcher to use for Main-thread state updates. Can be overridden in
 *   tests to use a test dispatcher.
 */
@Inject
class PairingViewModel(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val cameraRepository: CameraRepository,
    private val bluetoothBondingChecker: BluetoothBondingChecker,
    private val companionDeviceManagerHelper: CompanionDeviceManagerHelper,
    private val issueReporter: IssueReporter,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _state = mutableStateOf<PairingScreenState>(PairingScreenState.Scanning())

    /** The current UI state of the pairing screen. */
    val state: State<PairingScreenState> = _state

    private val _navigationEvents = Channel<PairingNavigationEvent>(Channel.BUFFERED)

    /** A flow of navigation events, such as when pairing is successful. */
    val navigationEvents: Flow<PairingNavigationEvent> = _navigationEvents.receiveAsFlow()

    private val _associationRequest = Channel<IntentSender>(Channel.BUFFERED)

    /** A flow of [IntentSender]s for launching the Companion Device Manager chooser. */
    val associationRequest: Flow<IntentSender> = _associationRequest.receiveAsFlow()

    private var pairingJob: Job? = null
    private var scanJob: Job? = null
    private val successJob = AtomicReference<Job?>(null)
    private val successEventSent = AtomicBoolean(false)
    private val probeJobs = ConcurrentHashMap<String, Job>()
    private val discoveredCameras = ConcurrentHashMap<String, DiscoveredCameraUi>()
    private val probeSemaphore = Semaphore(MODEL_PROBE_CONCURRENCY)

    init {
        startScanning()
    }

    private fun startScanning() {
        if (scanJob?.isActive == true) return

        cameraRepository.startScanning()
        scanJob =
            viewModelScope.launch(ioDispatcher) {
                cameraRepository.discoveredCameras.collect { camera ->
                    if (pairedDevicesRepository.isDevicePaired(camera.macAddress)) return@collect
                    if (discoveredCameras.containsKey(camera.macAddress)) return@collect

                    val supportsModelProbing = camera.vendor.getCapabilities().supportsModelName

                    val uiModel =
                        if (supportsModelProbing) {
                            // Start in Detecting state, will probe for actual model name
                            DiscoveredCameraUi.Detecting(camera)
                        } else {
                            // No probing needed, extract make/model from name
                            val make = camera.vendor.vendorName
                            val model = camera.vendor.extractModelFromPairingName(camera.name)
                            DiscoveredCameraUi.Detected(camera, make, model)
                        }

                    discoveredCameras[camera.macAddress] = uiModel
                    updateScanningState()

                    if (supportsModelProbing) {
                        probeModelName(camera)
                    }
                }
            }
    }

    private fun sortedDiscoveredCameras(): List<DiscoveredCameraUi> =
        discoveredCameras.values.sortedBy { it.camera.name ?: it.camera.macAddress }

    private suspend fun updateScanningState() {
        withContext(mainDispatcher) {
            val currentState = _state.value
            if (currentState is PairingScreenState.Scanning) {
                _state.value = currentState.copy(devices = sortedDiscoveredCameras())
            }
        }
    }

    private suspend fun markProbeFailed(
        camera: Camera,
        throwable: Throwable,
        messageSuffix: String,
    ) {
        Log.warn(tag = TAG, throwable = throwable) {
            "Failed to probe model name for ${camera.macAddress}$messageSuffix"
        }
        val existing = discoveredCameras[camera.macAddress]
        if (existing is DiscoveredCameraUi.Detecting) {
            // Fallback to extracted make/model when probing fails
            val make = camera.vendor.vendorName
            val model = camera.vendor.extractModelFromPairingName(camera.name)
            discoveredCameras[camera.macAddress] = DiscoveredCameraUi.Detected(camera, make, model)
            updateScanningState()
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Catch-all ensures device doesn't stay stuck in Detecting
    private fun probeModelName(camera: Camera) {
        if (probeJobs[camera.macAddress]?.isActive == true) return

        probeJobs[camera.macAddress] =
            viewModelScope.launch(ioDispatcher) {
                try {
                    // Use a single timeout for the entire probe operation (connect + read)
                    withTimeout(MODEL_PROBE_TIMEOUT_MS) {
                        probeSemaphore.withPermit {
                            val connection = cameraRepository.connect(camera)
                            try {
                                val modelName = connection.readModelName()
                                val existing = discoveredCameras[camera.macAddress]
                                if (existing is DiscoveredCameraUi.Detecting) {
                                    // Transition from Detecting to Detected with probed model name
                                    val make = camera.vendor.vendorName
                                    discoveredCameras[camera.macAddress] =
                                        DiscoveredCameraUi.Detected(camera, make, modelName)
                                    updateScanningState()
                                }
                            } finally {
                                @Suppress("TooGenericExceptionCaught")
                                try {
                                    withContext(NonCancellable) { connection.disconnect() }
                                } catch (e: Exception) {
                                    Log.warn(tag = TAG, throwable = e) {
                                        "Error disconnecting after model probe"
                                    }
                                }
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    markProbeFailed(camera, e, " (timeout)")
                } catch (e: IOException) {
                    markProbeFailed(camera, e, "")
                } catch (e: UnsupportedOperationException) {
                    markProbeFailed(camera, e, " (unsupported)")
                } catch (e: CancellationException) {
                    // Don't mark as failed for cancellation - just let it propagate
                    throw e
                } catch (e: IllegalStateException) {
                    markProbeFailed(camera, e, " (illegal state)")
                } catch (e: Throwable) {
                    // Catch-all for any other unexpected exceptions so device doesn't stay stuck
                    // in Detecting state (finally removes from probeJobs, cancelProbeJobs won't
                    // find it)
                    markProbeFailed(camera, e, " (unexpected: ${e.javaClass.simpleName})")
                } finally {
                    probeJobs.remove(camera.macAddress)
                }
            }
    }

    /** Starts the Companion Device Manager association process for a specific camera. */
    fun requestCompanionPairing(camera: Camera) {
        stopScanning()
        _state.value = PairingScreenState.Associating(camera)

        Log.info(tag = TAG) { "Requesting companion device pairing for ${camera.macAddress}" }
        companionDeviceManagerHelper.requestAssociation(
            callback =
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(chooserLauncher: IntentSender) {
                        Log.debug(tag = TAG) { "Companion chooser pending" }
                        viewModelScope.launch { _associationRequest.send(chooserLauncher) }
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.error(tag = TAG) { "Companion Device Association failed: $error" }
                        _state.value =
                            PairingScreenState.Error(
                                camera = camera,
                                error = PairingError.UNKNOWN,
                                canRetry = true,
                            )
                    }
                },
            macAddress = camera.macAddress,
        )
    }

    private fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        cancelProbeJobs()
        cameraRepository.stopScanning()
    }

    private fun cancelProbeJobs() {
        val macAddresses = probeJobs.keys.toList()
        probeJobs.values.forEach { it.cancel() }
        probeJobs.clear()
        macAddresses.forEach { macAddress ->
            val existing = discoveredCameras[macAddress]
            if (existing is DiscoveredCameraUi.Detecting) {
                // Transition to Detected with extracted make/model when cancelling probe
                val camera = existing.camera
                val make = camera.vendor.vendorName
                val model = camera.vendor.extractModelFromPairingName(camera.name)
                discoveredCameras[macAddress] = DiscoveredCameraUi.Detected(camera, make, model)
            }
        }
        if (macAddresses.isNotEmpty()) {
            viewModelScope.launch { updateScanningState() }
        }
    }

    /**
     * Handles the result of a companion device association.
     *
     * @param data The [Intent] returned from the companion device chooser.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onCompanionAssociationResult(data: Intent?) {
        val currentState = _state.value
        val camera =
            if (currentState is PairingScreenState.Associating) currentState.camera else null

        if (camera == null) {
            Log.warn(tag = TAG) { "Received association result but no camera was being associated" }
            // Fallback or ignore? If we don't have a camera, we can't proceed.
            // Maybe go back to scanning.
            _state.value = PairingScreenState.Scanning(sortedDiscoveredCameras())
            startScanning()
            return
        }

        if (data == null) {
            Log.warn(tag = TAG) { "Companion association result was null" }
            _state.value = PairingScreenState.Scanning(sortedDiscoveredCameras())
            startScanning()
            return
        }

        // We verify the device returned matches what we expect, or just proceed with our camera
        // object
        // The association grants us permission to access the device in background.

        Log.info(tag = TAG) { "Companion device associated: ${camera.name} (${camera.macAddress})" }
        proceedToBonding(camera)
    }

    private fun proceedToBonding(camera: Camera) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                withContext(mainDispatcher) { _state.value = PairingScreenState.Bonding(camera) }

                if (bluetoothBondingChecker.isDeviceBonded(camera.macAddress)) {
                    Log.info(tag = TAG) { "Device ${camera.macAddress} is already bonded" }
                    proceedToConnecting(camera)
                    return@launch
                }

                Log.info(tag = TAG) { "Initiating Bluetooth bonding for ${camera.macAddress}..." }
                if (!bluetoothBondingChecker.createBond(camera.macAddress)) {
                    Log.error(tag = TAG) {
                        "Failed to initiate Bluetooth bonding for ${camera.macAddress}"
                    }
                    withContext(mainDispatcher) {
                        _state.value =
                            PairingScreenState.Error(camera, PairingError.UNKNOWN, canRetry = true)
                    }
                    return@launch
                }

                if (waitForBonding(camera)) {
                    proceedToConnecting(camera)
                } else {
                    withContext(mainDispatcher) {
                        _state.value =
                            PairingScreenState.Error(camera, PairingError.TIMEOUT, canRetry = true)
                    }
                }
            }
    }

    private suspend fun proceedToConnecting(camera: Camera) {
        withContext(mainDispatcher) { _state.value = PairingScreenState.Connecting(camera) }
        performPairingConnection(camera)
    }

    private suspend fun waitForBonding(camera: Camera): Boolean {
        var lastBondState: Int? = null

        try {
            withTimeout(BONDING_TIMEOUT_MS) {
                while (true) {
                    delay(BOND_CHECK_DELAY_MS)
                    val bondState = bluetoothBondingChecker.getBondState(camera.macAddress)

                    if (bondState != lastBondState) {
                        logBondStateChange(camera.macAddress, bondState)
                        lastBondState = bondState
                    }

                    if (
                        bondState == android.bluetooth.BluetoothDevice.BOND_BONDED ||
                            bluetoothBondingChecker.isDeviceBonded(camera.macAddress)
                    ) {
                        Log.info(tag = TAG) {
                            "Bluetooth bonding completed for ${camera.macAddress}"
                        }
                        return@withTimeout
                    }
                }
            }
            return true
        } catch (_: TimeoutCancellationException) {
            Log.error(tag = TAG) { "Bluetooth bonding timed out for ${camera.macAddress}" }
            return false
        }
    }

    private fun logBondStateChange(macAddress: String, bondState: Int?) {
        val stateName =
            when (bondState) {
                android.bluetooth.BluetoothDevice.BOND_NONE -> "BOND_NONE"
                android.bluetooth.BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
                android.bluetooth.BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
                else -> "UNKNOWN($bondState)"
            }
        Log.info(tag = TAG) { "Bond state changed for $macAddress: $stateName" }
    }

    private suspend fun performPairingConnection(camera: Camera) {
        var connection: CameraConnection? = null
        try {
            Log.info(tag = TAG) { "Connecting to ${camera.name ?: camera.macAddress}..." }
            connection =
                withTimeout(PAIRING_CONNECTION_TIMEOUT_MS) { cameraRepository.connect(camera) }

            if (!connection.initializePairing()) {
                Log.error(tag = TAG) { "Vendor-specific pairing initialization failed" }
                withContext(mainDispatcher) {
                    _state.value =
                        PairingScreenState.Error(camera, PairingError.UNKNOWN, canRetry = true)
                }
                return
            }

            onPairingSuccessful(camera)
        } catch (e: TimeoutCancellationException) {
            Log.error(tag = TAG, throwable = e) { "Pairing timed out for ${camera.macAddress}" }
            withContext(mainDispatcher) {
                _state.value =
                    PairingScreenState.Error(camera, PairingError.TIMEOUT, canRetry = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            handlePairingException(camera, e)
        } catch (e: IllegalStateException) {
            handlePairingException(camera, e)
        } finally {
            try {
                withContext(NonCancellable) { connection?.disconnect() }
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting after pairing" }
            } catch (e: IllegalStateException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting after pairing" }
            }
        }
    }

    private suspend fun handlePairingException(camera: Camera, e: Exception) {
        Log.error(tag = TAG, throwable = e) { "Pairing failed" }
        val error =
            when {
                e.message?.contains("reject", ignoreCase = true) == true -> PairingError.REJECTED
                e.message?.contains("timeout", ignoreCase = true) == true -> PairingError.TIMEOUT
                else -> PairingError.UNKNOWN
            }
        withContext(mainDispatcher) {
            _state.value = PairingScreenState.Error(camera, error, canRetry = true)
        }
    }

    private suspend fun onPairingSuccessful(camera: Camera) {
        Log.info(tag = TAG) { "Pairing successful, adding ${camera.macAddress} to repository..." }
        pairedDevicesRepository.addDevice(camera, enabled = true)
        pairedDevicesRepository.setSyncEnabled(true)
        withContext(mainDispatcher) { _state.value = PairingScreenState.Success(camera) }

        // Auto-close after delay
        successEventSent.set(false)
        scheduleSuccessNavigation()
    }

    /**
     * Removes the system-level bond and restarts the pairing flow.
     *
     * @param camera The camera to unbond and retry pairing with.
     */
    fun removeBondAndRetry(camera: Camera) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                val wasBonded = bluetoothBondingChecker.isDeviceBonded(camera.macAddress)

                if (wasBonded) {
                    // Device is bonded, try to remove it
                    val bondRemoved = bluetoothBondingChecker.removeBond(camera.macAddress)
                    if (bondRemoved) {
                        Log.info(tag = TAG) {
                            "Bond removed for ${camera.macAddress}, restarting pairing"
                        }
                        delay(BOND_CHECK_DELAY_MS)
                    } else {
                        Log.warn(tag = TAG) {
                            "Failed to remove bond for ${camera.macAddress}, proceeding anyway"
                        }
                    }
                } else {
                    Log.info(tag = TAG) {
                        "Device ${camera.macAddress} is not bonded, restarting pairing"
                    }
                }

                // Remove from discovered devices and restart scanning
                discoveredCameras.remove(camera.macAddress)
                withContext(mainDispatcher) {
                    _state.value = PairingScreenState.Scanning(sortedDiscoveredCameras())
                }
                startScanning()
            }
    }

    /** Cancels the current pairing attempt. */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        stopScanning() // Stop any scanning before restart

        // Reset to scanning
        _state.value = PairingScreenState.Scanning(sortedDiscoveredCameras())
        startScanning()
    }

    fun manualCloseSuccess() {
        successJob.getAndSet(null)?.cancel()
        if (successEventSent.compareAndSet(false, true)) {
            viewModelScope.launch(mainDispatcher) {
                _navigationEvents.send(PairingNavigationEvent.DevicePaired)
            }
        }
    }

    /** Gathers diagnostic information and triggers the system intent to send a feedback report. */
    fun sendFeedback() {
        viewModelScope.launch(ioDispatcher) {
            val currentState = _state.value
            val extraInfo = buildString {
                appendLine("Pairing State Info:")
                appendLine("Current State: $currentState")

                val camera =
                    when (currentState) {
                        is PairingScreenState.Associating -> currentState.camera
                        is PairingScreenState.Bonding -> currentState.camera
                        is PairingScreenState.Connecting -> currentState.camera
                        is PairingScreenState.Success -> currentState.camera
                        is PairingScreenState.Error -> currentState.camera
                        is PairingScreenState.Scanning -> null
                    }

                if (camera != null) {
                    appendLine("Target Camera: ${camera.name} (${camera.macAddress})")
                }

                if (currentState is PairingScreenState.Error) {
                    appendLine("Error: ${currentState.error}")
                }
            }
            issueReporter.sendIssueReport(extraInfo = extraInfo)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pairingJob?.cancel()
        successJob.getAndSet(null)?.cancel()
        stopScanning()
    }

    private fun scheduleSuccessNavigation() {
        val job =
            viewModelScope.launch(mainDispatcher, start = CoroutineStart.LAZY) {
                delay(5_000L)
                if (successEventSent.compareAndSet(false, true)) {
                    _navigationEvents.send(PairingNavigationEvent.DevicePaired)
                }
            }
        successJob.getAndSet(job)?.cancel()
        if (successJob.get() === job && !job.isCancelled) {
            job.start()
        } else {
            job.cancel()
        }
    }
}

/** State of the pairing screen. */
sealed interface PairingScreenState {
    /** Actively scanning for cameras. */
    data class Scanning(val devices: List<DiscoveredCameraUi> = emptyList()) : PairingScreenState

    /** Associating with the device via Companion Device Manager. */
    data class Associating(val camera: Camera) : PairingScreenState

    /** Bonding with the device at OS level. */
    data class Bonding(val camera: Camera) : PairingScreenState

    /** Connecting to the device to finalize pairing. */
    data class Connecting(val camera: Camera) : PairingScreenState

    /** Pairing successful. */
    data class Success(val camera: Camera) : PairingScreenState

    /** Pairing failed. */
    data class Error(val camera: Camera, val error: PairingError, val canRetry: Boolean) :
        PairingScreenState
}

/** Navigation events emitted by the PairingViewModel. */
sealed interface PairingNavigationEvent {
    /** Emitted when a device has been successfully paired. */
    data object DevicePaired : PairingNavigationEvent
}
