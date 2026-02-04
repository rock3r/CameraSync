package dev.sebastiano.camerasync.devicesync

import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "LocationCollector"

/**
 * Default implementation of [LocationCollectionCoordinator].
 *
 * Manages location collection lifecycle based on registered devices. Location updates are collected
 * from a [LocationRepository] and exposed to all registered consumers.
 *
 * @param locationRepository The repository providing location updates.
 * @param coroutineScope Scope for launching collection coroutines.
 */
class DefaultLocationCollector
@AssistedInject
constructor(
    private val locationRepository: LocationRepository,
    @Assisted private val coroutineScope: CoroutineScope,
) : LocationCollectionCoordinator {

    /** Factory for creating [DefaultLocationCollector] with assisted injection. */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DefaultLocationCollector].
         *
         * @param coroutineScope Scope for launching collection coroutines.
         * @return The created collector.
         */
        fun create(coroutineScope: CoroutineScope): DefaultLocationCollector
    }

    private val _locationUpdates = MutableStateFlow<GpsLocation?>(null)
    override val locationUpdates: StateFlow<GpsLocation?> = _locationUpdates.asStateFlow()

    private val _isCollecting = MutableStateFlow(false)
    override val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val registeredDevices = ConcurrentHashMap.newKeySet<String>()
    private var collectionJob: Job? = null

    override fun startCollecting() {
        if (collectionJob != null) {
            Log.debug(tag = TAG) { "Location collection already active" }
            return
        }

        Log.info(tag = TAG) { "Starting location collection" }
        _isCollecting.value = true
        locationRepository.startLocationUpdates()

        collectionJob =
            coroutineScope.launch {
                locationRepository.locationUpdates.collect { location ->
                    _locationUpdates.value = location
                    if (location != null) {
                        Log.debug(tag = TAG) {
                            "New location: ${location.latitude}, ${location.longitude}"
                        }
                    }
                }
            }
    }

    override fun stopCollecting() {
        Log.info(tag = TAG) { "Stopping location collection" }
        collectionJob?.cancel()
        collectionJob = null
        locationRepository.stopLocationUpdates()
        _isCollecting.value = false
    }

    override fun registerDevice(deviceId: String) {
        val wasEmpty = registeredDevices.isEmpty()
        registeredDevices.add(deviceId)
        Log.info(tag = TAG) { "Device registered: $deviceId (total: ${registeredDevices.size})" }

        if (wasEmpty) {
            startCollecting()
        }
    }

    override fun unregisterDevice(deviceId: String) {
        registeredDevices.remove(deviceId)
        Log.info(tag = TAG) { "Device unregistered: $deviceId (total: ${registeredDevices.size})" }

        if (registeredDevices.isEmpty()) {
            stopCollecting()
        }
    }

    override fun getRegisteredDeviceCount(): Int = registeredDevices.size
}
