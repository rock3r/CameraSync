package dev.sebastiano.camerasync.ui.remote

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.devicesync.DeviceConnectionManager
import dev.sebastiano.camerasync.devicesync.IntentFactory
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val intentFactory: IntentFactory,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RemoteShootingUiState>(RemoteShootingUiState.Loading)
    val uiState: StateFlow<RemoteShootingUiState> = _uiState

    private var loadJob: Job? = null
    private var currentMacAddress: String? = null

    /**
     * Loads the device and observes connection changes for [macAddress].
     *
     * When the BLE connection for this device is dropped and re-established, the manager gets a new
     * [CameraConnection] (and thus a new [RemoteControlDelegate] with a fresh peripheral). This
     * method subscribes to [DeviceConnectionManager.connectionFlow] so the UI state is refreshed
     * whenever the connection is added, removed, or replaced—avoiding a stale delegate after
     * reconnect.
     */
    fun loadDevice(macAddress: String) {
        loadJob?.cancel()
        currentMacAddress = macAddress
        _uiState.value = RemoteShootingUiState.Loading
        loadJob =
            viewModelScope.launch(ioDispatcher) {
                deviceConnectionManager
                    .connectionFlow(macAddress)
                    .onEach { connection -> applyConnectionState(macAddress, connection) }
                    .catch { e ->
                        Log.warn(tag = TAG, throwable = e) {
                            "Error observing connection for $macAddress"
                        }
                        _uiState.value =
                            RemoteShootingUiState.Error(
                                "Failed to load device. Please go back and try again."
                            )
                    }
                    .launchIn(this)
            }
    }

    /**
     * Updates UI state from the current connection for [macAddress]. Called on initial load and
     * whenever [DeviceConnectionManager.connectionFlow] emits (e.g. on reconnect).
     */
    private suspend fun applyConnectionState(macAddress: String, connection: CameraConnection?) {
        if (connection == null) {
            _uiState.value =
                RemoteShootingUiState.Error(
                    "Device not connected via BLE. Please connect from the main list first."
                )
            return
        }
        val device = pairedDevicesRepository.getDevice(macAddress)
        if (device == null) {
            _uiState.value = RemoteShootingUiState.Error("Device not found")
            return
        }
        val delegate = connection.getRemoteControlDelegate()
        if (delegate == null) {
            _uiState.value =
                RemoteShootingUiState.Error("Remote control not supported for this device")
            return
        }
        val capabilities = connection.camera.vendor.getRemoteControlCapabilities()
        _uiState.value =
            RemoteShootingUiState.Ready(
                deviceName = device.name ?: "Camera",
                capabilities = capabilities,
                delegate = delegate,
                actionState = RemoteShootingActionState.Idle,
            )
    }

    /**
     * Triggers remote capture. Exceptions (e.g. BLE disconnect) are caught and logged so they do
     * not crash the app.
     */
    fun triggerCapture() {
        runAction(RemoteShootingAction.TriggerCapture) { delegate -> delegate.triggerCapture() }
    }

    /**
     * Disconnects Wi‑Fi remote session. Exceptions (e.g. BLE disconnect) are caught and logged so
     * they do not crash the app.
     */
    fun disconnectWifi() {
        runAction(RemoteShootingAction.DisconnectWifi) { delegate -> delegate.disconnectWifi() }
    }

    fun retryAction(action: RemoteShootingAction) {
        when (action) {
            RemoteShootingAction.TriggerCapture -> triggerCapture()
            RemoteShootingAction.DisconnectWifi -> disconnectWifi()
            RemoteShootingAction.ResetRemoteShooting -> resetRemoteShooting()
        }
    }

    fun resetRemoteShooting() {
        runAction(RemoteShootingAction.ResetRemoteShooting) {
            val macAddress = currentMacAddress ?: error("No device loaded for reset")
            val device = pairedDevicesRepository.getDevice(macAddress)
            if (device == null) {
                error("Device not found for reset")
            }
            if (!device.isEnabled) {
                error("Device is disabled. Enable sync to reset remote shooting.")
            }
            val intent = intentFactory.createRefreshDeviceIntent(context, macAddress)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun dismissActionError() {
        updateActionState(RemoteShootingActionState.Idle)
    }

    private fun runAction(
        action: RemoteShootingAction,
        block: suspend (RemoteControlDelegate) -> Unit,
    ) {
        val delegate = (_uiState.value as? RemoteShootingUiState.Ready)?.delegate ?: return
        updateActionState(RemoteShootingActionState.InProgress(action))
        viewModelScope.launch(ioDispatcher) {
            @Suppress("TooGenericExceptionCaught")
            try {
                block(delegate)
                updateActionState(RemoteShootingActionState.Idle)
            } catch (e: CancellationException) {
                updateActionState(RemoteShootingActionState.Idle)
                throw e
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Remote shooting action failed: $action" }
                updateActionState(buildActionError(action, e))
            }
        }
    }

    private fun updateActionState(actionState: RemoteShootingActionState) {
        val current = _uiState.value
        if (current is RemoteShootingUiState.Ready) {
            _uiState.value = current.copy(actionState = actionState)
        }
    }

    private fun buildActionError(
        action: RemoteShootingAction,
        error: Throwable,
    ): RemoteShootingActionState.Error {
        val message =
            when (error) {
                is UnsupportedOperationException -> error.message ?: "This action is not supported."
                is IllegalStateException -> error.message ?: "This action is not available."
                else -> "Action failed. Please try again."
            }
        val canRetry =
            action != RemoteShootingAction.ResetRemoteShooting &&
                error !is UnsupportedOperationException &&
                error !is IllegalStateException
        val canReset =
            action != RemoteShootingAction.ResetRemoteShooting &&
                error !is UnsupportedOperationException &&
                error !is IllegalStateException
        return RemoteShootingActionState.Error(
            action = action,
            message = message,
            canRetry = canRetry,
            canReset = canReset,
        )
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
        val actionState: RemoteShootingActionState,
    ) : RemoteShootingUiState
}

sealed interface RemoteShootingActionState {
    data object Idle : RemoteShootingActionState

    data class InProgress(val action: RemoteShootingAction) : RemoteShootingActionState

    data class Error(
        val action: RemoteShootingAction,
        val message: String,
        val canRetry: Boolean,
        val canReset: Boolean,
    ) : RemoteShootingActionState
}

sealed interface RemoteShootingAction {
    data object TriggerCapture : RemoteShootingAction

    data object DisconnectWifi : RemoteShootingAction

    data object ResetRemoteShooting : RemoteShootingAction
}
