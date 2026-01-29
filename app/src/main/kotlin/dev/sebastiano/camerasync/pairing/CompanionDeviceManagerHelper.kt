package dev.sebastiano.camerasync.pairing

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "CompanionDeviceManagerHelper"

/** Helper class for interacting with the Companion Device Manager API. */
@OptIn(ExperimentalUuidApi::class)
class CompanionDeviceManagerHelper(
    private val context: Context,
    private val vendorRegistry: CameraVendorRegistry,
) {

    private val deviceManager: CompanionDeviceManager by lazy {
        context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }

    /**
     * Gets all devices associated with this app via CompanionDeviceManager. Presence observations
     * only work for associated devices.
     */
    fun getAssociatedDevices(): List<AssociationInfo> =
        try {
            val associations = deviceManager.myAssociations
            Log.info(tag = TAG) {
                "CDM associated devices: ${associations.size} total. " +
                    "MACs: ${associations.mapNotNull { it.deviceMacAddress?.toString()?.uppercase() }}"
            }
            associations
        } catch (e: SecurityException) {
            Log.warn(tag = TAG, throwable = e) {
                "SecurityException accessing CDM associations (permission may be missing)"
            }
            emptyList()
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Failed to get CDM associations" }
            emptyList()
        }

    /** Gets the association info for a device by MAC address. */
    fun getAssociationInfo(macAddress: String): AssociationInfo? {
        val associatedDevices = getAssociatedDevices()
        return associatedDevices.firstOrNull { association ->
            association.deviceMacAddress?.toString()?.uppercase() == macAddress.uppercase()
        }
    }

    /**
     * Checks if a device is associated with this app via CompanionDeviceManager. Presence
     * observations only work for associated devices.
     */
    fun isDeviceAssociated(macAddress: String): Boolean {
        val associatedDevices = getAssociatedDevices()
        Log.info(tag = TAG) {
            "Checking CDM association for $macAddress. " +
                "Total associated devices: ${associatedDevices.size}. " +
                "Associated MACs: ${associatedDevices.mapNotNull { it.deviceMacAddress?.toString()?.uppercase() }}"
        }
        val isAssociated = getAssociationInfo(macAddress) != null
        if (!isAssociated) {
            Log.warn(tag = TAG) {
                "Device $macAddress is NOT associated with CDM. " +
                    "Presence observations will not work. " +
                    "Device must be paired through CompanionDeviceManager association flow. " +
                    "Associated devices: ${associatedDevices.map { it.deviceMacAddress }}"
            }
        } else {
            Log.info(tag = TAG) {
                "Device $macAddress IS associated with CDM. Presence observations should work."
            }
        }
        return isAssociated
    }

    /**
     * Initiates the association request for supported cameras.
     *
     * @param callback The callback to receive the IntentSender
     */
    fun requestAssociation(callback: CompanionDeviceManager.Callback) {
        val request = buildAssociationRequest()

        Log.info(tag = TAG) { "Requesting association with Companion Device Manager" }
        deviceManager.associate(request, context.mainExecutor, callback)
    }

    // Note: Presence observation methods removed - CDM callbacks are unreliable.
    // The app uses periodic checks in MultiDeviceSyncCoordinator instead.
    // CDM is only used for the pairing flow (requestAssociation).

    private fun buildAssociationRequest(): AssociationRequest {
        val builder = AssociationRequest.Builder()

        vendorRegistry.getAllVendors().forEach { vendor ->
            vendor.getCompanionDeviceFilters().forEach { filter -> builder.addDeviceFilter(filter) }
        }

        return builder.setSingleDevice(true).build()
    }
}
