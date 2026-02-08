@file:Suppress("unused")

package dev.sebastiano.camerasync.domain.model

import java.time.Duration

/** Represents the status of the camera's battery. */
data class BatteryInfo(
    val levelPercentage: Int, // 0-100
    val isCharging: Boolean = false,
    val powerSource: PowerSource = PowerSource.BATTERY,
    val position: BatteryPosition = BatteryPosition.INTERNAL,
)

enum class PowerSource {
    BATTERY,
    USB,
    AC_ADAPTER,
    UNKNOWN,
}

enum class BatteryPosition {
    INTERNAL,
    GRIP_1,
    GRIP_2,
    UNKNOWN,
}

/** Represents the status of the camera's storage media. */
data class StorageInfo(
    val slot: Int = 1,
    val isPresent: Boolean = false,
    val isWriteProtected: Boolean = false,
    val isFull: Boolean = false,
    val remainingShots: Int? = null,
    val remainingVideoDuration: Duration? = null,
    val format: String? = null, // e.g., "RAW", "JPG"
)

/** Represents the camera's high-level operation mode. */
enum class CameraMode {
    STILL_IMAGE,
    MOVIE,
    PLAYBACK,
    PC_REMOTE,
    UNKNOWN,
}

/** Represents the capture status (shutter lifecycle). */
sealed interface CaptureStatus {
    data object Idle : CaptureStatus

    data object Focusing : CaptureStatus

    data object Capturing : CaptureStatus

    data class Countdown(val secondsRemaining: Int) : CaptureStatus

    data object Processing : CaptureStatus
}

/** Represents the exposure program mode. */
enum class ExposureMode {
    PROGRAM_AUTO, // P
    APERTURE_PRIORITY, // A / Av
    SHUTTER_PRIORITY, // S / Tv
    MANUAL, // M
    BULB, // B
    TIME, // T
    BULB_TIMER, // BT
    SNAP_FOCUS_PROGRAM, // SFP (Ricoh)
    AUTO, // Full Auto
    UNKNOWN,
}

/** Represents the drive mode. */
enum class DriveMode {
    SINGLE_SHOOTING,
    CONTINUOUS_SHOOTING,
    SELF_TIMER_10S,
    SELF_TIMER_2S,
    BRACKET,
    INTERVAL,
    MULTI_EXPOSURE,
    UNKNOWN,
}

/** Represents the autofocus status. */
enum class FocusStatus {
    LOST, // Focus lost or not acquired
    SEARCHING, // AF is running
    LOCKED, // Focus locked/acquired
    MANUAL, // Manual focus mode
}

/** Represents the shutter release status. */
enum class ShutterStatus {
    READY,
    ACTIVE, // Shutter is open (e.g. long exposure)
    DISABLED, // Cannot release shutter
}

/** Represents the video recording status. */
enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    PROCESSING,
}

/** Represents a frame from the live view stream. */
data class LiveViewFrame(
    val jpegData: ByteArray,
    val focusFrame: FocusFrame? = null,
    // Add other overlay info as needed
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LiveViewFrame
        if (!jpegData.contentEquals(other.jpegData)) return false
        if (focusFrame != other.focusFrame) return false
        return true
    }

    override fun hashCode(): Int {
        var result = jpegData.contentHashCode()
        result = 31 * result + (focusFrame?.hashCode() ?: 0)
        return result
    }
}

data class FocusFrame(
    val x: Float, // Normalized 0-1
    val y: Float, // Normalized 0-1
    val width: Float, // Normalized 0-1
    val height: Float, // Normalized 0-1
    val status: FocusStatus,
)

/** Vendor-specific custom buttons. */
enum class CustomButton {
    AF_ON,
    AEL,
    C1,
    C2,
    C3,
    C4,
    USER,
    UNKNOWN,
}
