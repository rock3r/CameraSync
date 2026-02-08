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

    /** Firmware version service (same as CameraControl service). */
    object Firmware {
        val SERVICE_UUID: Uuid = Uuid.parse("9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1")
        val VERSION_CHARACTERISTIC_UUID: Uuid = Uuid.parse("b4eb8905-7411-40a6-a367-2834c2157ea7")
    }

    /** Date/time and geo-tagging (same service as CameraState). */
    object DateTime {
        val SERVICE_UUID: Uuid = Uuid.parse("4b445988-caa0-4dd3-941d-37b4f52aca86")
        val DATE_TIME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("fa46bbdd-8a8f-4796-8cf3-aa58949b130a")
        val GEO_TAGGING_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("a36afdcf-6b67-4046-9be7-28fb67dbc071")
    }

    override val firmwareServiceUuid: Uuid = Firmware.SERVICE_UUID
    override val firmwareVersionCharacteristicUuid: Uuid = Firmware.VERSION_CHARACTERISTIC_UUID
    override val dateTimeServiceUuid: Uuid = DateTime.SERVICE_UUID
    override val dateTimeCharacteristicUuid: Uuid = DateTime.DATE_TIME_CHARACTERISTIC_UUID
    override val geoTaggingCharacteristicUuid: Uuid = DateTime.GEO_TAGGING_CHARACTERISTIC_UUID

    /** Main camera control service and characteristics. */
    object CameraControl {
        val SERVICE_UUID: Uuid = Uuid.parse("9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1")
        val COMMAND_CHARACTERISTIC_UUID: Uuid = Uuid.parse("a3c51525-de3e-4777-a1c2-699e28736fcf")
        /** Battery/Info — Read, Notify. See docs/ricoh/README.md §2.1.2. */
        val BATTERY_INFO_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("fe3a32f8-a189-42de-a391-bc81ae4daa76")
    }

    /**
     * Paired device name (Camera Information Service). Used by setPairedDeviceName.
     *
     * UUID mapping verified: docs/ricoh/README.md §2.1.2 (FE3A32F8=Battery), §2.1.3 (97E34DA2=
     * bluetoothDeviceName); dumps/ricoh/RICOH_BLE_PROTOCOL.md and ida_script/addNames.py confirm.
     */
    object DeviceName {
        val SERVICE_UUID: Uuid = Uuid.parse("0f291746-0c80-4726-87a7-3c501fd3b4b6")
        /**
         * bluetoothDeviceName in CameraInformationServiceModel. See docs/ricoh/README.md §2.1.3.
         */
        val NAME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("97e34da2-2e1a-405b-b80d-f8f0aa9cc51c")
    }

    override val deviceNameServiceUuid: Uuid = DeviceName.SERVICE_UUID
    override val deviceNameCharacteristicUuid: Uuid = DeviceName.NAME_CHARACTERISTIC_UUID

    /** Camera State Notification Service and Characteristics. */
    object CameraState {
        val SERVICE_UUID: Uuid = Uuid.parse("4b445988-caa0-4dd3-941d-37b4f52aca86")

        // Operation Mode (Capture, Playback, etc.)
        val OPERATION_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("d9ae1c06-447d-4dea-8b7d-fc8b19c2cdae")

        // Capture Type (Still vs Video)
        val CAPTURE_TYPE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("3e0673e0-1c7b-4f97-8ca6-5c2c8bc56680")

        // Capture Mode (Single, Continuous, Interval, etc.)
        val CAPTURE_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("009a8e70-b306-4451-b943-7f54392eb971")

        // Exposure Mode (P, Av, Tv, M, etc.) - Primary
        val EXPOSURE_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("b5589c08-b5fd-46f5-be7d-ab1b8c074caa")

        // Storage Info List - Primary
        val STORAGE_INFO_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("e799198f-cf3f-4650-9373-b15dda1b618c")

        // Note: Drive Mode is on Command Characteristic (A3C5...) in CameraControl service.
        // Note: Battery Level is FE3A... in CameraControl service.
        // Note: Capture Status (tPa) UUID is not explicitly identified in README list 2.1.4,
        // but might be one of the other UUIDs or multiplexed.
        // For now we will rely on Operation Mode and Capture Mode.
    }

    /** Location sync service and characteristic. */
    object Location {
        val SERVICE_UUID: Uuid = Uuid.parse("84a0dd62-e8aa-4d0f-91db-819b6724c69e")
        val LOCATION_CHARACTERISTIC_UUID: Uuid = Uuid.parse("28f59d60-8b8e-4fcd-a81f-61bdb46595a9")
    }

    override val locationServiceUuid: Uuid = Location.SERVICE_UUID
    override val locationCharacteristicUuid: Uuid = Location.LOCATION_CHARACTERISTIC_UUID
}
