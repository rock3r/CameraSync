package dev.sebastiano.camerasync.logging

import kotlinx.coroutines.flow.Flow

/** Repository for accessing system and app logs. */
interface LogRepository {
    /** Returns a flow of log entries. */
    fun getLogs(): Flow<List<LogEntry>>

    /** Refreshes the logs from the system. */
    suspend fun refresh()
}
