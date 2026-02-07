package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GATT specification for Sony Alpha cameras.
 *
 * Based on reverse-engineered protocol from Sony Creators' App. See docs/sony/DATETIME_GPS_SYNC.md
 * for full protocol documentation.
 *
 * Note: While Sony services use a custom UUID format (8000xxxx-xxxx-FFFF-FFFF-FFFFFFFFFFFF), the
 * characteristics within those services use the standard Bluetooth SIG base UUID format.
 */
@OptIn(ExperimentalUuidApi::class)
object SonyGattSpec : CameraGattSpec {

    /** Remote Control Service UUID - used for scanning and filtering Sony camera advertisements. */
    val REMOTE_CONTROL_SERVICE_UUID: Uuid = Uuid.parse("8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF")

    /** Remote Control Command Characteristic (FF01) - write remote control codes. */
    val REMOTE_COMMAND_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000FF01-0000-1000-8000-00805f9b34fb")

    /** Remote Control Status Characteristic (FF02) - notify for status updates. */
    val REMOTE_STATUS_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000FF02-0000-1000-8000-00805f9b34fb")

    // ==================== Location Service (DD) ====================
    /** Location Service UUID - used for GPS synchronization. */
    val LOCATION_SERVICE_UUID: Uuid = Uuid.parse("8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF")

    /**
     * Location Status Notify Characteristic (DD01) - subscribe for transfer status notifications.
     */
    val LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD01-0000-1000-8000-00805f9b34fb")

    /** Location Data Write Characteristic (DD11) - write GPS location and timestamp data. */
    val LOCATION_DATA_WRITE_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD11-0000-1000-8000-00805f9b34fb")

    /**
     * Capability Info Characteristic (DD21) - read camera capabilities and timezone requirements.
     */
    val LOCATION_CONFIG_READ_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD21-0000-1000-8000-00805f9b34fb")

    /**
     * Lock Control Characteristic (DD30) - locks/unlocks location transfer session (1=Lock,
     * 0=Unlock).
     */
    val LOCATION_LOCK_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000DD30-0000-1000-8000-00805f9b34fb")

    /**
     * Transfer Enable Characteristic (DD31) - enables/disables location transfer (1=Enable,
     * 0=Disable).
     */
    val LOCATION_ENABLE_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD31-0000-1000-8000-00805f9b34fb")

    /** Time Correction Characteristic (DD32) - read for time correction data. */
    val TIME_CORRECTION_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD32-0000-1000-8000-00805f9b34fb")

    /** Area Adjustment Characteristic (DD33) - read for area adjustment data. */
    val AREA_ADJUSTMENT_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD33-0000-1000-8000-00805f9b34fb")

    // ==================== Pairing Service (EE) ====================
    /** Pairing Service UUID. */
    val PAIRING_SERVICE_UUID: Uuid = Uuid.parse("8000EE00-EE00-FFFF-FFFF-FFFFFFFFFFFF")

    /** Pairing Characteristic (EE01) - write pairing initialization data. */
    val PAIRING_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000EE01-0000-1000-8000-00805f9b34fb")

    // ==================== Camera Control Service (CC) ====================
    /** Camera Control Service UUID - used for date/time, firmware version, and model name. */
    val CAMERA_CONTROL_SERVICE_UUID: Uuid = Uuid.parse("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF")

    /**
     * Camera Status / Time Completion Characteristic (CC09) - dual-purpose; Read, Notify.
     * 1. Time setting completion: tag 0x0005 (1=Done, 0=Not Done). Used by date/time sync to check
     *    if time setting is done.
     * 2. Camera status TLV: tag 0x0008 = Movie Recording state (1=Recording, 0=Not Recording). This
     *    is recording start/stop, not camera mode. Only value 1 implies MOVIE; value 0 cannot
     *    distinguish still vs movie idle.
     *
     * Remote control derives MOVIE mode only when 0x0008=1. Payloads that are only time-completion
     * (e.g. after a sync) do not contain 0x0008 and must be ignored for mode observation.
     */
    val TIME_COMPLETION_STATUS_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000CC09-0000-1000-8000-00805f9b34fb")

    /** Firmware version characteristic (CC0A) - US-ASCII string. */
    val FIRMWARE_VERSION_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000CC0A-0000-1000-8000-00805f9b34fb")

    /** Model name characteristic (CC0B) - US-ASCII string. */
    val MODEL_NAME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000CC0B-0000-1000-8000-00805f9b34fb")

    /** Notification Characteristic (CC0E) - subscribe for date/time setting confirmation. */
    val DATETIME_NOTIFY_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000CC0E-0000-1000-8000-00805f9b34fb")

    /** Date Format Setting Characteristic (CC12) - sets date display format (YMD/DMY/etc). */
    val DATE_FORMAT_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000CC12-0000-1000-8000-00805f9b34fb")

    /**
     * Time Area Setting Characteristic (CC13) - sets current time and timezone. This is the proper
     * characteristic for date/time synchronization (13-byte payload).
     */
    val TIME_AREA_SETTING_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000CC13-0000-1000-8000-00805f9b34fb")

    /** Battery Info Characteristic (CC10) - Notify/Read. */
    val BATTERY_INFO_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000CC10-0000-1000-8000-00805f9b34fb")

    /** Storage Info Characteristic (CC0F) - Notify/Read. */
    val STORAGE_INFO_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000CC0F-0000-1000-8000-00805f9b34fb")

    // ==================== Overrides ====================
    override val scanFilterServiceUuids: List<Uuid> =
        listOf(REMOTE_CONTROL_SERVICE_UUID, PAIRING_SERVICE_UUID)

    /** Device name prefixes for Sony Alpha cameras (ILCE = Interchangeable Lens Camera E-mount). */
    override val scanFilterDeviceNames: List<String> = listOf("ILCE-")

    /** Camera Control Service - used for firmware version reading. */
    override val firmwareServiceUuid: Uuid = CAMERA_CONTROL_SERVICE_UUID
    override val firmwareVersionCharacteristicUuid: Uuid = FIRMWARE_VERSION_CHARACTERISTIC_UUID

    /** Standard Generic Access Service (not writable for device name on Sony). */
    override val deviceNameServiceUuid: Uuid? = null
    override val deviceNameCharacteristicUuid: Uuid? = null

    /**
     * Sony uses the Camera Control Service for dedicated date/time sync via CC13. Note: Location
     * packets (DD11) also contain timestamp data, but CC13 is the proper characteristic for setting
     * just date/time without location.
     */
    override val dateTimeServiceUuid: Uuid = CAMERA_CONTROL_SERVICE_UUID
    override val dateTimeCharacteristicUuid: Uuid = TIME_AREA_SETTING_CHARACTERISTIC_UUID

    /** Sony doesn't have a separate geo-tagging toggle characteristic. */
    override val geoTaggingCharacteristicUuid: Uuid? = null

    override val locationServiceUuid: Uuid = LOCATION_SERVICE_UUID
    override val locationCharacteristicUuid: Uuid = LOCATION_DATA_WRITE_CHARACTERISTIC_UUID

    override val pairingServiceUuid: Uuid = PAIRING_SERVICE_UUID
    override val pairingCharacteristicUuid: Uuid = PAIRING_CHARACTERISTIC_UUID
}
