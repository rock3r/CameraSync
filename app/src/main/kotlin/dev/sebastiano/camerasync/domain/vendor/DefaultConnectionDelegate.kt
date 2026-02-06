package dev.sebastiano.camerasync.domain.vendor

import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.withTimeout

/**
 * Default implementation of [VendorConnectionDelegate] for cameras with standard behavior.
 *
 * This delegate implements the standard BLE flow:
 * - Look up Service/Characteristic UUIDs from [CameraGattSpec]
 * - Encode data using [CameraProtocol]
 * - Write data using standard BLE write (WithResponse) and 30s timeout
 */
@OptIn(ExperimentalUuidApi::class)
open class DefaultConnectionDelegate : VendorConnectionDelegate {

    override suspend fun syncLocation(
        peripheral: Peripheral,
        camera: Camera,
        location: GpsLocation,
    ) {
        val gattSpec = camera.vendor.gattSpec
        val protocol = camera.vendor.protocol

        val serviceUuid =
            gattSpec.locationServiceUuid
                ?: throw IllegalStateException(
                    "Location service UUID not configured for ${camera.vendor.vendorName}"
                )
        val charUuid =
            gattSpec.locationCharacteristicUuid
                ?: throw IllegalStateException(
                    "Location characteristic UUID not configured for ${camera.vendor.vendorName}"
                )

        val service =
            peripheral.services.value.orEmpty().firstOrNull { it.serviceUuid == serviceUuid }
                ?: throw IllegalStateException("Location service not found: $serviceUuid")

        val char =
            service.characteristics.firstOrNull { it.characteristicUuid == charUuid }
                ?: throw IllegalStateException("Location characteristic not found: $charUuid")

        val data = protocol.encodeLocation(location)

        withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.write(char, data, WriteType.WithResponse) }
    }

    override suspend fun syncDateTime(
        peripheral: Peripheral,
        camera: Camera,
        dateTime: ZonedDateTime,
    ) {
        val gattSpec = camera.vendor.gattSpec
        val protocol = camera.vendor.protocol

        val serviceUuid =
            gattSpec.dateTimeServiceUuid
                ?: throw IllegalStateException(
                    "Date/time service UUID not configured for ${camera.vendor.vendorName}"
                )
        val charUuid =
            gattSpec.dateTimeCharacteristicUuid
                ?: throw IllegalStateException(
                    "Date/time characteristic UUID not configured for ${camera.vendor.vendorName}"
                )

        val service =
            peripheral.services.value.orEmpty().firstOrNull { it.serviceUuid == serviceUuid }
                ?: throw IllegalStateException("Date/time service not found: $serviceUuid")

        val char =
            service.characteristics.firstOrNull { it.characteristicUuid == charUuid }
                ?: throw IllegalStateException("Date/time characteristic not found: $charUuid")

        val data = protocol.encodeDateTime(dateTime)

        withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.write(char, data, WriteType.WithResponse) }
    }

    companion object {
        private const val BLE_WRITE_TIMEOUT_MS = 30_000L
    }
}
