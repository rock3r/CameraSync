package dev.sebastiano.camerasync.vendors.sony

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comprehensive tests for [SonyGattSpec].
 *
 * These tests verify:
 * - All characteristic UUIDs match Sony's protocol documentation
 * - Service and characteristic relationships are correct
 * - Date/Time uses CC13 (not DD11) per the protocol spec
 * - Location uses DD11 for GPS data
 *
 * See docs/sony/DATETIME_GPS_SYNC.md for protocol documentation.
 */
@OptIn(ExperimentalUuidApi::class)
class SonyGattSpecTest {

    // ==================== Service UUID Tests ====================

    @Test
    fun `remote control service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
        )
    }

    @Test
    fun `location service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_SERVICE_UUID,
        )
    }

    @Test
    fun `pairing service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000EE00-EE00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.PAIRING_SERVICE_UUID,
        )
    }

    @Test
    fun `camera control service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
        )
    }

    // ==================== Location Service (DD) Characteristic Tests ====================

    @Test
    fun `location status notify characteristic UUID (DD01) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD01-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location data write characteristic UUID (DD11) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD11-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location config read characteristic UUID (DD21) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD21-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.LOCATION_CONFIG_READ_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location lock characteristic UUID (DD30) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD30-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location enable characteristic UUID (DD31) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD31-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `time correction characteristic UUID (DD32) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD32-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.TIME_CORRECTION_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `area adjustment characteristic UUID (DD33) matches documentation`() {
        assertEquals(
            Uuid.parse("0000DD33-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.AREA_ADJUSTMENT_CHARACTERISTIC_UUID,
        )
    }

    // ==================== Pairing Service (EE) Characteristic Tests ====================

    @Test
    fun `pairing characteristic UUID (EE01) uses standard Bluetooth SIG base UUID`() {
        // Note: While Sony services use a custom UUID format, the characteristics
        // within those services use the standard Bluetooth SIG base UUID format.
        assertEquals(
            Uuid.parse("0000EE01-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.PAIRING_CHARACTERISTIC_UUID,
        )
    }

    // ==================== Camera Control Service (CC) Characteristic Tests ====================

    @Test
    fun `time completion status characteristic UUID (CC09) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC09-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.TIME_COMPLETION_STATUS_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `firmware version characteristic UUID (CC0A) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC0A-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.FIRMWARE_VERSION_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `model name characteristic UUID (CC0B) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC0B-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.MODEL_NAME_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `datetime notify characteristic UUID (CC0E) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC0E-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.DATETIME_NOTIFY_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `date format characteristic UUID (CC12) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC12-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.DATE_FORMAT_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `time area setting characteristic UUID (CC13) matches documentation`() {
        assertEquals(
            Uuid.parse("0000CC13-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID,
        )
    }

    // ==================== CameraGattSpec Override Tests ====================

    @Test
    fun `scan filter uses remote control and pairing service UUIDs`() {
        assertEquals(2, SonyGattSpec.scanFilterServiceUuids.size)
        assertEquals(
            listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID, SonyGattSpec.PAIRING_SERVICE_UUID),
            SonyGattSpec.scanFilterServiceUuids,
        )
    }

    @Test
    fun `scan filter device names includes ILCE prefix`() {
        assertEquals(listOf("ILCE-"), SonyGattSpec.scanFilterDeviceNames)
    }

    @Test
    fun `dateTime uses Camera Control Service and CC13 characteristic`() {
        assertEquals(SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID, SonyGattSpec.dateTimeServiceUuid)
        assertEquals(
            SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID,
            SonyGattSpec.dateTimeCharacteristicUuid,
        )
    }

    @Test
    fun `dateTime characteristic is NOT the same as location characteristic`() {
        // Critical test: These must be different to avoid timestamp overwrite issues
        assertNotEquals(
            SonyGattSpec.dateTimeCharacteristicUuid,
            SonyGattSpec.locationCharacteristicUuid,
        )
    }

    @Test
    fun `dateTime service is NOT the same as location service`() {
        // CC service for date/time, DD service for location
        assertNotEquals(SonyGattSpec.dateTimeServiceUuid, SonyGattSpec.locationServiceUuid)
    }

    @Test
    fun `location uses Location Service and DD11 characteristic`() {
        assertEquals(SonyGattSpec.LOCATION_SERVICE_UUID, SonyGattSpec.locationServiceUuid)
        assertEquals(
            SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
            SonyGattSpec.locationCharacteristicUuid,
        )
    }

    @Test
    fun `geoTagging characteristic is null`() {
        assertNull(SonyGattSpec.geoTaggingCharacteristicUuid)
    }

    @Test
    fun `firmware uses Camera Control Service`() {
        assertEquals(SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID, SonyGattSpec.firmwareServiceUuid)
        assertEquals(
            SonyGattSpec.FIRMWARE_VERSION_CHARACTERISTIC_UUID,
            SonyGattSpec.firmwareVersionCharacteristicUuid,
        )
    }

    @Test
    fun `device name is not supported`() {
        assertNull(SonyGattSpec.deviceNameServiceUuid)
        assertNull(SonyGattSpec.deviceNameCharacteristicUuid)
    }

    @Test
    fun `pairing uses Pairing Service and EE01 characteristic`() {
        assertEquals(SonyGattSpec.PAIRING_SERVICE_UUID, SonyGattSpec.pairingServiceUuid)
        assertEquals(
            SonyGattSpec.PAIRING_CHARACTERISTIC_UUID,
            SonyGattSpec.pairingCharacteristicUuid,
        )
    }

    // ==================== UUID Format Verification Tests ====================

    @Test
    fun `all service UUIDs use Sony custom format`() {
        // Sony services use format: 8000xxxx-xxxx-FFFF-FFFF-FFFFFFFFFFFF
        val serviceUuids =
            listOf(
                SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
                SonyGattSpec.LOCATION_SERVICE_UUID,
                SonyGattSpec.PAIRING_SERVICE_UUID,
                SonyGattSpec.CAMERA_CONTROL_SERVICE_UUID,
            )

        for (uuid in serviceUuids) {
            val uuidString = uuid.toString().uppercase()
            assert(uuidString.startsWith("8000")) {
                "Service UUID should start with 8000: $uuidString"
            }
            assert(uuidString.endsWith("FFFF-FFFF-FFFFFFFFFFFF")) {
                "Service UUID should end with FFFF-FFFF-FFFFFFFFFFFF: $uuidString"
            }
        }
    }

    @Test
    fun `all characteristic UUIDs use standard Bluetooth SIG base format`() {
        // Characteristics use format: 0000xxxx-0000-1000-8000-00805f9b34fb
        val characteristicUuids =
            listOf(
                SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID,
                SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
                SonyGattSpec.LOCATION_CONFIG_READ_CHARACTERISTIC_UUID,
                SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
                SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
                SonyGattSpec.TIME_CORRECTION_CHARACTERISTIC_UUID,
                SonyGattSpec.AREA_ADJUSTMENT_CHARACTERISTIC_UUID,
                SonyGattSpec.PAIRING_CHARACTERISTIC_UUID,
                SonyGattSpec.TIME_COMPLETION_STATUS_CHARACTERISTIC_UUID,
                SonyGattSpec.FIRMWARE_VERSION_CHARACTERISTIC_UUID,
                SonyGattSpec.MODEL_NAME_CHARACTERISTIC_UUID,
                SonyGattSpec.DATETIME_NOTIFY_CHARACTERISTIC_UUID,
                SonyGattSpec.DATE_FORMAT_CHARACTERISTIC_UUID,
                SonyGattSpec.TIME_AREA_SETTING_CHARACTERISTIC_UUID,
            )

        val bluetoothSigSuffix = "0000-1000-8000-00805F9B34FB"
        for (uuid in characteristicUuids) {
            val uuidString = uuid.toString().uppercase()
            assert(uuidString.startsWith("0000")) {
                "Characteristic UUID should start with 0000: $uuidString"
            }
            assert(uuidString.endsWith(bluetoothSigSuffix)) {
                "Characteristic UUID should end with Bluetooth SIG base: $uuidString"
            }
        }
    }
}
