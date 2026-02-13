package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive tests for Ricoh BLE remote control protocol: capture status, drive mode, shooting
 * mode.
 */
class RicohProtocolRemoteControlTest {

    // ==================== decodeCaptureStatus — length ====================

    @Test
    fun `decodeCaptureStatus returns Idle for empty array`() {
        assertEquals(CaptureStatus.Idle, RicohProtocol.decodeCaptureStatus(byteArrayOf()))
    }

    @Test
    fun `decodeCaptureStatus returns Idle for single byte`() {
        assertEquals(CaptureStatus.Idle, RicohProtocol.decodeCaptureStatus(byteArrayOf(0)))
        assertEquals(CaptureStatus.Idle, RicohProtocol.decodeCaptureStatus(byteArrayOf(1)))
    }

    @Test
    fun `decodeCaptureStatus detects countdown`() {
        val s = RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 1))
        assertTrue(s is CaptureStatus.Countdown)
        assertEquals(-1, (s as CaptureStatus.Countdown).secondsRemaining)
    }

    @Test
    fun `decodeCaptureStatus countdown takes precedence over capturing`() {
        val s = RicohProtocol.decodeCaptureStatus(byteArrayOf(1, 1))
        assertTrue(s is CaptureStatus.Countdown)
    }

    @Test
    fun `decodeCaptureStatus detects capturing`() {
        assertEquals(CaptureStatus.Capturing, RicohProtocol.decodeCaptureStatus(byteArrayOf(1, 0)))
    }

    @Test
    fun `decodeCaptureStatus detects idle`() {
        assertEquals(CaptureStatus.Idle, RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 0)))
    }

    @Test
    fun `decodeCaptureStatus uses only first two bytes when more present`() {
        assertEquals(CaptureStatus.Idle, RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 0, 1, 1)))
        assertEquals(
            CaptureStatus.Countdown(-1),
            RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 1, 0, 0)),
        )
    }

    @Test
    fun `decodeCaptureStatus capturing value 2 is still Capturing`() {
        assertEquals(CaptureStatus.Capturing, RicohProtocol.decodeCaptureStatus(byteArrayOf(2, 0)))
    }

    @Test
    fun `decodeCaptureStatus countdown value 2 is still Countdown`() {
        val s = RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 2))
        assertTrue(s is CaptureStatus.Countdown)
    }

    @Test
    fun `decodeCaptureStatus treats countdown byte as unsigned so 0x80 yields Countdown not Idle`() {
        // Regression: bytes are signed; 0x80 is -128. Without "and 0xFF", countdown > 0 would be
        // false.
        val s = RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 0x80.toByte()))
        assertTrue(s is CaptureStatus.Countdown)
    }

    @Test
    fun `decodeCaptureStatus treats capturing byte as unsigned so 0x80 yields Capturing not Idle`() {
        // Regression: bytes are signed; 0x80 is -128. Without "and 0xFF", capturing > 0 would be
        // false.
        assertEquals(
            CaptureStatus.Capturing,
            RicohProtocol.decodeCaptureStatus(byteArrayOf(0x80.toByte(), 0)),
        )
    }

    @Test
    fun `decodeCaptureStatus treats high bytes 0xFF as non-zero`() {
        assertTrue(
            RicohProtocol.decodeCaptureStatus(byteArrayOf(0, 0xFF.toByte()))
                is CaptureStatus.Countdown
        )
        assertEquals(
            CaptureStatus.Capturing,
            RicohProtocol.decodeCaptureStatus(byteArrayOf(0xFF.toByte(), 0)),
        )
    }

    // ==================== decodeDriveMode — all enum values ====================

    @Test
    fun `decodeDriveMode returns UNKNOWN for empty array`() {
        assertEquals(DriveMode.UNKNOWN, RicohProtocol.decodeDriveMode(byteArrayOf()))
    }

    @Test
    fun `decodeDriveMode value 0 is SINGLE_SHOOTING`() {
        assertEquals(DriveMode.SINGLE_SHOOTING, RicohProtocol.decodeDriveMode(byteArrayOf(0)))
    }

    @Test
    fun `decodeDriveMode value 1 is SELF_TIMER_10S`() {
        assertEquals(DriveMode.SELF_TIMER_10S, RicohProtocol.decodeDriveMode(byteArrayOf(1)))
    }

    @Test
    fun `decodeDriveMode value 2 is SELF_TIMER_2S`() {
        assertEquals(DriveMode.SELF_TIMER_2S, RicohProtocol.decodeDriveMode(byteArrayOf(2)))
    }

    @Test
    fun `decodeDriveMode value 3 is CONTINUOUS_SHOOTING`() {
        assertEquals(DriveMode.CONTINUOUS_SHOOTING, RicohProtocol.decodeDriveMode(byteArrayOf(3)))
    }

    @Test
    fun `decodeDriveMode value 4 is BRACKET`() {
        assertEquals(DriveMode.BRACKET, RicohProtocol.decodeDriveMode(byteArrayOf(4)))
    }

    @Test
    fun `decodeDriveMode values 7 to 9 are MULTI_EXPOSURE`() {
        for (v in 7..9) {
            assertEquals(
                DriveMode.MULTI_EXPOSURE,
                RicohProtocol.decodeDriveMode(byteArrayOf(v.toByte())),
            )
        }
    }

    @Test
    fun `decodeDriveMode values 10 to 15 are INTERVAL`() {
        for (v in 10..15) {
            assertEquals(DriveMode.INTERVAL, RicohProtocol.decodeDriveMode(byteArrayOf(v.toByte())))
        }
    }

    @Test
    fun `decodeDriveMode value 16 maps to SINGLE_SHOOTING`() {
        assertEquals(DriveMode.SINGLE_SHOOTING, RicohProtocol.decodeDriveMode(byteArrayOf(16)))
    }

    @Test
    fun `decodeDriveMode value 0xFF is UNKNOWN`() {
        assertEquals(DriveMode.UNKNOWN, RicohProtocol.decodeDriveMode(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `decodeDriveMode value 33 is BRACKET`() {
        assertEquals(DriveMode.BRACKET, RicohProtocol.decodeDriveMode(byteArrayOf(33)))
    }

    @Test
    fun `decodeDriveMode value 35 is MULTI_EXPOSURE`() {
        assertEquals(DriveMode.MULTI_EXPOSURE, RicohProtocol.decodeDriveMode(byteArrayOf(35)))
    }

    @Test
    fun `decodeDriveMode value 37 is INTERVAL`() {
        assertEquals(DriveMode.INTERVAL, RicohProtocol.decodeDriveMode(byteArrayOf(37)))
    }

    @Test
    fun `decodeDriveMode value 56 maps to SINGLE_SHOOTING`() {
        assertEquals(DriveMode.SINGLE_SHOOTING, RicohProtocol.decodeDriveMode(byteArrayOf(56)))
    }

    @Test
    fun `decodeDriveMode uses only first byte`() {
        assertEquals(DriveMode.SINGLE_SHOOTING, RicohProtocol.decodeDriveMode(byteArrayOf(0, 1, 2)))
    }

    // ==================== decodeExposureMode (single-byte characteristic) ====================

    @Test
    fun `decodeExposureMode returns UNKNOWN for empty array`() {
        assertEquals(ExposureMode.UNKNOWN, RicohProtocol.decodeExposureMode(byteArrayOf()))
    }

    @Test
    fun `decodeExposureMode 0 is PROGRAM_AUTO`() {
        assertEquals(ExposureMode.PROGRAM_AUTO, RicohProtocol.decodeExposureMode(byteArrayOf(0)))
    }

    @Test
    fun `decodeExposureMode 1 is APERTURE_PRIORITY`() {
        assertEquals(
            ExposureMode.APERTURE_PRIORITY,
            RicohProtocol.decodeExposureMode(byteArrayOf(1)),
        )
    }

    @Test
    fun `decodeExposureMode 3 is MANUAL`() {
        assertEquals(ExposureMode.MANUAL, RicohProtocol.decodeExposureMode(byteArrayOf(3)))
    }

    @Test
    fun `decodeExposureMode 7 is SNAP_FOCUS_PROGRAM`() {
        assertEquals(
            ExposureMode.SNAP_FOCUS_PROGRAM,
            RicohProtocol.decodeExposureMode(byteArrayOf(7)),
        )
    }

    @Test
    fun `decodeExposureMode 8 is UNKNOWN`() {
        assertEquals(ExposureMode.UNKNOWN, RicohProtocol.decodeExposureMode(byteArrayOf(8)))
    }

    @Test
    fun `decodeExposureMode 0xFF is UNKNOWN`() {
        assertEquals(
            ExposureMode.UNKNOWN,
            RicohProtocol.decodeExposureMode(byteArrayOf(0xFF.toByte())),
        )
    }

    @Test
    fun `decodeExposureMode uses only first byte`() {
        assertEquals(ExposureMode.MANUAL, RicohProtocol.decodeExposureMode(byteArrayOf(3, 0, 0)))
    }

    // ==================== decodeShootingMode — length ====================

    @Test
    fun `decodeShootingMode returns UNKNOWN pair for empty array`() {
        val p = RicohProtocol.decodeShootingMode(byteArrayOf())
        assertEquals(CameraMode.UNKNOWN, p.first)
        assertEquals(ExposureMode.UNKNOWN, p.second)
    }

    @Test
    fun `decodeShootingMode returns UNKNOWN pair for single byte`() {
        val p = RicohProtocol.decodeShootingMode(byteArrayOf(0))
        assertEquals(CameraMode.UNKNOWN, p.first)
        assertEquals(ExposureMode.UNKNOWN, p.second)
    }

    @Test
    fun `decodeShootingMode Still and P`() {
        val (mode, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 0))
        assertEquals(CameraMode.STILL_IMAGE, mode)
        assertEquals(ExposureMode.PROGRAM_AUTO, exp)
    }

    @Test
    fun `decodeShootingMode Movie and M`() {
        val (mode, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(1, 3))
        assertEquals(CameraMode.MOVIE, mode)
        assertEquals(ExposureMode.MANUAL, exp)
    }

    @Test
    fun `decodeShootingMode mode byte 0 is STILL_IMAGE`() {
        val (mode, _) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 0))
        assertEquals(CameraMode.STILL_IMAGE, mode)
    }

    @Test
    fun `decodeShootingMode mode byte 1 is MOVIE`() {
        val (mode, _) = RicohProtocol.decodeShootingMode(byteArrayOf(1, 0))
        assertEquals(CameraMode.MOVIE, mode)
    }

    @Test
    fun `decodeShootingMode mode byte 2 is UNKNOWN`() {
        val (mode, _) = RicohProtocol.decodeShootingMode(byteArrayOf(2, 0))
        assertEquals(CameraMode.UNKNOWN, mode)
    }

    @Test
    fun `decodeShootingMode mode byte 0xFF is UNKNOWN`() {
        val (mode, _) = RicohProtocol.decodeShootingMode(byteArrayOf(0xFF.toByte(), 0))
        assertEquals(CameraMode.UNKNOWN, mode)
    }

    @Test
    fun `decodeShootingMode exposure 0 is PROGRAM_AUTO`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 0))
        assertEquals(ExposureMode.PROGRAM_AUTO, exp)
    }

    @Test
    fun `decodeShootingMode exposure 1 is APERTURE_PRIORITY`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 1))
        assertEquals(ExposureMode.APERTURE_PRIORITY, exp)
    }

    @Test
    fun `decodeShootingMode exposure 2 is SHUTTER_PRIORITY`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 2))
        assertEquals(ExposureMode.SHUTTER_PRIORITY, exp)
    }

    @Test
    fun `decodeShootingMode exposure 3 is MANUAL`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 3))
        assertEquals(ExposureMode.MANUAL, exp)
    }

    @Test
    fun `decodeShootingMode exposure 4 is BULB`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 4))
        assertEquals(ExposureMode.BULB, exp)
    }

    @Test
    fun `decodeShootingMode exposure 5 is BULB_TIMER`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 5))
        assertEquals(ExposureMode.BULB_TIMER, exp)
    }

    @Test
    fun `decodeShootingMode exposure 6 is TIME`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 6))
        assertEquals(ExposureMode.TIME, exp)
    }

    @Test
    fun `decodeShootingMode exposure 7 is SNAP_FOCUS_PROGRAM`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 7))
        assertEquals(ExposureMode.SNAP_FOCUS_PROGRAM, exp)
    }

    @Test
    fun `decodeShootingMode exposure 8 is UNKNOWN`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 8))
        assertEquals(ExposureMode.UNKNOWN, exp)
    }

    @Test
    fun `decodeShootingMode exposure 0xFF is UNKNOWN`() {
        val (_, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(0, 0xFF.toByte()))
        assertEquals(ExposureMode.UNKNOWN, exp)
    }

    @Test
    fun `decodeShootingMode uses only first two bytes`() {
        val (mode, exp) = RicohProtocol.decodeShootingMode(byteArrayOf(1, 3, 0, 0))
        assertEquals(CameraMode.MOVIE, mode)
        assertEquals(ExposureMode.MANUAL, exp)
    }

    // ==================== encodeOperationRequest ====================

    @Test
    fun `encodeOperationRequest Start AF yields 0x01 0x01`() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x01),
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_START,
                RicohProtocol.OP_REQ_PARAM_AF,
            ),
        )
    }

    @Test
    fun `encodeOperationRequest Stop NoAF yields 0x02 0x00`() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x00),
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_STOP,
                RicohProtocol.OP_REQ_PARAM_NO_AF,
            ),
        )
    }

    @Test
    fun `encodeOperationRequest NOP GreenButton yields 0x00 0x02`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x02),
            RicohProtocol.encodeOperationRequest(
                RicohProtocol.OP_REQ_NOP,
                RicohProtocol.OP_REQ_PARAM_GREEN_BUTTON,
            ),
        )
    }

    // ==================== encodeRemoteControlCommand (legacy) ====================

    @Test
    fun `encodeRemoteControlCommand RC_SHUTTER_PRESS yields 0x01`() {
        assertArrayEquals(
            byteArrayOf(0x01),
            RicohProtocol.encodeRemoteControlCommand(RicohProtocol.RC_SHUTTER_PRESS),
        )
    }

    @Test
    fun `encodeRemoteControlCommand RC_SHUTTER_RELEASE yields 0x00`() {
        assertArrayEquals(
            byteArrayOf(0x00),
            RicohProtocol.encodeRemoteControlCommand(RicohProtocol.RC_SHUTTER_RELEASE),
        )
    }
}
