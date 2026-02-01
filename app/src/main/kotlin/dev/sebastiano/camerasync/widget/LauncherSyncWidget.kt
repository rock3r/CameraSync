package dev.sebastiano.camerasync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.widget.ui.RefreshButton
import dev.sebastiano.camerasync.widget.ui.StatusImage
import dev.sebastiano.camerasync.widget.ui.SyncToggle

class LauncherSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LauncherSyncWidget()
}

internal class LauncherSyncWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val repository = appGraph.pairedDevicesRepository()
        val syncStatusRepository = appGraph.syncStatusRepository()

        provideContent {
            val isSyncEnabled by repository.isSyncEnabled.collectAsState(initial = false)
            val connectedCount by syncStatusRepository.connectedDevicesCount.collectAsState()

            GlanceTheme {
                LauncherSyncWidgetContent(
                    isSyncEnabled = isSyncEnabled,
                    connectedCount = connectedCount,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme { LauncherSyncWidgetContent(isSyncEnabled = true, connectedCount = 2) }
        }
    }
}

@Composable
private fun LauncherSyncWidgetContent(isSyncEnabled: Boolean, connectedCount: Int) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sync")
                SyncToggle(isSyncEnabled)
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            StatusImage(isSyncEnabled, connectedCount)

            Spacer(modifier = GlanceModifier.defaultWeight())

            RefreshButton()
        }
    }
}
