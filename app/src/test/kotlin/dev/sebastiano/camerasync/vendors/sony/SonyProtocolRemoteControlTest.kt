package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.model.BatteryPosition
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.PowerSource
import dev.sebastiano.camerasync.domain.model.RecordingStatus
import dev.sebastiano.camerasync.domain.model.ShutterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `decodeCameraStatus value 0 returns UNKNOWN - tag 0x0008 is recording state not mode`() {
        // Regression: tag 0x0008 = Movie Recording (1=Recording, 0=Not Recording). Value 0 means
        // not recording; camera could be in still mode OR in movie mode but idle. Conflating 0 with
        // STILL_IMAGE misreports movie-mode-idle as still. Must return UNKNOWN.
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x00)
        assertNotEquals(CameraMode.STILL_IMAGE, SonyProtocol.decodeCameraStatus(bytes))
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN when tag 0x0008 value is 2`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0x02)
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
    }

    @Test
    fun `decodeCameraStatus returns UNKNOWN when tag 0x0008 value is 0xFF`() {
        val bytes = byteArrayOf(0x00, 0x08, 0x00, 0x01, 0xFF.toByte())
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
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
    fun `decodeCameraStatus returns UNKNOWN when tag 0x0008 is second TLV value 0`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x08, 0x00, 0x01, 0x00)
        assertEquals(CameraMode.UNKNOWN, SonyProtocol.decodeCameraStatus(bytes))
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

    // ==================== decodeBatteryInfo ====================

    @Test
    fun `decodeBatteryInfo decodes correct percentage and position`() {
        val data = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 85, 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(85, info.levelPercentage)
        assertEquals(BatteryPosition.INTERNAL, info.position)
        assertEquals(PowerSource.BATTERY, info.powerSource)
        assertFalse(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo detects USB power`() {
        val data = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 50, 3)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(50, info.levelPercentage)
        assertEquals(PowerSource.USB, info.powerSource)
        assertTrue(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo position byte 0x02 yields GRIP_1`() {
        val data = byteArrayOf(1, 1, 0x02, 0, 0, 0, 0, 50, 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(BatteryPosition.GRIP_1, info.position)
    }

    @Test
    fun `decodeBatteryInfo position byte 0x03 yields GRIP_2`() {
        val data = byteArrayOf(1, 1, 0x03, 0, 0, 0, 0, 50, 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(BatteryPosition.GRIP_2, info.position)
    }

    @Test
    fun `decodeBatteryInfo position byte with high bit set yields UNKNOWN (sign extension regression)`() {
        val data = byteArrayOf(1, 1, 0x80.toByte(), 0, 0, 0, 0, 50, 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(BatteryPosition.UNKNOWN, info.position)
    }

    @Test
    fun `decodeBatteryInfo power source byte with high bit set yields BATTERY (sign extension regression)`() {
        val data = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 50, 0x80.toByte())
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(PowerSource.BATTERY, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo power source 0x03 as byte yields USB (unsigned match regression)`() {
        val data = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 50, 0x03)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(PowerSource.USB, info.powerSource)
    }

    @Test
    fun `decodeBatteryInfo two packs uses power source at index 16 not 8`() {
        // 17 bytes: pack1 (0-7), pack2 (8-15), power at 16. Byte at 8 is second pack's Enable.
        // Put 0x03 at index 8 (would be misread as USB if offset were wrong) and 0 at index 16.
        val data =
            byteArrayOf(
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                50, // pack 1: internal, 50%
                0x03,
                1,
                0x02,
                0,
                0,
                0,
                0,
                75, // pack 2: first byte 0x03 (Enable), grip, 75%
                0, // power source = BATTERY
            )
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(50, info.levelPercentage)
        assertEquals(BatteryPosition.INTERNAL, info.position)
        assertEquals(PowerSource.BATTERY, info.powerSource)
        assertFalse(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo two packs with USB power at index 16`() {
        val data =
            byteArrayOf(
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                50, // pack 1
                1,
                1,
                0x02,
                0,
                0,
                0,
                0,
                75, // pack 2
                0x03, // power source = USB
            )
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(PowerSource.USB, info.powerSource)
        assertTrue(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo single pack 8 bytes has no power source`() {
        val data = byteArrayOf(1, 1, 1, 0, 0, 0, 0, 85) // exactly one pack, no power byte
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(85, info.levelPercentage)
        assertEquals(PowerSource.UNKNOWN, info.powerSource)
        assertFalse(info.isCharging)
    }

    @Test
    fun `decodeBatteryInfo clamps negative percentage to 0`() {
        // Create a byte array where the 4-byte big-endian int at offset 4 is negative
        // Using 0xFF, 0xFF, 0xFF, 0xFF = -1 as a signed int
        val data =
            byteArrayOf(1, 1, 1, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        assertEquals(0, info.levelPercentage)
    }

    @Test
    fun `decodeBatteryInfo clamps percentage exceeding 100 to 100`() {
        // Create a byte array where the 4-byte big-endian int at offset 4 is > 100
        // Using 0x00, 0x00, 0x00, 0x65 = 101 in decimal
        // Big-endian: bytes at offset 4-7 form the int value
        val data = byteArrayOf(1, 1, 1, 0, 0x00, 0x00, 0x00, 0x65, 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        // 0x00000065 = 101, which should be clamped to 100
        assertEquals(100, info.levelPercentage)
    }

    @Test
    fun `decodeBatteryInfo preserves valid boundary values 0 and 100`() {
        // Test that 0 is not clamped
        val dataZero = byteArrayOf(1, 1, 1, 0, 0x00, 0x00, 0x00, 0x00, 0)
        val infoZero = SonyProtocol.decodeBatteryInfo(dataZero)
        assertEquals(0, infoZero.levelPercentage)

        // Test that 100 is not clamped
        val dataHundred = byteArrayOf(1, 1, 1, 0, 0x00, 0x00, 0x00, 0x64, 0)
        val infoHundred = SonyProtocol.decodeBatteryInfo(dataHundred)
        assertEquals(100, infoHundred.levelPercentage)
    }

    @Test
    fun `decodeBatteryInfo clamps very large values to 100`() {
        // Test a very large value (255) is clamped to 100
        val data = byteArrayOf(1, 1, 1, 0, 0x00, 0x00, 0x00, 0xFF.toByte(), 0)
        val info = SonyProtocol.decodeBatteryInfo(data)
        // 0x000000FF = 255, which should be clamped to 100
        assertEquals(100, info.levelPercentage)
    }

    // ==================== decodeStorageInfo ====================

    @Test
    fun `decodeStorageInfo decodes presence and shots`() {
        val data = byteArrayOf(1, 0, 0, 0, 0x64, 0, 0, 0, 0)
        val info = SonyProtocol.decodeStorageInfo(data)
        assertTrue(info.isPresent)
        assertEquals(100, info.remainingShots)
        assertFalse(info.isFull)
    }

    @Test
    fun `decodeStorageInfo status 0 reports no media`() {
        val data = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)
        val info = SonyProtocol.decodeStorageInfo(data)
        assertFalse(info.isPresent)
        assertEquals(0, info.remainingShots)
        assertTrue(info.isFull)
    }

    @Test
    fun `decodeStorageInfo status 0x80 reports media present unsigned byte regression`() {
        val data = byteArrayOf(0x80.toByte(), 0, 0, 0, 50, 0, 0, 0, 0)
        val info = SonyProtocol.decodeStorageInfo(data)
        assertTrue(info.isPresent)
        assertEquals(50, info.remainingShots)
    }

    @Test
    fun `decodeStorageInfo status 0xFF reports media present unsigned byte regression`() {
        val data = byteArrayOf(0xFF.toByte(), 0, 0, 0, 10, 0, 0, 0, 0)
        val info = SonyProtocol.decodeStorageInfo(data)
        assertTrue(info.isPresent)
        assertEquals(10, info.remainingShots)
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
