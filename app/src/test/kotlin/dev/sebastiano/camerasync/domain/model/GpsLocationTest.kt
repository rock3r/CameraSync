package dev.sebastiano.camerasync.domain.model

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [GpsLocation] freshness checking.
 *
 * Per Sony protocol spec (docs/sony/DATETIME_GPS_SYNC.md):
 * - Only send location data if it is fresh (< 10 seconds old)
 * - Stale data should be discarded
 */
class GpsLocationTest {

    private val baseTime = ZonedDateTime.of(2024, 12, 25, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun createLocation(timestamp: ZonedDateTime) =
        GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = timestamp,
        )

    // ==================== isFresh() Tests ====================

    @Test
    fun `isFresh returns true for location 0 seconds old`() {
        val location = createLocation(baseTime)
        assertTrue(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns true for location 5 seconds old`() {
        val locationTime = baseTime.minusSeconds(5)
        val location = createLocation(locationTime)
        assertTrue(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns true for location 9 seconds old`() {
        val locationTime = baseTime.minusSeconds(9)
        val location = createLocation(locationTime)
        assertTrue(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns false for location exactly 10 seconds old`() {
        val locationTime = baseTime.minusSeconds(10)
        val location = createLocation(locationTime)
        assertFalse(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns false for location 11 seconds old`() {
        val locationTime = baseTime.minusSeconds(11)
        val location = createLocation(locationTime)
        assertFalse(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns false for location 60 seconds old`() {
        val locationTime = baseTime.minusSeconds(60)
        val location = createLocation(locationTime)
        assertFalse(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh returns false for location with future timestamp`() {
        val locationTime = baseTime.plusSeconds(5) // Future timestamp
        val location = createLocation(locationTime)
        assertFalse(location.isFresh(now = baseTime))
    }

    @Test
    fun `isFresh handles different timezones correctly`() {
        // Location at 12:00 UTC
        val locationTime = ZonedDateTime.of(2024, 12, 25, 12, 0, 0, 0, ZoneOffset.UTC)
        val location = createLocation(locationTime)

        // Now is 12:00:05 in UTC+8 (which is 04:00:05 UTC - 8 hours behind)
        // This would be 7:59:55 behind the location time, so NOT fresh
        val nowInDifferentZone = ZonedDateTime.of(2024, 12, 25, 12, 0, 5, 0, ZoneOffset.ofHours(8))

        // The location is from "the future" relative to the now time, so not fresh
        assertFalse(location.isFresh(now = nowInDifferentZone))
    }

    @Test
    fun `isFresh uses current time by default`() {
        // Create a location with current time
        val location = createLocation(ZonedDateTime.now())
        assertTrue(location.isFresh())
    }

    // ==================== ageInSeconds() Tests ====================

    @Test
    fun `ageInSeconds returns 0 for same timestamp`() {
        val location = createLocation(baseTime)
        assertEquals(0L, location.ageInSeconds(now = baseTime))
    }

    @Test
    fun `ageInSeconds returns positive value for past timestamp`() {
        val locationTime = baseTime.minusSeconds(30)
        val location = createLocation(locationTime)
        assertEquals(30L, location.ageInSeconds(now = baseTime))
    }

    @Test
    fun `ageInSeconds returns negative value for future timestamp`() {
        val locationTime = baseTime.plusSeconds(15)
        val location = createLocation(locationTime)
        assertEquals(-15L, location.ageInSeconds(now = baseTime))
    }

    @Test
    fun `ageInSeconds handles large time differences`() {
        val locationTime = baseTime.minusHours(1)
        val location = createLocation(locationTime)
        assertEquals(3600L, location.ageInSeconds(now = baseTime))
    }

    // ==================== GPS_FRESHNESS_THRESHOLD_SECONDS Constant Test ====================

    @Test
    fun `GPS_FRESHNESS_THRESHOLD_SECONDS is 10 as per spec`() {
        assertEquals(10L, GPS_FRESHNESS_THRESHOLD_SECONDS)
    }

    // ==================== Data Class Tests ====================

    @Test
    fun `GpsLocation stores all properties correctly`() {
        val timestamp = ZonedDateTime.now()
        val location =
            GpsLocation(
                latitude = 51.5074,
                longitude = -0.1278,
                altitude = 100.5,
                accuracy = 5.0f,
                timestamp = timestamp,
            )

        assertEquals(51.5074, location.latitude, 0.0001)
        assertEquals(-0.1278, location.longitude, 0.0001)
        assertEquals(100.5, location.altitude, 0.0001)
        assertEquals(5.0f, location.accuracy, 0.001f)
        assertEquals(timestamp, location.timestamp)
    }

    @Test
    fun `GpsLocation default accuracy is 0`() {
        val location = createLocation(baseTime)
        assertEquals(0f, location.accuracy, 0.001f)
    }

    // ==================== withFreshTimestamp() Tests ====================

    @Test
    fun `withFreshTimestamp creates copy with new timestamp`() {
        val oldTime = baseTime.minusMinutes(5)
        val location = createLocation(oldTime)
        val newTime = baseTime

        val freshLocation = location.withFreshTimestamp(newTime)

        assertEquals(newTime, freshLocation.timestamp)
        assertEquals(location.latitude, freshLocation.latitude, 0.0001)
        assertEquals(location.longitude, freshLocation.longitude, 0.0001)
        assertEquals(location.altitude, freshLocation.altitude, 0.0001)
        assertEquals(location.accuracy, freshLocation.accuracy, 0.001f)
    }

    @Test
    fun `withFreshTimestamp makes stale location fresh`() {
        val staleTime = baseTime.minusSeconds(30)
        val staleLocation = createLocation(staleTime)

        assertFalse(staleLocation.isFresh(now = baseTime))

        val freshLocation = staleLocation.withFreshTimestamp(baseTime)

        assertTrue(freshLocation.isFresh(now = baseTime))
    }

    @Test
    fun `withFreshTimestamp preserves coordinates exactly`() {
        val location =
            GpsLocation(
                latitude = 37.7749123456,
                longitude = -122.4194987654,
                altitude = 123.456,
                accuracy = 2.5f,
                timestamp = baseTime.minusHours(1),
            )

        val freshLocation = location.withFreshTimestamp(baseTime)

        assertEquals(37.7749123456, freshLocation.latitude, 0.0)
        assertEquals(-122.4194987654, freshLocation.longitude, 0.0)
        assertEquals(123.456, freshLocation.altitude, 0.0)
        assertEquals(2.5f, freshLocation.accuracy, 0.0f)
    }
}
