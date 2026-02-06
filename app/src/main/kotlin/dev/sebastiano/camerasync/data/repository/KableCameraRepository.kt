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
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.logging.KhronicleLogEngine
import java.io.IOException
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

            Log.info(tag = TAG) {
                "Scanning for advertisement for ${camera.macAddress} (attempt $attempt/$maxAttempts)..."
            }
            val scanner =
                com.juul.kable.Scanner {
                    @OptIn(ObsoleteKableApi::class)
                    filters { match { address = camera.macAddress } }
                }

            try {
                // Use longer timeout for bonded devices
                val timeout = if (isBonded) 20_000L else 10_000L
                val advertisement = withTimeout(timeout) { scanner.advertisements.first() }
                Log.info(tag = TAG) {
                    "Found advertisement for ${camera.name ?: camera.macAddress}: ${advertisement.identifier} (${advertisement.peripheralName})"
                }
                onFound?.invoke()

                // Delegate vendor-specific connection configuration (e.g. MTU)
                val delegate = camera.vendor.createConnectionDelegate()

                val peripheral =
                    Peripheral(advertisement) {
                        logging {
                            level = Logging.Level.Events
                            engine = KhronicleLogEngine
                            identifier = "CameraSync:${camera.vendor.vendorName}"
                        }

                        onServicesDiscovered {
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                val mtu = delegate.mtu
                                if (mtu != null) {
                                    requestMtu(mtu)
                                    Log.info(tag = TAG) {
                                        "Vendor-requested MTU request successful: $mtu"
                                    }
                                }
                            } catch (e: Exception) {
                                Log.warn(tag = TAG, throwable = e) {
                                    "Failed to request vendor MTU, continuing with default"
                                }
                            }
                        }
                    }

                Log.info(tag = TAG) { "Connecting to ${camera.name}..." }
                peripheral.connect()
                Log.info(tag = TAG) { "Connected to ${camera.name}" }

                // Notify delegate
                delegate.onConnected(peripheral, camera)

                return KableCameraConnection(camera, peripheral, delegate)
            } catch (e: TimeoutCancellationException) {
                lastException = e
                Log.warn(tag = TAG) {
                    "Timeout waiting for advertisement from ${camera.macAddress} (attempt $attempt/$maxAttempts)"
                }
                if (attempt < maxAttempts) {
                    continue
                }
            }
        }

        // All retries failed
        val errorMessage =
            if (isBonded) {
                "Timeout waiting for advertisement from ${camera.macAddress} after $maxAttempts attempts. " +
                    "The camera is bonded but not advertising."
            } else {
                "Timeout waiting for advertisement from ${camera.macAddress}. " +
                    "Make sure the camera is powered on and in range."
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

    private fun Advertisement.toCamera(): Camera? {
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

        val metadata = vendor.parseAdvertisementMetadata(mfrData)
        if (metadata.isNotEmpty()) {
            Log.info(tag = TAG) {
                "Discovered ${vendor.vendorName} camera $peripheralName metadata: $metadata"
            }
        }

        return Camera(
            identifier = identifier,
            name = peripheralName,
            macAddress = identifier,
            vendor = vendor,
            vendorMetadata = metadata,
        )
    }
}

/**
 * Implementation of [CameraConnection] using a Kable Peripheral.
 *
 * This implementation delegates most operations to the vendor-specific [VendorConnectionDelegate].
 */
@OptIn(ExperimentalUuidApi::class)
internal class KableCameraConnection(
    override val camera: Camera,
    private val peripheral: Peripheral,
    private val connectionDelegate: VendorConnectionDelegate,
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
                } ?: return false

            val char =
                service.characteristics.firstOrNull { it.characteristicUuid == pairingCharUuid }
                    ?: return false

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
            } ?: throw IllegalStateException("Firmware service not found")
        val char =
            service.characteristics.firstOrNull {
                it.characteristicUuid == gattSpec.firmwareVersionCharacteristicUuid
            } ?: throw IllegalStateException("Firmware characteristic not found")

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

    /** Syncs date/time to the camera. */
    override suspend fun syncDateTime(dateTime: ZonedDateTime) {
        if (!capabilities.supportsDateTimeSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support date/time synchronization"
            )
        }

        Log.debug(tag = TAG) { "Syncing date/time for ${camera.macAddress}" }
        Log.info(tag = TAG) {
            "Syncing date/time: ${protocol.decodeDateTime(protocol.encodeDateTime(dateTime))}"
        }

        // Delegate entire sync operation
        connectionDelegate.syncDateTime(peripheral, camera, dateTime)
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

    /** Syncs a GPS location to the camera. */
    override suspend fun syncLocation(location: GpsLocation) {
        if (!capabilities.supportsLocationSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support location synchronization"
            )
        }

        Log.debug(tag = TAG) { "Syncing location for ${camera.macAddress}" }
        Log.info(tag = TAG) {
            "Syncing location: ${protocol.decodeLocation(protocol.encodeLocation(location))}"
        }

        // Delegate entire sync operation
        connectionDelegate.syncLocation(peripheral, camera, location)
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun disconnect() {
        Log.info(tag = TAG) { "Disconnecting from ${camera.name}" }
        connectionDelegate.onDisconnecting(peripheral)
        peripheral.disconnect()
    }

    companion object {
        private const val TAG = "KableCameraConnection"
    }
}
