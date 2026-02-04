package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job

private const val TAG = "DeviceConnectionManager"

@Inject
class DeviceConnectionManager {
    private val deviceJobs = mutableMapOf<String, Job>()
    private val deviceConnections = mutableMapOf<String, CameraConnection>()

    fun addConnection(macAddress: String, connection: CameraConnection, job: Job) {
        synchronized(this) {
            deviceJobs[macAddress] = job
            deviceConnections[macAddress] = connection
        }
    }

    /** Removes the connection and returns the associated Job if present, so it can be cancelled. */
    fun removeConnection(macAddress: String): Pair<CameraConnection?, Job?> {
        return synchronized(this) {
            val connection = deviceConnections.remove(macAddress)
            val job = deviceJobs.remove(macAddress)
            connection to job
        }
    }

    fun getConnections(): Map<String, CameraConnection> =
        synchronized(this) { deviceConnections.toMap() }

    fun getConnection(macAddress: String): CameraConnection? =
        synchronized(this) { deviceConnections[macAddress] }
}
