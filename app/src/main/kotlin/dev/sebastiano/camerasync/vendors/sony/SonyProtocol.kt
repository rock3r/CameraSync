package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Protocol implementation for Sony Alpha cameras.
 *
 * Based on reverse-engineered protocol from Sony Creators' App. See docs/sony/DATETIME_GPS_SYNC.md
 * for full protocol documentation.
 *
 * IMPORTANT: All multi-byte integers are Big-Endian (Network Byte Order).
 */
object SonyProtocol : CameraProtocol {

    /** Packet size without timezone/DST data. */
    private const val PACKET_SIZE_WITHOUT_TZ = 91

    /** Packet size with timezone/DST data. */
    private const val PACKET_SIZE_WITH_TZ = 95

    /** Fixed header bytes. */
    private val HEADER = byteArrayOf(0x08, 0x02, 0xFC.toByte())

    /**
     * Padding after the timezone flag (5 bytes).
     * - Bytes 6-7: 0x00 (padding)
     * - Bytes 8-10: 0x10 (fixed padding, decimal 16)
     */
    private val PADDING_AFTER_FLAG = byteArrayOf(0x00, 0x00, 0x10, 0x10, 0x10)

    /** Timezone/DST flag: include timezone data. */
    private const val TZ_FLAG_INCLUDE: Byte = 0x03

    /** Timezone/DST flag: omit timezone data. */
    private const val TZ_FLAG_OMIT: Byte = 0x00

    /** Coordinate scaling factor. */
    private const val COORDINATE_SCALE = 10_000_000.0

    /** Time Area Setting (CC13) packet size. */
    private const val TIME_AREA_PACKET_SIZE = 13

    /** Time Area Setting (CC13) header. */
    private val TIME_AREA_HEADER = byteArrayOf(0x0C, 0x00, 0x00)

    override fun encodeDateTime(dateTime: ZonedDateTime): ByteArray {
        val buffer = ByteBuffer.allocate(TIME_AREA_PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)

        // Header (3 bytes)
        buffer.put(TIME_AREA_HEADER)

        // Year (2 bytes, Big-Endian)
        buffer.putShort(dateTime.year.toShort())

        // Month, Day, Hour, Minute, Second (1 byte each)
        buffer.put(dateTime.monthValue.toByte())
        buffer.put(dateTime.dayOfMonth.toByte())
        buffer.put(dateTime.hour.toByte())
        buffer.put(dateTime.minute.toByte())
        buffer.put(dateTime.second.toByte())

        // DST Flag - check if the zone is in DST
        val isDst = dateTime.zone.rules.isDaylightSavings(dateTime.toInstant())
        buffer.put(if (isDst) 0x01.toByte() else 0x00.toByte())

        // Timezone offset
        val totalOffsetSeconds = dateTime.offset.totalSeconds
        val offsetHours = totalOffsetSeconds / 3600
        val offsetMinutes = kotlin.math.abs((totalOffsetSeconds % 3600) / 60)
        buffer.put(offsetHours.toByte())
        buffer.put(offsetMinutes.toByte())

        return buffer.array()
    }

    override fun decodeDateTime(bytes: ByteArray): String {
        return when {
            // CC13 Time Area format (13 bytes)
            bytes.size == TIME_AREA_PACKET_SIZE -> decodeTimeAreaPacket(bytes)
            // DD11 Location packet format (91 or 95 bytes)
            bytes.size >= PACKET_SIZE_WITHOUT_TZ -> decodeLocationDateTime(bytes)
            else ->
                "Invalid data: expected $TIME_AREA_PACKET_SIZE or $PACKET_SIZE_WITHOUT_TZ+ bytes, got ${bytes.size}"
        }
    }

