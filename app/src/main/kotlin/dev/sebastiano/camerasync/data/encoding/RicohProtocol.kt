@file:Suppress("DEPRECATION")

package dev.sebastiano.camerasync.data.encoding

import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZonedDateTime

/**
 * Backward compatibility wrapper for the Ricoh protocol.
 *
 * @deprecated This class has been moved to vendors.ricoh package. Use
 *   dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol instead.
 */
@Deprecated(
    message = "Moved to vendors.ricoh package",
    replaceWith =
        ReplaceWith(
            "dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol",
            "dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol",
        ),
    level = DeprecationLevel.WARNING,
)
object RicohProtocol {

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.DATE_TIME_SIZE */
    const val DATE_TIME_SIZE = 7

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.LOCATION_SIZE */
    const val LOCATION_SIZE = 32

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.encodeDateTime */
    fun encodeDateTime(dateTime: ZonedDateTime): ByteArray =
        dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.encodeDateTime(dateTime)

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.decodeDateTime */
    fun decodeDateTime(bytes: ByteArray): DecodedDateTime {
        val decodedString =
            dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.decodeDateTime(bytes)
        // Parse the string back to DecodedDateTime for backward compatibility
        // Format is "YYYY-MM-DD HH:mm:ss"
        val parts = decodedString.split(" ", "-", ":")
        return DecodedDateTime(
            year = parts[0].toInt(),
            month = parts[1].toInt(),
            day = parts[2].toInt(),
            hour = parts[3].toInt(),
            minute = parts[4].toInt(),
            second = parts[5].toInt(),
        )
    }

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.encodeLocation */
    fun encodeLocation(location: GpsLocation): ByteArray =
        dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.encodeLocation(location)

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.decodeLocation */
    fun decodeLocation(bytes: ByteArray): DecodedLocation {
        val decodedString =
            dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.decodeLocation(bytes)
        // Parse the string for backward compatibility
        // Format is "(latitude, longitude), altitude: altitude. Time: YYYY-MM-DD HH:mm:ss"
        val match =
            Regex("""\(([-\d.]+), ([-\d.]+)\), altitude: ([-\d.]+)\. Time: (.+)""")
                .find(decodedString) ?: error("Failed to parse location string: $decodedString")

        val lat = match.groupValues[1]
        val lon = match.groupValues[2]
        val alt = match.groupValues[3]
        val timeStr = match.groupValues[4]
        val timeParts = timeStr.split(" ", "-", ":")

        return DecodedLocation(
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
            altitude = alt.toDouble(),
            dateTime =
                DecodedDateTime(
                    year = timeParts[0].toInt(),
                    month = timeParts[1].toInt(),
                    day = timeParts[2].toInt(),
                    hour = timeParts[3].toInt(),
                    minute = timeParts[4].toInt(),
                    second = timeParts[5].toInt(),
                ),
        )
    }

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.formatDateTimeHex */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatDateTimeHex(bytes: ByteArray): String =
        dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.formatDateTimeHex(bytes)

    /** @see dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.formatLocationHex */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatLocationHex(bytes: ByteArray): String =
        dev.sebastiano.camerasync.vendors.ricoh.RicohProtocol.formatLocationHex(bytes)
}

/** Decoded date/time from the Ricoh protocol. */
data class DecodedDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    override fun toString(): String {
        val y = year.toString().padStart(4, '0')
        val mo = month.toString().padStart(2, '0')
        val d = day.toString().padStart(2, '0')
        val h = hour.toString().padStart(2, '0')
        val mi = minute.toString().padStart(2, '0')
        val s = second.toString().padStart(2, '0')
        return "$y-$mo-$d $h:$mi:$s"
    }
}

/** Decoded location from the Ricoh protocol. */
data class DecodedLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val dateTime: DecodedDateTime,
) {
    override fun toString(): String =
        "($latitude, $longitude), altitude: $altitude. Time: $dateTime"
}
