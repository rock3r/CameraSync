package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SonyProtocol] CC13 Time Area Setting encoding/decoding and date/time handling.
 *
 * See docs/sony/DATETIME_GPS_SYNC.md for protocol documentation.
 */
class SonyProtocolDateTimeTest {

    @Test
    fun `encodeDateTime produces 13-byte CC13 packet`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals("CC13 packet should be exactly 13 bytes", 13, encoded.size)
    }

    @Test
    fun `encodeDateTime sets correct CC13 header`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(0x0C.toByte(), encoded[0])
        assertEquals(0x00.toByte(), encoded[1])
        assertEquals(0x00.toByte(), encoded[2])
    }

    @Test
    fun `encodeDateTime uses Big-Endian for year`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(0x07.toByte(), encoded[3])
        assertEquals(0xE8.toByte(), encoded[4])

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
    }

    @Test
    fun `encodeDateTime sets correct date components`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(12, encoded[5].toInt() and 0xFF)
        assertEquals(25, encoded[6].toInt() and 0xFF)
        assertEquals(14, encoded[7].toInt() and 0xFF)
        assertEquals(30, encoded[8].toInt() and 0xFF)
        assertEquals(45, encoded[9].toInt() and 0xFF)
    }

    @Test
    fun `encodeDateTime sets DST flag to 0 for standard time`() {
        val dateTime = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(0x00.toByte(), encoded[10])
    }

    @Test
    fun `encodeDateTime sets DST flag to 1 during daylight saving time`() {
        val dstZone = ZoneId.of("America/New_York")
        val dateTime = ZonedDateTime.of(2024, 7, 15, 12, 0, 0, 0, dstZone)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(0x01.toByte(), encoded[10])
    }

    @Test
    fun `encodeDateTime uses split format for timezone offset`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(8.toByte(), encoded[11])
        assertEquals(0.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime handles negative timezone offset`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(-5))
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals((-5).toByte(), encoded[11])
        assertEquals(0.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime handles UTC timezone`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(0.toByte(), encoded[11])
        assertEquals(0.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime handles fractional timezone offset`() {
        val dateTime =
            ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHoursMinutes(5, 30))
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(5.toByte(), encoded[11])
        assertEquals(30.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime preserves local time not UTC`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
        assertEquals(12, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.get().toInt() and 0xFF)
        assertEquals(14, buffer.get().toInt() and 0xFF)
        assertEquals(30, buffer.get().toInt() and 0xFF)
        assertEquals(45, buffer.get().toInt() and 0xFF)
    }

    @Test
    fun `decodeDateTime handles CC13 format`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        val decoded = SonyProtocol.decodeDateTime(encoded)

        assertTrue("Should contain year", decoded.contains("2024"))
        assertTrue("Should contain month-day", decoded.contains("12-25"))
        assertTrue("Should contain time", decoded.contains("14:30:45"))
    }

    @Test
    fun `decodeDateTime handles DD11 format`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
            )
        val encoded = SonyProtocol.encodeLocation(location)
        val decoded = SonyProtocol.decodeDateTime(encoded)

        assertTrue("Should contain date", decoded.contains("2024-12-25"))
        assertTrue("Should contain time", decoded.contains("14:30:45"))
    }

    @Test
    fun `decodeDateTime returns error for invalid size`() {
        val decoded = SonyProtocol.decodeDateTime(ByteArray(10))
        assertTrue("Should indicate invalid data", decoded.contains("Invalid data"))
    }

    @Test
    fun `decodeDateTime shows DST indicator when set`() {
        val dstZone = ZoneId.of("America/New_York")
        val dateTime = ZonedDateTime.of(2024, 7, 15, 12, 0, 0, 0, dstZone)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        val decoded = SonyProtocol.decodeDateTime(encoded)
        assertTrue("Should indicate DST", decoded.contains("DST"))
    }

    @Test
    fun `encodeDateTime handles fractional timezone offset near zero`() {
        val dateTime =
            ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHoursMinutes(0, -30))
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        val decoded = SonyProtocol.decodeDateTime(encoded)
        assertTrue(
            "Decoded string should contain UTC+00:30 due to Sony's bug, but was: $decoded",
            decoded.contains("+00:30"),
        )
    }

    @Test
    fun `CC13 encode-decode round trip preserves data`() {
        val dateTime = ZonedDateTime.of(2024, 6, 15, 10, 25, 30, 0, ZoneOffset.ofHours(-5))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
        assertEquals(6, buffer.get().toInt() and 0xFF)
        assertEquals(15, buffer.get().toInt() and 0xFF)
        assertEquals(10, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.get().toInt() and 0xFF)
        assertEquals(30, buffer.get().toInt() and 0xFF)
        buffer.get()
        assertEquals(-5, buffer.get().toInt())
        assertEquals(0, buffer.get().toInt() and 0xFF)
    }

    @Test
    fun `verify CC13 uses Big-Endian - explicit byte check`() {
        val dateTime = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(9))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals("Year high byte", 0x07.toByte(), encoded[3])
        assertEquals("Year low byte", 0xE9.toByte(), encoded[4])
        assertEquals("TZ hours", 9.toByte(), encoded[11])
        assertEquals("TZ minutes", 0.toByte(), encoded[12])
    }
}
