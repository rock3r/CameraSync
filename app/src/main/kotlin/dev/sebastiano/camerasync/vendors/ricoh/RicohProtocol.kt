package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.BatteryPosition
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PowerSource
import dev.sebastiano.camerasync.domain.model.StorageInfo
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

    // --- Operation Request (Shooting service 9F00F387, characteristic 559644B8) ---
    // Per dm-zharov/ricoh-gr-bluetooth-api: 2 bytes [OperationCode, Parameter].
    /** Operation Request: NOP. */
    const val OP_REQ_NOP = 0
    /** Operation Request: Start Shooting/Recording. */
    const val OP_REQ_START = 1
    /** Operation Request: Stop Shooting/Recording. */
    const val OP_REQ_STOP = 2
    /** Operation Request parameter: No AF. */
    const val OP_REQ_PARAM_NO_AF = 0
    /** Operation Request parameter: AF. */
    const val OP_REQ_PARAM_AF = 1
    /** Operation Request parameter: Green Button Function. */
    const val OP_REQ_PARAM_GREEN_BUTTON = 2

    /**
     * Encodes an Operation Request payload for the Shooting service characteristic (559644B8).
     *
     * @param operationCode One of [OP_REQ_NOP], [OP_REQ_START], [OP_REQ_STOP].
     * @param parameter One of [OP_REQ_PARAM_NO_AF], [OP_REQ_PARAM_AF], [OP_REQ_PARAM_GREEN_BUTTON].
     */
    fun encodeOperationRequest(operationCode: Int, parameter: Int): ByteArray =
        byteArrayOf(operationCode.toByte(), parameter.toByte())

    // --- Legacy: Command characteristic (A3C51525) single-byte codes ---
    // Kept for reference; remote shutter uses Operation Request (encodeOperationRequest) per
    // dm-zharov spec. Command char is still used for drive mode notifications.
    /** @suppress Legacy single-byte shutter press (use Operation Request in new code). */
    const val RC_SHUTTER_PRESS = 0x01
    /** @suppress Legacy single-byte shutter release (use Operation Request in new code). */
    const val RC_SHUTTER_RELEASE = 0x00

    /**
     * Encodes a single-byte command for the Command characteristic (A3C51525). Prefer
     * [encodeOperationRequest] for remote shutter per dm-zharov spec.
     *
     * @param code One of [RC_SHUTTER_PRESS], [RC_SHUTTER_RELEASE].
     */
    fun encodeRemoteControlCommand(code: Int): ByteArray = byteArrayOf(code.toByte())

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

    /** Decodes battery information from the 875FC41D characteristic. */
    fun decodeBatteryInfo(bytes: ByteArray): BatteryInfo {
        if (bytes.isEmpty()) {
            return BatteryInfo(
                0,
                position = BatteryPosition.UNKNOWN,
                powerSource = PowerSource.UNKNOWN,
            )
        }
        val level = (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
        val powerSource =
            when (bytes.getOrNull(1)?.toInt() ?: -1) {
                0 -> PowerSource.BATTERY
                1 -> PowerSource.AC_ADAPTER
                else -> PowerSource.UNKNOWN
            }
        return BatteryInfo(
            levelPercentage = level,
            isCharging = false, // Ricoh BLE doesn't seem to report charging status directly here
            powerSource = powerSource,
            position = BatteryPosition.INTERNAL,
        )
    }

    /**
     * Decodes storage information from the eOa notification.
     *
     * Format: List of storage entries. We usually care about the first one (internal or SD). The
     * exact binary format is complex (TLV or struct), but based on observation: It's often a status
     * byte followed by remaining shots (int).
     */
    fun decodeStorageInfo(bytes: ByteArray): StorageInfo {
        // Simplified decoding based on common patterns. Real implementation might need more reverse
        // engineering.
        // Assuming: [Status, RemainingShots (4 bytes LE)]
        if (bytes.size < 5) return StorageInfo(isPresent = false)

        // Status byte interpretation needs verification. Assuming non-zero is present/ready.
        val status = bytes[0].toInt() and 0xFF
        val isPresent = status != 0

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(1)
        val remainingShots = buffer.int

        return StorageInfo(
            slot = 1,
            isPresent = isPresent,
            remainingShots = remainingShots,
            isFull = remainingShots == 0, // heuristic
        )
    }

    /**
     * Decodes capture status from tPa notification.
     *
     * Format (dm-zharov): [CapturingStatus, CountdownStatus, ...] Capturing: 0 = Idle, 1 =
     * Capturing. Countdown: 0 = None, 1 = Countdown.
     */
    fun decodeCaptureStatus(bytes: ByteArray): CaptureStatus {
        if (bytes.size < 2) return CaptureStatus.Idle

        val capturing = bytes[0].toInt() and 0xFF
        val countdown = bytes[1].toInt() and 0xFF

        return when {
            countdown > 0 ->
                CaptureStatus.Countdown(
                    secondsRemaining = -1
                ) // Specific seconds not provided in simple status
            capturing > 0 -> CaptureStatus.Capturing
            else -> CaptureStatus.Idle
        }
    }

    /**
     * Decodes exposure mode from a single-byte characteristic (e.g. EXPOSURE_MODE_CHARACTERISTIC).
     *
     * Format: 1 byte enum (P=0, Av=1, Tv=2, M=3, B=4, BT=5, T=6, SFP=7).
     */
    fun decodeExposureMode(bytes: ByteArray): ExposureMode {
        if (bytes.isEmpty()) return ExposureMode.UNKNOWN
        return exposureModeFromByte(bytes[0].toInt() and 0xFF)
    }

    /**
     * Decodes shooting mode from uPa notification.
     *
     * Format: [ShootingMode (Still=0/Movie=1), ExposureMode]. Unknown mode bytes are mapped to
     * [CameraMode.UNKNOWN]; behavior is aligned with
     * [RicohRemoteControlDelegate.observeCameraMode].
     */
    fun decodeShootingMode(bytes: ByteArray): Pair<CameraMode, ExposureMode> {
        if (bytes.size < 2) return Pair(CameraMode.UNKNOWN, ExposureMode.UNKNOWN)

        val modeByte = bytes[0].toInt() and 0xFF
        val cameraMode =
            when (modeByte) {
                0 -> CameraMode.STILL_IMAGE
                1 -> CameraMode.MOVIE
                else -> CameraMode.UNKNOWN
            }

        val exposureMode = exposureModeFromByte(bytes[1].toInt() and 0xFF)
        return Pair(cameraMode, exposureMode)
    }

    private fun exposureModeFromByte(exposureByte: Int): ExposureMode =
        when (exposureByte) {
            0 -> ExposureMode.PROGRAM_AUTO
            1 -> ExposureMode.APERTURE_PRIORITY
            2 -> ExposureMode.SHUTTER_PRIORITY
            3 -> ExposureMode.MANUAL
            4 -> ExposureMode.BULB
            5 -> ExposureMode.BULB_TIMER
            6 -> ExposureMode.TIME
            7 -> ExposureMode.SNAP_FOCUS_PROGRAM
            else -> ExposureMode.UNKNOWN
        }

    /**
     * Decodes drive mode from DRIVE_MODE_CHARACTERISTIC (B29E6DE3) notification.
     *
     * Format: 1 byte enum (0-65 values; see dm-zharov Drive Mode list).
     */
    fun decodeDriveMode(bytes: ByteArray): DriveMode {
        if (bytes.isEmpty()) return DriveMode.UNKNOWN
        val value = bytes[0].toInt() and 0xFF

        return when (value) {
            0 -> DriveMode.SINGLE_SHOOTING
            1 -> DriveMode.SELF_TIMER_10S
            2 -> DriveMode.SELF_TIMER_2S
            3,
            in 18..32 -> DriveMode.CONTINUOUS_SHOOTING
            4,
            5,
            6,
            33,
            34,
            in 46..50,
            in 51..55 -> DriveMode.BRACKET
            7,
            8,
            9,
            35,
            36 -> DriveMode.MULTI_EXPOSURE
            10,
            11,
            12,
            13,
            14,
            15,
            37,
            38,
            39,
            40,
            in 41..45,
            in 61..65 -> DriveMode.INTERVAL
            16,
            17,
            in 56..60 -> DriveMode.SINGLE_SHOOTING
            else -> DriveMode.UNKNOWN
        }
    }

    /**
     * Formats raw date/time bytes as a hex string for debugging.
     *
     * Format: YYYY_MM_DD_HH_mm_ss (each segment as hex)
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatDateTimeHex(bytes: ByteArray): String = buildString {
        if (bytes.size >= 7) {
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
        } else {
            append(bytes.toHexString())
        }
    }

    /**
     * Formats raw location bytes as a hex string for debugging.
     *
     * Format: lat_lon_alt_YYYY_MM_DD_HH_mm_ss_pad (each segment as hex)
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun formatLocationHex(bytes: ByteArray): String = buildString {
        if (bytes.size >= 32) {
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
        } else {
            append(bytes.toHexString())
        }
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
