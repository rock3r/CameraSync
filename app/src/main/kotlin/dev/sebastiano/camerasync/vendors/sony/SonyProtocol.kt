package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Protocol implementation for Sony Alpha cameras.
 *
 * Based on the protocol documentation at:
 * https://github.com/whc2001/ILCE7M3ExternalGps/blob/main/PROTOCOL_EN.md
 *
 * Packet structure (Big Endian):
 * - Bytes 0-1: Payload length (excludes these 2 bytes)
 * - Bytes 2-4: Fixed header (0x08 0x02 0xFC)
 * - Byte 5: Timezone/DST flag (0x03 = include, 0x00 = omit)
 * - Bytes 6-10: Fixed data (0x00 0x00 0x10 0x10 0x10)
 * - Bytes 11-14: Latitude × 10,000,000 (signed int32)
 * - Bytes 15-18: Longitude × 10,000,000 (signed int32)
 * - Bytes 19-20: UTC Year
 * - Byte 21: UTC Month (1-12)
 * - Byte 22: UTC Day (1-31)
 * - Byte 23: UTC Hour (0-23)
 * - Byte 24: UTC Minute (0-59)
 * - Byte 25: UTC Second (0-59)
 * - Bytes 26-90: Padding (zeros)
 * - Bytes 91-92: Timezone offset in minutes (only if flag is 0x03)
 * - Bytes 93-94: DST offset in minutes (only if flag is 0x03)
 */
object SonyProtocol : CameraProtocol {

    /** Packet size without timezone/DST data. */
    private const val PACKET_SIZE_WITHOUT_TZ = 91

    /** Packet size with timezone/DST data. */
    private const val PACKET_SIZE_WITH_TZ = 95

    /** Fixed header bytes. */
    private val HEADER = byteArrayOf(0x08, 0x02, 0xFC.toByte())

    /** Fixed data after the timezone flag. */
    private val FIXED_DATA = byteArrayOf(0x00, 0x00, 0x10, 0x10, 0x10)

    /** Timezone/DST flag: include timezone data. */
    private const val TZ_FLAG_INCLUDE: Byte = 0x03

    /** Timezone/DST flag: omit timezone data. */
    private const val TZ_FLAG_OMIT: Byte = 0x00

    /** Coordinate scaling factor. */
    private const val COORDINATE_SCALE = 10_000_000.0

    /**
     * Encodes date/time for Sony cameras.
     *
     * Note: Sony combines location and time in the same packet. This method sends a packet with
     * zero coordinates but valid time data.
     */
    override fun encodeDateTime(dateTime: ZonedDateTime): ByteArray {
        return encodeLocationPacket(
            latitude = 0.0,
            longitude = 0.0,
            dateTime = dateTime,
            includeTimezone = true,
        )
    }

    override fun decodeDateTime(bytes: ByteArray): String {
        if (bytes.size < PACKET_SIZE_WITHOUT_TZ) {
            return "Invalid data: expected at least $PACKET_SIZE_WITHOUT_TZ bytes, got ${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Skip to date/time fields (offset 19)
        buffer.position(19)
        val year = buffer.short.toInt()
        val month = buffer.get().toInt()
        val day = buffer.get().toInt()
        val hour = buffer.get().toInt()
        val minute = buffer.get().toInt()
        val second = buffer.get().toInt()

        val dateTime = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC)
        return dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }

    override fun encodeLocation(location: GpsLocation): ByteArray {
        return encodeLocationPacket(
            latitude = location.latitude,
            longitude = location.longitude,
            dateTime = location.timestamp,
            includeTimezone = true,
        )
    }

