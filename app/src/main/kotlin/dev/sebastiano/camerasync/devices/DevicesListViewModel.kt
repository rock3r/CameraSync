package dev.sebastiano.camerasync.devices

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.pairing.BluetoothBondingChecker
import dev.sebastiano.camerasync.util.AndroidBatteryOptimizationChecker
import dev.sebastiano.camerasync.util.BatteryOptimizationChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "DevicesListViewModel"

/**
 * ViewModel for the devices list screen.
 *
 * Manages the list of paired devices and their connection states. Communicates with the
 * [MultiDeviceSyncService] to control device sync.
 *
 * @param ioDispatcher The dispatcher to use for IO operations. Defaults to [Dispatchers.IO]. Can be
 *   overridden in tests to use a test dispatcher.
 */
class DevicesListViewModel(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val locationRepository: LocationRepository,
    private val bindingContextProvider: () -> Context,
    private val vendorRegistry: CameraVendorRegistry,
    private val bluetoothBondingChecker: BluetoothBondingChecker,
    private val issueReporter: IssueReporter,
    private val batteryOptimizationChecker: BatteryOptimizationChecker =
        AndroidBatteryOptimizationChecker(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = mutableStateOf<DevicesListState>(DevicesListState.Loading)
    val state: State<DevicesListState> = _state

    private var service: MultiDeviceSyncService? = null
    private var serviceConnection: ServiceConnection? = null
    private val deviceStatesFromService =
        MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())
    private val isScanningFromService = MutableStateFlow(false)
    private val batteryOptimizationStatus = MutableStateFlow(false)
    private var stateCollectionJob: Job? = null
    private var scanningCollectionJob: Job? = null

    init {
        observeDevices()
        bindToService()
        locationRepository.startLocationUpdates()
        checkBatteryOptimizationStatus()
    }

    private fun observeDevices() {
        viewModelScope.launch(ioDispatcher) {
            combine(
                    pairedDevicesRepository.pairedDevices,
                    deviceStatesFromService,
                    isScanningFromService,
                    pairedDevicesRepository.isSyncEnabled,
                    locationRepository.locationUpdates,
                    batteryOptimizationStatus,
                ) { flows ->
                    val pairedDevices = flows[0] as List<*>
                    @Suppress("UNCHECKED_CAST")
                    val connectionStates = flows[1] as Map<String, DeviceConnectionState>
                    val isScanning = flows[2] as Boolean
                    val isSyncEnabled = flows[3] as Boolean
                    val currentLocation = flows[4] as GpsLocation?
                    val isIgnoringBatteryOptimizations = flows[5] as Boolean

                    if (pairedDevices.isEmpty()) {
                        DevicesListState.Empty
                    } else {
                        val devicesWithState =
                            pairedDevices.map { device ->
                                device as PairedDevice
                                val connectionState =
                                    when {
                                        !device.isEnabled -> DeviceConnectionState.Disabled
                                        else ->
                                            connectionStates[device.macAddress]
                                                ?: DeviceConnectionState.Disconnected
                                    }
                                PairedDeviceWithState(device, connectionState)
                            }
                        val displayInfoMap =
                            computeDeviceDisplayInfo(devicesWithState.map { it.device })
                        DevicesListState.HasDevices(
                            devices = devicesWithState,
                            displayInfoMap = displayInfoMap,
                            isScanning = isScanning,
                            isSyncEnabled = isSyncEnabled,
                            currentLocation = currentLocation,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        )
                    }
                }
                .collect { newState -> _state.value = newState }
        }
    }

    /** Checks and updates the battery optimization status. Call when returning from settings. */
    fun checkBatteryOptimizationStatus() {
        viewModelScope.launch(ioDispatcher) {
            val context = bindingContextProvider()
            val isIgnoring = batteryOptimizationChecker.isIgnoringBatteryOptimizations(context)
            batteryOptimizationStatus.value = isIgnoring
        }
    }

    private fun bindToService() {
        viewModelScope.launch(Dispatchers.Main) {
            val context = bindingContextProvider()
            val intent = Intent(context, MultiDeviceSyncService::class.java)

            // Start the service if there are enabled devices AND global sync is enabled
            viewModelScope.launch(ioDispatcher) {
                if (
                    pairedDevicesRepository.hasEnabledDevices() &&
                        pairedDevicesRepository.isSyncEnabled.first()
                ) {
                    context.startService(intent)
                }
            }

            val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        Log.info(tag = TAG) { "Connected to MultiDeviceSyncService" }
                        service =
                            MultiDeviceSyncService.getInstanceFrom(binder as android.os.Binder)

                        // Observe device states from service
                        stateCollectionJob =
                            viewModelScope.launch(ioDispatcher) {
                                service?.deviceStates?.collect { states ->
                                    deviceStatesFromService.value = states
                                }
                            }

                        // Observe scanning state from service
                        scanningCollectionJob =
                            viewModelScope.launch(ioDispatcher) {
                                service?.isScanning?.collect { isScanning ->
                                    isScanningFromService.value = isScanning
                                }
                            }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.info(tag = TAG) { "Disconnected from MultiDeviceSyncService" }
                        service = null
                        stateCollectionJob?.cancel()
                        scanningCollectionJob?.cancel()
                        deviceStatesFromService.value = emptyMap()
                        isScanningFromService.value = false
                    }
                }

            serviceConnection = connection
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Sets whether a device is enabled for sync. */
    fun setDeviceEnabled(macAddress: String, enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            pairedDevicesRepository.setDeviceEnabled(macAddress, enabled)

            // Start service if we just enabled a device AND global sync is enabled
            // We call startService even if service is already bound, to ensure onStartCommand is
            // triggered and monitoring starts if it was stopped.
            if (enabled && pairedDevicesRepository.isSyncEnabled.first()) {
                val context = bindingContextProvider()
                context.startService(Intent(context, MultiDeviceSyncService::class.java))
            }
        }
    }

    /** Unpairs (removes) a device. */
    fun unpairDevice(macAddress: String) {
        viewModelScope.launch(ioDispatcher) {
            // First disconnect if connected
            service?.disconnectDevice(macAddress)

            // Remove the OS-level Bluetooth bond
            val bondRemoved = bluetoothBondingChecker.removeBond(macAddress)
            if (!bondRemoved) {
                Log.warn(tag = TAG) {
                    "Could not remove OS-level bond for $macAddress (may not have been bonded)"
                }
            }

            // Then remove from storage
            pairedDevicesRepository.removeDevice(macAddress)
        }
    }

    /** Retries connection to a failed device. */
    fun retryConnection(macAddress: String) {
        viewModelScope.launch(ioDispatcher) {
            val device = pairedDevicesRepository.getDevice(macAddress) ?: return@launch
            service?.connectDevice(device)
        }
    }

    /** Manually triggers a scan for all enabled but disconnected devices. */
    fun refreshConnections() {
        viewModelScope.launch(ioDispatcher) {
            pairedDevicesRepository.setSyncEnabled(true)
            val context = bindingContextProvider()
            val intent = MultiDeviceSyncService.createRefreshIntent(context)
            context.startService(intent)
        }
    }

    /** Sends a feedback report. */
    fun sendFeedback() {
        viewModelScope.launch(ioDispatcher) {
             // For devices list, we might want to attach info about all devices or just general state
             // The IssueReporter handles general state. We can pass extra info about paired devices status.
             val extraInfo = buildString {
                 appendLine("Paired Devices Status:")
                 val states = _state.value
                 if (states is DevicesListState.HasDevices) {
                     states.devices.forEach { deviceWithState ->
                         appendLine("- ${deviceWithState.device.name} (${deviceWithState.device.macAddress}): ${deviceWithState.connectionState}")
                     }
                 } else {
                     appendLine("No devices or loading.")
                 }
             }
             issueReporter.sendIssueReport(extraInfo = extraInfo)
        }
    }

    /**
     * Computes display information for all devices, determining make, model, and whether to show
     * pairing name.
     */
    private fun computeDeviceDisplayInfo(
        devices: List<PairedDevice>
    ): Map<String, DeviceDisplayInfo> {
        // Group devices by make/model to determine if we need to show pairing names
        val makeModelGroups =
            devices.groupBy { device ->
                val vendor = vendorRegistry.getVendorById(device.vendorId)
                val make = vendor?.vendorName ?: device.vendorId.replaceFirstChar { it.uppercase() }
                val model =
                    vendor?.extractModelFromPairingName(device.name) ?: device.name ?: "Unknown"
                MakeModel(make, model)
            }

        return devices.associate { device ->
            val vendor = vendorRegistry.getVendorById(device.vendorId)
            val make = vendor?.vendorName ?: device.vendorId.replaceFirstChar { it.uppercase() }
            val model = vendor?.extractModelFromPairingName(device.name) ?: device.name ?: "Unknown"
            val makeModel = MakeModel(make, model)
            val showPairingName = (makeModelGroups[makeModel]?.size ?: 0) > 1

            device.macAddress to
                DeviceDisplayInfo(
                    make = make,
                    model = model,
                    pairingName = device.name,
                    showPairingName = showPairingName,
                )
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationRepository.stopLocationUpdates()
        stateCollectionJob?.cancel()
        scanningCollectionJob?.cancel()
        serviceConnection?.let { connection ->
            try {
                bindingContextProvider().unbindService(connection)
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Error unbinding service" }
            }
        }
    }
}

/** Display information for a device in the UI. */
data class DeviceDisplayInfo(
    val make: String,
    val model: String,
    val pairingName: String?,
    val showPairingName: Boolean,
)

/** Internal helper to group devices by make and model. */
private data class MakeModel(val make: String, val model: String)

/** UI state for the devices list screen. */
sealed interface DevicesListState {
    /** Loading devices from storage. */
    data object Loading : DevicesListState

    /** No paired devices. */
    data object Empty : DevicesListState

    /** Has one or more paired devices. */
    data class HasDevices(
        val devices: List<PairedDeviceWithState>,
        val displayInfoMap: Map<String, DeviceDisplayInfo>,
        val isScanning: Boolean = false,
        val isSyncEnabled: Boolean = true,
        val currentLocation: GpsLocation? = null,
        val isIgnoringBatteryOptimizations: Boolean = true,
    ) : DevicesListState
}
