package dev.sebastiano.camerasync.pairing

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.util.size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.zacsweers.metro.Inject
import java.io.IOException
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
private const val BONDING_TIMEOUT_MS = 60_000L
private const val BOND_CHECK_DELAY_MS = 500L

/**
 * ViewModel for the pairing screen.
 *
 * Handles the companion device association flow and the subsequent camera pairing process.
 *
 * @param pairedDevicesRepository Repository for managing paired devices.
 * @param cameraRepository Repository for BLE camera communication.
 * @param vendorRegistry Registry of supported camera vendors.
 * @param bluetoothBondingChecker Utility for checking and managing Bluetooth bonding.
 * @param companionDeviceManagerHelper Helper for Companion Device Manager association.
 * @param issueReporter Utility for sending feedback reports.
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

    /** The current UI state of the pairing screen. */
    val state: State<PairingScreenState> = _state

    private val _navigationEvents = Channel<PairingNavigationEvent>(Channel.BUFFERED)

    /** A flow of navigation events, such as when pairing is successful. */
    val navigationEvents: Flow<PairingNavigationEvent> = _navigationEvents.receiveAsFlow()

    private val _associationRequest = Channel<IntentSender>(Channel.BUFFERED)

    /** A flow of [IntentSender]s for launching the Companion Device Manager chooser. */
    val associationRequest: Flow<IntentSender> = _associationRequest.receiveAsFlow()

    private var pairingJob: Job? = null

    /** Starts the Companion Device Manager association process. */
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
                    _state.value = PairingScreenState.Idle
                }
            }
        )
    }

    /**
     * Handles the result of a companion device association.
     *
     * @param data The [Intent] returned from the companion device chooser.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("DEPRECATION")
    fun onCompanionAssociationResult(data: Intent?) {
        if (data == null) {
            Log.warn(tag = TAG) { "Companion association result was null" }
            return
        }

        val camera = extractCameraFromIntent(data)
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun extractCameraFromIntent(data: Intent): Camera? {
        return try {
            val scanResult =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
            scanResult?.toCamera()
                ?: data
                    .getParcelableExtra(
                        CompanionDeviceManager.EXTRA_DEVICE,
                        BluetoothDevice::class.java,
                    )
                    ?.toCamera()
        } catch (e: IllegalArgumentException) {
            Log.warn(tag = TAG, throwable = e) { "Could not extract device from Intent" }
            null
        } catch (e: SecurityException) {
            Log.warn(tag = TAG, throwable = e) { "Could not extract device from Intent" }
            null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @OptIn(ExperimentalUuidApi::class)
    private fun ScanResult.toCamera(): Camera? {
        val record = scanRecord ?: return null
        val mfrData = record.manufacturerSpecificData

        val mfrMap = mutableMapOf<Int, ByteArray>()
        for (i in 0 until mfrData.size) {
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
            ) ?: return null

        // Parse BLE protocol version for Sony cameras
        val protocolVersion =
            if (vendor.vendorId == "sony") {
                SonyCameraVendor.parseProtocolVersion(mfrMap)
            } else {
                null
            }

        return Camera(
            identifier = device.address,
            name = name,
            macAddress = device.address,
            vendor = vendor,
            bleProtocolVersion = protocolVersion,
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @OptIn(ExperimentalUuidApi::class)
    private fun BluetoothDevice.toCamera(): Camera? {
        val name = name
        val vendor =
            vendorRegistry.identifyVendor(
                deviceName = name,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
            ) ?: return null

        return Camera(identifier = address, name = name, macAddress = address, vendor = vendor)
    }

    /**
     * Initiates the pairing process with a specific [camera].
     *
     * This includes ensuring the device is bonded at the OS level and performing any
     * vendor-specific pairing initialization.
     *
     * @param camera The camera to pair with.
     * @param allowExistingBond Whether to allow pairing with a device that is already bonded.
     */
    fun pairDevice(camera: Camera, allowExistingBond: Boolean = false) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                if (pairedDevicesRepository.isDevicePaired(camera.macAddress)) {
                    onDeviceAlreadyPaired(camera)
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

                if (!ensureDeviceBonded(camera)) return@launch

                performPairingConnection(camera)
            }
    }

    private suspend fun onDeviceAlreadyPaired(camera: Camera) {
        Log.info(tag = TAG) { "Device ${camera.macAddress} already paired" }
        pairedDevicesRepository.setSyncEnabled(true)
        _state.value = PairingScreenState.Idle
        _navigationEvents.send(PairingNavigationEvent.DevicePaired)
    }

    private suspend fun ensureDeviceBonded(camera: Camera): Boolean {
        if (bluetoothBondingChecker.isDeviceBonded(camera.macAddress)) {
            Log.info(tag = TAG) { "Device ${camera.macAddress} is already bonded" }
            return true
        }

        Log.info(tag = TAG) { "Initiating Bluetooth bonding for ${camera.macAddress}..." }
        if (!bluetoothBondingChecker.createBond(camera.macAddress)) {
            Log.error(tag = TAG) { "Failed to initiate Bluetooth bonding for ${camera.macAddress}" }
            _state.value = PairingScreenState.Pairing(camera, error = PairingError.UNKNOWN)
            return false
        }

        return waitForBonding(camera)
    }

    private suspend fun waitForBonding(camera: Camera): Boolean {
        val bondingStartTime = System.currentTimeMillis()
        var lastBondState: Int? = null

        while ((System.currentTimeMillis() - bondingStartTime) < BONDING_TIMEOUT_MS) {
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
                Log.info(tag = TAG) { "Bluetooth bonding completed for ${camera.macAddress}" }
                return true
            }
        }

        Log.error(tag = TAG) { "Bluetooth bonding timed out for ${camera.macAddress}" }
        _state.value = PairingScreenState.Pairing(camera, error = PairingError.TIMEOUT)
        return false
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
                _state.value = PairingScreenState.Pairing(camera, error = PairingError.UNKNOWN)
                return
            }

            onPairingSuccessful(camera)
        } catch (e: TimeoutCancellationException) {
            Log.error(tag = TAG, throwable = e) { "Pairing timed out for ${camera.macAddress}" }
            _state.value = PairingScreenState.Pairing(camera, error = PairingError.TIMEOUT)
        } catch (e: IOException) {
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
        } catch (e: IllegalStateException) {
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
            try {
                connection?.disconnect()
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting after pairing" }
            } catch (e: IllegalStateException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting after pairing" }
            }
        }
    }

    private suspend fun onPairingSuccessful(camera: Camera) {
        Log.info(tag = TAG) { "Pairing successful, adding ${camera.macAddress} to repository..." }
        pairedDevicesRepository.addDevice(camera, enabled = true)
        pairedDevicesRepository.setSyncEnabled(true)
        _state.value = PairingScreenState.Idle
        _navigationEvents.send(PairingNavigationEvent.DevicePaired)
    }

    /**
     * Removes the system-level bond and restarts the pairing flow.
     *
     * @param camera The camera to unbond and retry pairing with.
     */
    fun removeBondAndRetry(camera: Camera) {
        pairingJob =
            viewModelScope.launch(ioDispatcher) {
                if (bluetoothBondingChecker.removeBond(camera.macAddress)) {
                    Log.info(tag = TAG) {
                        "Bond removed for ${camera.macAddress}, restarting pairing"
                    }
                    delay(BOND_CHECK_DELAY_MS)
                    _state.value = PairingScreenState.Idle
                    requestCompanionPairing()
                } else {
                    Log.warn(tag = TAG) { "Failed to remove bond for ${camera.macAddress}" }
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

    /** Gathers diagnostic information and triggers the system intent to send a feedback report. */
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
    /** No pairing in progress. */
    data object Idle : PairingScreenState

    /** Device is already bonded at the OS level but not paired in the app. */
    data class AlreadyBonded(val camera: Camera, val removeFailed: Boolean = false) :
        PairingScreenState

    /** Pairing is actively in progress with a specific [camera]. */
    data class Pairing(val camera: Camera, val error: PairingError? = null) : PairingScreenState
}

/** Navigation events emitted by the PairingViewModel. */
sealed interface PairingNavigationEvent {
    /** Emitted when a device has been successfully paired. */
    data object DevicePaired : PairingNavigationEvent
}