    override fun decodeLocation(bytes: ByteArray): String {
        if (bytes.size < PACKET_SIZE_WITHOUT_TZ) {
            return "Invalid data: expected at least $PACKET_SIZE_WITHOUT_TZ bytes, got ${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Skip header (offset 11 for coordinates)
        buffer.position(11)
        val latRaw = buffer.int
        val lonRaw = buffer.int
        val latitude = latRaw / COORDINATE_SCALE
        val longitude = lonRaw / COORDINATE_SCALE

        val year = buffer.short.toInt()
        val month = buffer.get().toInt()
        val day = buffer.get().toInt()
        val hour = buffer.get().toInt()
        val minute = buffer.get().toInt()
        val second = buffer.get().toInt()

        val dateTimeStr =
            "%04d-%02d-%02d %02d:%02d:%02d UTC".format(year, month, day, hour, minute, second)

        return "Lat: $latitude, Lon: $longitude, Time: $dateTimeStr"
    }

    // Sony doesn't have a separate geo-tagging toggle characteristic.
    override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray = byteArrayOf()

    override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean = false

    override fun getPairingInitData(): ByteArray = createPairingInit()

    /**
     * Encodes a complete location packet for Sony cameras.
     *
     * @param latitude Latitude in degrees (-90 to 90)
     * @param longitude Longitude in degrees (-180 to 180)
     * @param dateTime The timestamp to encode
     * @param includeTimezone Whether to include timezone/DST offset data
     */
    fun encodeLocationPacket(
        latitude: Double,
        longitude: Double,
        dateTime: ZonedDateTime,
        includeTimezone: Boolean,
    ): ByteArray {
        val utcDateTime = dateTime.withZoneSameInstant(ZoneOffset.UTC)
        val packetSize = if (includeTimezone) PACKET_SIZE_WITH_TZ else PACKET_SIZE_WITHOUT_TZ
        val payloadSize = packetSize - 2 // Exclude the 2-byte length field

        val buffer = ByteBuffer.allocate(packetSize).order(ByteOrder.BIG_ENDIAN)

        // Payload length (2 bytes)
        buffer.putShort(payloadSize.toShort())

        // Fixed header (3 bytes)
        buffer.put(HEADER)

        // Timezone/DST flag (1 byte)
        buffer.put(if (includeTimezone) TZ_FLAG_INCLUDE else TZ_FLAG_OMIT)

        // Fixed data (5 bytes)
        buffer.put(FIXED_DATA)

        // Latitude × 10,000,000 (4 bytes)
        buffer.putInt((latitude * COORDINATE_SCALE).toInt())

        // Longitude × 10,000,000 (4 bytes)
        buffer.putInt((longitude * COORDINATE_SCALE).toInt())

        // UTC Year (2 bytes)
        buffer.putShort(utcDateTime.year.toShort())

        // UTC Month, Day, Hour, Minute, Second (1 byte each)
        buffer.put(utcDateTime.monthValue.toByte())
        buffer.put(utcDateTime.dayOfMonth.toByte())
        buffer.put(utcDateTime.hour.toByte())
        buffer.put(utcDateTime.minute.toByte())
        buffer.put(utcDateTime.second.toByte())

        // Padding (65 bytes of zeros) - bytes 26-90
        repeat(65) { buffer.put(0) }

        // Timezone and DST offsets (if included)
        if (includeTimezone) {
            val offsetSeconds = dateTime.offset.totalSeconds
            val offsetMinutes = offsetSeconds / 60

            // For simplicity, we put the full offset as timezone and 0 for DST
            // A more sophisticated implementation would separate DST from standard offset
            buffer.putShort(offsetMinutes.toShort())
            buffer.putShort(0) // DST offset - would need calendar data to compute accurately
        }

        return buffer.array()
    }

    /**
     * Parses a configuration response from DD21 to determine if timezone data is required.
     *
     * Response format: 6 bytes, byte 4 bit 2 indicates timezone requirement.
     *
     * @return true if timezone/DST data should be included in location packets
     */
    fun parseConfigRequiresTimezone(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        return (bytes[4].toInt() and 0x04) != 0
    }

    /**
     * Creates the status notification enable command.
     *
     * Write this to DD01 to enable status notifications.
     */
    fun createStatusNotifyEnable(): ByteArray = byteArrayOf(0x03, 0x01, 0x02, 0x01)

    /**
     * Creates the status notification disable command.
     *
     * Write this to DD01 to disable status notifications.
     */
    fun createStatusNotifyDisable(): ByteArray = byteArrayOf(0x03, 0x01, 0x02, 0x00)

    /**
     * Creates the pairing initialization command.
     *
     * Write this to EE01 when the camera is in pairing mode.
     */
    fun createPairingInit(): ByteArray = byteArrayOf(0x06, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00)
}
