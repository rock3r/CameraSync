package dev.sebastiano.camerasync.widget.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.text.Text
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import kotlinx.coroutines.flow.first

@Composable
internal fun TextSyncToggle(enabled: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.clickable(actionRunCallback<ToggleSyncAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sync")
            Text(if (enabled) "on" else "off")
        }
    }
}

internal class ToggleSyncAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repository =
            (context.applicationContext as CameraSyncApp).appGraph.pairedDevicesRepository()
        val isSyncing = repository.isSyncEnabled.first()
        val newState = !isSyncing
        Log.info(javaClass.simpleName) { "Toggling sync; new enabled state: $newState" }
        repository.setSyncEnabled(newState)

        if (newState) {
            context.startForegroundService(MultiDeviceSyncService.createStartIntent(context))
        } else {
            context.startService(MultiDeviceSyncService.createStopIntent(context))
        }
    }
}
