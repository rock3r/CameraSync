package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.model.BatteryPosition
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PowerSource
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RicohProtocolTest {

    @Test
    fun `encodeDateTime produces correct byte array size`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        assertEquals(RicohProtocol.DATE_TIME_SIZE, encoded.size)
    }

    @Test
    fun `encodeDateTime and decodeDateTime are inverse operations`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        val decoded = RicohProtocol.decodeDateTime(encoded)

        assertEquals("2024-12-25 14:30:45", decoded)
    }

    @Test
    fun `encodeDateTime handles year boundary correctly`() {
        val newYearEve = ZonedDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC"))
        val newYear = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))

        val decodedEve = RicohProtocol.decodeDateTime(RicohProtocol.encodeDateTime(newYearEve))
        val decodedNew = RicohProtocol.decodeDateTime(RicohProtocol.encodeDateTime(newYear))

        assertEquals("2023-12-31 23:59:59", decodedEve)
        assertEquals("2024-01-01 00:00:00", decodedNew)
    }

    @Test
    fun `encodeDateTime year is little-endian`() {
        // Year 2024 = 0x07E8
        // Little-endian: E8 07
        val dateTime = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)

        assertEquals(0xE8.toByte(), encoded[0])
        assertEquals(0x07.toByte(), encoded[1])
    }

    @Test
    fun `decodeDateTime throws on insufficient data`() {
        val tooShort = ByteArray(5)
        assertThrows(IllegalArgumentException::class.java) {
            RicohProtocol.decodeDateTime(tooShort)
        }
    }

    @Test
    fun `encodeLocation produces correct byte array size`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )
        val encoded = RicohProtocol.encodeLocation(location)
        assertEquals(RicohProtocol.LOCATION_SIZE, encoded.size)
    }

    @Test
    fun `encodeLocation and decodeLocation are inverse operations`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.5,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )

        val encoded = RicohProtocol.encodeLocation(location)
        val decoded = RicohProtocol.decodeLocation(encoded)

        assertTrue(decoded.contains("37.7749"))
        assertTrue(decoded.contains("-122.4194"))
        assertTrue(decoded.contains("10.5"))
        assertTrue(decoded.contains("2024-12-25 14:30:45"))
    }

    @Test
    fun `encodeLocation handles extreme coordinates`() {
        // North Pole
        val northPole =
            GpsLocation(
                latitude = 90.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedNorth = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(northPole))
        assertTrue(decodedNorth.contains("90.0"))

        // South Pole
        val southPole =
            GpsLocation(
                latitude = -90.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedSouth = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(southPole))
        assertTrue(decodedSouth.contains("-90.0"))

        // International Date Line
        val dateLine =
            GpsLocation(
                latitude = 0.0,
                longitude = 180.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedDateLine = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(dateLine))
        assertTrue(decodedDateLine.contains("180.0"))
    }

    @Test
    fun `encodeLocation handles negative altitude`() {
        // Dead Sea - below sea level
        val deadSea =
            GpsLocation(
                latitude = 31.5,
                longitude = 35.5,
                altitude = -430.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decoded = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(deadSea))
        assertTrue(decoded.contains("-430.0"))
    }

    @Test
    fun `encodeLocation handles high altitude`() {
        // Mount Everest
        val everest =
            GpsLocation(
                latitude = 27.9881,
                longitude = 86.9250,
                altitude = 8848.86,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decoded = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(everest))
        assertTrue(decoded.contains("8848.86"))
    }

    @Test
    fun `decodeLocation throws on insufficient data`() {
        val tooShort = ByteArray(30)
        assertThrows(IllegalArgumentException::class.java) {
            RicohProtocol.decodeLocation(tooShort)
        }
    }

    @Test
    fun `formatDateTimeHex produces expected format`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        val hex = RicohProtocol.formatDateTimeHex(encoded)

        // Should have 6 underscore-separated segments
        assertEquals(6, hex.split("_").size)
    }

    @Test
    fun `formatLocationHex produces expected format`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )
        val encoded = RicohProtocol.encodeLocation(location)
        val hex = RicohProtocol.formatLocationHex(encoded)

        // Should have 10 underscore-separated segments
        assertEquals(10, hex.split("_").size)
    }

    @Test
    fun `DecodedDateTime toString formats correctly`() {
        val dateTime = ZonedDateTime.of(2024, 1, 5, 9, 3, 7, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        val decoded = RicohProtocol.decodeDateTime(encoded)
        assertEquals("2024-01-05 09:03:07", decoded)
    }

    @Test
    fun `DecodedLocation toString formats correctly`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.5,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )
        val encoded = RicohProtocol.encodeLocation(location)
        val str = RicohProtocol.decodeLocation(encoded)
        assertTrue(str.contains("37.7749"))
        assertTrue(str.contains("-122.4194"))
        assertTrue(str.contains("10.5"))
        assertTrue(str.contains("2024-12-25 14:30:45"))
    }

    @Test
    fun `encodeGeoTaggingEnabled encodes correctly`() {
        val enabled = RicohProtocol.encodeGeoTaggingEnabled(true)
        assertEquals(1, enabled.size)
        assertEquals(1.toByte(), enabled[0])

        val disabled = RicohProtocol.encodeGeoTaggingEnabled(false)
        assertEquals(1, disabled.size)
        assertEquals(0.toByte(), disabled[0])
    }

    @Test
    fun `decodeGeoTaggingEnabled decodes correctly`() {
        assertTrue(RicohProtocol.decodeGeoTaggingEnabled(byteArrayOf(1)))
        assertTrue(!RicohProtocol.decodeGeoTaggingEnabled(byteArrayOf(0)))
    }

    @Test
    fun `decodeGeoTaggingEnabled throws on empty data`() {
        assertThrows(IllegalArgumentException::class.java) {
            RicohProtocol.decodeGeoTaggingEnabled(byteArrayOf())
        }
    }

    // --- New Tests for Remote Control ---

    @Test
    fun `decodeBatteryInfo decodes correct percentage`() {
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(85, 0))
        assertEquals(85, info.levelPercentage)
        assertEquals(BatteryPosition.INTERNAL, info.position)
        assertEquals(PowerSource.BATTERY, info.powerSource)
        assertFalse(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo clamps percentage exceeding 100 to 100`() {
        // Test value of 150 (0x96)
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(150.toByte(), 0))
        assertEquals(100, info.levelPercentage)
        assertEquals(PowerSource.BATTERY, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo preserves valid boundary values 0 and 100`() {
        // Test value of 0
        val infoZero = RicohProtocol.decodeBatteryInfo(byteArrayOf(0, 0))
        assertEquals(0, infoZero.levelPercentage)
        assertEquals(PowerSource.BATTERY, infoZero.powerSource)

        // Test value of 100
        val infoHundred = RicohProtocol.decodeBatteryInfo(byteArrayOf(100, 0))
        assertEquals(100, infoHundred.levelPercentage)
        assertEquals(PowerSource.BATTERY, infoHundred.powerSource)
    }

    @Test
    fun `decodeBatteryInfo clamps very large values to 100`() {
        // Test value of 255 (0xFF) - maximum unsigned byte value
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(255.toByte(), 0))
        assertEquals(100, info.levelPercentage)
        assertEquals(PowerSource.BATTERY, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo handles negative byte values without sign extension`() {
        // Test that a negative byte (e.g. 0x80 = -128 as signed, 128 as unsigned)
        // is correctly interpreted as unsigned and clamped to 100
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(0x80.toByte(), 0))
        assertEquals(100, info.levelPercentage)
        assertEquals(PowerSource.BATTERY, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo handles empty array`() {
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf())
        assertEquals(0, info.levelPercentage)
        assertEquals(BatteryPosition.UNKNOWN, info.position)
        assertEquals(PowerSource.UNKNOWN, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo detects AC adapter power source`() {
        // Power source byte: 0 = Battery, 1 = AC Adapter
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(75, 1))
        assertEquals(75, info.levelPercentage)
        assertEquals(PowerSource.AC_ADAPTER, info.powerSource)
        assertEquals(BatteryPosition.INTERNAL, info.position)
    }

    @Test
    fun `decodeBatteryInfo handles unknown power source`() {
        // Unknown power source value (2 in this case)
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(50, 2))
        assertEquals(50, info.levelPercentage)
        assertEquals(PowerSource.UNKNOWN, info.powerSource)
        assertEquals(BatteryPosition.INTERNAL, info.position)
    }

    @Test
    fun `decodeBatteryInfo handles missing power source byte`() {
        // Only battery level, no power source byte
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(90))
        assertEquals(90, info.levelPercentage)
        assertEquals(PowerSource.UNKNOWN, info.powerSource)
        assertEquals(BatteryPosition.INTERNAL, info.position)
    }

    @Test
    fun `decodeBatteryInfo boundary value at 101 is clamped`() {
        // Test the boundary just above 100
        val info = RicohProtocol.decodeBatteryInfo(byteArrayOf(101, 0))
        assertEquals(100, info.levelPercentage)
    }

    @Test
    fun `decodeStorageInfo decodes basic presence and remaining shots`() {
        // Status 1 (present), Remaining 100 (0x64 00 00 00 LE)
        val data = byteArrayOf(1, 0x64, 0, 0, 0)
        val info = RicohProtocol.decodeStorageInfo(data)

        assertTrue(info.isPresent)
        assertEquals(100, info.remainingShots)
        assertFalse(info.isFull)
    }

    @Test
    fun `decodeStorageInfo detects full storage`() {
        // Status 1 (present), Remaining 0
        val data = byteArrayOf(1, 0, 0, 0, 0)
        val info = RicohProtocol.decodeStorageInfo(data)

        assertTrue(info.isPresent)
        assertEquals(0, info.remainingShots)
        assertTrue(info.isFull)
    }

    @Test
    fun `decodeCaptureStatus detects countdown`() {
        // Capturing = 0, Countdown = 1
        val data = byteArrayOf(0, 1)
        val status = RicohProtocol.decodeCaptureStatus(data)
        assertTrue(status is CaptureStatus.Countdown)
    }

    @Test
    fun `decodeCaptureStatus detects capturing`() {
        // Capturing = 1, Countdown = 0
        val data = byteArrayOf(1, 0)
        val status = RicohProtocol.decodeCaptureStatus(data)
        assertEquals(CaptureStatus.Capturing, status)
    }

    @Test
    fun `decodeCaptureStatus detects idle`() {
        // Countdown = 0, Capturing = 0
        val data = byteArrayOf(0, 0)
        val status = RicohProtocol.decodeCaptureStatus(data)
        assertEquals(CaptureStatus.Idle, status)
    }

    @Test
    fun `decodeShootingMode decodes Still + P`() {
        // Mode 0 (Still), Exposure 0 (P)
        val data = byteArrayOf(0, 0)
        val (mode, exposure) = RicohProtocol.decodeShootingMode(data)
        assertEquals(CameraMode.STILL_IMAGE, mode)
        assertEquals(ExposureMode.PROGRAM_AUTO, exposure)
    }

    @Test
    fun `decodeShootingMode decodes Movie + M`() {
        // Mode 1 (Movie), Exposure 3 (M)
        val data = byteArrayOf(1, 3)
        val (mode, exposure) = RicohProtocol.decodeShootingMode(data)
        assertEquals(CameraMode.MOVIE, mode)
        assertEquals(ExposureMode.MANUAL, exposure)
    }

    @Test
    fun `decodeDriveMode decodes Single`() {
        val mode = RicohProtocol.decodeDriveMode(byteArrayOf(0))
        assertEquals(DriveMode.SINGLE_SHOOTING, mode)
    }

    @Test
    fun `decodeDriveMode decodes SelfTimer`() {
        val mode = RicohProtocol.decodeDriveMode(byteArrayOf(2))
        assertEquals(DriveMode.SELF_TIMER_2S, mode)
    }
}
