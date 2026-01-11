package dev.sebastiano.camerasync.domain.model

import java.time.ZonedDateTime

/** Information about the last successful location sync. */
data class LocationSyncInfo(val syncTime: ZonedDateTime, val location: GpsLocation)
