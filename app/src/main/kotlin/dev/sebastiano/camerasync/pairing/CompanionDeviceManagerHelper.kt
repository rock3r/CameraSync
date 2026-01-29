package dev.sebastiano.camerasync.pairing

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
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

    /** Starts observing device presence to keep the app active when the device is in range. */
    fun startObservingDevicePresence(deviceAddress: String) {
        Log.info(tag = TAG) { "Attempting to start presence observation for $deviceAddress" }

        // Check if device is actually associated with CDM first
        val associationInfo = getAssociationInfo(deviceAddress)
        if (associationInfo == null) {
            Log.error(tag = TAG) {
                "Cannot start presence observation for $deviceAddress: device is not associated with CDM. " +
                    "Presence observations only work for devices paired through CompanionDeviceManager. " +
                    "The device may need to be re-paired through the app's pairing flow."
            }
            // Try anyway - sometimes CDM might still work even if not in myAssociations
            Log.warn(tag = TAG) {
                "Attempting to start presence observation anyway - CDM might still work"
            }
        }

        try {
            // Android 16+ (API 36+): Use new ObservingDevicePresenceRequest API
            if (associationInfo == null) {
                Log.error(tag = TAG) {
                    "Cannot use new API without association info for $deviceAddress"
                }
                return
            }
            val request =
                ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(associationInfo.id)
                    .build()
            Log.info(tag = TAG) {
                "Calling deviceManager.startObservingDevicePresence(request) for association ID ${associationInfo.id} ($deviceAddress)"
            }
            deviceManager.startObservingDevicePresence(request)
            Log.info(tag = TAG) {
                "Successfully started presence observation for $deviceAddress (API 36+). " +
                    "CDM will automatically bind CompanionPresenceService and call onDeviceEvent() when device appears."
            }
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) {
                "SecurityException starting presence observation for $deviceAddress. " +
                    "Check REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE permission."
            }
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) {
                "IllegalArgumentException starting presence observation for $deviceAddress. " +
                    "Device may not be associated with CDM or address format is invalid."
            }
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) {
                "Failed to start observing device presence for $deviceAddress"
            }
        }
    }

    /** Stops observing device presence. */
    fun stopObservingDevicePresence(deviceAddress: String) {
        try {
            Log.info(tag = TAG) { "Stopping presence observation for $deviceAddress" }
            // Android 16+ (API 36+): Use new API with association ID
            val associationInfo = getAssociationInfo(deviceAddress)
            if (associationInfo != null) {
                val request =
                    ObservingDevicePresenceRequest.Builder()
                        .setAssociationId(associationInfo.id)
                        .build()
                deviceManager.stopObservingDevicePresence(request)
                Log.info(tag = TAG) {
                    "Stopped presence observation for $deviceAddress using new API (association ID ${associationInfo.id})"
                }
            } else {
                Log.warn(tag = TAG) {
                    "Cannot stop presence observation for $deviceAddress: no association info found"
                }
            }
        } catch (e: Exception) {
            Log.error(tag = TAG, throwable = e) { "Failed to stop observing device presence" }
        }
    }

    private fun buildAssociationRequest(): AssociationRequest {
        val builder = AssociationRequest.Builder()

        vendorRegistry.getAllVendors().forEach { vendor ->
            vendor.getCompanionDeviceFilters().forEach { filter -> builder.addDeviceFilter(filter) }
        }

        return builder.setSingleDevice(true).build()
    }
}
