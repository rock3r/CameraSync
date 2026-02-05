package dev.sebastiano.camerasync.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Unit tests for [LogcatLogParser] (logcat -v threadtime parsing). */
class LogcatLogParserTest {

    private fun parse(vararg lines: String): List<LogEntry> =
        LogcatLogParser.parseLines(lines.asSequence())

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList<LogEntry>(), parse())
    }

    @Test
    fun `single valid log line is parsed`() {
        val lines = arrayOf("01-24 16:35:45.321  1234  5678 I MyTag: Hello world")
        val result = parse(*lines)
        assertEquals(1, result.size)
        assertEquals("01-24 16:35:45.321", result[0].timestamp)
        assertEquals(LogLevel.INFO, result[0].level)
        assertEquals("MyTag", result[0].tag)
        assertEquals("Hello world", result[0].message)
        assertEquals(1234, result[0].pid)
        assertEquals(5678, result[0].tid)
    }

    @Test
    fun `buffer separator lines are skipped and not appended to previous entry`() {
        val lines =
            arrayOf(
                "01-24 16:35:45.321  1234  5678 I Tag: actual message",
                "--------- beginning of system",
            )
        val result = parse(*lines)
        assertEquals(1, result.size)
        assertEquals("actual message", result[0].message)
        assertFalse(result[0].message.contains("---------"))
    }

    @Test
    fun `buffer separator beginning of main is skipped`() {
        val lines =
            arrayOf("--------- beginning of main", "01-24 16:35:45.321  1234  5678 I Tag: first")
        val result = parse(*lines)
        assertEquals(1, result.size)
        assertEquals("first", result[0].message)
    }

    @Test
    fun `multiple buffer separators between entries do not pollute messages`() {
        val lines =
            arrayOf(
                "01-24 16:35:45.321  1234  5678 I Tag: first",
                "--------- beginning of main",
                "--------- beginning of system",
                "01-24 16:35:46.000  1234  5678 W Tag: second",
            )
        val result = parse(*lines)
        assertEquals(2, result.size)
        assertEquals("first", result[0].message)
        assertEquals("second", result[1].message)
    }

    @Test
    fun `continuation line is appended to previous entry`() {
        val lines =
            arrayOf(
                "01-24 16:35:45.321  1234  5678 E Tag: Exception",
                "    at com.example.Foo.bar(Foo.kt:42)",
            )
        val result = parse(*lines)
        assertEquals(1, result.size)
        assertEquals("Exception\n    at com.example.Foo.bar(Foo.kt:42)", result[0].message)
    }

    @Test
    fun `multiple continuation lines append to same entry`() {
        val lines =
            arrayOf(
                "01-24 16:35:45.321  1234  5678 E Tag: Caused by:",
                "    at foo.Bar.baz(Bar.kt:1)",
                "    at foo.Bar.qux(Bar.kt:2)",
            )
        val result = parse(*lines)
        assertEquals(1, result.size)
        assertEquals(
            "Caused by:\n    at foo.Bar.baz(Bar.kt:1)\n    at foo.Bar.qux(Bar.kt:2)",
            result[0].message,
        )
    }

    @Test
    fun `continuation after buffer separator appends to next log not previous`() {
        val lines =
            arrayOf(
                "01-24 16:35:45.321  1234  5678 I Tag: before separator",
                "--------- beginning of crash",
                "01-24 16:35:46.000  1234  5678 E Tag: after separator",
                "    stack trace line",
            )
        val result = parse(*lines)
        assertEquals(2, result.size)
        assertEquals("before separator", result[0].message)
        assertEquals("after separator\n    stack trace line", result[1].message)
    }

    @Test
    fun `all log levels parsed`() {
        val levels = listOf("V", "D", "I", "W", "E", "A")
        val expected =
            listOf(
                LogLevel.VERBOSE,
                LogLevel.DEBUG,
                LogLevel.INFO,
                LogLevel.WARN,
                LogLevel.ERROR,
                LogLevel.ASSERT,
            )
        levels.zip(expected).forEach { (letter, level) ->
            val result = parse("01-24 16:35:45.321  1  2 $letter Tag: msg")
            assertEquals(1, result.size)
            assertEquals(level, result[0].level)
        }
    }
}
