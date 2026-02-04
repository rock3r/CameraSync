package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job

/**
 * Manages active camera connections and their associated coroutine jobs.
 *
 * This manager provides a centralized way to track which devices are currently connected and to
 * ensure that their synchronization jobs can be properly cancelled when needed. All operations are
 * thread-safe.
 */
@Inject
class DeviceConnectionManager {
    private val deviceJobs = mutableMapOf<String, Job>()
    private val deviceConnections = mutableMapOf<String, CameraConnection>()

    /**
     * Adds a new active connection for a device.
     *
     * @param macAddress The MAC address of the device.
     * @param connection The [CameraConnection] instance.
     * @param job The coroutine [Job] managing the connection lifecycle.
     */
    fun addConnection(macAddress: String, connection: CameraConnection, job: Job) {
        synchronized(this) {
            deviceJobs[macAddress] = job
            deviceConnections[macAddress] = connection
        }
    }

    /**
     * Removes the connection and returns the associated [CameraConnection] and [Job] if present.
     *
     * This is typically used when a device is disconnected to ensure the connection is cleaned up
     * and the job can be cancelled.
     *
     * @param macAddress The MAC address of the device.
     * @return A [Pair] containing the [CameraConnection] and [Job], if they existed.
     */
    fun removeConnection(macAddress: String): Pair<CameraConnection?, Job?> {
        return synchronized(this) {
            val connection = deviceConnections.remove(macAddress)
            val job = deviceJobs.remove(macAddress)
            connection to job
        }
    }

    /**
     * Returns a map of all currently active connections.
     *
     * @return A map where keys are MAC addresses and values are [CameraConnection]s.
     */
    fun getConnections(): Map<String, CameraConnection> =
        synchronized(this) { deviceConnections.toMap() }

    /**
     * Returns the active connection for a specific device, if it exists.
     *
     * @param macAddress The MAC address of the device.
     * @return The [CameraConnection], or null if not connected.
     */
    fun getConnection(macAddress: String): CameraConnection? =
        synchronized(this) { deviceConnections[macAddress] }
}
