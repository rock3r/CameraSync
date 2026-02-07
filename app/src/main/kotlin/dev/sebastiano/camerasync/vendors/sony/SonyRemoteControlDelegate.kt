package dev.sebastiano.camerasync.vendors.sony

import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.CustomButton
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.LiveViewFrame
import dev.sebastiano.camerasync.domain.model.RecordingStatus
import dev.sebastiano.camerasync.domain.model.ShutterStatus
import dev.sebastiano.camerasync.domain.model.StorageInfo
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalUuidApi::class)
class SonyRemoteControlDelegate(
    private val peripheral: com.juul.kable.Peripheral,
    @Suppress("UNUSED_PARAMETER") private val camera: dev.sebastiano.camerasync.domain.model.Camera,
    /**
     * Optional scope for the BLE trigger sequence (shareIn + withTimeoutOrNull). When provided
     * (e.g. test scope), timeouts use that scope's scheduler for deterministic tests.
     */
    private val captureScope: CoroutineScope? = null,
) : RemoteControlDelegate {

    private val _connectionMode = MutableStateFlow(ShootingConnectionMode.BLE_ONLY)
    override val connectionMode: StateFlow<ShootingConnectionMode> = _connectionMode.asStateFlow()

    override suspend fun triggerCapture() {
        if (_connectionMode.value == ShootingConnectionMode.FULL) {
            throw UnsupportedOperationException(
                "Remote capture in Wi-Fi mode is not yet implemented. Use BLE mode for shutter control."
            )
        } else {
            // BLE: Event-driven sequence per BLE_STATE_MONITORING.md §2.5
            // 1. Half Down → wait for FF02 focus acquired (or timeout) → 2. Full Down →
            //    wait for FF02 shutter active → 3. Full Up → 4. Half Up
            val commandChar =
                characteristicOf(
                    service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                    characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
                )
            val notifyChar =
                characteristicOf(
                    service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                    characteristic = SonyGattSpec.REMOTE_STATUS_CHARACTERISTIC_UUID,
                )

            if (captureScope != null) {
                withContext(captureScope.coroutineContext) {
                    coroutineScope { this.runTriggerSequence(commandChar, notifyChar, this) }
                }
            } else {
                coroutineScope { this.runTriggerSequence(commandChar, notifyChar, this) }
            }
        }
    }

    private suspend fun CoroutineScope.runTriggerSequence(
        commandChar: com.juul.kable.Characteristic,
        notifyChar: com.juul.kable.Characteristic,
        shareScope: CoroutineScope,
    ) {
        val ff02Parsed =
            peripheral
                .observe(notifyChar)
                .mapNotNull { SonyProtocol.parseFf02Notification(it) }
                .shareIn(shareScope, SharingStarted.Eagerly, replay = 1)

        // 1. Shutter Half Down (acquire focus)
        peripheral.write(
            commandChar,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_PRESS),
            WriteType.WithoutResponse,
        )
        // 2. Wait for focus acquired (3s timeout; proceed anyway if MF or no response)
        withTimeoutOrNull(FOCUS_ACQUIRED_TIMEOUT_MS) {
            ff02Parsed
                .filter {
                    it is SonyProtocol.Ff02Notification.Focus && it.status == FocusStatus.LOCKED
                }
                .first()
        }
        // 3. Shutter Full Down (take picture)
        peripheral.write(
            commandChar,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_PRESS),
            WriteType.WithoutResponse,
        )
        // 4. Wait for shutter active (picture taken)
        withTimeoutOrNull(SHUTTER_ACTIVE_TIMEOUT_MS) {
            ff02Parsed
                .filter {
                    it is SonyProtocol.Ff02Notification.Shutter && it.status == ShutterStatus.ACTIVE
                }
                .first()
        }
        // 5. Full Up, 6. Half Up (matched pairs per doc)
        peripheral.write(
            commandChar,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_RELEASE),
            WriteType.WithoutResponse,
        )
        peripheral.write(
            commandChar,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_RELEASE),
            WriteType.WithoutResponse,
        )
    }

    companion object {
        /**
         * Timeout waiting for FF02 focus-acquired; proceed with shutter anyway if exceeded (e.g.
         * MF).
         */
        private const val FOCUS_ACQUIRED_TIMEOUT_MS = 3_000L
        /** Timeout waiting for FF02 shutter-active after full press. */
        private const val SHUTTER_ACTIVE_TIMEOUT_MS = 5_000L
    }

    override suspend fun startBulbExposure() {
        if (_connectionMode.value != ShootingConnectionMode.BLE_ONLY) {
            throw UnsupportedOperationException(
                "Bulb exposure in Wi-Fi mode is not yet implemented."
            )
        }
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        // BLE: Hold shutter full down (exposure runs until release)
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_PRESS),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun stopBulbExposure() {
        if (_connectionMode.value != ShootingConnectionMode.BLE_ONLY) {
            throw UnsupportedOperationException(
                "Bulb exposure in Wi-Fi mode is not yet implemented."
            )
        }
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_RELEASE),
            WriteType.WithoutResponse,
        )
    }

    override fun observeBatteryLevel(): Flow<BatteryInfo> {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.BATTERY_INFO_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { SonyProtocol.decodeBatteryInfo(it) }
    }

    override fun observeStorageStatus(): Flow<StorageInfo> {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.STORAGE_INFO_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { SonyProtocol.decodeStorageInfo(it) }
    }

    override fun observeCameraMode(): Flow<CameraMode> {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.TIME_COMPLETION_STATUS_CHARACTERISTIC_UUID, // CC09
            )
        return peripheral.observe(characteristic).map { SonyProtocol.decodeCameraStatus(it) }
    }

    override fun observeCaptureStatus(): Flow<CaptureStatus> {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_STATUS_CHARACTERISTIC_UUID,
            )
        return peripheral
            .observe(characteristic)
            .mapNotNull { SonyProtocol.parseFf02Notification(it) }
            .mapNotNull { notif ->
                when (notif) {
                    is SonyProtocol.Ff02Notification.Shutter ->
                        when (notif.status) {
                            ShutterStatus.READY -> CaptureStatus.Idle
                            ShutterStatus.ACTIVE -> CaptureStatus.Capturing
                            else -> null
                        }
                    else -> null
                }
            }
    }

    override fun observeExposureMode(): Flow<ExposureMode> {
        // BLE: Not available; Wi-Fi PTP/IP later
        if (_connectionMode.value == ShootingConnectionMode.BLE_ONLY) return emptyFlow()
        return emptyFlow()
    }

    override fun observeDriveMode(): Flow<DriveMode> {
        // BLE: Not available; Wi-Fi PTP/IP later
        if (_connectionMode.value == ShootingConnectionMode.BLE_ONLY) return emptyFlow()
        return emptyFlow()
    }

    override suspend fun connectWifi() {
        // Wi-Fi connection (CC08, credentials, Android Wifi, PTP/IP) is not yet implemented.
        // Do not set mode to FULL here: that would make triggerCapture/startBulbExposure/
        // stopBulbExposure no-ops with no user feedback.
        throw UnsupportedOperationException("Wi-Fi connection is not yet implemented.")
    }

    override suspend fun disconnectWifi() {
        // Teardown PTP/IP
        // Disconnect Wifi
        _connectionMode.value = ShootingConnectionMode.BLE_ONLY
    }

    // --- Sony Specific Features ---

    override suspend fun halfPressAF() {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_PRESS),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun releaseAF() {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_RELEASE),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun focusNear(speed: Int) {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_FOCUS_NEAR, speed),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun focusFar(speed: Int) {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_FOCUS_FAR, speed),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun zoomIn(speed: Int) {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_ZOOM_TELE, speed),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun zoomOut(speed: Int) {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_ZOOM_WIDE, speed),
            WriteType.WithoutResponse,
        )
    }

    override suspend fun pressCustomButton(button: CustomButton) {
        // BLE: Write FF01 custom code
        // Need custom button codes (AF-ON, C1)
    }

    override suspend fun releaseCustomButton(button: CustomButton) {
        // BLE: Write FF01 release code
    }

    override fun observeFocusStatus(): Flow<FocusStatus>? {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_STATUS_CHARACTERISTIC_UUID,
            )
        return peripheral
            .observe(characteristic)
            .mapNotNull { SonyProtocol.parseFf02Notification(it) }
            .mapNotNull { notif -> (notif as? SonyProtocol.Ff02Notification.Focus)?.status }
    }

    override fun observeShutterStatus(): Flow<ShutterStatus>? {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_STATUS_CHARACTERISTIC_UUID,
            )
        return peripheral
            .observe(characteristic)
            .mapNotNull { SonyProtocol.parseFf02Notification(it) }
            .mapNotNull { notif -> (notif as? SonyProtocol.Ff02Notification.Shutter)?.status }
    }

    override suspend fun touchAF(x: Float, y: Float) {
        if (_connectionMode.value != ShootingConnectionMode.FULL) return
        // PTP/IP RemoteTouchOperation
    }

    override suspend fun toggleAELock() {
        if (_connectionMode.value != ShootingConnectionMode.FULL) return
        // PTP/IP AELButton
    }

    override fun observeLiveView(): Flow<LiveViewFrame>? {
        if (_connectionMode.value != ShootingConnectionMode.FULL) return null
        return emptyFlow() // PTP/IP LiveView stream
    }

    override suspend fun toggleVideoRecording() {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_COMMAND_CHARACTERISTIC_UUID,
            )
        peripheral.write(
            characteristic,
            SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_VIDEO_REC),
            WriteType.WithoutResponse,
        )
    }

    override fun observeRecordingStatus(): Flow<RecordingStatus>? {
        val characteristic =
            characteristicOf(
                service = SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                characteristic = SonyGattSpec.REMOTE_STATUS_CHARACTERISTIC_UUID,
            )
        return peripheral
            .observe(characteristic)
            .mapNotNull { SonyProtocol.parseFf02Notification(it) }
            .mapNotNull { notif -> (notif as? SonyProtocol.Ff02Notification.Recording)?.status }
    }
}
