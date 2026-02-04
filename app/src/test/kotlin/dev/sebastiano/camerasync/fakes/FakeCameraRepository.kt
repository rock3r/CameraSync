package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class FakeCameraRepository : CameraRepository {

    private val _discoveredCameras = MutableSharedFlow<Camera>()
    override val discoveredCameras: Flow<Camera> = _discoveredCameras

    var startScanningCalled = false
        private set

    var stopScanningCalled = false
        private set

    var connectDelay = 0L
    var connectException: Exception? = null
    var connectionToReturn: FakeCameraConnection? = null
    /**
     * When true, throws exception if connectionToReturn is null (default: false for backward
     * compatibility)
     */
    var failIfConnectionNull = false
    var cameraToReturn: Camera? = null
    var connectCallCount = 0
        private set

    /**
     * Callback invoked after a successful connect() call. Useful for simulating OS-level bonding
     * after BLE connection.
     */
    var onConnectSuccess: ((Camera) -> Unit)? = null

    override fun startScanning() {
        startScanningCalled = true
    }

    override fun stopScanning() {
        stopScanningCalled = true
    }

    var startPassiveScanCalled = false
        private set

    var stopPassiveScanCalled = false
        private set

    override fun startPassiveScan(pendingIntent: android.app.PendingIntent) {
        startPassiveScanCalled = true
    }

    override fun stopPassiveScan(pendingIntent: android.app.PendingIntent) {
        stopPassiveScanCalled = true
    }

    override fun findCameraByMacAddress(macAddress: String): Flow<Camera> = flow {
        cameraToReturn?.let { emit(it) }
    }

    override suspend fun connect(camera: Camera, onFound: (() -> Unit)?): CameraConnection {
        connectCallCount++
        if (connectDelay > 0) delay(connectDelay)
        onFound?.invoke()
        connectException?.let { throw it }
        if (
            failIfConnectionNull &&
                connectionToReturn == null &&
                !connectionsByMac.containsKey(camera.macAddress)
        ) {
            error("Connection not available (connectionToReturn is null)")
        }
        val connection =
            connectionsByMac[camera.macAddress]
                ?: connectionToReturn
                ?: FakeCameraConnection(camera)
        onConnectSuccess?.invoke(camera)
        return connection
    }

    private val connectionsByMac = mutableMapOf<String, FakeCameraConnection>()

    fun setConnectionForMac(macAddress: String, connection: FakeCameraConnection) {
        connectionsByMac[macAddress] = connection
    }

    suspend fun emitDiscoveredCamera(camera: Camera) {
        _discoveredCameras.emit(camera)
    }
}
