package dev.sebastiano.camerasync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.R.drawable.ic_linked_camera_24dp
import dev.sebastiano.camerasync.R.drawable.ic_photo_camera_24dp
import dev.sebastiano.camerasync.R.drawable.ic_refresh_24dp
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import kotlinx.coroutines.flow.first

class SyncWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val repository = appGraph.pairedDevicesRepository()
        val syncStatusRepository = appGraph.syncStatusRepository()

        provideContent {
            val isSyncEnabled by repository.isSyncEnabled.collectAsState(initial = false)
            val connectedCount by syncStatusRepository.connectedDevicesCount.collectAsState()

            GlanceTheme {
                SyncWidgetContent(isSyncEnabled = isSyncEnabled, connectedCount = connectedCount)
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                SyncWidgetContent(isSyncEnabled = true, connectedCount = 2)
            }
        }
    }
}

@Composable
private fun SyncWidgetContent(isSyncEnabled: Boolean, connectedCount: Int) {
    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(28.dp)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyncToggle(isSyncEnabled)

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(GlanceModifier.size(56.dp), contentAlignment = Alignment.Center) {
                val icon =
                    if (isSyncEnabled && connectedCount > 0) {
                        ic_linked_camera_24dp
                    } else {
                        ic_photo_camera_24dp
                    }

                Image(
                    provider = ImageProvider(icon),
                    contentDescription = null,
                    modifier = GlanceModifier.size(48.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )

                if (isSyncEnabled && connectedCount > 0) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            GlanceModifier.size(20.dp)
                                .background(GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                connectedCount.toString(),
                                style =
                                    TextDefaults.defaultTextStyle.copy(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                modifier =
                    GlanceModifier.size(48.dp)
                        .background(GlanceTheme.colors.tertiary)
                        .cornerRadius(24.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(ic_refresh_24dp),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary),
                )
            }
        }
    }
}

@Composable
private fun SyncToggle(enabled: Boolean) {
    Box(
        modifier =
            GlanceModifier.size(width = 48.dp, height = 32.dp)
                .background(if (enabled) GlanceTheme.colors.primary else GlanceTheme.colors.outline)
                .cornerRadius(16.dp)
                .padding(2.dp)
                .clickable(actionRunCallback<ToggleSyncAction>()),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier =
                GlanceModifier.size(width = 44.dp, height = 28.dp)
                    .background(
                        if (enabled) GlanceTheme.colors.primary
                        else GlanceTheme.colors.surfaceVariant
                    )
                    .cornerRadius(14.dp)
        ) {
            // Intentionally empty (track color)
        }

        Box(GlanceModifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    GlanceModifier.size(if (enabled) 24.dp else 16.dp)
                        .background(
                            if (enabled) GlanceTheme.colors.onPrimary
                            else GlanceTheme.colors.outline
                        )
                        .cornerRadius(if (enabled) 12.dp else 8.dp)
            ) {
                // Intentionally empty (handle color)
            }
        }
    }
}

class ToggleSyncAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repository =
            (context.applicationContext as CameraSyncApp).appGraph.pairedDevicesRepository()
        val current = repository.isSyncEnabled.first()
        val newState = !current
        repository.setSyncEnabled(newState)

        if (newState) {
            context.startForegroundService(MultiDeviceSyncService.createStartIntent(context))
        } else {
            context.startService(MultiDeviceSyncService.createStopIntent(context))
        }

        // Update all instances of the widget
        SyncWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.startForegroundService(MultiDeviceSyncService.createRefreshIntent(context))
        SyncWidget().updateAll(context)
    }
}

class SyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyncWidget()
}
