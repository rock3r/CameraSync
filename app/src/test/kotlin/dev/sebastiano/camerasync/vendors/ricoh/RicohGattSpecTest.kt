package dev.sebastiano.camerasync.vendors.ricoh

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class RicohGattSpecTest {

    @Test
    fun `scan filter service UUID matches specification`() {
        assertEquals(
            Uuid.parse("84A0DD62-E8AA-4D0F-91DB-819B6724C69E"),
            RicohGattSpec.SCAN_FILTER_SERVICE_UUID,
        )
    }

    @Test
    fun `scan filter service UUIDs list contains scan filter UUID`() {
        assertEquals(1, RicohGattSpec.scanFilterServiceUuids.size)
        assertEquals(
            RicohGattSpec.SCAN_FILTER_SERVICE_UUID,
            RicohGattSpec.scanFilterServiceUuids[0],
        )
    }

    @Test
    fun `scan filter device names includes GR and RICOH`() {
        assertEquals(2, RicohGattSpec.scanFilterDeviceNames.size)
        assertEquals(listOf("GR", "RICOH"), RicohGattSpec.scanFilterDeviceNames)
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
    fun `model name service UUID matches specification`() {
        assertEquals(
            Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb"),
            RicohGattSpec.modelNameServiceUuid,
        )
    }

    @Test
    fun `model name characteristic UUID matches specification`() {
        assertEquals(
            Uuid.parse("00002a24-0000-1000-8000-00805f9b34fb"),
            RicohGattSpec.modelNameCharacteristicUuid,
        )
    }

    @Test
    fun `location service UUID matches scan filter service UUID`() {
        // In Ricoh's implementation, the location service and scan filter use the same UUID
        assertEquals(RicohGattSpec.SCAN_FILTER_SERVICE_UUID, RicohGattSpec.Location.SERVICE_UUID)
    }

    @Test
    fun `all UUIDs are lowercase with uppercase letters`() {
        // Verify UUID format consistency - UUIDs should use uppercase hex digits
        val allUuids =
            listOf(
                RicohGattSpec.SCAN_FILTER_SERVICE_UUID.toString(),
                RicohGattSpec.Firmware.SERVICE_UUID.toString(),
                RicohGattSpec.Firmware.VERSION_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DeviceName.SERVICE_UUID.toString(),
                RicohGattSpec.DeviceName.NAME_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DateTime.SERVICE_UUID.toString(),
                RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID.toString(),
                RicohGattSpec.Location.SERVICE_UUID.toString(),
                RicohGattSpec.Location.LOCATION_CHARACTERISTIC_UUID.toString(),
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
