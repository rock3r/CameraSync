package dev.sebastiano.camerasync.widget.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import kotlinx.coroutines.flow.first

@Composable
internal fun SyncToggle(enabled: Boolean) {
    Box(
        modifier =
            GlanceModifier.size(width = 48.dp, height = 32.dp)
                .background(
                    if (enabled) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.outline
                    }
                )
                .cornerRadius(16.dp)
                .padding(3.dp)
                .clickable(actionRunCallback<ToggleSyncAction>()),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier =
                GlanceModifier.size(width = 42.dp, height = 26.dp)
                    .background(
                        if (enabled) {
                            GlanceTheme.colors.primary
                        } else {
                            GlanceTheme.colors.surfaceVariant
                        }
                    )
                    .cornerRadius(14.dp)
        ) {
            // Intentionally empty (track color)
        }

        Box(GlanceModifier.size(26.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    GlanceModifier.size(if (enabled) 24.dp else 16.dp)
                        .background(
                            if (enabled) {
                                GlanceTheme.colors.onPrimary
                            } else {
                                GlanceTheme.colors.outline
                            }
                        )
                        .cornerRadius(if (enabled) 12.dp else 8.dp)
            ) {
                // Intentionally empty (handle color)
            }
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
        Log.info(javaClass.name) { "Toggling sync; new enabled state: $newState" }
        repository.setSyncEnabled(newState)

        if (newState) {
            context.startForegroundService(MultiDeviceSyncService.createStartIntent(context))
        } else {
            context.startService(MultiDeviceSyncService.createStopIntent(context))
        }
    }
}
