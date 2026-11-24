package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCameraConnection(override val camera: Camera) : CameraConnection {

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: Flow<Boolean> = _isConnected

    var firmwareVersion = "1.0.0"
    var pairedDeviceName: String? = null
        private set

    var syncedDateTime: ZonedDateTime? = null
        private set

    var geoTaggingEnabled = false
        private set

    var hardwareRevision = "1.0.0"
        private set

    var lastSyncedLocation: GpsLocation? = null
        private set

    var disconnectCalled = false
        private set

    var readDateTimeCalled = false
        private set

    var readFirmwareVersionCalled = false
        private set

    var readHardwareRevisionCalled = false
        private set

    var modelName = "Test Model"
        private set

    var readModelNameCalled = false
        private set

    /** When set, readModelName() throws this instead of returning. */
    var readModelNameException: Throwable? = null
    var readModelNameDelay = 0L

    /** When true, disconnect() throws if called from a cancelled coroutine. */
    var disconnectRequiresActive = false

    var initializePairingCalled = false
        private set

    var initializePairingResult = true

    var throwOnFirmwareRead = false

    override suspend fun initializePairing(): Boolean {
        initializePairingCalled = true
        return initializePairingResult
    }

    override suspend fun readFirmwareVersion(): String {
        readFirmwareVersionCalled = true
        if (throwOnFirmwareRead) {
            throw IOException("Simulated firmware read failure")
        }
        return firmwareVersion
    }

    override suspend fun readHardwareRevision(): String {
        readHardwareRevisionCalled = true
        return hardwareRevision
    }

    override suspend fun readModelName(): String {
        readModelNameCalled = true
        if (readModelNameDelay > 0) {
            delay(readModelNameDelay)
        }
        readModelNameException?.let { e -> throw e }
        return modelName
    }

    override suspend fun setPairedDeviceName(name: String) {
        pairedDeviceName = name
    }

    override suspend fun syncDateTime(dateTime: ZonedDateTime) {
        syncedDateTime = dateTime
    }

    override suspend fun readDateTime(): ByteArray {
        readDateTimeCalled = true
        return byteArrayOf()
    }

    override suspend fun setGeoTaggingEnabled(enabled: Boolean) {
        geoTaggingEnabled = enabled
    }

    override suspend fun isGeoTaggingEnabled(): Boolean = geoTaggingEnabled

    override suspend fun syncLocation(location: GpsLocation) {
        lastSyncedLocation = location
    }

    override suspend fun disconnect() {
        if (disconnectRequiresActive) {
            currentCoroutineContext().ensureActive()
        }
        disconnectCalled = true
        _isConnected.value = false
    }

    fun setModelName(name: String) {
        modelName = name
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }
}
