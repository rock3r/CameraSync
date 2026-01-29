package dev.sebastiano.camerasync.pairing

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.zacsweers.metro.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "PairingViewModel"
internal const val PAIRING_CONNECTION_TIMEOUT_MS = 30_000L

/**
 * ViewModel for the pairing screen.
 *
 * Handles the companion device association flow and pairing process.
 */
/**
 * @param ioDispatcher The dispatcher to use for IO operations. Can be overridden in tests to use a
 *   test dispatcher.
 */
@Inject
class PairingViewModel(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val cameraRepository: CameraRepository,
    private val vendorRegistry: CameraVendorRegistry,
    private val bluetoothBondingChecker: BluetoothBondingChecker,
    private val companionDeviceManagerHelper: CompanionDeviceManagerHelper,
    private val issueReporter: IssueReporter,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = mutableStateOf<PairingScreenState>(PairingScreenState.Idle)
    val state: State<PairingScreenState> = _state

    private val _navigationEvents = Channel<PairingNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<PairingNavigationEvent> = _navigationEvents.receiveAsFlow()

    private val _associationRequest = Channel<IntentSender>(Channel.BUFFERED)
    val associationRequest: Flow<IntentSender> = _associationRequest.receiveAsFlow()

    private var pairingJob: Job? = null

    fun requestCompanionPairing() {
        Log.info(tag = TAG) { "Requesting companion device pairing" }
        companionDeviceManagerHelper.requestAssociation(
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(chooserLauncher: IntentSender) {
                    Log.debug(tag = TAG) { "Companion chooser pending" }
                    viewModelScope.launch { _associationRequest.send(chooserLauncher) }
                }

                override fun onFailure(error: CharSequence?) {
                    Log.error(tag = TAG) { "Companion Device Association failed: $error" }
                    _state.value = PairingScreenState.Idle // Reset state?
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    fun onCompanionAssociationResult(data: Intent?) {
        if (data == null) {
            Log.warn(tag = TAG) { "Companion association result was null" }
            return
        }

        // Try ScanResult first (BLE devices), then BluetoothDevice (classic Bluetooth)
        // The Intent only contains one type, so we need to try them separately
        val camera =
            try {
                val scanResult =
                    data.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_DEVICE,
                        ScanResult::class.java,
                    )
                scanResult?.toCamera()
            } catch (e: ClassCastException) {
                // If ScanResult fails, try BluetoothDevice
                Log.debug(tag = TAG) {
                    "EXTRA_DEVICE is not a ScanResult, trying BluetoothDevice: ${e.message}"
                }
                try {
                    val device =
                        data.getParcelableExtra(
                            CompanionDeviceManager.EXTRA_DEVICE,
                            BluetoothDevice::class.java,
                        )
                    device?.toCamera()
                } catch (e2: Exception) {
                    Log.warn(tag = TAG, throwable = e2) {
                        "Could not extract device from Intent: ${e2.message}"
                    }
                    null
                }
            }

        if (camera != null) {
            Log.info(tag = TAG) {
                "Companion device selected: ${camera.name} (${camera.macAddress})"
            }
            pairDevice(camera, allowExistingBond = true)
        } else {
            Log.warn(tag = TAG) { "Could not convert companion device result to Camera" }
            _state.value = PairingScreenState.Idle
        }
    }

    @OptIn(ExperimentalUuidApi::class)
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

    @OptIn(ExperimentalUuidApi::class)
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

    /** Starts pairing with the selected camera. */
    fun pairDevice(camera: Camera, allowExistingBond: Boolean = false) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                if (pairedDevicesRepository.isDevicePaired(camera.macAddress)) {
                    Log.info(tag = TAG) { "Device ${camera.macAddress} already paired" }
                    pairedDevicesRepository.setSyncEnabled(true)
                    // Note: Presence observations are managed by PresenceObservationManager
                    // It will automatically pick up this device since it watches pairedDevices flow
                    _state.value = PairingScreenState.Idle
                    _navigationEvents.send(PairingNavigationEvent.DevicePaired)
                    return@launch
                }

                if (
                    !allowExistingBond && bluetoothBondingChecker.isDeviceBonded(camera.macAddress)
                ) {
                    Log.warn(tag = TAG) {
                        "Device ${camera.macAddress} is already bonded at system level"
                    }
                    _state.value = PairingScreenState.AlreadyBonded(camera)
                    return@launch
                }

                _state.value = PairingScreenState.Pairing(camera)

                // After CDM association, ensure Bluetooth bonding is complete before connecting
                // CDM association doesn't automatically bond the device - we need to do it
                // explicitly
                if (!bluetoothBondingChecker.isDeviceBonded(camera.macAddress)) {
                    Log.info(tag = TAG) {
                        "Device ${camera.macAddress} is not bonded yet. " +
                            "CDM association completed but Bluetooth bonding is required. " +
                            "Initiating bonding..."
                    }
                    val bondingInitiated = bluetoothBondingChecker.createBond(camera.macAddress)
                    if (!bondingInitiated) {
                        Log.error(tag = TAG) {
                            "Failed to initiate Bluetooth bonding for ${camera.macAddress}"
                        }
                        _state.value =
                            PairingScreenState.Pairing(camera, error = PairingError.UNKNOWN)
                        return@launch
                    }

                    // Wait for bonding to complete (user will see PIN dialog here if required)
                    // Some BLE devices bond without a PIN, so we check bond state directly
                    Log.info(tag = TAG) {
                        "Waiting for Bluetooth bonding to complete (PIN dialog may appear if required)..."
                    }
                    val bondingTimeout = 60_000L // 60 seconds for user to enter PIN if needed
                    val bondingStartTime = System.currentTimeMillis()
                    var bondingComplete = false
                    var lastBondState: Int? = null

                    while (
                        !bondingComplete &&
                            (System.currentTimeMillis() - bondingStartTime) < bondingTimeout
                    ) {
                        delay(500) // Check every 500ms
                        val bondState = bluetoothBondingChecker.getBondState(camera.macAddress)

                        // Log state changes
                        if (bondState != lastBondState) {
                            val stateName =
                                when (bondState) {
                                    android.bluetooth.BluetoothDevice.BOND_NONE -> "BOND_NONE"
                                    android.bluetooth.BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
                                    android.bluetooth.BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
                                    else -> "UNKNOWN($bondState)"
                                }
                            Log.info(tag = TAG) {
                                "Bond state changed for ${camera.macAddress}: $stateName"
                            }
                            lastBondState = bondState
                        }

                        // Check if bonding is complete
                        bondingComplete =
                            bondState == android.bluetooth.BluetoothDevice.BOND_BONDED ||
                                bluetoothBondingChecker.isDeviceBonded(camera.macAddress)

                        if (bondingComplete) {
                            Log.info(tag = TAG) {
                                "Bluetooth bonding completed for ${camera.macAddress}"
                            }
                        } else if (bondState == android.bluetooth.BluetoothDevice.BOND_BONDING) {
                            // Still bonding, continue waiting
                            Log.debug(tag = TAG) {
                                "Bonding in progress for ${camera.macAddress}..."
                            }
                        }
                    }

                    if (!bondingComplete) {
                        val finalState = bluetoothBondingChecker.getBondState(camera.macAddress)
                        Log.error(tag = TAG) {
                            "Bluetooth bonding timed out for ${camera.macAddress}. " +
                                "Final bond state: $finalState. " +
                                "User may have cancelled the PIN dialog or bonding failed."
                        }
                        _state.value =
                            PairingScreenState.Pairing(camera, error = PairingError.TIMEOUT)
                        return@launch
                    }
                } else {
                    Log.info(tag = TAG) {
                        "Device ${camera.macAddress} is already bonded, proceeding with connection"
                    }
                }

                var connection: CameraConnection? = null
                try {
                    // Now establish a BLE connection to verify everything works
                    Log.info(tag = TAG) { "Connecting to ${camera.name ?: camera.macAddress}..." }
                    connection =
                        withTimeout(PAIRING_CONNECTION_TIMEOUT_MS) {
                            cameraRepository.connect(camera)
                        }

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
                    pairedDevicesRepository.setSyncEnabled(true)
                    // Note: Presence observations are managed by PresenceObservationManager
                    // It will automatically pick up this device since it watches pairedDevices flow

                    Log.info(tag = TAG) {
                        "Device paired successfully: ${camera.name ?: camera.macAddress}"
                    }
                    // Emit navigation event instead of setting success flag in state
                    _navigationEvents.send(PairingNavigationEvent.DevicePaired)
                } catch (e: TimeoutCancellationException) {
                    Log.error(tag = TAG, throwable = e) {
                        "Pairing timed out while connecting to ${camera.macAddress}"
                    }
                    _state.value = PairingScreenState.Pairing(camera, error = PairingError.TIMEOUT)
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

    /** Removes the system-level bond and restarts the system pairing flow. */
    fun removeBondAndRetry(camera: Camera) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                val removed = bluetoothBondingChecker.removeBond(camera.macAddress)
                if (removed) {
                    Log.info(tag = TAG) {
                        "Bond removed for ${camera.macAddress}, restarting companion pairing"
                    }
                    // Wait a moment for the system to process the unbond
                    delay(500)
                    _state.value = PairingScreenState.Idle
                    requestCompanionPairing()
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

    override fun onCleared() {
        super.onCleared()
        pairingJob?.cancel()
    }
}

/** State of the pairing screen. */
sealed interface PairingScreenState {
    /** Initial state, ready to pair. */
    data object Idle : PairingScreenState

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
