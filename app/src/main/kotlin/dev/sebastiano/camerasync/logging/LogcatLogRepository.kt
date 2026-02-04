package dev.sebastiano.camerasync.logging

import android.content.Context
import com.juul.khronicle.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Implementation of [LogRepository] that reads from Android's logcat. */
class LogcatLogRepository(context: Context) : LogRepository {

    private val packageName: String = context.applicationInfo.packageName

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
                        val g = match.groupValues
                        val timestamp = g[1]
                        val pid = g[2]
                        val tid = g[3]
                        val level = g[4]
                        val tag = g[5]
                        val message = g[6]
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
            } catch (e: IOException) {
                Log.warn("LogcatLogRepository", throwable = e) {
                    "Failed to refresh logs for $packageName"
                }
            }
        }
    }
}
