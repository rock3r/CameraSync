package dev.sebastiano.camerasync.logging

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Implementation of [LogRepository] that reads from Android's logcat. */
class LogcatLogRepository(private val context: Context) : LogRepository {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    override fun getLogs(): Flow<List<LogEntry>> = _logs.asStateFlow()

    // Regex for logcat -v threadtime:
    // 01-24 16:35:45.321  1234  5678 I Tag: message
    private val logRegex =
        Regex(
            """^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s([VDIWEA])\s+(.*?):\s?(.*)$"""
        )

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                // Using -v threadtime for consistent parsing
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val newLogs = mutableListOf<LogEntry>()

                reader.forEachLine { line ->
                    val match = logRegex.matchEntire(line)
                    if (match != null) {
                        val (timestamp, pid, tid, level, tag, message) = match.destructured
                        newLogs.add(
                            LogEntry(
                                timestamp = timestamp,
                                level = LogLevel.fromLogcat(level),
                                tag = tag.trim(),
                                message = message,
                                pid = pid.toIntOrNull(),
                                tid = tid.toIntOrNull(),
                            )
                        )
                    }
                }

                // We want newest logs first in the viewer
                _logs.value = newLogs.reversed()
            } catch (e: Exception) {
                // Log the error using Khronicle if possible, but avoid infinite loops
                // For now, just return empty list or keep old logs
            }
        }
    }
}
