package dev.sebastiano.camerasync.vendors.ricoh

import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GATT specification for Ricoh cameras.
 *
 * Contains all known service and characteristic UUIDs used for communication with Ricoh cameras
 * over Bluetooth Low Energy.
 *
 * This implementation is specific to Ricoh cameras (tested with GR IIIx).
 */
@OptIn(ExperimentalUuidApi::class)
object RicohGattSpec : CameraGattSpec {

    /** Service UUID used for scanning and filtering Ricoh camera advertisements. */
    val SCAN_FILTER_SERVICE_UUID: Uuid = Uuid.parse("84A0DD62-E8AA-4D0F-91DB-819B6724C69E")

    override val scanFilterServiceUuids: List<Uuid> = listOf(SCAN_FILTER_SERVICE_UUID)

    override val scanFilterDeviceNames: List<String> = listOf("GR", "RICOH")

    /** Firmware version service and characteristic. */
    object Firmware {
        val SERVICE_UUID: Uuid = Uuid.parse("9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1")
        val VERSION_CHARACTERISTIC_UUID: Uuid = Uuid.parse("b4eb8905-7411-40a6-a367-2834c2157ea7")
    }

    override val firmwareServiceUuid: Uuid = Firmware.SERVICE_UUID
    override val firmwareVersionCharacteristicUuid: Uuid = Firmware.VERSION_CHARACTERISTIC_UUID

    /** Paired device name service and characteristic. */
    object DeviceName {
        val SERVICE_UUID: Uuid = Uuid.parse("0f291746-0c80-4726-87a7-3c501fd3b4b6")
        val NAME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("fe3a32f8-a189-42de-a391-bc81ae4daa76")
    }

    override val deviceNameServiceUuid: Uuid = DeviceName.SERVICE_UUID
    override val deviceNameCharacteristicUuid: Uuid = DeviceName.NAME_CHARACTERISTIC_UUID

    /** Date/time and geo-tagging service and characteristics. */
    object DateTime {
        val SERVICE_UUID: Uuid = Uuid.parse("4b445988-caa0-4dd3-941d-37b4f52aca86")
        val DATE_TIME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("fa46bbdd-8a8f-4796-8cf3-aa58949b130a")
        val GEO_TAGGING_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("a36afdcf-6b67-4046-9be7-28fb67dbc071")
    }

    override val dateTimeServiceUuid: Uuid = DateTime.SERVICE_UUID
    override val dateTimeCharacteristicUuid: Uuid = DateTime.DATE_TIME_CHARACTERISTIC_UUID
    override val geoTaggingCharacteristicUuid: Uuid = DateTime.GEO_TAGGING_CHARACTERISTIC_UUID

    /** Location sync service and characteristic. */
    object Location {
        val SERVICE_UUID: Uuid = Uuid.parse("84a0dd62-e8aa-4d0f-91db-819b6724c69e")
        val LOCATION_CHARACTERISTIC_UUID: Uuid = Uuid.parse("28f59d60-8b8e-4fcd-a81f-61bdb46595a9")
    }

    override val locationServiceUuid: Uuid = Location.SERVICE_UUID
    override val locationCharacteristicUuid: Uuid = Location.LOCATION_CHARACTERISTIC_UUID

    // Standard Device Information Service (0x180A)
    // Model Number String (0x2A24)
    override val modelNameServiceUuid: Uuid = Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb")
    override val modelNameCharacteristicUuid: Uuid =
        Uuid.parse("00002a24-0000-1000-8000-00805f9b34fb")
}
