package dev.sebastiano.camerasync.data.repository

import dev.sebastiano.camerasync.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory implementation of [SyncStatusRepository].
 */
class InMemorySyncStatusRepository : SyncStatusRepository {

    private val _connectedDevicesCount = MutableStateFlow(0)
    override val connectedDevicesCount: StateFlow<Int> = _connectedDevicesCount.asStateFlow()

    override fun updateConnectedDevicesCount(count: Int) {
        _connectedDevicesCount.value = count
    }
}
