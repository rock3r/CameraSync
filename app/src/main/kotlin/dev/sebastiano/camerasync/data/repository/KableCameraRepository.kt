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
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

private const val TAG = "KableCameraRepository"

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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
            // Give bonded cameras time to start advertising after power-on
            // This is especially important when cameras are turned on while the app is running
            // We already have a delay in setDevicePresence, but add extra time here too
            delay(5_000L)
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
        throw IllegalStateException(errorMessage, lastException)
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

        Log.info(tag = TAG) { "Discovered ${vendor.vendorName} camera: $peripheralName" }
        return Camera(
            identifier = identifier,
            name = peripheralName,
            macAddress = identifier, // On Android, identifier is the MAC address
            vendor = vendor,
        )
    }
}

/**
 * Implementation of [CameraConnection] using a Kable Peripheral.
 *
 * This implementation is vendor-agnostic and uses the camera's vendor specification to interact
 * with the camera's BLE services.
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
        } catch (e: Exception) {
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
                ?: throw IllegalStateException("Hardware revision service not found: $serviceUuid")

        val char =
            service.characteristics.firstOrNull { it.characteristicUuid == charUuid }
                ?: throw IllegalStateException(
                    "Hardware revision characteristic not found: $charUuid"
                )

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
        peripheral.write(char, data, locationWriteType())
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

        val char =
            service.characteristics.firstOrNull {
                it.characteristicUuid == gattSpec.locationCharacteristicUuid
            }
                ?: throw IllegalStateException(
                    "Location characteristic not found. Characteristic UUID: ${gattSpec.locationCharacteristicUuid}. " +
                        "Available characteristics in service ${service.serviceUuid}: ${service.characteristics.map { it.characteristicUuid }}"
                )

        val data = protocol.encodeLocation(location)
        Log.info(tag = TAG) { "Syncing location: ${protocol.decodeLocation(data)}" }
        peripheral.write(char, data, locationWriteType())
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun disconnect() {
        Log.info(tag = TAG) { "Disconnecting from ${camera.name}" }
        peripheral.disconnect()
    }

    companion object {
        private const val TAG = "KableCameraConnection"
    }

    private fun locationWriteType(): WriteType =
        when (camera.vendor.vendorId) {
            "sony" -> WriteType.WithoutResponse
            else -> WriteType.WithResponse
        }
}
