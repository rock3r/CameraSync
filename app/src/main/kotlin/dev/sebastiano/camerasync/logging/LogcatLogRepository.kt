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

/**
 * Parses logcat -v threadtime output into [LogEntry] list. Internal for unit testing; used by
 * [LogcatLogRepository].
 */
internal object LogcatLogParser {

    // Regex for logcat -v threadtime:
    // 01-24 16:35:45.321  1234  5678 I Tag: message
    private val logRegex =
        Regex(
            """^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s([VDIWEA])\s+(.*?):\s?(.*)$"""
        )

    // logcat -d outputs buffer separator lines that don't match logRegex (e.g. "--------- beginning
    // of main")
    private const val BUFFER_SEPARATOR_PREFIX = "--------- beginning of"

    fun parseLines(lines: Sequence<String>): List<LogEntry> {
        val newLogs = mutableListOf<LogEntry>()
        for (line in lines) {
            val match = logRegex.matchEntire(line)
            if (match != null) {
                val g = match.groupValues
                newLogs.add(
                    LogEntry(
                        timestamp = g[1],
                        level = LogLevel.fromLogcat(g[4]),
                        tag = g[5].trim(),
                        message = g[6],
                        pid = g[2].toIntOrNull(),
                        tid = g[3].toIntOrNull(),
                    )
                )
            } else if (!line.startsWith(BUFFER_SEPARATOR_PREFIX)) {
                // Continuation line (e.g. stack trace): append to previous entry.
                if (newLogs.isNotEmpty()) {
                    val last = newLogs.removeAt(newLogs.size - 1)
                    newLogs.add(last.copy(message = last.message + "\n" + line))
                }
            }
            // else: buffer separator line, skip
        }
        return newLogs
    }
}

/** Implementation of [LogRepository] that reads from Android's logcat. */
class LogcatLogRepository(context: Context) : LogRepository {

    private val packageName: String = context.applicationInfo.packageName

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    override fun getLogs(): Flow<List<LogEntry>> = _logs.asStateFlow()

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                _logs.value = reader.useLines { LogcatLogParser.parseLines(it) }
            } catch (e: IOException) {
                Log.warn("LogcatLogRepository", throwable = e) {
                    "Failed to refresh logs for $packageName"
                }
            }
        }
    }
}
