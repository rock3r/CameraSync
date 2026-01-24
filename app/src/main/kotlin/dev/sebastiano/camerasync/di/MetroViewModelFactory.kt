package dev.sebastiano.camerasync.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.sebastiano.camerasync.devices.DevicesListViewModel
import dev.sebastiano.camerasync.logging.LogViewerViewModel
import dev.sebastiano.camerasync.pairing.PairingViewModel

/**
 * A [ViewModelProvider.Factory] that uses the [AppGraph] to create ViewModels.
 *
 * This allows ViewModels to use constructor injection while still being managed by the ViewModel
 * lifecycle.
 */
class MetroViewModelFactory(private val appGraph: AppGraph) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DevicesListViewModel::class.java) ->
                appGraph.devicesListViewModel() as T
            modelClass.isAssignableFrom(PairingViewModel::class.java) ->
                appGraph.pairingViewModel() as T
            modelClass.isAssignableFrom(LogViewerViewModel::class.java) ->
                appGraph.logViewerViewModel() as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
