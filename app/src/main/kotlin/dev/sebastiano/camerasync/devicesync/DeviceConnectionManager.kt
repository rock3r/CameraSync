package dev.sebastiano.camerasync.devicesync

import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.zacsweers.metro.Inject
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

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

    fun removeConnection(macAddress: String): CameraConnection? {
        return synchronized(this) {
            deviceJobs.remove(macAddress)
            deviceConnections.remove(macAddress)
        }
    }

    fun getJob(macAddress: String): Job? = synchronized(this) { deviceJobs[macAddress] }

    fun addJob(macAddress: String, job: Job) {
        synchronized(this) { deviceJobs[macAddress] = job }
    }

    fun removeJob(macAddress: String): Job? = synchronized(this) { deviceJobs.remove(macAddress) }

    fun getConnections(): Map<String, CameraConnection> =
        synchronized(this) { deviceConnections.toMap() }

    fun getConnection(macAddress: String): CameraConnection? =
        synchronized(this) { deviceConnections[macAddress] }

    suspend fun stopAll() {
        val jobs =
            synchronized(this) {
                val allJobs = deviceJobs.values.toList()
                deviceJobs.clear()
                allJobs
            }
        jobs.forEach { it.cancel() }
        jobs.joinAll()

        val connections =
            synchronized(this) {
                val allConnections = deviceConnections.values.toList()
                deviceConnections.clear()
                allConnections
            }
        connections.forEach {
            try {
                it.disconnect()
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting during stopAll" }
            } catch (e: IllegalStateException) {
                Log.warn(tag = TAG, throwable = e) { "Error disconnecting during stopAll" }
            }
        }
    }

    fun isSyncing(macAddress: String): Boolean =
        synchronized(this) { deviceJobs.containsKey(macAddress) }
}
