package dev.sebastiano.camerasync.vendors.ricoh

import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.StorageInfo
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalUuidApi::class)
class RicohRemoteControlDelegate(
    private val peripheral: Peripheral,
    @Suppress("UNUSED_PARAMETER") private val camera: Camera,
) : RemoteControlDelegate {

    private val _connectionMode = MutableStateFlow(ShootingConnectionMode.BLE_ONLY)
    override val connectionMode: StateFlow<ShootingConnectionMode> = _connectionMode.asStateFlow()

    private val operationRequestCharacteristic
        get() =
            characteristicOf(
                service = RicohGattSpec.Shooting.SERVICE_UUID,
                characteristic = RicohGattSpec.Shooting.OPERATION_REQUEST_CHARACTERISTIC_UUID,
            )

    override suspend fun triggerCapture() {
        // Start with AF (dm-zharov Operation Request: 1=Start, 1=AF)
        peripheral.write(
            operationRequestCharacteristic,
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_START,
                RicohProtocol.OP_REQ_PARAM_AF,
            ),
            WriteType.WithResponse,
        )
        delay(CAPTURE_DELAY_MS)
        // Stop (2=Stop, 0=No AF)
        peripheral.write(
            operationRequestCharacteristic,
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_STOP,
                RicohProtocol.OP_REQ_PARAM_NO_AF,
            ),
            WriteType.WithResponse,
        )
    }

    override suspend fun startBulbExposure() {
        // Use NO_AF: bulb mode is for long exposures in dark conditions where the user has
        // typically pre-focused manually. AF would hunt and fail, potentially blocking the start.
        peripheral.write(
            operationRequestCharacteristic,
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_START,
                RicohProtocol.OP_REQ_PARAM_NO_AF,
            ),
            WriteType.WithResponse,
        )
    }

    override suspend fun stopBulbExposure() {
        peripheral.write(
            operationRequestCharacteristic,
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_STOP,
                RicohProtocol.OP_REQ_PARAM_NO_AF,
            ),
            WriteType.WithResponse,
        )
    }

    override fun observeBatteryLevel(): Flow<BatteryInfo> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraState.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraState.BATTERY_LEVEL_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { RicohProtocol.decodeBatteryInfo(it) }
    }

    override fun observeStorageStatus(): Flow<StorageInfo> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraState.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraState.STORAGE_INFO_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { data ->
            RicohProtocol.decodeStorageInfo(data)
        }
    }

    override fun observeCameraMode(): Flow<CameraMode> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.Shooting.SERVICE_UUID,
                characteristic = RicohGattSpec.Shooting.CAPTURE_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { data ->
            // Capture mode byte: 0=Still, 2=Movie.
            if (data.isEmpty()) return@map CameraMode.UNKNOWN
            when (data[0].toInt() and 0xFF) {
                0 -> CameraMode.STILL_IMAGE
                2 -> CameraMode.MOVIE
                else -> CameraMode.UNKNOWN
            }
        }
    }

    override fun observeCaptureStatus(): Flow<CaptureStatus> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.Shooting.SERVICE_UUID,
                characteristic = RicohGattSpec.Shooting.CAPTURE_STATUS_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { RicohProtocol.decodeCaptureStatus(it) }
    }

    override fun observeExposureMode(): Flow<ExposureMode> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.Shooting.SERVICE_UUID,
                characteristic = RicohGattSpec.Shooting.SHOOTING_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map {
            RicohProtocol.decodeShootingMode(it).second
        }
    }

    override fun observeDriveMode(): Flow<DriveMode> {
        // Drive mode is on B29E6DE3 in the Shooting service.
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.Shooting.SERVICE_UUID,
                characteristic = RicohGattSpec.Shooting.DRIVE_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral
            .observe(characteristic)
            .map { RicohProtocol.decodeDriveMode(it) }
            .distinctUntilChanged()
    }

    override suspend fun connectWifi() {
        // Wi-Fi connection (WLAN-on write, read credentials, Android Wi-Fi connect) is not yet
        // implemented. Do not set mode to FULL here: that would make Wi-Fi-dependent UI appear and
        // Wi-Fi-only features silently fail.
        throw UnsupportedOperationException("Wi-Fi connection is not yet implemented.")
    }

    override suspend fun disconnectWifi() {
        // Disconnect Wifi
        _connectionMode.value = ShootingConnectionMode.BLE_ONLY
    }

    companion object {
        /**
         * Delay between shutter press and release for normal captures.
         *
         * This ensures the camera has time to process the capture before releasing the shutter. For
         * bulb mode, the shutter is held down until [stopBulbExposure] is called.
         */
        private const val CAPTURE_DELAY_MS = 200L

        // No drive-mode echo filtering needed: we only observe the drive-mode characteristic.
    }
}
