package dev.sebastiano.camerasync.vendors.sony

import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "SonyConnectionDelegate"
private const val SONY_MTU_SIZE = 158
private const val BLE_WRITE_TIMEOUT_MS = 30_000L
private const val LOCATION_WRITE_MAX_RETRIES = 3

/**
 * Sony-specific connection delegate.
 *
 * Handles the complex lifecycle of Sony camera connections, including:
 * - MTU negotiation (158 bytes)
 * - Location service locking/unlocking (DD30/DD31)
 * - Capability reading (DD21/DD32/DD33)
 * - Notification subscription (DD01)
 * - Retry logic for location writes
 */
@OptIn(ExperimentalUuidApi::class)
class SonyConnectionDelegate : DefaultConnectionDelegate() {

    override val mtu: Int = SONY_MTU_SIZE

    private var sonyLocationServiceEnabled = false
    private var sonyCapabilities: SonyCapabilities? = null
    private var sonyNotificationObservationJob: Job? = null

    /** Holds Sony-specific camera capabilities read from GATT characteristics. */
    private data class SonyCapabilities(
        val requiresTimezone: Boolean,
        val timeCorrection: ByteArray? = null,
        val areaAdjustment: ByteArray? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SonyCapabilities
            if (requiresTimezone != other.requiresTimezone) return false
            if (timeCorrection != null) {
                if (other.timeCorrection == null) return false
                if (!timeCorrection.contentEquals(other.timeCorrection)) return false
            } else if (other.timeCorrection != null) return false
            if (areaAdjustment != null) {
                if (other.areaAdjustment == null) return false
                if (!areaAdjustment.contentEquals(other.areaAdjustment)) return false
            } else if (other.areaAdjustment != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = requiresTimezone.hashCode()
            result = 31 * result + (timeCorrection?.contentHashCode() ?: 0)
            result = 31 * result + (areaAdjustment?.contentHashCode() ?: 0)
            return result
        }
    }

    override suspend fun syncLocation(
        peripheral: Peripheral,
        camera: Camera,
        location: GpsLocation,
    ) {
        val serviceUuid = SonyGattSpec.LOCATION_SERVICE_UUID
        val service =
            peripheral.services.value.orEmpty().firstOrNull { it.serviceUuid == serviceUuid }
                ?: throw IllegalStateException("Location service not found: $serviceUuid")

        // 1. Ensure service is enabled/locked (Prepare)
        // For Sony cameras, re-enable location service before EVERY write.
        if (!sonyLocationServiceEnabled) {
            Log.info(tag = TAG) {
                "Sony location service characteristics: ${service.characteristics.map { it.characteristicUuid }}"
            }
        }
        enableSonyLocationService(peripheral, service, camera)

        // 2. Encode
        val includeTimezone = sonyCapabilities?.requiresTimezone ?: true
        Log.debug(tag = TAG) { "Sony location packet: includeTimezone=$includeTimezone" }

        val data =
            SonyProtocol.encodeLocationPacket(
                latitude = location.latitude,
                longitude = location.longitude,
                dateTime = location.timestamp,
                includeTimezone = includeTimezone,
            )

        if (includeTimezone && data.size >= 95) {
            Log.info(tag = TAG) {
                "Sony DD11 TZ bytes (91-94): ${data.slice(91..94).toByteArray().toHexString()}"
            }
        }

        // 3. Write with retry
        val charUuid = SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID
        val char =
            service.characteristics.firstOrNull { it.characteristicUuid == charUuid }
                ?: throw IllegalStateException("Location characteristic not found: $charUuid")

        var lastException: Exception? = null
        repeat(LOCATION_WRITE_MAX_RETRIES) { attempt ->
            @Suppress("TooGenericExceptionCaught")
            try {
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(char, data, WriteType.WithResponse)
                }
                Log.debug(tag = TAG) { "Location write succeeded on attempt ${attempt + 1}" }
                return
            } catch (e: TimeoutCancellationException) {
                Log.warn(tag = TAG) {
                    "Location write timed out on attempt ${attempt + 1}/$LOCATION_WRITE_MAX_RETRIES"
                }
                lastException = e
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) {
                    "Location write failed on attempt ${attempt + 1}/$LOCATION_WRITE_MAX_RETRIES"
                }
                lastException = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.error(tag = TAG, throwable = e) {
                    "Unexpected error on location write attempt ${attempt + 1}/$LOCATION_WRITE_MAX_RETRIES"
                }
                lastException = e
            }

