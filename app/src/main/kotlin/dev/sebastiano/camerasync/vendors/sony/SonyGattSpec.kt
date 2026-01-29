package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GATT specification for Sony Alpha cameras.
 *
 * Based on the DI Remote Control protocol documentation. See:
 * https://gethypoxic.com/blogs/technical/sony-camera-ble-control-protocol-di-remote-control See:
 * https://github.com/whc2001/ILCE7M3ExternalGps/blob/main/PROTOCOL_EN.md
 */
@OptIn(ExperimentalUuidApi::class)
object SonyGattSpec : CameraGattSpec {

    /** Remote Control Service UUID - used for scanning and filtering Sony camera advertisements. */
    val REMOTE_CONTROL_SERVICE_UUID: Uuid = Uuid.parse("8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF")

    /** Location Service UUID - used for GPS and time synchronization. */
    val LOCATION_SERVICE_UUID: Uuid = Uuid.parse("8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF")

    /** Location Status Notify Characteristic (DD01) - subscribe for status notifications. */
    val LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD01-0000-1000-8000-00805f9b34fb")

    /** Location Data Write Characteristic (DD11) - write location and time data here. */
    val LOCATION_DATA_WRITE_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD11-0000-1000-8000-00805f9b34fb")

    /**
     * Configuration Read Characteristic (DD21) - read to check if timezone/DST data is required.
     */
    val LOCATION_CONFIG_READ_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD21-0000-1000-8000-00805f9b34fb")

    /** Lock Location Endpoint (DD30) - firmware 3.02+ only. */
    val LOCATION_LOCK_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000DD30-0000-1000-8000-00805f9b34fb")

    /** Enable Location Update (DD31) - firmware 3.02+ only. */
    val LOCATION_ENABLE_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000DD31-0000-1000-8000-00805f9b34fb")

    /** Pairing Service UUID. */
    val PAIRING_SERVICE_UUID: Uuid = Uuid.parse("8000EE00-EE00-FFFF-FFFF-FFFFFFFFFFFF")

    /**
     * Pairing Characteristic (EE01) - write pairing initialization data.
     *
     * Note: While Sony services use a custom UUID format (8000xxxx-xxxx-FFFF-FFFF-FFFFFFFFFFFF),
     * the characteristics within those services use the standard Bluetooth SIG base UUID format.
     */
    val PAIRING_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000EE01-0000-1000-8000-00805f9b34fb")

    /** Camera Control Service UUID - used for firmware version and model name. */
    val CAMERA_CONTROL_SERVICE_UUID: Uuid = Uuid.parse("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF")

    /** Firmware version characteristic (CC0A) - US-ASCII string. */
    val FIRMWARE_VERSION_CHARACTERISTIC_UUID: Uuid =
        Uuid.parse("0000CC0A-0000-1000-8000-00805f9b34fb")

    /** Model name characteristic (CC0B) - US-ASCII string. */
    val MODEL_NAME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("0000CC0B-0000-1000-8000-00805f9b34fb")

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

    /** Sony uses the Location Service for date/time sync (combined with location). */
    override val dateTimeServiceUuid: Uuid = LOCATION_SERVICE_UUID
    override val dateTimeCharacteristicUuid: Uuid = LOCATION_DATA_WRITE_CHARACTERISTIC_UUID

    /** Sony doesn't have a separate geo-tagging toggle characteristic. */
    override val geoTaggingCharacteristicUuid: Uuid? = null

    override val locationServiceUuid: Uuid = LOCATION_SERVICE_UUID
    override val locationCharacteristicUuid: Uuid = LOCATION_DATA_WRITE_CHARACTERISTIC_UUID

    override val pairingServiceUuid: Uuid = PAIRING_SERVICE_UUID
    override val pairingCharacteristicUuid: Uuid = PAIRING_CHARACTERISTIC_UUID
}
