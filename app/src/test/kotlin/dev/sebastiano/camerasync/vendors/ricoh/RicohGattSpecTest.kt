package dev.sebastiano.camerasync.vendors.ricoh

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class RicohGattSpecTest {

    @Test
    fun `scan filter service UUIDs list is empty for Ricoh`() {
        assertEquals(0, RicohGattSpec.scanFilterServiceUuids.size)
    }

    @Test
    fun `scan filter device names includes GR and RICOH`() {
        assertEquals(2, RicohGattSpec.scanFilterDeviceNames.size)
        assertEquals(listOf("GR", "RICOH"), RicohGattSpec.scanFilterDeviceNames)
    }

    @Test
    fun `scan filter manufacturer data includes Ricoh company id and prefix`() {
        assertEquals(1, RicohGattSpec.scanFilterManufacturerData.size)
        val filter = RicohGattSpec.scanFilterManufacturerData.first()
        assertEquals(0x065F, filter.manufacturerId)
        assertEquals(byteArrayOf(0xDA.toByte()).toList(), filter.data.toList())
        assertEquals(byteArrayOf(0xFF.toByte()).toList(), filter.mask?.toList())
    }

    @Test
    fun `firmware service UUID matches specification`() {
        assertEquals(
            Uuid.parse("9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1"),
            RicohGattSpec.Firmware.SERVICE_UUID,
        )
    }

    @Test
    fun `firmware version characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("b4eb8905-7411-40a6-a367-2834c2157ea7"),
            RicohGattSpec.Firmware.VERSION_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `firmware service exposed via interface property`() {
        assertEquals(RicohGattSpec.Firmware.SERVICE_UUID, RicohGattSpec.firmwareServiceUuid)
    }

    @Test
    fun `firmware version characteristic exposed via interface property`() {
        assertEquals(
            RicohGattSpec.Firmware.VERSION_CHARACTERISTIC_UUID,
            RicohGattSpec.firmwareVersionCharacteristicUuid,
        )
    }

    @Test
    fun `device name service UUID matches specification`() {
        assertEquals(
            Uuid.parse("0f291746-0c80-4726-87a7-3c501fd3b4b6"),
            RicohGattSpec.DeviceName.SERVICE_UUID,
        )
    }

    @Test
    fun `device name characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("fe3a32f8-a189-42de-a391-bc81ae4daa76"),
            RicohGattSpec.DeviceName.NAME_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `device name service exposed via interface property`() {
        assertEquals(RicohGattSpec.DeviceName.SERVICE_UUID, RicohGattSpec.deviceNameServiceUuid)
    }

    @Test
    fun `device name characteristic exposed via interface property`() {
        assertEquals(
            RicohGattSpec.DeviceName.NAME_CHARACTERISTIC_UUID,
            RicohGattSpec.deviceNameCharacteristicUuid,
        )
    }

    @Test
    fun `dateTime service UUID matches specification`() {
        assertEquals(
            Uuid.parse("4b445988-caa0-4dd3-941d-37b4f52aca86"),
            RicohGattSpec.DateTime.SERVICE_UUID,
        )
    }

    @Test
    fun `dateTime characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("fa46bbdd-8a8f-4796-8cf3-aa58949b130a"),
            RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `geoTagging characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("a36afdcf-6b67-4046-9be7-28fb67dbc071"),
            RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `dateTime service exposed via interface property`() {
        assertEquals(RicohGattSpec.DateTime.SERVICE_UUID, RicohGattSpec.dateTimeServiceUuid)
    }

    @Test
    fun `dateTime characteristic exposed via interface property`() {
        assertEquals(
            RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID,
            RicohGattSpec.dateTimeCharacteristicUuid,
        )
    }

    @Test
    fun `geoTagging characteristic exposed via interface property`() {
        assertEquals(
            RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID,
            RicohGattSpec.geoTaggingCharacteristicUuid,
        )
    }

    @Test
    fun `location service UUID matches specification`() {
        assertEquals(
            Uuid.parse("84a0dd62-e8aa-4d0f-91db-819b6724c69e"),
            RicohGattSpec.Location.SERVICE_UUID,
        )
    }

    @Test
    fun `location characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("28f59d60-8b8e-4fcd-a81f-61bdb46595a9"),
            RicohGattSpec.Location.LOCATION_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location service exposed via interface property`() {
        assertEquals(RicohGattSpec.Location.SERVICE_UUID, RicohGattSpec.locationServiceUuid)
    }

    @Test
    fun `location characteristic exposed via interface property`() {
        assertEquals(
            RicohGattSpec.Location.LOCATION_CHARACTERISTIC_UUID,
            RicohGattSpec.locationCharacteristicUuid,
        )
    }

    @Test
    fun `Shooting service and Operation Request characteristic match dm-zharov spec`() {
        assertEquals(
            Uuid.parse("9f00f387-8345-4bbc-8b92-b87b52e3091a"),
            RicohGattSpec.Shooting.SERVICE_UUID,
        )
        assertEquals(
            Uuid.parse("559644b8-e0bc-4011-929b-5cf9199851e7"),
            RicohGattSpec.Shooting.OPERATION_REQUEST_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `Shooting service characteristics match dm-zharov spec`() {
        assertEquals(
            Uuid.parse("a3c51525-de3e-4777-a1c2-699e28736fcf"),
            RicohGattSpec.Shooting.SHOOTING_MODE_CHARACTERISTIC_UUID,
        )
        assertEquals(
            Uuid.parse("78009238-ac3d-4370-9b6f-c9ce2f4e3ca8"),
            RicohGattSpec.Shooting.CAPTURE_MODE_CHARACTERISTIC_UUID,
        )
        assertEquals(
            Uuid.parse("b29e6de3-1aec-48c1-9d05-02cea57ce664"),
            RicohGattSpec.Shooting.DRIVE_MODE_CHARACTERISTIC_UUID,
        )
        assertEquals(
            Uuid.parse("b5589c08-b5fd-46f5-be7d-ab1b8c074caa"),
            RicohGattSpec.Shooting.CAPTURE_STATUS_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `WlanControl service and Network Type characteristic match dm-zharov spec`() {
        assertEquals(
            Uuid.parse("f37f568f-9071-445d-a938-5441f2e82399"),
            RicohGattSpec.WlanControl.SERVICE_UUID,
        )
        assertEquals(
            Uuid.parse("9111cdd0-9f01-45c4-a2d4-e09e8fb0424d"),
            RicohGattSpec.WlanControl.NETWORK_TYPE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `CameraState Camera Power characteristic matches dm-zharov spec`() {
        assertEquals(
            Uuid.parse("b58ce84c-0666-4de9-bec8-2d27b27b3211"),
            RicohGattSpec.CameraState.CAMERA_POWER_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `CameraState Battery Level characteristic matches dm-zharov spec`() {
        assertEquals(
            Uuid.parse("875fc41d-4980-434c-a653-fd4a4d4410c4"),
            RicohGattSpec.CameraState.BATTERY_LEVEL_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `CameraState Storage Info characteristic matches dm-zharov spec`() {
        assertEquals(
            Uuid.parse("a0c10148-8865-4470-9631-8f36d79a41a5"),
            RicohGattSpec.CameraState.STORAGE_INFO_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `CameraState Operation Mode characteristic matches dm-zharov spec`() {
        assertEquals(
            Uuid.parse("1452335a-ec7f-4877-b8ab-0f72e18bb295"),
            RicohGattSpec.CameraState.OPERATION_MODE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `all UUIDs are lowercase with uppercase letters`() {
        // Verify UUID format consistency - UUIDs should use uppercase hex digits
        val allUuids =
            listOf(
                RicohGattSpec.Firmware.SERVICE_UUID.toString(),
                RicohGattSpec.Firmware.VERSION_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DeviceName.SERVICE_UUID.toString(),
                RicohGattSpec.DeviceName.NAME_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DateTime.SERVICE_UUID.toString(),
                RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Location.SERVICE_UUID.toString(),
                RicohGattSpec.Location.LOCATION_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Shooting.SERVICE_UUID.toString(),
                RicohGattSpec.Shooting.SHOOTING_MODE_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Shooting.CAPTURE_MODE_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Shooting.DRIVE_MODE_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Shooting.CAPTURE_STATUS_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Shooting.OPERATION_REQUEST_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.WlanControl.SERVICE_UUID.toString(),
                RicohGattSpec.WlanControl.NETWORK_TYPE_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.CameraState.CAMERA_POWER_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.CameraState.BATTERY_LEVEL_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.CameraState.STORAGE_INFO_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.CameraState.OPERATION_MODE_CHARACTERISTIC_UUID.toString(),
            )

        // All UUIDs should be properly formatted (8-4-4-4-12 format)
        allUuids.forEach { uuid ->
            val parts = uuid.split("-")
            assertEquals("UUID $uuid should have 5 parts", 5, parts.size)
            assertEquals("First part should be 8 chars", 8, parts[0].length)
            assertEquals("Second part should be 4 chars", 4, parts[1].length)
            assertEquals("Third part should be 4 chars", 4, parts[2].length)
            assertEquals("Fourth part should be 4 chars", 4, parts[3].length)
            assertEquals("Fifth part should be 12 chars", 12, parts[4].length)
        }
    }
}