            if (attempt < LOCATION_WRITE_MAX_RETRIES - 1) {
                delay(500)
            }
        }
        throw IOException(
            "Location write failed after $LOCATION_WRITE_MAX_RETRIES attempts",
            lastException,
        )
    }

    private suspend fun enableSonyLocationService(
        peripheral: Peripheral,
        service: DiscoveredService,
        camera: Camera,
    ) {
        val isFirstEnable = !sonyLocationServiceEnabled

        // Protocol version check using metadata map
        val protocolVersion = camera.vendorMetadata["bleProtocolVersion"] as? Int
        val requiresUnlock =
            protocolVersion != null &&
                protocolVersion >= SonyCameraVendor.PROTOCOL_VERSION_REQUIRES_UNLOCK

        val lockChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID
            }
        val enableChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID
            }
        val hasUnlockChars = lockChar != null && enableChar != null

        if (isFirstEnable) {
            Log.info(tag = TAG) {
                "Sony camera ${camera.macAddress}: protocol version=$protocolVersion, " +
                    "requires unlock=$requiresUnlock, has DD30/DD31=$hasUnlockChars"
            }
        }

        if (!hasUnlockChars) {
            if (isFirstEnable) {
                if (requiresUnlock) {
                    Log.warn(tag = TAG) {
                        "Sony camera ${camera.macAddress} protocol version $protocolVersion requires " +
                            "DD30/DD31 unlock but characteristics not found! Location sync may not work."
                    }
                } else {
                    Log.debug(tag = TAG) { "Sony camera ${camera.macAddress} uses legacy protocol" }
                }
                sonyCapabilities = readSonyCapabilities(peripheral, service)
            }
            sonyLocationServiceEnabled = true
            return
        }

        if (isFirstEnable) {
            Log.info(tag = TAG) { "Enabling Sony location service via DD30/DD31" }
        } else {
            Log.debug(tag = TAG) { "Re-enabling Sony location service (keep-alive)" }
        }

        if (isFirstEnable) {
            val notifyChar =
                service.characteristics.firstOrNull {
                    it.characteristicUuid == SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID
                }
            if (notifyChar != null) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    sonyNotificationObservationJob?.cancel()
                    val supervisorJob = SupervisorJob(currentCoroutineContext()[Job])
                    val observeScope = CoroutineScope(currentCoroutineContext() + supervisorJob)
                    sonyNotificationObservationJob = supervisorJob
                    observeScope.launch {
                        peripheral.observe(notifyChar).collect { data ->
                            Log.info(tag = TAG) {
                                "DD01 notification received: ${data.toHexString()}"
                            }
                        }
                    }
                    delay(100)
                } catch (e: Exception) {
                    Log.warn(tag = TAG, throwable = e) { "Failed to subscribe to DD01" }
                }
            }
        }

        Log.debug(tag = TAG) { "Writing 0x01 to DD30 (Lock Session)" }
        withTimeout(BLE_WRITE_TIMEOUT_MS) {
            peripheral.write(lockChar, byteArrayOf(0x01), WriteType.WithResponse)
        }

        Log.debug(tag = TAG) { "Writing 0x01 to DD31 (Enable Transfer)" }
        withTimeout(BLE_WRITE_TIMEOUT_MS) {
            peripheral.write(enableChar, byteArrayOf(0x01), WriteType.WithResponse)
        }

        if (isFirstEnable) {
            sonyCapabilities = readSonyCapabilities(peripheral, service)
            Log.info(tag = TAG) { "Sony location service enabled" }
            delay(500)
        }

        sonyLocationServiceEnabled = true
    }

    private suspend fun readSonyCapabilities(
        peripheral: Peripheral,
        service: DiscoveredService,
    ): SonyCapabilities? {
        val configChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_CONFIG_READ_CHARACTERISTIC_UUID
            }
        val timeCorrectionChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.TIME_CORRECTION_CHARACTERISTIC_UUID
            }
        val areaAdjustmentChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.AREA_ADJUSTMENT_CHARACTERISTIC_UUID
            }

        var requiresTimezone = true
        var timeCorrection: ByteArray? = null
        var areaAdjustment: ByteArray? = null

        if (timeCorrectionChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                timeCorrection =
                    withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(timeCorrectionChar) }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD32 time correction" }
            }
        }

        if (areaAdjustmentChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                areaAdjustment =
                    withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(areaAdjustmentChar) }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD33 area adjustment" }
            }
        }

        if (configChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val configData = withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(configChar) }
                requiresTimezone = SonyProtocol.parseConfigRequiresTimezone(configData)
                Log.info(tag = TAG) { "Sony DD21 capabilities: requiresTimezone=$requiresTimezone" }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD21 capabilities" }
            }
        }

        return SonyCapabilities(requiresTimezone, timeCorrection, areaAdjustment)
    }

    override suspend fun onDisconnecting(peripheral: Peripheral) {
        if (!sonyLocationServiceEnabled) return

        sonyNotificationObservationJob?.cancel()
        sonyNotificationObservationJob = null

        val service =
            peripheral.services.value.orEmpty().firstOrNull {
                it.serviceUuid == SonyGattSpec.LOCATION_SERVICE_UUID
            } ?: return
        val lockChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID
            }
        val enableChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID
            }

        if (lockChar != null && enableChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                Log.debug(tag = TAG) { "Disabling Sony location service" }
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(enableChar, byteArrayOf(0x00), WriteType.WithResponse)
                }
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(lockChar, byteArrayOf(0x00), WriteType.WithResponse)
                }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to disable Sony location service" }
            }
        }
        sonyLocationServiceEnabled = false
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
