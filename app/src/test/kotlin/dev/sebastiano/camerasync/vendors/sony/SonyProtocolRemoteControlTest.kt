package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.RecordingStatus
import dev.sebastiano.camerasync.domain.model.ShutterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive tests for Sony BLE remote control: FF02 notifications, CC09 TLV, and FF01 commands.
 */
class SonyProtocolRemoteControlTest {

    // ==================== FF02 parseFf02Notification — length & prefix ====================

    @Test
    fun `parseFf02Notification returns null for empty array`() {
        assertNull(SonyProtocol.parseFf02Notification(byteArrayOf()))
    }

    @Test
    fun `parseFf02Notification returns null for single byte`() {
        assertNull(SonyProtocol.parseFf02Notification(byteArrayOf(0x02.toByte())))
    }

    @Test
    fun `parseFf02Notification returns null for two bytes`() {
        assertNull(SonyProtocol.parseFf02Notification(byteArrayOf(0x02.toByte(), 0x3F.toByte())))
    }

    @Test
    fun `parseFf02Notification returns null when first byte is 0x00`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x00.toByte(), 0x3F.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null when first byte is 0x01`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x01.toByte(), 0x3F.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null when first byte is 0x03`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x03.toByte(), 0x3F.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification accepts payload with extra bytes after third`() {
        val notif =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(
                    0x02.toByte(),
                    0x3F.toByte(),
                    0x20.toByte(),
                    0x99.toByte(),
                    0x99.toByte(),
                )
            )
        assertTrue(notif is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOCKED, (notif as SonyProtocol.Ff02Notification.Focus).status)
    }

    // ==================== FF02 Focus (0x3F) — every value ====================

    @Test
    fun `parseFf02Notification Focus value 0x00 is LOST`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0x00.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOST, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    @Test
    fun `parseFf02Notification Focus value 0x01 is LOST`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0x01.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOST, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    @Test
    fun `parseFf02Notification Focus value 0x1F is LOST`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0x1F.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOST, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    @Test
    fun `parseFf02Notification Focus value 0x20 is LOCKED`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0x20.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOCKED, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    @Test
    fun `parseFf02Notification Focus value 0x21 is LOST`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0x21.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOST, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    @Test
    fun `parseFf02Notification Focus value 0xFF is LOST`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3F.toByte(), 0xFF.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Focus)
        assertEquals(FocusStatus.LOST, (n as SonyProtocol.Ff02Notification.Focus).status)
    }

    // ==================== FF02 Shutter (0xA0) — every value ====================

    @Test
    fun `parseFf02Notification Shutter value 0x00 is READY`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA0.toByte(), 0x00.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Shutter)
        assertEquals(ShutterStatus.READY, (n as SonyProtocol.Ff02Notification.Shutter).status)
    }

    @Test
    fun `parseFf02Notification Shutter value 0x20 is ACTIVE`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA0.toByte(), 0x20.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Shutter)
        assertEquals(ShutterStatus.ACTIVE, (n as SonyProtocol.Ff02Notification.Shutter).status)
    }

    @Test
    fun `parseFf02Notification Shutter value 0x1F is READY`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA0.toByte(), 0x1F.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Shutter)
        assertEquals(ShutterStatus.READY, (n as SonyProtocol.Ff02Notification.Shutter).status)
    }

    @Test
    fun `parseFf02Notification Shutter value 0x21 is READY`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA0.toByte(), 0x21.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Shutter)
        assertEquals(ShutterStatus.READY, (n as SonyProtocol.Ff02Notification.Shutter).status)
    }

    @Test
    fun `parseFf02Notification Shutter value 0xFF is READY`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA0.toByte(), 0xFF.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Shutter)
        assertEquals(ShutterStatus.READY, (n as SonyProtocol.Ff02Notification.Shutter).status)
    }

    // ==================== FF02 Recording (0xD5) — every value ====================

    @Test
    fun `parseFf02Notification Recording value 0x00 is IDLE`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD5.toByte(), 0x00.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Recording)
        assertEquals(RecordingStatus.IDLE, (n as SonyProtocol.Ff02Notification.Recording).status)
    }

    @Test
    fun `parseFf02Notification Recording value 0x20 is RECORDING`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD5.toByte(), 0x20.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Recording)
        assertEquals(
            RecordingStatus.RECORDING,
            (n as SonyProtocol.Ff02Notification.Recording).status,
        )
    }

    @Test
    fun `parseFf02Notification Recording value 0x1F is IDLE`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD5.toByte(), 0x1F.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Recording)
        assertEquals(RecordingStatus.IDLE, (n as SonyProtocol.Ff02Notification.Recording).status)
    }

    @Test
    fun `parseFf02Notification Recording value 0x21 is IDLE`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD5.toByte(), 0x21.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Recording)
        assertEquals(RecordingStatus.IDLE, (n as SonyProtocol.Ff02Notification.Recording).status)
    }

    @Test
    fun `parseFf02Notification Recording value 0xFF is IDLE`() {
        val n =
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD5.toByte(), 0xFF.toByte())
            )
        assertTrue(n is SonyProtocol.Ff02Notification.Recording)
        assertEquals(RecordingStatus.IDLE, (n as SonyProtocol.Ff02Notification.Recording).status)
    }

    // ==================== FF02 unknown type bytes ====================

    @Test
    fun `parseFf02Notification returns null for type 0x00`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x00.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0x3E`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x3E.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0x40`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x40.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0x9F`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0x9F.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0xA1`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xA1.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0xD4`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD4.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0xD6`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xD6.toByte(), 0x20.toByte())
            )
        )
    }

    @Test
    fun `parseFf02Notification returns null for type 0xFF`() {
        assertNull(
            SonyProtocol.parseFf02Notification(
                byteArrayOf(0x02.toByte(), 0xFF.toByte(), 0x00.toByte())
            )
        )
    }

    // ==================== CC09 decodeCameraStatus — length & structure ====================

    @Test
    fun `decodeCameraStatus returns UNKNOWN for empty array`() {
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(byteArrayOf()))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN for 1 byte`() {
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(byteArrayOf(0x00)))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN for 2 bytes`() {
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(byteArrayOf(0x00, 0x08)))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN for 3 bytes`() {
        assertEquals(
            CameraMode.UNKNOWN,
            SonyProtocol.decodeCameraStatus(byteArrayOf(0x00, 0x08, 0x00)),
        )
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN for 4 bytes only header`() {
        assertEquals(
            CameraMode.UNKNOWN,
            SonyProtocol.decodeCameraStatus(byteArrayOf(0x00, 0x08, 0x00, 0x01)),
        )
    }

    @Test
    fun `decodeCameraStatus returns MOVIE when tag 0x0008 value is 1`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x01)
        assertEquals(CameraMode.MOVIE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns STILL_IMAGE when tag 0x0008 value is 0`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x00)
        assertEquals(CameraMode.STILL_IMAGE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns STILL_IMAGE when tag 0x0008 value is 2`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x02)
        assertEquals(CameraMode.STILL_IMAGE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns STILL_IMAGE when tag 0x0008 value is 0xFF`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0xFF.toByte())
        assertEquals(CameraMode.STILL_IMAGE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN when tag 0x0008 has length 0`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x00)
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN when first tag is 0x0001 not 0x0008`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x01, 0x00)
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns MOVIE when tag 0x0008 is second TLV`() {
        // First TLV: tag 0x0001 length 1 value 0; Second: tag 0x0008 length 1 value 1
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x08, 0x00, 0x01, 0x01)
        assertEquals(CameraMode.MOVIE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns STILL_IMAGE when tag 0x0008 is second TLV value 0`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x08, 0x00, 0x01, 0x00)
        assertEquals(CameraMode.STILL_IMAGE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus uses only first value byte when length is 2`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x02, 0x01, 0x00)
        assertEquals(CameraMode.MOVIE, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN when TLV length would exceed buffer`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x05, 0x01)
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
    }

    // ==================== encodeRemoteControlCommand ====================

    @Test
    fun `encodeRemoteControlCommand two-byte form for RC_SHUTTER_HALF_PRESS`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_HALF_PRESS)
        assertEquals(2, b.size)
        assertEquals(0x01, b[0].toInt() and 0xFF)
        assertEquals(SonyProtocol.RC_SHUTTER_HALF_PRESS, b[1].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand two-byte form for RC_SHUTTER_FULL_RELEASE`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_SHUTTER_FULL_RELEASE)
        assertEquals(2, b.size)
        assertEquals(0x01, b[0].toInt() and 0xFF)
        assertEquals(SonyProtocol.RC_SHUTTER_FULL_RELEASE, b[1].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand two-byte form for RC_VIDEO_REC`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_VIDEO_REC)
        assertEquals(2, b.size)
        assertEquals(0x01, b[0].toInt() and 0xFF)
        assertEquals(SonyProtocol.RC_VIDEO_REC, b[1].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand three-byte form with parameter`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_FOCUS_NEAR, 0x20)
        assertEquals(3, b.size)
        assertEquals(0x02, b[0].toInt() and 0xFF)
        assertEquals(SonyProtocol.RC_FOCUS_NEAR, b[1].toInt() and 0xFF)
        assertEquals(0x20, b[2].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand three-byte form with parameter zero`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_ZOOM_TELE, 0)
        assertEquals(3, b.size)
        assertEquals(0x02, b[0].toInt() and 0xFF)
        assertEquals(SonyProtocol.RC_ZOOM_TELE, b[1].toInt() and 0xFF)
        assertEquals(0, b[2].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand three-byte form with parameter 0x7F`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_FOCUS_FAR, 0x7F)
        assertEquals(3, b.size)
        assertEquals(0x7F, b[2].toInt() and 0xFF)
    }

    @Test
    fun `encodeRemoteControlCommand three-byte form with parameter 0xFF`() {
        val b = SonyProtocol.encodeRemoteControlCommand(SonyProtocol.RC_ZOOM_WIDE, 0xFF)
        assertEquals(3, b.size)
        assertEquals(0xFF, b[2].toInt() and 0xFF)
    }
}
