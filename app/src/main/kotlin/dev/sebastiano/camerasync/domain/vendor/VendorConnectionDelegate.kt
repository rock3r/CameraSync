package dev.sebastiano.camerasync.domain.vendor

import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZonedDateTime

/**
 * Delegate for handling vendor-specific BLE connection lifecycle and sync operations.
 *
 * This interface allows vendors to take full control of how data is synchronized to the camera,
 * encapsulating all protocol-specific logic (encoding, packet splitting, retries, keep-alives)
 * within the vendor implementation.
 */
interface VendorConnectionDelegate {

    /**
     * The MTU size to request upon connection.
     *
     * @return The MTU size, or null to use the default.
     */
    val mtu: Int?
        get() = null

    /**
     * Called after the connection is fully established and ready for use.
     *
     * @return true if setup was successful, false otherwise.
     */
    suspend fun onConnected(peripheral: Peripheral, camera: Camera) {}

    /**
     * Synchronizes a GPS location to the camera.
     *
     * The implementation is responsible for:
     * 1. Finding the appropriate service and characteristic (from [Camera.vendor])
     * 2. Encoding the location data (using [Camera.vendor])
     * 3. Writing the data to the peripheral (handling any vendor-specific retries or flows)
     */
    suspend fun syncLocation(peripheral: Peripheral, camera: Camera, location: GpsLocation)

    /**
     * Synchronizes the date/time to the camera.
     *
     * The implementation is responsible for:
     * 1. Finding the appropriate service and characteristic (from [Camera.vendor])
     * 2. Encoding the date/time data (using [Camera.vendor])
     * 3. Writing the data to the peripheral
     */
    suspend fun syncDateTime(peripheral: Peripheral, camera: Camera, dateTime: ZonedDateTime)

    /**
     * Called when the connection is being closed.
     *
     * Use this to perform cleanup, such as releasing locks or disabling services.
     */
    suspend fun onDisconnecting(peripheral: Peripheral) {}
}
