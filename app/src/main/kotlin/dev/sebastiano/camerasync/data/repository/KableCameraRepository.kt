package dev.sebastiano.camerasync.data.repository

import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.juul.kable.Advertisement
import com.juul.kable.ExperimentalApi
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.logs.Logging
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.ble.buildManufacturerDataMap
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.logging.KhronicleLogEngine
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyGattSpec
import dev.sebastiano.camerasync.vendors.sony.SonyProtocol
import java.io.IOException
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "KableCameraRepository"

/** MTU size for Sony cameras (per Sony Creators' App which requests MTU 158). */
private const val SONY_MTU_SIZE = 158

/**
 * Implementation of [CameraRepository] using the Kable BLE library.
 *
 * This repository is vendor-agnostic and supports cameras from multiple manufacturers through the
 * [CameraVendorRegistry].
 */
@OptIn(ExperimentalUuidApi::class)
class KableCameraRepository(
    private val vendorRegistry: CameraVendorRegistry,
    private val context: Context,
) : CameraRepository {

    @OptIn(ObsoleteKableApi::class)
    private val scanner by lazy {
        val scanFilterUuids = vendorRegistry.getAllScanFilterUuids()
        Log.info(tag = TAG) {
            "Scanning for cameras from ${vendorRegistry.getAllVendors().size} vendors"
        }
        Log.info(tag = TAG) { "Scan filter UUIDs: $scanFilterUuids" }

        com.juul.kable.Scanner {
            // We don't use filters here because some cameras might not advertise
            // the service UUID in the advertisement packet.
            // We filter discovered devices in discoveredCameras instead.
            logging {
                engine = KhronicleLogEngine
                level = Logging.Level.Events
                format = Logging.Format.Multiline
            }
            scanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        }
    }

    override val discoveredCameras: Flow<Camera>
        get() =
            scanner.advertisements.mapNotNull { advertisement ->
                Log.debug(tag = TAG) {
                    "Scan advertisement: ${advertisement.identifier} (${advertisement.peripheralName})"
                }
                advertisement.toCamera()
            }

    override fun startScanning() {
        // Scanner is lazy and starts when advertisements flow is collected
        Log.info(tag = TAG) { "Starting camera scan" }
    }

    override fun stopScanning() {
        Log.info(tag = TAG) { "Stopping camera scan" }
        // Scanner stops when the flow collection is cancelled
    }

    override fun findCameraByMacAddress(macAddress: String): Flow<Camera> {
        Log.info(tag = TAG) { "Scanning for camera by MAC: $macAddress" }
        @OptIn(ObsoleteKableApi::class)
        val scanner =
            com.juul.kable.Scanner {
                filters { match { address = macAddress } }
                logging {
                    engine = KhronicleLogEngine
                    level = Logging.Level.Events
                }
            }
        return scanner.advertisements.mapNotNull { it.toCamera() }
    }

    override fun startPassiveScan(pendingIntent: PendingIntent) {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.warn(tag = TAG) {
                "Bluetooth adapter not available or disabled, cannot start passive scan"
            }
            return
        }

        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.warn(tag = TAG) { "Bluetooth LE scanner not available" }
                return
            }

            val settings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    .build()

            // We need to filter by Service UUIDs to avoid waking up for every BLE device
            val filters =
                vendorRegistry.getAllScanFilterUuids().map { uuid ->
                    android.bluetooth.le.ScanFilter.Builder()
                        .setServiceUuid(
                            android.os.ParcelUuid(java.util.UUID.fromString(uuid.toString()))
                        )
                        .build()
                }

            Log.info(tag = TAG) {
                "Starting passive PendingIntent scan with ${filters.size} filters"
            }
            scanner.startScan(filters, settings, pendingIntent)
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) { "SecurityException starting passive scan" }
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Error starting passive scan" }
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) { "Error starting passive scan" }
        }
    }

    override fun stopPassiveScan(pendingIntent: PendingIntent) {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return

        try {
            val scanner = adapter.bluetoothLeScanner
            Log.info(tag = TAG) { "Stopping passive PendingIntent scan" }
            scanner?.stopScan(pendingIntent)
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) { "SecurityException stopping passive scan" }
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Error stopping passive scan" }
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) { "Error stopping passive scan" }
        }
    }

    override suspend fun connect(camera: Camera, onFound: (() -> Unit)?): CameraConnection {
        Log.info(tag = TAG) {
            "Looking for ${camera.name ?: camera.macAddress} (${camera.macAddress})"
        }

        // Check if device is bonded - if so, it might not be advertising immediately
        val isBonded = isDeviceBonded(camera.macAddress)
        if (isBonded) {
            Log.info(tag = TAG) {
                "Device ${camera.macAddress} is bonded. " +
                    "Cameras may need 10-15 seconds to start advertising after being turned on."
            }
        }

        // Retry logic for bonded devices that might be slow to advertise
        // 3 total attempts (1 initial + 2 retries) with simplified fixed delays
        val maxAttempts = if (isBonded) 3 else 1
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                val delayMs = 3_000L // Simple 3s delay between retries
                Log.info(tag = TAG) {
                    "Retry attempt $attempt/$maxAttempts for ${camera.macAddress} after ${delayMs}ms delay"
                }
                delay(delayMs)
            }

            // Scan for advertisement (Kable requires an Advertisement to create a Peripheral)
            Log.info(tag = TAG) {
                "Scanning for advertisement for ${camera.macAddress} (attempt $attempt/$maxAttempts)..."
            }
            val scanner =
                com.juul.kable.Scanner {
                    @OptIn(ObsoleteKableApi::class)
                    filters { match { address = camera.macAddress } }
                }

            try {
                // Use longer timeout for bonded devices that might be slow to advertise
                // Bonded devices may take longer to start advertising after power-on
                val timeout = if (isBonded) 20_000L else 10_000L
                val advertisement = withTimeout(timeout) { scanner.advertisements.first() }
                Log.info(tag = TAG) {
                    "Found advertisement for ${camera.name ?: camera.macAddress}: ${advertisement.identifier} (${advertisement.peripheralName})"
                }
                onFound?.invoke()

                val peripheral =
                    Peripheral(advertisement) {
                        logging {
                            level = Logging.Level.Events
                            engine = KhronicleLogEngine
                            identifier = "CameraSync:${camera.vendor.vendorName}"
                        }
                        // Sony cameras require MTU 158 (per Sony Creators' App)
                        if (camera.vendor.vendorId == "sony") {
                            onServicesDiscovered {
                                @Suppress("TooGenericExceptionCaught")
                                try {
                                    requestMtu(SONY_MTU_SIZE)
                                    Log.info(tag = TAG) {
                                        "Sony MTU request successful: $SONY_MTU_SIZE"
                                    }
                                } catch (e: Exception) {
                                    // MTU request failure is not critical - continue anyway
                                    Log.warn(tag = TAG, throwable = e) {
                                        "Failed to request MTU $SONY_MTU_SIZE, continuing with default"
                                    }
                                }
                            }
                        }
                    }

                Log.info(tag = TAG) { "Connecting to ${camera.name}..." }
                peripheral.connect()
                Log.info(tag = TAG) { "Connected to ${camera.name}" }

                return KableCameraConnection(camera, peripheral)
            } catch (e: TimeoutCancellationException) {
                lastException = e
                Log.warn(tag = TAG) {
                    "Timeout waiting for advertisement from ${camera.macAddress} (attempt $attempt/$maxAttempts)"
                }
                // Continue to next retry if we have attempts left
                if (attempt < maxAttempts) {
                    continue
                }
            }
        }

        // All retries failed
        val errorMessage =
            if (isBonded) {
                "Timeout waiting for advertisement from ${camera.macAddress} after $maxAttempts attempts. " +
                    "The camera is bonded but not advertising. " +
                    "This usually happens when: 1) The camera just turned on and needs more time, " +
                    "2) The camera is already connected to another app, " +
                    "3) Bluetooth needs to be reset. " +
                    "Try: 1) Wait a few seconds and refresh, 2) Disconnect the camera from other apps, " +
                    "3) Turn Bluetooth off/on, or 4) Restart the camera."
            } else {
                "Timeout waiting for advertisement from ${camera.macAddress}. " +
                    "Make sure the camera is powered on, Bluetooth is enabled, " +
                    "and the camera is in pairing/discoverable mode."
            }
        Log.error(tag = TAG, throwable = lastException) { errorMessage }
        if (lastException is TimeoutCancellationException) throw lastException
        else throw IllegalStateException(errorMessage, lastException)
    }

    private fun isDeviceBonded(macAddress: String): Boolean {
        val bluetoothManager = context.getSystemService<BluetoothManager>()
        val adapter = bluetoothManager?.adapter ?: return false
        if (!adapter.isEnabled) return false

        // Check for BLUETOOTH_CONNECT permission
        if (
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            adapter.bondedDevices.any { device ->
                device.address.equals(macAddress, ignoreCase = true)
            }
        } catch (e: SecurityException) {
            Log.warn(tag = TAG, throwable = e) { "SecurityException accessing bonded devices" }
            false
        }
    }

    /**
     * Converts a BLE Advertisement to a Camera by identifying the vendor.
     *
     * @return A Camera instance if a vendor is recognized, or null if no vendor matches.
     */
    private fun Advertisement.toCamera(): Camera? {
        // Build manufacturer data map from the advertisement
        val mfrData = buildManufacturerDataMap()

        val vendor =
            vendorRegistry.identifyVendor(
                deviceName = peripheralName,
                serviceUuids = uuids,
                manufacturerData = mfrData,
            )

        if (vendor == null) {
            Log.warn(tag = TAG) {
                "No vendor recognized for device: $peripheralName (services: $uuids, mfr: ${mfrData.keys})"
            }
            return null
        }

        // Parse BLE protocol version for Sony cameras
        val protocolVersion =
            if (vendor.vendorId == "sony") {
                SonyCameraVendor.parseProtocolVersion(mfrData).also { version ->
                    if (version != null) {
                        Log.info(tag = TAG) {
                            "Sony camera $peripheralName BLE protocol version: $version" +
                                if (version >= SonyCameraVendor.PROTOCOL_VERSION_REQUIRES_UNLOCK) {
                                    " (requires DD30/DD31 unlock)"
                                } else {
                                    " (legacy protocol)"
                                }
                        }
                    }
                }
            } else {
                null
            }

        Log.info(tag = TAG) { "Discovered ${vendor.vendorName} camera: $peripheralName" }
        return Camera(
            identifier = identifier,
            name = peripheralName,
            macAddress = identifier, // On Android, identifier is the MAC address
            vendor = vendor,
            bleProtocolVersion = protocolVersion,
        )
    }
}