    /** Decodes a Time Area Setting (CC13) packet. */
    private fun decodeTimeAreaPacket(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Skip header (3 bytes)
        buffer.position(3)
        val year = buffer.short.toInt() and 0xFFFF
        val month = buffer.get().toInt() and 0xFF
        val day = buffer.get().toInt() and 0xFF
        val hour = buffer.get().toInt() and 0xFF
        val minute = buffer.get().toInt() and 0xFF
        val second = buffer.get().toInt() and 0xFF
        val dstFlag = buffer.get().toInt() and 0xFF
        val tzHours = buffer.get().toInt() // signed
        val tzMinutes = buffer.get().toInt() and 0xFF

        val offsetSeconds = tzHours * 3600 + (if (tzHours >= 0) tzMinutes else -tzMinutes) * 60
        val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
        val dateTime = ZonedDateTime.of(year, month, day, hour, minute, second, 0, offset)
        val dstStr = if (dstFlag == 1) " (DST)" else ""
        return dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) + dstStr
    }

    /** Decodes date/time from a DD11 location packet. */
    private fun decodeLocationDateTime(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Skip to date/time fields (offset 19)
        buffer.position(19)
        val year = buffer.short.toInt() and 0xFFFF
        val month = buffer.get().toInt() and 0xFF
        val day = buffer.get().toInt() and 0xFF
        val hour = buffer.get().toInt() and 0xFF
        val minute = buffer.get().toInt() and 0xFF
        val second = buffer.get().toInt() and 0xFF

        val dateTime = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC)
        return dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }

    override fun encodeLocation(location: GpsLocation): ByteArray {
        return encodeLocationPacket(
            latitude = location.latitude,
            longitude = location.longitude,
            dateTime = location.timestamp,
            includeTimezone = true, // Default to including timezone for backward compatibility
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

        val year = buffer.short.toInt() and 0xFFFF
        val month = buffer.get().toInt() and 0xFF
        val day = buffer.get().toInt() and 0xFF
        val hour = buffer.get().toInt() and 0xFF
        val minute = buffer.get().toInt() and 0xFF
        val second = buffer.get().toInt() and 0xFF

        val dateTimeStr =
            "%04d-%02d-%02d %02d:%02d:%02d UTC".format(year, month, day, hour, minute, second)

        return "Lat: $latitude, Lon: $longitude, Time: $dateTimeStr"
    }

    override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray = byteArrayOf()

    override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean = false

    override fun getPairingInitData(): ByteArray = createPairingInit()

    /**
     * Encodes a complete location packet for Sony cameras.
     *
     * IMPORTANT: All multi-byte integers are Big-Endian (Network Byte Order).
     */
    fun encodeLocationPacket(
        latitude: Double,
        longitude: Double,
        dateTime: ZonedDateTime,
        includeTimezone: Boolean,
    ): ByteArray {
        // DD11 uses UTC time (unlike CC13 which uses local time)
        val utcDateTime = dateTime.withZoneSameInstant(ZoneOffset.UTC)

        // Get system timezone for the timezone offset fields
        val systemZone = java.time.ZoneId.systemDefault()
        val zoneRules = systemZone.rules
        val instant = dateTime.toInstant()
        val standardOffset = zoneRules.getStandardOffset(instant)
        val actualOffset = zoneRules.getOffset(instant)

        val packetSize = if (includeTimezone) PACKET_SIZE_WITH_TZ else PACKET_SIZE_WITHOUT_TZ
        val payloadSize = packetSize - 2 // Exclude the 2-byte length field

        val buffer = ByteBuffer.allocate(packetSize).order(ByteOrder.BIG_ENDIAN)

        // Payload length (2 bytes, Big-Endian)
        buffer.putShort(payloadSize.toShort())

        // Fixed header (3 bytes)
        buffer.put(HEADER)

        // Timezone/DST flag (1 byte)
        buffer.put(if (includeTimezone) TZ_FLAG_INCLUDE else TZ_FLAG_OMIT)

        // Padding (5 bytes of zeros)
        buffer.put(PADDING_AFTER_FLAG)

        // Latitude × 10,000,000 (4 bytes, Big-Endian)
        buffer.putInt((latitude * COORDINATE_SCALE).toInt())

        // Longitude × 10,000,000 (4 bytes, Big-Endian)
        buffer.putInt((longitude * COORDINATE_SCALE).toInt())

        // UTC Year (2 bytes, Big-Endian)
        buffer.putShort(utcDateTime.year.toShort())

        // UTC Month, Day, Hour, Minute, Second (1 byte each)
        buffer.put(utcDateTime.monthValue.toByte())
        buffer.put(utcDateTime.dayOfMonth.toByte())
        buffer.put(utcDateTime.hour.toByte())
        buffer.put(utcDateTime.minute.toByte())
        buffer.put(utcDateTime.second.toByte())

        // Padding (65 bytes of zeros) - bytes 26-90
        repeat(65) { buffer.put(0) }

        // Timezone and DST offsets (if included, Big-Endian)
        if (includeTimezone) {
            val dstOffsetSeconds = actualOffset.totalSeconds - standardOffset.totalSeconds
            val dstOffsetMinutes = dstOffsetSeconds / 60
            val tzOffsetMinutes = standardOffset.totalSeconds / 60

            buffer.putShort(tzOffsetMinutes.toShort())
            buffer.putShort(dstOffsetMinutes.toShort())
        }

        return buffer.array()
    }

    /** Parses a configuration response from DD21 to determine if timezone data is supported. */
    fun parseConfigRequiresTimezone(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        return (bytes[4].toInt() and 0x02) != 0
    }

    /** Creates the status notification enable command. */
    fun createStatusNotifyEnable(): ByteArray = byteArrayOf(0x03, 0x01, 0x02, 0x01)

    /** Creates the status notification disable command. */
    fun createStatusNotifyDisable(): ByteArray = byteArrayOf(0x03, 0x01, 0x02, 0x00)

    /** Creates the pairing initialization command. */
    fun createPairingInit(): ByteArray = byteArrayOf(0x06, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00)
}
