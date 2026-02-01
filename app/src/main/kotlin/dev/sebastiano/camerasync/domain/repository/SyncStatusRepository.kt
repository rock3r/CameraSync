package dev.sebastiano.camerasync.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for tracking the runtime status of the synchronization process.
 * Unlike [PairedDevicesRepository], this repository holds transient state that
 * is only relevant while the app or service is running.
 */
interface SyncStatusRepository {

    /** The number of devices currently connected and syncing. */
    val connectedDevicesCount: StateFlow<Int>

    /**
     * Updates the count of connected devices.
     *
     * @param count The new count of connected devices.
     */
    fun updateConnectedDevicesCount(count: Int)
}
