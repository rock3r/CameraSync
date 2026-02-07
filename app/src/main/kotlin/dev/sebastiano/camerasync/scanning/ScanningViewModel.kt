package dev.sebastiano.camerasync.scanning

import android.bluetooth.le.ScanSettings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.ble.buildManufacturerDataMap
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.logging.KhronicleLogEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

private const val TAG = "ScanningViewModel"

/**
 * ViewModel for the camera scanning screen.
 *
 * Manages BLE scanning for supported cameras and exposes discovered devices to the UI. Supports
 * multiple camera vendors through the camera vendor registry.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ScanningViewModel(private val vendorRegistry: CameraVendorRegistry) : ViewModel() {

    private val _state = mutableStateOf<PairingState>(PairingState.Loading)
    val state: State<PairingState> = _state

    private var scanJob: Job? = null

    @OptIn(ObsoleteKableApi::class)
    private val scanner = Scanner {
        // No filters to allow all devices and filter in onDiscovery
        logging {
            engine = KhronicleLogEngine
            level = Logging.Level.Events
            format = Logging.Format.Multiline
        }
        scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    init {
        doScan()
    }

    /** Starts scanning for supported cameras. */
    fun doScan() {
        if (scanJob != null) return
        _state.value = PairingState.Scanning(mutableStateMapOf())

        scanJob =
            scanner.advertisements
                .onEach { advertisement ->
                    if (_state.value !is PairingState.Scanning) {
                        _state.value = PairingState.Scanning(mutableStateMapOf())
                    }
                    onDiscovery(advertisement)
                }
                .onStart { Log.info(tag = TAG) { "BLE scan started" } }
                .onCompletion { Log.info(tag = TAG) { "BLE scan completed" } }
                .flowOn(Dispatchers.IO)
                .launchIn(viewModelScope)
    }

    private fun onDiscovery(advertisement: PlatformAdvertisement) {
        val currentState = _state.value as PairingState.Scanning
        val camera = advertisement.toCamera()
        if (camera != null) {
            currentState.found[advertisement.identifier] = camera
        }
    }

    /** Stops the current scan. */
    fun stopScan() {
        if (scanJob == null) return
        scanJob?.cancel("Stopping scan")
        scanJob = null

        val previousState = _state.value
        val discoveredDevices =
            if (previousState is PairingState.Scanning) {
                previousState.found
            } else {
                mutableStateMapOf()
            }

        _state.value = PairingState.Done(discoveredDevices)
    }

    /**
     * Converts a BLE advertisement to a Camera by identifying the vendor.
     *
     * @return A Camera instance if a vendor is recognized, or null if no vendor matches.
     */
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

        // Generic metadata parsing
        val metadata = vendor.parseAdvertisementMetadata(mfrData)

        Log.info(tag = TAG) { "Discovered ${vendor.vendorName} camera: ${peripheralName ?: name}" }
        return Camera(
            identifier = identifier,
            name = peripheralName ?: name,
            macAddress = identifier,
            vendor = vendor,
            vendorMetadata = metadata,
        )
    }
}

/** State of the camera pairing/scanning process. */
internal sealed interface PairingState {

    /** Initial loading state. */
    data object Loading : PairingState

    /** Actively scanning for cameras. */
    data class Scanning(override val found: SnapshotStateMap<String, Camera>) :
        PairingState, WithResults

    /** Scanning completed. */
    data class Done(override val found: SnapshotStateMap<String, Camera>) :
        PairingState, WithResults

    /** Interface for states that have scan results. */
    interface WithResults {
        val found: Map<String, Camera>
    }
}
