package dev.sebastiano.camerasync.devicesync

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.zacsweers.metro.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** A Quick Settings Tile for toggling the global sync state. */
@Inject
class SyncTileService(private val pairedDevicesRepository: PairedDevicesRepository) :
    TileService(), CoroutineScope {

    override val coroutineContext: CoroutineContext =
        Dispatchers.Main + CoroutineName("SyncTileService") + SupervisorJob()

    private var observationJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observationJob?.cancel()
        observationJob =
            pairedDevicesRepository.isSyncEnabled
                .onEach { isEnabled -> updateTile(isEnabled) }
                .launchIn(this)
    }

    override fun onStopListening() {
        super.onStopListening()
        observationJob?.cancel()
        observationJob = null
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val currentlyEnabled = tile.state == Tile.STATE_ACTIVE

        launch { pairedDevicesRepository.setSyncEnabled(!currentlyEnabled) }
    }

    private fun updateTile(isEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
