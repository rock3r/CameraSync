package dev.sebastiano.camerasync.pairing

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.DeviceFilter
import android.content.Context
import android.content.IntentSender
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import java.util.concurrent.Executor
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "CompanionDeviceManagerHelper"

/**
 * Helper class for interacting with the Companion Device Manager API.
 */
@OptIn(ExperimentalUuidApi::class)
class CompanionDeviceManagerHelper(
    private val context: Context,
    private val vendorRegistry: CameraVendorRegistry,
) {

    private val deviceManager: CompanionDeviceManager by lazy {
        context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }

    /**
     * Initiates the association request for supported cameras.
     *
     * @param callback The callback to receive the IntentSender
     */
    fun requestAssociation(
        callback: CompanionDeviceManager.Callback
    ) {
        val request = buildAssociationRequest()
        
        Log.info(tag = TAG) { "Requesting association with Companion Device Manager" }
        deviceManager.associate(request, context.mainExecutor, callback)
    }

    /**
     * Starts observing device presence to keep the app active when the device is in range.
     */
    fun startObservingDevicePresence(deviceAddress: String) {
        try {
            Log.info(tag = TAG) { "Starting presence observation for $deviceAddress" }
            deviceManager.startObservingDevicePresence(deviceAddress)
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Failed to start observing device presence" }
        }
    }

    /**
     * Stops observing device presence.
     */
    fun stopObservingDevicePresence(deviceAddress: String) {
        try {
            Log.info(tag = TAG) { "Stopping presence observation for $deviceAddress" }
            deviceManager.stopObservingDevicePresence(deviceAddress)
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Failed to stop observing device presence" }
        }
    }

    private fun buildAssociationRequest(): AssociationRequest {
        val builder = AssociationRequest.Builder()
        
        vendorRegistry.getAllVendors().forEach { vendor ->
            vendor.getCompanionDeviceFilters().forEach { filter ->
                builder.addDeviceFilter(filter)
            }
        }

        return builder
            .setSingleDevice(true)
            .build()
    }
}
