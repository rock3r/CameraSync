package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Fake implementation of [PairedDevicesRepository] for testing. */
class FakePairedDevicesRepository : PairedDevicesRepository {

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())

    override val pairedDevices: Flow<List<PairedDevice>> = _devices

    override val enabledDevices: Flow<List<PairedDevice>> =
        _devices.map { devices -> devices.filter { it.isEnabled } }

    private val _isSyncEnabled = MutableStateFlow(true)

    override val isSyncEnabled: Flow<Boolean> = _isSyncEnabled

    override suspend fun setSyncEnabled(enabled: Boolean) {
        _isSyncEnabled.value = enabled
    }

    // Tracking for test verification
    var addDeviceCalled = false
        private set

    var removeDeviceCalled = false
        private set

    var setDeviceEnabledCalled = false
        private set

    var lastAddedCamera: Camera? = null
        private set

    var lastRemovedMacAddress: String? = null
        private set

    // Test control variables
    var addDeviceException: Exception? = null
    var addDeviceDelay: Long = 0L

    override suspend fun addDevice(camera: Camera, enabled: Boolean) {
        if (addDeviceDelay > 0) {
            delay(addDeviceDelay)
        }

        addDeviceException?.let { throw it }

        addDeviceCalled = true
        lastAddedCamera = camera

        val newDevice =
            PairedDevice(
                macAddress = camera.macAddress,
                name = camera.name,
                vendorId = camera.vendor.vendorId,
                isEnabled = enabled,
            )

        _devices.update { devices ->
            val existingIndex = devices.indexOfFirst { it.macAddress == camera.macAddress }
            if (existingIndex >= 0) {
                devices.toMutableList().apply { set(existingIndex, newDevice) }
            } else {
                devices + newDevice
            }
        }
    }

    override suspend fun removeDevice(macAddress: String) {
        removeDeviceCalled = true
        lastRemovedMacAddress = macAddress

        _devices.update { devices -> devices.filter { it.macAddress != macAddress } }
    }

    override suspend fun setDeviceEnabled(macAddress: String, enabled: Boolean) {
        setDeviceEnabledCalled = true

        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(isEnabled = enabled)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun updateDeviceName(macAddress: String, name: String?) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(name = name)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun updateLastSyncedAt(macAddress: String, timestamp: Long) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(lastSyncedAt = timestamp)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun isDevicePaired(macAddress: String): Boolean =
        _devices.value.any { it.macAddress == macAddress }

    override suspend fun getDevice(macAddress: String): PairedDevice? =
        _devices.value.find { it.macAddress == macAddress }

    override suspend fun hasAnyDevices(): Boolean = _devices.value.isNotEmpty()

    override suspend fun hasEnabledDevices(): Boolean = _devices.value.any { it.isEnabled }

    override suspend fun updateFirmwareVersion(macAddress: String, firmwareVersion: String?) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(firmwareVersion = firmwareVersion)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun setFirmwareUpdateInfo(
        macAddress: String,
        latestVersion: String?,
        lastCheckedAt: Long,
    ) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(
                        latestFirmwareVersion = latestVersion,
                        lastFirmwareCheckedAt = lastCheckedAt,
                        firmwareUpdateNotificationShown =
                            false, // Clear flag when update info changes
                    )
                } else {
                    device
                }
            }
        }
    }

    override suspend fun setFirmwareUpdateNotificationShown(macAddress: String) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(firmwareUpdateNotificationShown = true)
                } else {
                    device
                }
            }
        }
    }

    // Test helpers

    /** Adds a device directly for test setup. */
    fun addTestDevice(device: PairedDevice) {
        _devices.update { it + device }
    }

    /** Sets devices directly for test setup. */
    fun setPairedDevices(devices: List<PairedDevice>) {
        _devices.value = devices
    }

    /** Sets devices directly for test setup (alias for compatibility). */
    fun setTestDevices(devices: List<PairedDevice>) {
        setPairedDevices(devices)
    }

    /** Clears all devices. */
    fun clear() {
        _devices.value = emptyList()
    }

    /** Resets all tracking flags. */
    fun resetTracking() {
        addDeviceCalled = false
        removeDeviceCalled = false
        setDeviceEnabledCalled = false
        lastAddedCamera = null
        lastRemovedMacAddress = null
        addDeviceException = null
        addDeviceDelay = 0L
    }
}
