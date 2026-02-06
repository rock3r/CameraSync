package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import okio.Buffer

/**
 * Handles encoding and decoding of data for the Ricoh camera BLE protocol.
 *
 * The protocol uses a mix of little-endian (for year) and big-endian (for doubles) byte ordering.
 */
object RicohProtocol : CameraProtocol {

    /** Size of the encoded date/time data in bytes. */
    const val DATE_TIME_SIZE = 7

    /** Size of the encoded location data in bytes. */
    const val LOCATION_SIZE = 32

    /**
     * Encodes a date/time value to the Ricoh camera format.
     *
     * Format (7 bytes):
     * - Bytes 0-1: Year (little-endian short)
     * - Byte 2: Month (1-12)
     * - Byte 3: Day (1-31)
     * - Byte 4: Hour (0-23)
     * - Byte 5: Minute (0-59)
     * - Byte 6: Second (0-59)
     */
    override fun encodeDateTime(dateTime: ZonedDateTime): ByteArray =
        Buffer()
            .writeShortLe(dateTime.year)
            .writeByte(dateTime.monthValue)
            .writeByte(dateTime.dayOfMonth)
            .writeByte(dateTime.hour)
            .writeByte(dateTime.minute)
            .writeByte(dateTime.second)
            .readByteArray()

    /**
     * Decodes a date/time value from the Ricoh camera format.
     *
     * @throws IllegalArgumentException if the byte array is not 7 bytes.
     */
    override fun decodeDateTime(bytes: ByteArray): String {
        require(bytes.size >= DATE_TIME_SIZE) {
            "DateTime data must be at least $DATE_TIME_SIZE bytes, got ${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes)

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val year = buffer.short.toInt()

        buffer.order(ByteOrder.BIG_ENDIAN)
        val month = buffer.get().toInt()
        val day = buffer.get().toInt()
        val hour = buffer.get().toInt()
        val minute = buffer.get().toInt()
        val second = buffer.get().toInt()

        val decoded = DecodedDateTime(year, month, day, hour, minute, second)
        return decoded.toString()
    }

    /**
     * Encodes a GPS location to the Ricoh camera format.
     *
     * Format (32 bytes):
     * - Bytes 0-7: Latitude (big-endian double as raw bits)
     * - Bytes 8-15: Longitude (big-endian double as raw bits)
     * - Bytes 16-23: Altitude (big-endian double as raw bits)
     * - Bytes 24-25: Year (little-endian short)
     * - Byte 26: Month (1-12)
     * - Byte 27: Day (1-31)
     * - Byte 28: Hour (0-23)
     * - Byte 29: Minute (0-59)
     * - Byte 30: Second (0-59)
     * - Byte 31: Padding (0)
     */
    override fun encodeLocation(location: GpsLocation): ByteArray =
        Buffer()
            .writeLong(location.latitude.toRawBits())
            .writeLong(location.longitude.toRawBits())
            .writeLong(location.altitude.toRawBits())
            .writeShortLe(location.timestamp.year)
            .writeByte(location.timestamp.monthValue)
            .writeByte(location.timestamp.dayOfMonth)
            .writeByte(location.timestamp.hour)
            .writeByte(location.timestamp.minute)
            .writeByte(location.timestamp.second)
            .writeByte(0) // padding
            .readByteArray()

    /**
     * Decodes a GPS location from the Ricoh camera format.
     *
     * @throws IllegalArgumentException if the byte array is not 32 bytes.
     */
    override fun decodeLocation(bytes: ByteArray): String {
        require(bytes.size >= LOCATION_SIZE) {
            "Location data must be at least $LOCATION_SIZE bytes, got ${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        val latitude = Double.fromBits(buffer.long)
        val longitude = Double.fromBits(buffer.long)
        val altitude = Double.fromBits(buffer.long)

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val year = buffer.short.toInt()

        buffer.order(ByteOrder.BIG_ENDIAN)
        val month = buffer.get().toInt()
        val day = buffer.get().toInt()
        val hour = buffer.get().toInt()
        val minute = buffer.get().toInt()
        val second = buffer.get().toInt()

        val dateTime = DecodedDateTime(year, month, day, hour, minute, second)
        val decoded =
            DecodedLocation(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                dateTime = dateTime,
            )
        return decoded.toString()
    }

    /**
     * Encodes the geo-tagging enabled/disabled state to Ricoh format.
     *
     * Format: 1 byte (0x00 = disabled, 0x01 = enabled)
     */
    override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray =
        ByteArray(1) { if (enabled) 1 else 0 }

    /** Decodes the geo-tagging enabled/disabled state from Ricoh format. */
    override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean {
        require(bytes.isNotEmpty()) { "Geo-tagging data must be at least 1 byte" }
        return bytes.first() == 1.toByte()
    }

    /**
     * Formats raw date/time bytes as a hex string for debugging.
     *
     * Format: YYYY_MM_DD_HH_mm_ss (each segment as hex)
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatDateTimeHex(bytes: ByteArray): String = buildString {
        append(bytes.sliceArray(0..1).toHexString())
        append("_")
        append(bytes[2].toHexString())
        append("_")
        append(bytes[3].toHexString())
        append("_")
        append(bytes[4].toHexString())
        append("_")
        append(bytes[5].toHexString())
        append("_")
        append(bytes[6].toHexString())
    }

    /**
     * Formats raw location bytes as a hex string for debugging.
     *
     * Format: lat_lon_alt_YYYY_MM_DD_HH_mm_ss_pad (each segment as hex)
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatLocationHex(bytes: ByteArray): String = buildString {
        append(bytes.sliceArray(0..7).toHexString())
        append("_")
        append(bytes.sliceArray(8..15).toHexString())
        append("_")
        append(bytes.sliceArray(16..23).toHexString())
        append("_")
        append(bytes.sliceArray(24..25).toHexString())
        append("_")
        append(bytes[26].toHexString())
        append("_")
        append(bytes[27].toHexString())
        append("_")
        append(bytes[28].toHexString())
        append("_")
        append(bytes[29].toHexString())
        append("_")
        append(bytes[30].toHexString())
        append("_")
        append(bytes[31].toHexString())
    }
}

/** Decoded date/time from the Ricoh protocol. */
internal data class DecodedDateTime(
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
internal data class DecodedLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val dateTime: DecodedDateTime,
) {
    override fun toString(): String =
        "($latitude, $longitude), altitude: $altitude. Time: $dateTime"
}
