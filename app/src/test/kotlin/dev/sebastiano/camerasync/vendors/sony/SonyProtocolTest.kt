package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive tests for [SonyProtocol] implementation.
 *
 * These tests verify:
 * - CC13 Time Area Setting encoding (13-byte packet for date/time sync)
 * - DD11 Location packet encoding (91/95-byte packet for GPS sync)
 * - Big-Endian byte order for all multi-byte values (critical for Sony protocol)
 * - Decoding of both packet formats
 * - Edge cases and boundary conditions
 *
 * See docs/sony/DATETIME_GPS_SYNC.md for protocol documentation.
 */
class SonyProtocolTest {

    // ==================== CC13 Time Area Setting Tests ====================

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

        // Bytes 0-2: Header 0x0C 0x00 0x00
        assertEquals(0x0C.toByte(), encoded[0])
        assertEquals(0x00.toByte(), encoded[1])
        assertEquals(0x00.toByte(), encoded[2])
    }

    @Test
    fun `encodeDateTime uses Big-Endian for year`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Year 2024 = 0x07E8 in hex
        // Big-Endian: high byte first = 0x07, 0xE8
        assertEquals(0x07.toByte(), encoded[3]) // High byte
        assertEquals(0xE8.toByte(), encoded[4]) // Low byte

        // Verify by reading as Big-Endian
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
    }

    @Test
    fun `encodeDateTime sets correct date components`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(12, encoded[5].toInt() and 0xFF) // Month
        assertEquals(25, encoded[6].toInt() and 0xFF) // Day
        assertEquals(14, encoded[7].toInt() and 0xFF) // Hour
        assertEquals(30, encoded[8].toInt() and 0xFF) // Minute
        assertEquals(45, encoded[9].toInt() and 0xFF) // Second
    }

    @Test
    fun `encodeDateTime sets DST flag to 0 for standard time`() {
        // UTC has no DST
        val dateTime = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(0x00.toByte(), encoded[10])
    }

    @Test
    fun `encodeDateTime sets DST flag to 1 during daylight saving time`() {
        // Use a timezone that observes DST, during summer
        val dstZone = ZoneId.of("America/New_York")
        val dateTime = ZonedDateTime.of(2024, 7, 15, 12, 0, 0, 0, dstZone)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(0x01.toByte(), encoded[10])
    }

    @Test
    fun `encodeDateTime uses split format for timezone offset`() {
        // UTC+8 = +8 hours, 0 minutes
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Byte 11: Signed hours = +8
        // Byte 12: Minutes = 0
        assertEquals(8.toByte(), encoded[11])
        assertEquals(0.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime handles negative timezone offset`() {
        // UTC-5 = -5 hours, 0 minutes
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(-5))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Byte 11: Signed hours = -5
        // Byte 12: Minutes = 0
        assertEquals((-5).toByte(), encoded[11])
        assertEquals(0.toByte(), encoded[12])
    }

    @Test
    fun `encodeDateTime handles UTC timezone`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(0.toByte(), encoded[11]) // Hours
        assertEquals(0.toByte(), encoded[12]) // Minutes
    }

    @Test
    fun `encodeDateTime handles fractional timezone offset`() {
        // UTC+5:30 (India) = +5 hours, 30 minutes
        val dateTime =
            ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHoursMinutes(5, 30))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        assertEquals(5.toByte(), encoded[11]) // Hours
        assertEquals(30.toByte(), encoded[12]) // Minutes
    }

    @Test
    fun `encodeDateTime preserves local time not UTC`() {
        // CC13 uses local time, not UTC
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
        assertEquals(12, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.get().toInt() and 0xFF)
        assertEquals(14, buffer.get().toInt() and 0xFF) // Local hour, not UTC
        assertEquals(30, buffer.get().toInt() and 0xFF)
        assertEquals(45, buffer.get().toInt() and 0xFF)
    }

    // ==================== DD11 Location Packet Tests ====================

    @Test
    fun `encodeLocation produces 95 bytes with timezone`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
            )
        val encoded = SonyProtocol.encodeLocation(location)
        assertEquals(95, encoded.size)
    }

    @Test
    fun `encodeLocationPacket without timezone produces 91 bytes`() {
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        assertEquals(91, packet.size)
    }

    @Test
    fun `encodeLocationPacket uses Big-Endian for payload length`() {
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )

        // Payload length 93 = 0x005D
        // Big-Endian: 0x00, 0x5D (high byte first)
        assertEquals(0x00.toByte(), packet[0])
        assertEquals(0x5D.toByte(), packet[1])

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        assertEquals(93, buffer.short.toInt())
    }

    @Test
    fun `encodeLocationPacket sets correct fixed header`() {
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )

        // Bytes 2-4: Fixed header 0x08 0x02 0xFC
        assertEquals(0x08.toByte(), packet[2])
        assertEquals(0x02.toByte(), packet[3])
        assertEquals(0xFC.toByte(), packet[4])
    }

    @Test
    fun `encodeLocationPacket sets timezone flag correctly`() {
        val withTz =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )
        assertEquals(0x03.toByte(), withTz[5])

        val withoutTz =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        assertEquals(0x00.toByte(), withoutTz[5])
    }

    @Test
    fun `encodeLocationPacket sets correct padding bytes`() {
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )

        // Bytes 6-7: Padding (zeros)
        // Bytes 8-10: Fixed padding (0x10)
        assertEquals(0x00.toByte(), packet[6])
        assertEquals(0x00.toByte(), packet[7])
        assertEquals(0x10.toByte(), packet[8])
        assertEquals(0x10.toByte(), packet[9])
        assertEquals(0x10.toByte(), packet[10])
    }

    @Test
    fun `encodeLocationPacket uses Big-Endian for coordinates`() {
        // San Francisco: 37.7749, -122.4194
        // Latitude * 10,000,000 = 377,749,000 = 0x168429B8
        // Big-Endian: 16, 84, 29, B8
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 37.7749,
                longitude = -122.4194,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)

        val expectedLat = (37.7749 * 10_000_000).toInt()
        val expectedLon = (-122.4194 * 10_000_000).toInt()

        assertEquals(expectedLat, buffer.int)
        assertEquals(expectedLon, buffer.int)
    }

    @Test
    fun `encodeLocationPacket handles negative latitude correctly`() {
        // Sydney: -33.8688
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = -33.8688,
                longitude = 151.2093,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = true,
            )

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)

        val expectedLat = (-33.8688 * 10_000_000).toInt()
        val expectedLon = (151.2093 * 10_000_000).toInt()

        assertEquals(expectedLat, buffer.int)
        assertEquals(expectedLon, buffer.int)
    }

    @Test
    fun `encodeLocationPacket uses Big-Endian for year`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = dateTime,
                includeTimezone = true,
            )

        // Year at offset 19
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(19)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
    }

    @Test
    fun `encodeLocationPacket converts to UTC`() {
        // DD11 uses UTC time (per Sony decompiled code)
        // 14:30 in UTC+8 should become 06:30 UTC
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = dateTime,
                includeTimezone = true,
            )

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(19)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
        assertEquals(12, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.get().toInt() and 0xFF)
        assertEquals(6, buffer.get().toInt() and 0xFF) // 14 - 8 = 6 UTC
        assertEquals(30, buffer.get().toInt() and 0xFF)
        assertEquals(45, buffer.get().toInt() and 0xFF)
    }

    @Test
    fun `encodeLocationPacket uses system timezone for offset`() {
        // The timezone offset now uses the system default timezone,
        // not the dateTime's offset (GPS timestamps are typically UTC)
        val dateTime = ZonedDateTime.now(ZoneOffset.UTC)
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = dateTime,
                includeTimezone = true,
            )

        // Calculate expected values from system timezone
        val systemZone = java.time.ZoneId.systemDefault()
        val now = java.time.Instant.now()
        val zoneRules = systemZone.rules
        val standardOffset = zoneRules.getStandardOffset(now)
        val actualOffset = zoneRules.getOffset(now)

        val expectedTzMinutes = standardOffset.totalSeconds / 60
        val expectedDstMinutes = (actualOffset.totalSeconds - standardOffset.totalSeconds) / 60

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(91)
        assertEquals(expectedTzMinutes.toShort(), buffer.short) // System TZ offset in minutes
        assertEquals(expectedDstMinutes.toShort(), buffer.short) // DST offset
    }

    @Test
    fun `encodeLocationPacket handles extreme coordinates`() {
        // North Pole
        val northPole =
            SonyProtocol.encodeLocationPacket(
                latitude = 90.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        var buffer = ByteBuffer.wrap(northPole).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)
        assertEquals(900_000_000, buffer.int)

        // South Pole
        val southPole =
            SonyProtocol.encodeLocationPacket(
                latitude = -90.0,
                longitude = 0.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        buffer = ByteBuffer.wrap(southPole).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)
        assertEquals(-900_000_000, buffer.int)

        // International Date Line
        val dateLine =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 180.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        buffer = ByteBuffer.wrap(dateLine).order(ByteOrder.BIG_ENDIAN)
        buffer.position(15)
        assertEquals(1_800_000_000, buffer.int)

        val negDateLine =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = -180.0,
                dateTime = ZonedDateTime.now(ZoneOffset.UTC),
                includeTimezone = false,
            )
        buffer = ByteBuffer.wrap(negDateLine).order(ByteOrder.BIG_ENDIAN)
        buffer.position(15)
        assertEquals(-1_800_000_000, buffer.int)
    }

    // ==================== Decode Tests ====================

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

        // DD11 uses UTC time
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
    fun `decodeLocation correctly decodes encoded location`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
            )
        val encoded = SonyProtocol.encodeLocation(location)
        val decoded = SonyProtocol.decodeLocation(encoded)

        assertTrue("Decoded string should contain latitude", decoded.contains("37.7749"))
        assertTrue("Decoded string should contain longitude", decoded.contains("-122.4194"))
        assertTrue("Decoded string should contain date", decoded.contains("2024-12-25"))
    }

    @Test
    fun `decodeLocation handles negative coordinates`() {
        val location =
            GpsLocation(
                latitude = -33.8688,
                longitude = 151.2093,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
            )
        val encoded = SonyProtocol.encodeLocation(location)
        val decoded = SonyProtocol.decodeLocation(encoded)

        assertTrue("Should contain negative latitude", decoded.contains("-33.8688"))
        assertTrue("Should contain positive longitude", decoded.contains("151.2093"))
    }

    @Test
    fun `decodeLocation returns error for short data`() {
        val decoded = SonyProtocol.decodeLocation(ByteArray(10))
        assertTrue("Should indicate invalid data", decoded.contains("Invalid data"))
    }

    // ==================== Round-trip Tests ====================

    @Test
    fun `CC13 encode-decode round trip preserves data`() {
        val dateTime = ZonedDateTime.of(2024, 6, 15, 10, 25, 30, 0, ZoneOffset.ofHours(-5))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Manually verify the round trip by reading bytes
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(3)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
        assertEquals(6, buffer.get().toInt() and 0xFF)
        assertEquals(15, buffer.get().toInt() and 0xFF)
        assertEquals(10, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.get().toInt() and 0xFF)
        assertEquals(30, buffer.get().toInt() and 0xFF)
        buffer.get() // DST flag
        assertEquals(-5, buffer.get().toInt()) // Hours (signed)
        assertEquals(0, buffer.get().toInt() and 0xFF) // Minutes
    }

    @Test
    fun `DD11 encode-decode round trip preserves coordinates`() {
        val location =
            GpsLocation(
                latitude = 51.5074,
                longitude = -0.1278,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 3, 20, 15, 45, 0, 0, ZoneOffset.UTC),
            )
        val encoded = SonyProtocol.encodeLocation(location)
        val decoded = SonyProtocol.decodeLocation(encoded)

        // Allow for floating-point precision differences
        assertTrue("Should contain latitude ~51.5074", decoded.contains("51.507"))
        assertTrue("Should contain longitude ~-0.1278", decoded.contains("-0.127"))
        assertTrue("Should contain date", decoded.contains("2024-03-20"))
        assertTrue("Should contain time", decoded.contains("15:45:00"))
    }

    // ==================== Configuration Parsing Tests ====================

    @Test
    fun `parseConfigRequiresTimezone returns true when bit 1 is set`() {
        // byte[4] = 0x02 (bit 1 set) → timezone supported, use 95-byte payload
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x02, 0x00)
        assertTrue(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns true when bit 1 is set with other bits`() {
        // byte[4] = 0x06 (bits 1 and 2 set) → timezone supported, use 95-byte payload
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x06, 0x00)
        assertTrue(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false when bit 1 is not set`() {
        // byte[4] = 0x04 (bit 2 set, but not bit 1) → timezone NOT supported, use 91-byte payload
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x04, 0x00)
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false when byte 4 is zero`() {
        // byte[4] = 0x00 (no bits set) → timezone NOT supported, use 91-byte payload
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x00, 0x00)
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false for short data`() {
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(byteArrayOf(0x01, 0x02)))
    }

    // ==================== Helper Command Tests ====================

    @Test
    fun `createStatusNotifyEnable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x01)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyEnable())
    }

    @Test
    fun `createStatusNotifyDisable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x00)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyDisable())
    }

    @Test
    fun `createPairingInit returns correct bytes`() {
        val expected = byteArrayOf(0x06, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, SonyProtocol.createPairingInit())
    }

    @Test
    fun `getPairingInitData returns same as createPairingInit`() {
        assertArrayEquals(SonyProtocol.createPairingInit(), SonyProtocol.getPairingInitData())
    }

    // ==================== Geo-tagging Tests ====================

    @Test
    fun `encodeGeoTaggingEnabled returns empty array`() {
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(true).size)
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(false).size)
    }

    @Test
    fun `decodeGeoTaggingEnabled returns false`() {
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf(0x01)))
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf()))
    }

    // ==================== Byte Order Verification Tests ====================

    @Test
    fun `verify Big-Endian is used throughout - explicit byte check`() {
        val dateTime = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val location =
            GpsLocation(
                latitude = 1.0, // 10,000,000 = 0x00989680
                longitude = 2.0, // 20,000,000 = 0x01312D00
                altitude = 0.0,
                timestamp = dateTime,
            )
        val encoded = SonyProtocol.encodeLocation(location)

        // Year 2025 = 0x07E9
        // If Big-Endian: bytes would be 0x07, 0xE9
        assertEquals("Year high byte should be 0x07 for Big-Endian", 0x07.toByte(), encoded[19])
        assertEquals("Year low byte should be 0xE9 for Big-Endian", 0xE9.toByte(), encoded[20])

        // Latitude 10,000,000 = 0x00989680
        // Big-Endian: 0x00, 0x98, 0x96, 0x80
        assertEquals(0x00.toByte(), encoded[11])
        assertEquals(0x98.toByte(), encoded[12])
        assertEquals(0x96.toByte(), encoded[13])
        assertEquals(0x80.toByte(), encoded[14])

        // Longitude 20,000,000 = 0x01312D00
        // Big-Endian: 0x01, 0x31, 0x2D, 0x00
        assertEquals(0x01.toByte(), encoded[15])
        assertEquals(0x31.toByte(), encoded[16])
        assertEquals(0x2D.toByte(), encoded[17])
        assertEquals(0x00.toByte(), encoded[18])
    }

    @Test
    fun `verify CC13 uses Big-Endian - explicit byte check`() {
        // Year 2025 = 0x07E9
        // UTC+9 = +9 hours, 0 minutes (split format)
        val dateTime = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(9))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Year (bytes 3-4) - Big-Endian
        assertEquals("Year high byte", 0x07.toByte(), encoded[3])
        assertEquals("Year low byte", 0xE9.toByte(), encoded[4])

        // Timezone offset (bytes 11-12) - Split format
        assertEquals("TZ hours", 9.toByte(), encoded[11])
        assertEquals("TZ minutes", 0.toByte(), encoded[12])
    }
}
