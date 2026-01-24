package dev.sebastiano.camerasync.logging

/** Level of a log entry. */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT,
    UNKNOWN;

    companion object {
        /** Parses a log level from a logcat level string. */
        fun fromLogcat(level: String): LogLevel =
            when (level.trim().uppercase()) {
                "V" -> VERBOSE
                "D" -> DEBUG
                "I" -> INFO
                "W" -> WARN
                "E" -> ERROR
                "A" -> ASSERT
                else -> UNKNOWN
            }
    }
}

/** A single log entry. */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val pid: Int? = null,
    val tid: Int? = null
)
