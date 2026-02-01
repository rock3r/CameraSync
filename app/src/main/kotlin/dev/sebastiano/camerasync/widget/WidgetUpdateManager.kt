package dev.sebastiano.camerasync.widget

import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.repository.SyncStatusRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "WidgetUpdateManager"

/**
 * Manager that observes sync-related state and triggers widget updates reactively. This ensures
 * that widgets always reflect the latest state without needing manual update calls everywhere state
 * is modified.
 */
@Inject
class WidgetUpdateManager(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val widgetUpdateHelper: WidgetUpdateHelper,
) {
    /**
     * Starts observing state changes and updating widgets.
     *
     * @param scope The scope in which to run the observation.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            Log.info(tag = TAG) { "Starting widget update observation" }
            combine(
                    pairedDevicesRepository.isSyncEnabled,
                    syncStatusRepository.connectedDevicesCount,
                    syncStatusRepository.isSearching,
                ) { isSyncEnabled, connectedCount, isSearching ->
                    Triple(isSyncEnabled, connectedCount, isSearching)
                }
                .collect { (isSyncEnabled, connectedCount, isSearching) ->
                    Log.debug(tag = TAG) {
                        "State changed: enabled=$isSyncEnabled, connected=$connectedCount, searching=$isSearching. Triggering widget update."
                    }
                    widgetUpdateHelper.updateWidgets()
                }
        }
    }
}
