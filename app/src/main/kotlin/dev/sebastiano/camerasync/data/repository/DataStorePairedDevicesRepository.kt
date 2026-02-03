package dev.sebastiano.camerasync.data.repository

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.proto.PairedDeviceProto
import dev.sebastiano.camerasync.proto.PairedDevicesProto
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-based implementation of [PairedDevicesRepository].
 *
 * Uses Protocol Buffers for efficient binary serialization of paired devices.
 */
class DataStorePairedDevicesRepository(private val dataStore: DataStore<PairedDevicesProto>) :
    PairedDevicesRepository {

    override val pairedDevices: Flow<List<PairedDevice>> =
        dataStore.data.map { proto -> proto.devicesList.map { it.toDomain() } }

    override val enabledDevices: Flow<List<PairedDevice>> =
        pairedDevices.map { devices -> devices.filter { it.isEnabled } }

    override val isSyncEnabled: Flow<Boolean> =
        dataStore.data.map { proto -> if (proto.hasSyncEnabled()) proto.syncEnabled else true }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().setSyncEnabled(enabled).build()
        }
    }

    override suspend fun addDevice(camera: Camera, enabled: Boolean) {
        dataStore.updateData { currentData ->
            // Check if device already exists
            val existingIndex =
                currentData.devicesList.indexOfFirst { it.macAddress == camera.macAddress }

            val newDevice =
                PairedDeviceProto.newBuilder()
                    .setMacAddress(camera.macAddress)
                    .setVendorId(camera.vendor.vendorId)
                    .setEnabled(enabled)
                    .apply { camera.name?.let { setName(it) } }
                    .build()

            if (existingIndex >= 0) {
                // Update existing device
                currentData.toBuilder().setDevices(existingIndex, newDevice).build()
            } else {
                // Add new device
                currentData.toBuilder().addDevices(newDevice).build()
            }
        }
    }

    override suspend fun removeDevice(macAddress: String) {
        dataStore.updateData { currentData ->
            val filteredDevices = currentData.devicesList.filter { it.macAddress != macAddress }

            currentData.toBuilder().clearDevices().addAllDevices(filteredDevices).build()
        }
    }

    override suspend fun setDeviceEnabled(macAddress: String, enabled: Boolean) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex].toBuilder().setEnabled(enabled).build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }

    override suspend fun updateDeviceName(macAddress: String, name: String?) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex]
                    .toBuilder()
                    .apply { if (name != null) setName(name) else clearName() }
                    .build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }

    override suspend fun updateLastSyncedAt(macAddress: String, timestamp: Long) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex].toBuilder().setLastSyncedAt(timestamp).build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }

    override suspend fun isDevicePaired(macAddress: String): Boolean {
        return dataStore.data.first().devicesList.any { it.macAddress == macAddress }
    }

    override suspend fun getDevice(macAddress: String): PairedDevice? {
        return dataStore.data.first().devicesList.find { it.macAddress == macAddress }?.toDomain()
    }

    override suspend fun hasAnyDevices(): Boolean {
        return dataStore.data.first().devicesList.isNotEmpty()
    }

    override suspend fun hasEnabledDevices(): Boolean {
        return dataStore.data.first().devicesList.any { it.enabled }
    }

    override suspend fun updateFirmwareVersion(macAddress: String, firmwareVersion: String?) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex]
                    .toBuilder()
                    .apply {
                        if (firmwareVersion != null) {
                            setFirmwareVersion(firmwareVersion)
                        } else {
                            clearFirmwareVersion()
                        }
                    }
                    .build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }

    override suspend fun setFirmwareUpdateInfo(macAddress: String, latestVersion: String?) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex]
                    .toBuilder()
                    .apply {
                        if (latestVersion != null) {
                            setLatestFirmwareVersion(latestVersion)
                            // Clear notification shown flag when new update is found
                            setFirmwareUpdateNotificationShown(false)
                        } else {
                            clearLatestFirmwareVersion()
                            setFirmwareUpdateNotificationShown(false)
                        }
                    }
                    .build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }

    override suspend fun setFirmwareUpdateNotificationShown(macAddress: String) {
        dataStore.updateData { currentData ->
            val deviceIndex = currentData.devicesList.indexOfFirst { it.macAddress == macAddress }

            if (deviceIndex < 0) return@updateData currentData

            val updatedDevice =
                currentData.devicesList[deviceIndex]
                    .toBuilder()
                    .setFirmwareUpdateNotificationShown(true)
                    .build()

            currentData.toBuilder().setDevices(deviceIndex, updatedDevice).build()
        }
    }
}

/** Converts a proto [PairedDeviceProto] to a domain [PairedDevice]. */
private fun PairedDeviceProto.toDomain(): PairedDevice =
    PairedDevice(
        macAddress = macAddress,
        name = if (hasName()) name else null,
        vendorId = vendorId,
        isEnabled = enabled,
        lastSyncedAt = if (hasLastSyncedAt()) lastSyncedAt else null,
        firmwareVersion = if (hasFirmwareVersion()) firmwareVersion else null,
        latestFirmwareVersion = if (hasLatestFirmwareVersion()) latestFirmwareVersion else null,
        firmwareUpdateNotificationShown = firmwareUpdateNotificationShown,
    )

/** Serializer for [PairedDevicesProto] used by DataStore. */
internal object PairedDevicesProtoSerializer : Serializer<PairedDevicesProto> {
    override val defaultValue: PairedDevicesProto = PairedDevicesProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PairedDevicesProto {
        try {
            return PairedDevicesProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read paired devices proto.", exception)
        }
    }

    override suspend fun writeTo(t: PairedDevicesProto, output: OutputStream) {
        t.writeTo(output)
    }
}

/** Extension property to access the paired devices DataStore from a Context. */
val Context.pairedDevicesDataStoreV2: DataStore<PairedDevicesProto> by
    dataStore(fileName = "paired-devices-v2.pb", serializer = PairedDevicesProtoSerializer)
