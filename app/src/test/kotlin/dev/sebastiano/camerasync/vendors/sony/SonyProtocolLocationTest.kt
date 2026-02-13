package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SonyProtocol] DD11 Location packet encoding/decoding and GPS sync.
 *
 * See docs/sony/DATETIME_GPS_SYNC.md for protocol documentation.
 */
class SonyProtocolLocationTest {

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
        assertEquals(0x00.toByte(), packet[6])
        assertEquals(0x00.toByte(), packet[7])
        assertEquals(0x10.toByte(), packet[8])
        assertEquals(0x10.toByte(), packet[9])
        assertEquals(0x10.toByte(), packet[10])
    }

    @Test
    fun `encodeLocationPacket uses Big-Endian for coordinates`() {
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
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(19)
        assertEquals(2024, buffer.short.toInt() and 0xFFFF)
    }

    @Test
    fun `encodeLocationPacket converts to UTC`() {
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
        assertEquals(6, buffer.get().toInt() and 0xFF)
        assertEquals(30, buffer.get().toInt() and 0xFF)
        assertEquals(45, buffer.get().toInt() and 0xFF)
    }

    @Test
    fun `encodeLocationPacket uses system timezone for offset`() {
        val dateTime = ZonedDateTime.now(ZoneOffset.UTC)
        val packet =
            SonyProtocol.encodeLocationPacket(
                latitude = 0.0,
                longitude = 0.0,
                dateTime = dateTime,
                includeTimezone = true,
            )
        val systemZone = java.time.ZoneId.systemDefault()
        val now = java.time.Instant.now()
        val zoneRules = systemZone.rules
        val standardOffset = zoneRules.getStandardOffset(now)
        val actualOffset = zoneRules.getOffset(now)
        val expectedTzMinutes = standardOffset.totalSeconds / 60
        val expectedDstMinutes = (actualOffset.totalSeconds - standardOffset.totalSeconds) / 60
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(91)
        assertEquals(expectedTzMinutes.toShort(), buffer.short)
        assertEquals(expectedDstMinutes.toShort(), buffer.short)
    }

    @Test
    fun `encodeLocationPacket handles extreme coordinates`() {
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
        assertTrue(decoded.contains("37.7749"))
        assertTrue(decoded.contains("-122.4194"))
        assertTrue(decoded.contains("2024-12-25"))
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
        assertTrue(decoded.contains("-33.8688"))
        assertTrue(decoded.contains("151.2093"))
    }

    @Test
    fun `decodeLocation returns error for short data`() {
        val decoded = SonyProtocol.decodeLocation(ByteArray(10))
        assertTrue(decoded.contains("Invalid data"))
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
        assertTrue(decoded.contains("51.507"))
        assertTrue(decoded.contains("-0.127"))
        assertTrue(decoded.contains("2024-03-20"))
        assertTrue(decoded.contains("15:45:00"))
    }

    @Test
    fun `verify Big-Endian is used throughout - explicit byte check`() {
        val dateTime = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val location =
            GpsLocation(latitude = 1.0, longitude = 2.0, altitude = 0.0, timestamp = dateTime)
        val encoded = SonyProtocol.encodeLocation(location)
        assertEquals(0x07.toByte(), encoded[19])
        assertEquals(0xE9.toByte(), encoded[20])
        assertEquals(0x00.toByte(), encoded[11])
        assertEquals(0x98.toByte(), encoded[12])
        assertEquals(0x96.toByte(), encoded[13])
        assertEquals(0x80.toByte(), encoded[14])
        assertEquals(0x01.toByte(), encoded[15])
        assertEquals(0x31.toByte(), encoded[16])
        assertEquals(0x2D.toByte(), encoded[17])
        assertEquals(0x00.toByte(), encoded[18])
    }
}
