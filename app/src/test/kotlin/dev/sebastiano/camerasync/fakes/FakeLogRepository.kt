package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.logging.LogEntry
import dev.sebastiano.camerasync.logging.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake implementation of [LogRepository] for testing. */
class FakeLogRepository : LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override fun getLogs(): Flow<List<LogEntry>> = _logs.asStateFlow()

    var refreshCalled = false

    fun setLogs(logs: List<LogEntry>) {
        _logs.value = logs
    }

    override suspend fun refresh() {
        refreshCalled = true
    }
}
