package dev.sebastiano.camerasync.domain.model

import java.time.ZonedDateTime

/**
 * Information about a successful location synchronization event.
 *
 * @property syncTime The time when the synchronization occurred.
 * @property location The GPS location that was synchronized to the device.
 */
data class LocationSyncInfo(val syncTime: ZonedDateTime, val location: GpsLocation)
