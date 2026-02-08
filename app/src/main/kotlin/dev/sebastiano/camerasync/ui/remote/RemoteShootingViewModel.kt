package dev.sebastiano.camerasync.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.devicesync.DeviceConnectionManager
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Remote Shooting screen.
 *
 * Manages the connection to the camera's remote control delegate and exposes capabilities and state
 * for the UI.
 */
@Inject
class RemoteShootingViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RemoteShootingUiState>(RemoteShootingUiState.Loading)
    val uiState: StateFlow<RemoteShootingUiState> = _uiState

    private var loadJob: Job? = null

    fun loadDevice(macAddress: String) {
        loadJob?.cancel()
        _uiState.value = RemoteShootingUiState.Loading
        loadJob =
            viewModelScope.launch(ioDispatcher) {
                val device = pairedDevicesRepository.getDevice(macAddress)
                if (device == null) {
                    _uiState.value = RemoteShootingUiState.Error("Device not found")
                    return@launch
                }

                // Get active connection from manager
                // Note: This assumes the device is already connected via the service.
                // If the service is running in another process or bound, we might need a different
                // way
                // to access the connection object.
                // However, DeviceConnectionManager is likely a singleton in the app process.
                val connection = deviceConnectionManager.getConnection(macAddress)
                if (connection == null) {
                    _uiState.value =
                        RemoteShootingUiState.Error(
                            "Device not connected via BLE. Please connect from the main list first."
                        )
                    return@launch
                }

                val delegate = connection.getRemoteControlDelegate()
                if (delegate == null) {
                    _uiState.value =
                        RemoteShootingUiState.Error("Remote control not supported for this device")
                    return@launch
                }

                val capabilities = connection.camera.vendor.getRemoteControlCapabilities()
                _uiState.value =
                    RemoteShootingUiState.Ready(
                        deviceName = device.name ?: "Camera",
                        capabilities = capabilities,
                        delegate = delegate,
                    )
            }
    }

    /**
     * Triggers remote capture. Exceptions (e.g. BLE disconnect) are caught and logged so they do
     * not crash the app.
     */
    fun triggerCapture() {
        val delegate = (_uiState.value as? RemoteShootingUiState.Ready)?.delegate ?: return
        viewModelScope.launch(ioDispatcher) {
            @Suppress("TooGenericExceptionCaught")
            try {
                delegate.triggerCapture()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Trigger capture failed (e.g. camera disconnected)"
                }
            }
        }
    }

    /**
     * Disconnects Wi‑Fi remote session. Exceptions (e.g. BLE disconnect) are caught and logged so
     * they do not crash the app.
     */
    fun disconnectWifi() {
        val delegate = (_uiState.value as? RemoteShootingUiState.Ready)?.delegate ?: return
        viewModelScope.launch(ioDispatcher) {
            @Suppress("TooGenericExceptionCaught")
            try {
                delegate.disconnectWifi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Disconnect Wi‑Fi failed (e.g. camera disconnected)"
                }
            }
        }
    }

    companion object {
        private const val TAG = "RemoteShootingViewModel"
    }
}

sealed interface RemoteShootingUiState {
    data object Loading : RemoteShootingUiState

    data class Error(val message: String) : RemoteShootingUiState

    data class Ready(
        val deviceName: String,
        val capabilities: RemoteControlCapabilities,
        val delegate: RemoteControlDelegate,
    ) : RemoteShootingUiState
}
