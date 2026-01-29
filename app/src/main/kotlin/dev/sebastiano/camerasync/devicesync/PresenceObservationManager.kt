package dev.sebastiano.camerasync.devicesync

import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PresenceObservationManager"

/**
 * Manages CDM presence observations for ALL paired devices.
 *
 * This manager ensures presence observations are ALWAYS active whenever the app is running,
 * regardless of:
 * - Whether sync is enabled
 * - Whether devices are enabled
 * - Whether the service is running
 * - Whether the app is in foreground or background
 *
 * Presence observations must be active BEFORE devices appear for CDM callbacks to work.
 */
class PresenceObservationManager(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val companionDeviceManagerHelper: CompanionDeviceManagerHelper,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var observedMacAddresses = emptySet<String>()

    /**
     * Starts observing presence for all paired devices and keeps observations updated as the paired
     * devices list changes.
     */
    fun start() {
        Log.info(tag = TAG) { "Starting presence observation manager" }
        coroutineScope.launch {
            // Start observations immediately for any existing paired devices
            val initialDevices = pairedDevicesRepository.pairedDevices.first()
            if (initialDevices.isNotEmpty()) {
                Log.info(tag = TAG) {
                    "Starting initial presence observations for ${initialDevices.size} paired devices"
                }
                updateObservations(initialDevices.map { it.macAddress }.toSet())
            }

            // Then keep observations updated as devices are added/removed
            pairedDevicesRepository.pairedDevices.distinctUntilChanged().collect { pairedDevices ->
                updateObservations(pairedDevices.map { it.macAddress }.toSet())
            }
        }
    }

    /** Stops all presence observations and cancels the manager. */
    fun stop() {
        Log.info(tag = TAG) { "Stopping presence observation manager" }
        updateObservations(emptySet())
        coroutineScope.cancel()
    }

    private fun updateObservations(newMacAddresses: Set<String>) {
        val toStart = newMacAddresses - observedMacAddresses
        val toStop = observedMacAddresses - newMacAddresses

        Log.info(tag = TAG) {
            "Updating presence observations: start=${toStart.size}, stop=${toStop.size}, total=${newMacAddresses.size}. " +
                "MACs: $newMacAddresses"
        }

        if (toStart.isNotEmpty()) {
            Log.info(tag = TAG) { "Starting presence observations for: $toStart" }
        }

        toStart.forEach { macAddress ->
            try {
                companionDeviceManagerHelper.startObservingDevicePresence(macAddress)
                Log.info(tag = TAG) { "Started presence observation for $macAddress" }
            } catch (e: Exception) {
                Log.error(tag = TAG, throwable = e) {
                    "Failed to start presence observation for $macAddress"
                }
            }
        }

        toStop.forEach { macAddress ->
            try {
                companionDeviceManagerHelper.stopObservingDevicePresence(macAddress)
                Log.info(tag = TAG) { "Stopped presence observation for $macAddress" }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to stop presence observation for $macAddress"
                }
            }
        }

        observedMacAddresses = newMacAddresses
    }
}
