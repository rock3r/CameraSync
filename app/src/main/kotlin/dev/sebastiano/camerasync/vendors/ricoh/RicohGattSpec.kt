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

    /** Manufacturer ID for Ricoh advertisement data (Bluetooth SIG Company ID 0x065F). */
    const val RICOH_MANUFACTURER_ID: Int = 0x065F

    /** Manufacturer data prefix for Ricoh cameras (0xDA). */
    private val MANUFACTURER_DATA_PREFIX: ByteArray = byteArrayOf(0xDA.toByte())

    /** Mask for matching the manufacturer data prefix. */
    private val MANUFACTURER_DATA_MASK: ByteArray = byteArrayOf(0xFF.toByte())

    override val scanFilterServiceUuids: List<Uuid> = emptyList()

    override val scanFilterDeviceNames: List<String> = listOf("GR", "RICOH")

    override val scanFilterManufacturerData: List<CameraGattSpec.ManufacturerDataFilter> =
        listOf(
            CameraGattSpec.ManufacturerDataFilter(
                manufacturerId = RICOH_MANUFACTURER_ID,
                data = MANUFACTURER_DATA_PREFIX,
                mask = MANUFACTURER_DATA_MASK,
            )
        )

    /** Firmware version service (Camera Information Service). */
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

    /**
     * Shooting service (dm-zharov Operation Request). Service 9F00F387; remote shutter uses
     * OPERATION_REQUEST_CHARACTERISTIC_UUID (559644B8).
     */
    object Shooting {
        val SERVICE_UUID: Uuid = Uuid.parse("9f00f387-8345-4bbc-8b92-b87b52e3091a")
        /** Shooting Mode (P/Av/Tv/etc). */
        val SHOOTING_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("a3c51525-de3e-4777-a1c2-699e28736fcf")
        /** Capture Mode (still/movie). */
        val CAPTURE_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("78009238-ac3d-4370-9b6f-c9ce2f4e3ca8")
        /** Drive Mode (0-65 enum). */
        val DRIVE_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("b29e6de3-1aec-48c1-9d05-02cea57ce664")
        /** Capture Status. */
        val CAPTURE_STATUS_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("b5589c08-b5fd-46f5-be7d-ab1b8c074caa")
        /** Operation Request — Write. 2 bytes: [OperationCode, Parameter]. See RicohProtocol. */
        val OPERATION_REQUEST_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("559644b8-e0bc-4011-929b-5cf9199851e7")
    }

    /** WLAN Control (dm-zharov). Network Type 0=OFF, 1=AP mode. */
    object WlanControl {
        val SERVICE_UUID: Uuid = Uuid.parse("f37f568f-9071-445d-a938-5441f2e82399")
        val NETWORK_TYPE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("9111cdd0-9f01-45c4-a2d4-e09e8fb0424d")
    }

    /** Paired device name (Bluetooth Control Command service). Used by setPairedDeviceName. */
    object DeviceName {
        val SERVICE_UUID: Uuid = Uuid.parse("0f291746-0c80-4726-87a7-3c501fd3b4b6")
        /** Paired device name (utf8). */
        val NAME_CHARACTERISTIC_UUID: Uuid = Uuid.parse("fe3a32f8-a189-42de-a391-bc81ae4daa76")
    }

    override val deviceNameServiceUuid: Uuid = DeviceName.SERVICE_UUID
    override val deviceNameCharacteristicUuid: Uuid = DeviceName.NAME_CHARACTERISTIC_UUID

    /** Camera Service and characteristics (state, power, storage, etc.). */
    object CameraState {
        val SERVICE_UUID: Uuid = Uuid.parse("4b445988-caa0-4dd3-941d-37b4f52aca86")

        // Operation Mode (Capture, Playback, etc.)
        val OPERATION_MODE_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("1452335a-ec7f-4877-b8ab-0f72e18bb295")

        // Storage Info List - Primary
        val STORAGE_INFO_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("a0c10148-8865-4470-9631-8f36d79a41a5")

        /** Battery Level + Power Source. */
        val BATTERY_LEVEL_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("875fc41d-4980-434c-a653-fd4a4d4410c4")

        /** File Transfer List. */
        val FILE_TRANSFER_LIST_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("d9ae1c06-447d-4dea-8b7d-fc8b19c2cdae")

        /** Power Off During File Transfer (behavior + resize). */
        val POWER_OFF_DURING_TRANSFER_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("bd6725fc-5d16-496a-a48a-f784594c8ecb")

        /** Camera Power (dm-zharov): 0=Off, 1=On, 2=Sleep. Write/Read/Notify. */
        val CAMERA_POWER_CHARACTERISTIC_UUID: Uuid =
            Uuid.parse("b58ce84c-0666-4de9-bec8-2d27b27b3211")

        // Note: Drive Mode, Capture Mode, and Capture Status are in the Shooting service.
    }

    /** Location sync service and characteristic. */
    object Location {
        val SERVICE_UUID: Uuid = Uuid.parse("84a0dd62-e8aa-4d0f-91db-819b6724c69e")
        val LOCATION_CHARACTERISTIC_UUID: Uuid = Uuid.parse("28f59d60-8b8e-4fcd-a81f-61bdb46595a9")
    }

    override val locationServiceUuid: Uuid = Location.SERVICE_UUID
    override val locationCharacteristicUuid: Uuid = Location.LOCATION_CHARACTERISTIC_UUID
}