/**
 * Implementation of [CameraConnection] using a Kable Peripheral.
 *
 * This implementation is vendor-agnostic and uses the camera's vendor specification to interact
 * with the camera's BLE services.
 *
 * Sony protocol specifics (see docs/sony/DATETIME_GPS_SYNC.md):
 * - All BLE write operations have a 30-second timeout
 * - Location writes (DD11) are retried up to 3 times on failure
 * - Capabilities must be read from DD21/DD32/DD33 before location sync
 */
@OptIn(ExperimentalUuidApi::class)
internal class KableCameraConnection(
    override val camera: Camera,
    private val peripheral: Peripheral,
) : CameraConnection {

    override val isConnected: Flow<Boolean> = peripheral.state.map { it is State.Connected }

    private val gattSpec = camera.vendor.gattSpec
    private val protocol = camera.vendor.protocol
    private val capabilities = camera.vendor.getCapabilities()

    /**
     * Tracks whether the Sony location service unlock sequence (DD30/DD31) has been performed. This
     * is required for Sony cameras with BLE protocol version >= 65 (newer protocol). The
     * characteristics only exist on cameras that need them.
     */
    private var sonyLocationServiceEnabled = false

    /**
     * Cached Sony camera capabilities read from DD21. Used to determine if timezone data should be
     * included in location packets.
     */
    private var sonyCapabilities: SonyCapabilities? = null

    /**
     * Job for observing DD01 (Location Status Notify) characteristic notifications. Must be
     * cancelled when disconnecting to prevent resource leaks.
     */
    private var sonyNotificationObservationJob: Job? = null

    /**
     * Holds Sony-specific camera capabilities read from GATT characteristics.
     *
     * @param requiresTimezone Whether the camera requires timezone data in location packets (from
     *   DD21)
     * @param timeCorrection Raw time correction data (from DD32)
     * @param areaAdjustment Raw area adjustment data (from DD33)
     */
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

    override suspend fun initializePairing(): Boolean {
        if (!capabilities.requiresVendorPairing) {
            Log.info(tag = TAG) {
                "${camera.vendor.vendorName} cameras do not require vendor-specific pairing"
            }
            return true
        }

        val pairingServiceUuid = gattSpec.pairingServiceUuid
        val pairingCharUuid = gattSpec.pairingCharacteristicUuid

        if (pairingServiceUuid == null || pairingCharUuid == null) {
            Log.warn(tag = TAG) {
                "${camera.vendor.vendorName} requires vendor pairing but no pairing UUIDs configured"
            }
            return false
        }

        val pairingData = protocol.getPairingInitData()
        if (pairingData == null) {
            Log.warn(tag = TAG) {
                "${camera.vendor.vendorName} requires vendor pairing but no pairing data provided"
            }
            return false
        }

        return try {
            val service =
                peripheral.services.value.orEmpty().firstOrNull {
                    it.serviceUuid == pairingServiceUuid
                }

            if (service == null) {
                Log.warn(tag = TAG) {
                    "Pairing service not found: $pairingServiceUuid. " +
                        "Available services: ${peripheral.services.value?.map { it.serviceUuid }}"
                }
                return false
            }

            val char =
                service.characteristics.firstOrNull { it.characteristicUuid == pairingCharUuid }

            if (char == null) {
                Log.warn(tag = TAG) {
                    "Pairing characteristic not found: $pairingCharUuid. " +
                        "Available characteristics: ${service.characteristics.map { it.characteristicUuid }}"
                }
                return false
            }

            Log.info(tag = TAG) {
                "Writing pairing initialization data to ${camera.vendor.vendorName} camera"
            }
            peripheral.write(char, pairingData, WriteType.WithResponse)
            Log.info(tag = TAG) { "Pairing initialization successful" }
            true
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Failed to initialize pairing" }
            false
        } catch (e: IllegalStateException) {
            Log.error(tag = TAG, throwable = e) { "Failed to initialize pairing" }
            false
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) { "Failed to initialize pairing" }
            false
        }
    }

    override suspend fun readFirmwareVersion(): String {
        if (!capabilities.supportsFirmwareVersion) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support firmware version reading"
            )
        }

        Log.debug(tag = TAG) { "Reading firmware version for ${camera.macAddress}" }
        val service =
            peripheral.services.value.orEmpty().firstOrNull {
                it.serviceUuid == gattSpec.firmwareServiceUuid
            }
                ?: throw IllegalStateException(
                    "Firmware service not found. Service UUID: ${gattSpec.firmwareServiceUuid}. " +
                        "Available services: ${peripheral.services.value?.map { it.serviceUuid } ?: "N/A"}"
                )
        val char =
            service.characteristics.firstOrNull {
                it.characteristicUuid == gattSpec.firmwareVersionCharacteristicUuid
            }
                ?: throw IllegalStateException(
                    "Firmware characteristic not found. Characteristic UUID: ${gattSpec.firmwareVersionCharacteristicUuid}. " +
                        "Available characteristics in service ${service.serviceUuid}: ${service.characteristics.map { it.characteristicUuid }}"
                )

        val firmwareBytes = peripheral.read(char)
        val version = firmwareBytes.decodeToString().trimEnd(Char(0))
        Log.info(tag = TAG) { "Firmware version: $version" }
        return version
    }

    override suspend fun readHardwareRevision(): String {
        if (!capabilities.supportsHardwareRevision) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support hardware revision reading"
            )
        }

        val serviceUuid = gattSpec.hardwareRevisionServiceUuid
        val charUuid = gattSpec.hardwareRevisionCharacteristicUuid

        if (serviceUuid == null || charUuid == null) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support hardware revision reading (UUIDs not configured)"
            )
        }

        val service =
            peripheral.services.value.orEmpty().firstOrNull { it.serviceUuid == serviceUuid }
                ?: error("Hardware revision service not found: $serviceUuid")

        val char =
            service.characteristics.firstOrNull { it.characteristicUuid == charUuid }
                ?: error("Hardware revision characteristic not found: $charUuid")

        val bytes = peripheral.read(char)
        val revision = bytes.decodeToString().trimEnd(Char(0))
        Log.info(tag = TAG) { "Hardware revision: $revision" }
        return revision
    }

    override suspend fun setPairedDeviceName(name: String) {
        if (!capabilities.supportsDeviceName) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support setting paired device name"
            )
        }

        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid == gattSpec.deviceNameServiceUuid
            }
        val char =
            service.characteristics.first {
                it.characteristicUuid == gattSpec.deviceNameCharacteristicUuid
            }

        Log.info(tag = TAG) { "Setting paired device name: $name" }
        peripheral.write(
            characteristic = char,
            data = name.encodeToByteArray(),
            writeType = WriteType.WithResponse,
        )
    }

    /**
     * Syncs date/time to the camera.
     *
     * Per Sony protocol spec, all BLE write operations have a 30-second timeout.
     */
    override suspend fun syncDateTime(dateTime: ZonedDateTime) {
        if (!capabilities.supportsDateTimeSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support date/time synchronization"
            )
        }

        Log.debug(tag = TAG) { "Syncing date/time for ${camera.macAddress}" }
        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid == gattSpec.dateTimeServiceUuid
            }
        val char =
            service.characteristics.first {
                it.characteristicUuid == gattSpec.dateTimeCharacteristicUuid
            }

        val data = protocol.encodeDateTime(dateTime)
        Log.info(tag = TAG) { "Syncing date/time: ${protocol.decodeDateTime(data)}" }

        // Per spec: 30-second timeout for BLE write operations
        withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.write(char, data, locationWriteType()) }
    }

    override suspend fun readDateTime(): ByteArray {
        if (!capabilities.supportsDateTimeSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support date/time reading"
            )
        }

        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid == gattSpec.dateTimeServiceUuid
            }
        val char =
            service.characteristics.first {
                it.characteristicUuid == gattSpec.dateTimeCharacteristicUuid
            }

        val data = peripheral.read(char)
        Log.info(tag = TAG) { "Read camera date/time: ${protocol.decodeDateTime(data)}" }
        return data
    }

    override suspend fun setGeoTaggingEnabled(enabled: Boolean) {
        if (!capabilities.supportsGeoTagging) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support geo-tagging control"
            )
        }

        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid == gattSpec.dateTimeServiceUuid
            }
        val char =
            service.characteristics.first {
                it.characteristicUuid == gattSpec.geoTaggingCharacteristicUuid
            }

        val currentlyEnabled = isGeoTaggingEnabled()
        if (currentlyEnabled == enabled) {
            Log.info(tag = TAG) {
                "Geo-tagging already ${if (enabled) "enabled" else "disabled"}, skipping"
            }
            return
        }

        Log.info(tag = TAG) { "${if (enabled) "Enabling" else "Disabling"} geo-tagging" }
        peripheral.write(
            characteristic = char,
            data = protocol.encodeGeoTaggingEnabled(enabled),
            writeType = WriteType.WithResponse,
        )
    }

    override suspend fun isGeoTaggingEnabled(): Boolean {
        if (!capabilities.supportsGeoTagging) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support geo-tagging"
            )
        }

        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid == gattSpec.dateTimeServiceUuid
            }
        val char =
            service.characteristics.first {
                it.characteristicUuid == gattSpec.geoTaggingCharacteristicUuid
            }

        val data = peripheral.read(char)
        val enabled = protocol.decodeGeoTaggingEnabled(data)
        Log.info(tag = TAG) { "Geo-tagging is ${if (enabled) "enabled" else "disabled"}" }
        return enabled
    }

    /**
     * Syncs a GPS location to the camera.
     *
     * Per Sony protocol spec:
     * - All BLE write operations have a 30-second timeout
     * - Write failures to DD11 are retried up to 3 times
     */
    override suspend fun syncLocation(location: GpsLocation) {
        if (!capabilities.supportsLocationSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support location synchronization"
            )
        }

        Log.debug(tag = TAG) { "Syncing location for ${camera.macAddress}" }
        val service =
            peripheral.services.value.orEmpty().firstOrNull {
                it.serviceUuid == gattSpec.locationServiceUuid
            }
                ?: throw IllegalStateException(
                    "Location service not found. Service UUID: ${gattSpec.locationServiceUuid}. " +
                        "Available services: ${peripheral.services.value?.map { it.serviceUuid } ?: "N/A"}"
                )

        // For Sony cameras, re-enable location service before EVERY write.
        // The camera appears to have an internal session timeout that invalidates
        // the DD30/DD31 lock after some period, causing the 🚫 icon to appear.
        // Re-enabling ensures the camera is always ready to receive location data.
        if (camera.vendor.vendorId == "sony") {
            if (!sonyLocationServiceEnabled) {
                Log.info(tag = TAG) {
                    "Sony location service characteristics for ${camera.macAddress}: " +
                        service.characteristics.map { it.characteristicUuid }
                }
            }
            enableSonyLocationService(service)
        }

        val char =
            service.characteristics.firstOrNull {
                it.characteristicUuid == gattSpec.locationCharacteristicUuid
            }
                ?: throw IllegalStateException(
                    "Location characteristic not found. Characteristic UUID: ${gattSpec.locationCharacteristicUuid}. " +
                        "Available characteristics in service ${service.serviceUuid}: ${service.characteristics.map { it.characteristicUuid }}"
                )

        // For Sony cameras, use the capabilities-based timezone setting from DD21
        val data =
            if (camera.vendor.vendorId == "sony") {
                val includeTimezone = sonyCapabilities?.requiresTimezone ?: true
                Log.debug(tag = TAG) {
                    "Sony location packet: includeTimezone=$includeTimezone " +
                        "(packet size: ${if (includeTimezone) 95 else 91} bytes)"
                }
                val packet =
                    SonyProtocol.encodeLocationPacket(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        dateTime = location.timestamp,
                        includeTimezone = includeTimezone,
                    )
                // Enhanced logging for debugging Sony protocol
                val utcTime = location.timestamp.withZoneSameInstant(java.time.ZoneOffset.UTC)
                Log.info(tag = TAG) {
                    "Sony DD11 encoding: UTC=${utcTime.year}-${utcTime.monthValue.toString().padStart(2, '0')}-" +
                        "${utcTime.dayOfMonth.toString().padStart(2, '0')} ${utcTime.hour.toString().padStart(2, '0')}:" +
                        "${utcTime.minute.toString().padStart(2, '0')}:${utcTime.second.toString().padStart(2, '0')}, " +
                        "Lat=${location.latitude}, Lon=${location.longitude}"
                }
                // Log header bytes (0-10) for debugging
                Log.info(tag = TAG) {
                    "Sony DD11 header (bytes 0-10): ${packet.take(11).toByteArray().toHexString()}"
                }
                // Log coordinates and time (bytes 11-25)
                Log.info(tag = TAG) {
                    "Sony DD11 coords+time (bytes 11-25): ${packet.slice(11..25).toByteArray().toHexString()}"
                }
                // Log timezone bytes if included (bytes 91-94)
                if (includeTimezone && packet.size >= 95) {
                    Log.info(tag = TAG) {
                        "Sony DD11 TZ bytes (91-94): ${packet.slice(91..94).toByteArray().toHexString()}"
                    }
                }
                Log.info(tag = TAG) { "Sony DD11 packet total size: ${packet.size} bytes" }
                packet
            } else {
                protocol.encodeLocation(location)
            }
        Log.info(tag = TAG) { "Syncing location: ${protocol.decodeLocation(data)}" }

        // For Sony cameras, implement retry logic (3 retries per spec)
        if (camera.vendor.vendorId == "sony") {
            writeLocationWithRetry(char, data)
        } else {
            withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.write(char, data, locationWriteType()) }
        }
    }

    /**
     * Writes location data with retry logic for Sony cameras.
     *
     * Per Sony protocol spec: Write failures to DD11 are retried up to 3 times.
     *
     * @param char The characteristic to write to (DD11)
     * @param data The location data payload
     * @throws IOException if all retries fail
     */
    private suspend fun writeLocationWithRetry(
        char: com.juul.kable.DiscoveredCharacteristic,
        data: ByteArray,
    ) {
        var lastException: Exception? = null
        repeat(LOCATION_WRITE_MAX_RETRIES) { attempt ->
            @Suppress("TooGenericExceptionCaught")
            try {
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(char, data, locationWriteType())
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
                // Preserve structured concurrency - don't catch cancellation
                throw e
            } catch (e: Exception) {
                Log.error(tag = TAG, throwable = e) {
                    "Unexpected error on location write attempt ${attempt + 1}/$LOCATION_WRITE_MAX_RETRIES"
                }
                lastException = e
            }

            // Small delay before retry
            if (attempt < LOCATION_WRITE_MAX_RETRIES - 1) {
                delay(500)
            }
        }

        throw IOException(
            "Location write failed after $LOCATION_WRITE_MAX_RETRIES attempts",
            lastException,
        )
    }

    /**
     * Enables the Sony location service by writing to DD30 (Lock) and DD31 (Enable)
     * characteristics, then reading camera capabilities from DD21, DD32, and DD33.
     *
     * Per Sony protocol spec (see docs/sony/DATETIME_GPS_SYNC.md):
     * 1. Lock Session: Write { 0x01 } to DD30
     * 2. Enable Transfer: Write { 0x01 } to DD31
     * 3. Read Capabilities: Read DD21 (Camera Info), DD32 (Time Correction), DD33 (Area Adjustment)
     *
     * This is required for Sony cameras with BLE protocol version >= 65 (typically firmware 3.02+,
     * but also newer camera models like Alpha 7 IV even with earlier firmware versions).
     *
     * The function checks both the protocol version from advertisement data AND whether DD30/DD31
     * characteristics actually exist (as a fallback for when advertisement data wasn't available).
     */
    private suspend fun enableSonyLocationService(service: com.juul.kable.DiscoveredService) {
        val isFirstEnable = !sonyLocationServiceEnabled
        val protocolVersion = camera.bleProtocolVersion
        val requiresUnlock =
            protocolVersion != null &&
                protocolVersion >= SonyCameraVendor.PROTOCOL_VERSION_REQUIRES_UNLOCK

        // Find DD30 (Lock Location Endpoint) characteristic
        val lockChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID
            }

        // Find DD31 (Enable Location Update) characteristic
        val enableChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID
            }

        val hasUnlockChars = lockChar != null && enableChar != null

        // Log protocol version info (only on first enable)
        if (isFirstEnable) {
            Log.info(tag = TAG) {
                "Sony camera ${camera.macAddress}: protocol version=$protocolVersion, " +
                    "requires unlock=$requiresUnlock, has DD30/DD31=$hasUnlockChars"
            }
        }

        // If DD30/DD31 don't exist, this camera uses the older protocol - no action needed
        if (!hasUnlockChars) {
            if (isFirstEnable) {
                if (requiresUnlock) {
                    Log.warn(tag = TAG) {
                        "Sony camera ${camera.macAddress} protocol version $protocolVersion requires " +
                            "DD30/DD31 unlock but characteristics not found! Location sync may not work."
                    }
                } else {
                    Log.debug(tag = TAG) {
                        "Sony camera ${camera.macAddress} uses legacy protocol (no unlock required)"
                    }
                }
                // Read capabilities even for legacy protocol (if available)
                sonyCapabilities = readSonyCapabilities(service)
            }
            sonyLocationServiceEnabled = true
            return
        }

        // Even if protocol version < 65, if DD30/DD31 exist, use them (defensive approach)
        // At this point we know both lockChar and enableChar are non-null (hasUnlockChars == true)
        if (isFirstEnable) {
            Log.info(tag = TAG) {
                "Enabling Sony location service for ${camera.macAddress} via DD30/DD31 unlock sequence"
            }
        } else {
            Log.debug(tag = TAG) {
                "Re-enabling Sony location service for ${camera.macAddress} (keep-alive)"
            }
        }

        // Phase 1, Step 0: Subscribe to DD01 notifications FIRST (per Sony decompiled code)
        // Only subscribe on first enable - subscription persists across re-enables
        if (isFirstEnable) {
            val notifyChar =
                service.characteristics.firstOrNull {
                    it.characteristicUuid == SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID
                }
            if (notifyChar != null) {
                Log.debug(tag = TAG) { "Subscribing to DD01 notifications" }
                @Suppress("TooGenericExceptionCaught")
                try {
                    // Cancel any existing observation job before starting a new one
                    sonyNotificationObservationJob?.cancel()
                    // Start observing DD01 in a background coroutine
                    // This enables notifications (writes CCCD) and collects status updates
                    // Use SupervisorJob so cancellation can be controlled independently
                    val supervisorJob = SupervisorJob(currentCoroutineContext()[Job])
                    val observeScope = CoroutineScope(currentCoroutineContext() + supervisorJob)
                    sonyNotificationObservationJob = supervisorJob
                    observeScope.launch {
                        peripheral.observe(notifyChar).collect { data ->
                            Log.info(tag = TAG) {
                                "DD01 notification received: ${data.toHexString()}"
                            }
                            // Check for LOCATION_TRANSFER_DISABLE (camera turned off location)
                            if (data.contentEquals(byteArrayOf(0x00))) {
                                Log.warn(tag = TAG) {
                                    "Camera disabled location transfer (DD01 = 0x00)"
                                }
                            }
                        }
                    }
                    // Give time for the CCCD write to complete
                    delay(100)
                } catch (e: Exception) {
                    // Non-fatal: continue even if subscription fails
                    Log.warn(tag = TAG, throwable = e) {
                        "Failed to subscribe to DD01, continuing anyway"
                    }
                }
            } else {
                Log.debug(tag = TAG) {
                    "DD01 (Status Notify) characteristic not found, skipping subscription"
                }
            }
        }

        // Phase 1, Step 1: Lock Session - Write 0x01 to DD30
        // Always re-write to keep the session alive
        Log.debug(tag = TAG) { "Writing 0x01 to DD30 (Lock Session)" }
        withTimeout(BLE_WRITE_TIMEOUT_MS) {
            peripheral.write(lockChar, byteArrayOf(0x01), WriteType.WithResponse)
        }

        // Phase 1, Step 2: Enable Transfer - Write 0x01 to DD31
        // Always re-write to keep the transfer enabled
        Log.debug(tag = TAG) { "Writing 0x01 to DD31 (Enable Transfer)" }
        withTimeout(BLE_WRITE_TIMEOUT_MS) {
            peripheral.write(enableChar, byteArrayOf(0x01), WriteType.WithResponse)
        }

        // Phase 1, Step 3: Read Capabilities (only on first enable - they don't change)
        if (isFirstEnable) {
            sonyCapabilities = readSonyCapabilities(service)
            Log.info(tag = TAG) { "Sony location service enabled for ${camera.macAddress}" }
            // Longer delay before first location write - Sony app uses callback flow which
            // introduces a natural delay. Camera may need time to transition to ready state.
            delay(500)
        }

        sonyLocationServiceEnabled = true
    }

    /**
     * Reads Sony camera capabilities from DD32 (Time Correction), DD33 (Area Adjustment), and DD21
     * (Camera Info) characteristics.
     *
     * Per Sony decompiled code, the read order is: DD32 -> DD33 -> DD21. These should be read after
     * locking and enabling the location service.
     *
     * @return SonyCapabilities containing the parsed data, or null if reading fails
     */
    private suspend fun readSonyCapabilities(
        service: com.juul.kable.DiscoveredService
    ): SonyCapabilities? {
        // Find capability characteristics
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

        var requiresTimezone = true // Default to including timezone
        var timeCorrection: ByteArray? = null
        var areaAdjustment: ByteArray? = null

        // Read DD32 (Time Correction) - FIRST per Sony app order
        if (timeCorrectionChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                timeCorrection =
                    withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(timeCorrectionChar) }
                Log.info(tag = TAG) { "Sony DD32 time correction: ${timeCorrection.toHexString()}" }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD32 time correction" }
            }
        } else {
            Log.debug(tag = TAG) { "DD32 (Time Correction) characteristic not found" }
        }

        // Read DD33 (Area Adjustment) - SECOND per Sony app order
        if (areaAdjustmentChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                areaAdjustment =
                    withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(areaAdjustmentChar) }
                Log.info(tag = TAG) { "Sony DD33 area adjustment: ${areaAdjustment.toHexString()}" }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD33 area adjustment" }
            }
        } else {
            Log.debug(tag = TAG) { "DD33 (Area Adjustment) characteristic not found" }
        }

        // Read DD21 (Camera Info / Capability Info) - LAST per Sony app order
        if (configChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val configData = withTimeout(BLE_WRITE_TIMEOUT_MS) { peripheral.read(configChar) }
                requiresTimezone =
                    (protocol as? dev.sebastiano.camerasync.vendors.sony.SonyProtocol)
                        ?.parseConfigRequiresTimezone(configData) ?: true
                Log.info(tag = TAG) {
                    "Sony DD21 capabilities: requiresTimezone=$requiresTimezone, " +
                        "raw=${configData.toHexString()}"
                }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read DD21 capabilities" }
            }
        } else {
            Log.debug(tag = TAG) { "DD21 (Camera Info) characteristic not found" }
        }

        return SonyCapabilities(
            requiresTimezone = requiresTimezone,
            timeCorrection = timeCorrection,
            areaAdjustment = areaAdjustment,
        )
    }

    /** Converts a ByteArray to a hex string for logging. */
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    /**
     * Disables the Sony location service by writing to DD31 and DD30.
     *
     * Should be called before disconnecting from Sony cameras that use the newer protocol.
     */
    private suspend fun disableSonyLocationService() {
        if (!sonyLocationServiceEnabled || camera.vendor.vendorId != "sony") return

        // Cancel the notification observation job to prevent resource leaks
        sonyNotificationObservationJob?.cancel()
        sonyNotificationObservationJob = null

        val service =
            peripheral.services.value.orEmpty().firstOrNull {
                it.serviceUuid == gattSpec.locationServiceUuid
            } ?: return

        val lockChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID
            }
        val enableChar =
            service.characteristics.firstOrNull {
                it.characteristicUuid == SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID
            }

        // Only disable if characteristics exist (newer protocol)
        if (lockChar != null && enableChar != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                Log.debug(tag = TAG) { "Disabling Sony location service for ${camera.macAddress}" }
                // Write 0x00 to DD31 first, then DD30
                // Per spec: 30-second timeout for BLE write operations
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(enableChar, byteArrayOf(0x00), WriteType.WithResponse)
                }
                withTimeout(BLE_WRITE_TIMEOUT_MS) {
                    peripheral.write(lockChar, byteArrayOf(0x00), WriteType.WithResponse)
                }
                Log.info(tag = TAG) { "Sony location service disabled for ${camera.macAddress}" }
            } catch (e: Exception) {
                // BLE writes can fail for various reasons during disconnect; just log and continue
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to disable Sony location service for ${camera.macAddress}"
                }
            }
        }

        sonyLocationServiceEnabled = false
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun disconnect() {
        Log.info(tag = TAG) { "Disconnecting from ${camera.name}" }
        // Disable Sony location service before disconnecting (recommended by protocol)
        disableSonyLocationService()
        peripheral.disconnect()
    }

    companion object {
        private const val TAG = "KableCameraConnection"

        /** Timeout for BLE write operations per Sony protocol spec (30 seconds). */
        private const val BLE_WRITE_TIMEOUT_MS = 30_000L

        /** Maximum number of retries for DD11 location writes per Sony protocol spec. */
        private const val LOCATION_WRITE_MAX_RETRIES = 3
    }

    private fun locationWriteType(): WriteType =
        when (camera.vendor.vendorId) {
            // Sony cameras: Use WithResponse (per Sony Creators' App which uses
            // the characteristic's default write type, typically WRITE_TYPE_DEFAULT)
            "sony" -> WriteType.WithResponse
            else -> WriteType.WithResponse
        }
}
