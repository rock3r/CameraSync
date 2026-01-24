package dev.sebastiano.camerasync.pairing

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.LogEngine
import com.juul.kable.logs.Logging
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.ble.buildManufacturerDataMap
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.logging.KhronicleLogEngine
import dev.zacsweers.metro.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val TAG = "PairingViewModel"

/**
 * ViewModel for the pairing screen.
 *
 * Manages BLE scanning for supported cameras and handles the pairing process. Excludes
 * already-paired devices from the scan results.
 */
/**
 * @param ioDispatcher The dispatcher to use for IO operations. Can be overridden in tests to use a
 *   test dispatcher.
 */
@OptIn(ExperimentalUuidApi::class)
class PairingViewModel
@Inject
constructor(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val cameraRepository: CameraRepository,
    private val vendorRegistry: CameraVendorRegistry,
    private val bluetoothBondingChecker: BluetoothBondingChecker,
    private val companionDeviceManagerHelper: CompanionDeviceManagerHelper,
    private val issueReporter: IssueReporter,
    private val ioDispatcher: CoroutineDispatcher,
    private val loggingEngine: LogEngine = KhronicleLogEngine,
) : ViewModel() {

    private val _state = mutableStateOf<PairingScreenState>(PairingScreenState.Idle)
    val state: State<PairingScreenState> = _state

    private val _navigationEvents = Channel<PairingNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<PairingNavigationEvent> = _navigationEvents.receiveAsFlow()

    private val _associationRequest = Channel<IntentSender>(Channel.BUFFERED)
    val associationRequest: Flow<IntentSender> = _associationRequest.receiveAsFlow()

    private var scanJob: Job? = null
    private var pairingJob: Job? = null

    @OptIn(ObsoleteKableApi::class)
    private val scanner: Scanner<PlatformAdvertisement>? =
        try {
            Scanner {
                // We don't use filters here because some cameras might not advertise
                // the service UUID in the advertisement packet.
                // We filter discovered devices in onDiscovery instead.
                logging {
                    engine = loggingEngine
                    level = Logging.Level.Events
                    format = Logging.Format.Multiline
                }
                // Use SCAN_MODE_BALANCED to avoid Android throttling
                // LOW_LATENCY scans too frequently and gets throttled after repeated use
                scanSettings =
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            }
        } catch (e: Exception) {
            Log.warn(tag = TAG, throwable = e) {
                "Failed to create Scanner (may be in test environment)"
            }
            null
        }

    private val discoveredDevices = mutableMapOf<String, Camera>()

    init {
        // Automatically start scanning when the ViewModel is created
        startScanning()
    }

    fun requestCompanionPairing() {
        companionDeviceManagerHelper.requestAssociation(
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(chooserLauncher: IntentSender) {
                    viewModelScope.launch { _associationRequest.send(chooserLauncher) }
                }

                override fun onFailure(error: CharSequence?) {
                    Log.error(tag = TAG) { "Companion Device Association failed: $error" }
                    _state.value = PairingScreenState.Idle // Reset state?
                }
            }
        )
    }

    fun onCompanionAssociationResult(data: Intent?) {
        if (data == null) return

        val scanResult =
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)

        val device =
            data.getParcelableExtra(
                CompanionDeviceManager.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            )

        val camera = scanResult?.toCamera() ?: device?.toCamera()

        if (camera != null) {
            Log.info(tag = TAG) {
                "Companion device selected: ${camera.name} (${camera.macAddress})"
            }
            pairDevice(camera)
        } else {
            Log.warn(tag = TAG) { "Could not convert companion device result to Camera" }
        }
    }

    private fun ScanResult.toCamera(): Camera? {
        val record = scanRecord ?: return null
        val mfrData = record.manufacturerSpecificData

        val mfrMap = mutableMapOf<Int, ByteArray>()
        for (i in 0 until mfrData.size()) {
            mfrMap[mfrData.keyAt(i)] = mfrData.valueAt(i)
        }

        val serviceUuids =
            record.serviceUuids?.map { Uuid.parse(it.uuid.toString()) } ?: emptyList()

        val name = device.name ?: record.deviceName

        val vendor =
            vendorRegistry.identifyVendor(
                deviceName = name,
                serviceUuids = serviceUuids,
                manufacturerData = mfrMap,
            )

        if (vendor == null) return null

        return Camera(
            identifier = device.address,
            name = name,
            macAddress = device.address,
            vendor = vendor,
        )
    }

    private fun BluetoothDevice.toCamera(): Camera? {
        val name = name
        // Try to identify based on name only (vendor registry supports it fallback)
        val vendor =
            vendorRegistry.identifyVendor(
                deviceName = name,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            )

        if (vendor == null) return null

        return Camera(identifier = address, name = name, macAddress = address, vendor = vendor)
    }

    /** Starts scanning for cameras. */
    fun startScanning() {
        if (scanJob != null) return
        if (scanner == null) {
            // Scanner not available (e.g., in test environment)
            return
        }

        discoveredDevices.clear()
        _state.value = PairingScreenState.Scanning(emptyList())

        scanJob =
            scanner.advertisements
                .onEach { advertisement -> onDiscovery(advertisement) }
                .onStart { Log.info(tag = TAG) { "BLE scan started" } }
                .onCompletion { Log.info(tag = TAG) { "BLE scan completed" } }
                .flowOn(ioDispatcher)
                .launchIn(viewModelScope)
    }

    /** Stops the current scan. */
    fun stopScanning() {
        scanJob?.cancel("Stopping scan")
        scanJob = null

        val currentState = _state.value
        if (currentState is PairingScreenState.Scanning) {
            _state.value = currentState.copy(isScanning = false)
        }
    }

    private suspend fun onDiscovery(advertisement: PlatformAdvertisement) {
        val camera = advertisement.toCamera() ?: return

        // Check if already paired
        if (pairedDevicesRepository.isDevicePaired(camera.macAddress)) {
            Log.debug(tag = TAG) { "Device ${camera.macAddress} already paired, skipping" }
            return
        }

        discoveredDevices[camera.macAddress] = camera

        val currentState = _state.value
        if (currentState is PairingScreenState.Scanning) {
            _state.value =
                PairingScreenState.Scanning(
                    discoveredDevices =
                        discoveredDevices.values.toList().sortedByDescending { it.name }
                )
        }
    }

    /** Starts pairing with the selected camera. */
    fun pairDevice(camera: Camera) {
        stopScanning()

        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                // Check if device is already bonded at system level
                if (bluetoothBondingChecker.isDeviceBonded(camera.macAddress)) {
                    Log.warn(tag = TAG) {
                        "Device ${camera.macAddress} is already bonded at system level"
                    }
                    _state.value = PairingScreenState.AlreadyBonded(camera)
                    return@launch
                }

                _state.value = PairingScreenState.Pairing(camera)

                var connection: CameraConnection? = null
                try {
                    // First, initiate OS-level bonding explicitly
                    // This triggers the system pairing dialog
                    Log.info(tag = TAG) {
                        "Initiating OS bonding for ${camera.name ?: camera.macAddress}..."
                    }

                    val bondInitiated = bluetoothBondingChecker.createBond(camera.macAddress)
                    if (!bondInitiated) {
                        Log.error(tag = TAG) { "Failed to initiate bonding" }
                        _state.value =
                            PairingScreenState.Pairing(camera, error = PairingError.UNKNOWN)
                        return@launch
                    }

                    // Wait for bonding to complete (user accepts the pairing dialog)
                    // Poll the bond state with a timeout
                    val bondTimeout = 60_000L // 60 seconds for user to accept
                    val pollInterval = 500L
                    var elapsed = 0L

                    while (
                        !bluetoothBondingChecker.isDeviceBonded(camera.macAddress) &&
                            elapsed < bondTimeout
                    ) {
                        delay(pollInterval)
                        elapsed += pollInterval
                    }

                    if (!bluetoothBondingChecker.isDeviceBonded(camera.macAddress)) {
                        Log.error(tag = TAG) {
                            "Bonding timed out or was rejected for ${camera.macAddress}"
                        }
                        _state.value =
                            PairingScreenState.Pairing(camera, error = PairingError.REJECTED)
                        return@launch
                    }

                    Log.info(tag = TAG) { "OS bonding successful, establishing BLE connection..." }

                    // Now establish a BLE connection to verify everything works
                    Log.info(tag = TAG) { "Connecting to ${camera.name ?: camera.macAddress}..." }
                    connection = cameraRepository.connect(camera)

                    // Perform vendor-specific pairing initialization if required
                    // (e.g., Sony cameras need a specific command written to EE01)
                    val pairingSuccess = connection.initializePairing()
                    if (!pairingSuccess) {
                        Log.error(tag = TAG) { "Vendor-specific pairing initialization failed" }
                        _state.value =
                            PairingScreenState.Pairing(camera, error = PairingError.UNKNOWN)
                        return@launch
                    }

                    // If we get here, pairing was successful (user accepted the dialog)
                    Log.info(tag = TAG) { "Pairing successful, adding device to repository..." }

                    // Now add the device to the paired devices repository
                    pairedDevicesRepository.addDevice(camera, enabled = true)

                    Log.info(tag = TAG) {
                        "Device paired successfully: ${camera.name ?: camera.macAddress}"
                    }
                    // Emit navigation event instead of setting success flag in state
                    _navigationEvents.send(PairingNavigationEvent.DevicePaired)
                } catch (e: Exception) {
                    Log.error(tag = TAG, throwable = e) { "Pairing failed" }
                    val error =
                        when {
                            e.message?.contains("reject", ignoreCase = true) == true ->
                                PairingError.REJECTED
                            e.message?.contains("timeout", ignoreCase = true) == true ->
                                PairingError.TIMEOUT
                            else -> PairingError.UNKNOWN
                        }
                    _state.value = PairingScreenState.Pairing(camera, error = error)
                } finally {
                    // Disconnect after pairing - the sync coordinator will reconnect
                    // when the device is enabled
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        Log.warn(tag = TAG, throwable = e) { "Error disconnecting after pairing" }
                    }
                }
            }
    }

    /** Removes the system-level bond and retries pairing. */
    fun removeBondAndRetry(camera: Camera) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                val removed = bluetoothBondingChecker.removeBond(camera.macAddress)
                if (removed) {
                    Log.info(tag = TAG) {
                        "Bond removed for ${camera.macAddress}, retrying pairing"
                    }
                    // Wait a moment for the system to process the unbond
                    delay(500)
                    // Retry pairing
                    pairDevice(camera)
                } else {
                    Log.warn(tag = TAG) {
                        "Failed to remove bond for ${camera.macAddress}, showing manual instructions"
                    }
                    _state.value = PairingScreenState.AlreadyBonded(camera, removeFailed = true)
                }
            }
    }

    /** Cancels the current pairing attempt. */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.value = PairingScreenState.Idle
    }

    /** Sends feedback report with current pairing context. */
    fun sendFeedback() {
        viewModelScope.launch(ioDispatcher) {
            val currentState = _state.value
            val extraInfo = buildString {
                appendLine("Pairing State Info:")
                appendLine("Current State: $currentState")
                if (currentState is PairingScreenState.Scanning) {
                    appendLine(
                        "Discovered Devices: ${currentState.discoveredDevices.map { "${it.name} (${it.macAddress})" }}"
                    )
                }
                if (currentState is PairingScreenState.Pairing) {
                    appendLine(
                        "Target Camera: ${currentState.camera.name} (${currentState.camera.macAddress})"
                    )
                    appendLine("Error: ${currentState.error}")
                }
            }
            issueReporter.sendIssueReport(extraInfo = extraInfo)
        }
    }

    /** Converts a BLE advertisement to a Camera by identifying the vendor. */
    private fun PlatformAdvertisement.toCamera(): Camera? {
        // Build manufacturer data map from the advertisement
        val mfrData = buildManufacturerDataMap()

        val vendor =
            vendorRegistry.identifyVendor(
                deviceName = peripheralName ?: name,
                serviceUuids = uuids,
                manufacturerData = mfrData,
            )

        if (vendor == null) {
            Log.warn(tag = TAG) {
                "No vendor recognized for device: ${peripheralName ?: name} (services: $uuids, mfr: ${mfrData.keys})"
            }
            return null
        }

        Log.info(tag = TAG) { "Discovered ${vendor.vendorName} camera: ${peripheralName ?: name}" }
        return Camera(
            identifier = identifier,
            name = peripheralName ?: name,
            macAddress = identifier,
            vendor = vendor,
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        pairingJob?.cancel()
    }
}

/** State of the pairing screen. */
sealed interface PairingScreenState {
    /** Initial state, ready to scan. */
    data object Idle : PairingScreenState

    /** Actively scanning for cameras. */
    data class Scanning(val discoveredDevices: List<Camera>, val isScanning: Boolean = true) :
        PairingScreenState

    /** Device is already bonded at system level. */
    data class AlreadyBonded(val camera: Camera, val removeFailed: Boolean = false) :
        PairingScreenState

    /** Pairing with a selected camera. */
    data class Pairing(val camera: Camera, val error: PairingError? = null) : PairingScreenState
}

/** Navigation events emitted by the PairingViewModel. */
sealed interface PairingNavigationEvent {
    /** Emitted when a device is successfully paired. */
    data object DevicePaired : PairingNavigationEvent
}
