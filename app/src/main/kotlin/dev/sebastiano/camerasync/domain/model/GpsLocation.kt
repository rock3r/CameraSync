package dev.sebastiano.camerasync.domain.model

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Maximum age in seconds for a GPS location to be considered fresh. Per Sony protocol spec, only
 * locations < 10 seconds old should be sent.
 */
const val GPS_FRESHNESS_THRESHOLD_SECONDS = 10L

/**
 * Represents a GPS location with coordinates, altitude, and timestamp.
 *
 * This is a domain model decoupled from Android's Location class.
 */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float = 0f,
    val timestamp: ZonedDateTime,
) {

    /**
     * Checks if this location is fresh enough to be sent to the camera.
     *
     * Per Sony protocol specification, location data should only be sent if it is less than
     * [GPS_FRESHNESS_THRESHOLD_SECONDS] seconds old. Stale data should be discarded.
     *
     * @param now The current time to compare against. Defaults to [ZonedDateTime.now].
     * @return true if the location is fresh (< 10 seconds old), false otherwise.
     */
    fun isFresh(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val age = Duration.between(timestamp, now)
        return age.seconds < GPS_FRESHNESS_THRESHOLD_SECONDS && !age.isNegative
    }

    /**
     * Returns the age of this location in seconds.
     *
     * @param now The current time to compare against. Defaults to [ZonedDateTime.now].
     * @return The age in seconds, or a negative value if the timestamp is in the future.
     */
    fun ageInSeconds(now: ZonedDateTime = ZonedDateTime.now()): Long =
        Duration.between(timestamp, now).seconds

    /**
     * Creates a copy of this location with a fresh timestamp (now).
     *
     * This is used for keep-alive messages where we want to re-send the last known coordinates but
     * with a current timestamp so the camera accepts it as fresh data.
     *
     * @param now The timestamp to use. Defaults to [ZonedDateTime.now].
     * @return A new [GpsLocation] with the same coordinates but the specified timestamp.
     */
    fun withFreshTimestamp(now: ZonedDateTime = ZonedDateTime.now()): GpsLocation =
        copy(timestamp = now)
}
