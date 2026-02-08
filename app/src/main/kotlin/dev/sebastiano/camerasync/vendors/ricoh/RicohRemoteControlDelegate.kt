package dev.sebastiano.camerasync.vendors.ricoh

import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.StorageInfo
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalUuidApi::class)
class RicohRemoteControlDelegate(
    private val peripheral: com.juul.kable.Peripheral,
    @Suppress("UNUSED_PARAMETER") private val camera: dev.sebastiano.camerasync.domain.model.Camera,
) : RemoteControlDelegate {

    private val _connectionMode = MutableStateFlow(ShootingConnectionMode.BLE_ONLY)
    override val connectionMode: StateFlow<ShootingConnectionMode> = _connectionMode.asStateFlow()

    override suspend fun triggerCapture() {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraControl.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraControl.COMMAND_CHARACTERISTIC_UUID,
            )
        // Command to trigger shutter (using placeholder byte 0x01)
        val command = byteArrayOf(0x01)
        peripheral.write(characteristic, command, WriteType.WithResponse)
    }

    override suspend fun startBulbExposure() {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraControl.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraControl.COMMAND_CHARACTERISTIC_UUID,
            )
        // Bulb/Time: first write starts exposure (same command as shutter trigger).
        val command = byteArrayOf(0x01)
        peripheral.write(characteristic, command, WriteType.WithResponse)
    }

    override suspend fun stopBulbExposure() {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraControl.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraControl.COMMAND_CHARACTERISTIC_UUID,
            )
        // Bulb/Time: second write stops exposure.
        val command = byteArrayOf(0x01)
        peripheral.write(characteristic, command, WriteType.WithResponse)
    }

    override fun observeBatteryLevel(): Flow<BatteryInfo> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraControl.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraControl.BATTERY_INFO_CHARACTERISTIC_UUID,
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
                service = RicohGattSpec.CameraState.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraState.OPERATION_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { data ->
            // Operation mode byte: 0=Still, 1=Movie (aligned with
            // RicohProtocol.decodeShootingMode).
            // Empty or unknown values mean status not yet available or unsupported.
            if (data.isEmpty()) return@map CameraMode.UNKNOWN
            when (data[0].toInt() and 0xFF) {
                0 -> CameraMode.STILL_IMAGE
                1 -> CameraMode.MOVIE
                else -> CameraMode.UNKNOWN
            }
        }
    }

    override fun observeCaptureStatus(): Flow<CaptureStatus> =
        // tPa (Capture Status) is part of Camera State Notification; exact characteristic UUID
        // not yet identified in our GATT spec. RicohProtocol.decodeCaptureStatus is ready when we
        // have it.
        emptyFlow()

    override fun observeExposureMode(): Flow<ExposureMode> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraState.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraState.EXPOSURE_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { RicohProtocol.decodeExposureMode(it) }
    }

    override fun observeDriveMode(): Flow<DriveMode> {
        val characteristic =
            characteristicOf(
                service = RicohGattSpec.CameraState.SERVICE_UUID,
                characteristic = RicohGattSpec.CameraState.CAPTURE_MODE_CHARACTERISTIC_UUID,
            )
        return peripheral.observe(characteristic).map { RicohProtocol.decodeDriveMode(it) }
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
}
