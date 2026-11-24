package dev.sebastiano.camerasync.pairing

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "CompanionDeviceManagerHelper"

/** Interface for interacting with the Companion Device Manager API. */
interface CompanionDeviceManagerHelper {
    /**
     * Initiates the association request for supported cameras.
     *
     * @param callback The callback to receive the IntentSender
     * @param macAddress Optional MAC address to target a specific device. If provided, filters will
     *   be restricted to this device.
     */
    fun requestAssociation(callback: CompanionDeviceManager.Callback, macAddress: String? = null)
}

/** Android implementation of [CompanionDeviceManagerHelper]. */
@OptIn(ExperimentalUuidApi::class)
class AndroidCompanionDeviceManagerHelper(
    private val context: Context,
    private val vendorRegistry: CameraVendorRegistry,
) : CompanionDeviceManagerHelper {
    private val deviceManager: CompanionDeviceManager by lazy {
        context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }

    override fun requestAssociation(
        callback: CompanionDeviceManager.Callback,
        macAddress: String?,
    ) {
        val request = buildAssociationRequest(macAddress)

        Log.info(tag = TAG) {
            "Requesting association with Companion Device Manager (target: $macAddress)"
        }
        deviceManager.associate(request, context.mainExecutor, callback)
    }

    private fun buildAssociationRequest(macAddress: String?): AssociationRequest {
        val builder = AssociationRequest.Builder()

        if (macAddress != null) {
            // Target specific device by MAC address
            val deviceFilter =
                android.companion.BluetoothDeviceFilter.Builder().setAddress(macAddress).build()
            builder.addDeviceFilter(deviceFilter)

            // Also add BLE filter for the specific device
            val bleFilter =
                android.companion.BluetoothLeDeviceFilter.Builder()
                    .setScanFilter(
                        android.bluetooth.le.ScanFilter.Builder()
                            .setDeviceAddress(macAddress)
                            .build()
                    )
                    .build()
            builder.addDeviceFilter(bleFilter)
        } else {
            // Generic filters for all supported vendors
            vendorRegistry.getAllVendors().forEach { vendor ->
                vendor.getCompanionDeviceFilters().forEach { filter ->
                    builder.addDeviceFilter(filter)
                }
            }
        }

        return builder.setSingleDevice(true).build()
    }
}
