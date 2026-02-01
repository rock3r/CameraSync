package dev.sebastiano.camerasync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.widget.ui.RefreshButton
import dev.sebastiano.camerasync.widget.ui.StatusImage
import dev.sebastiano.camerasync.widget.ui.SyncToggle

class LockScreenSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockScreenSyncWidget()
}

internal class LockScreenSyncWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val repository = appGraph.pairedDevicesRepository()
        val syncStatusRepository = appGraph.syncStatusRepository()

        provideContent {
            val isSyncEnabled by repository.isSyncEnabled.collectAsState(initial = false)
            val connectedCount by syncStatusRepository.connectedDevicesCount.collectAsState()
            val isSearching by syncStatusRepository.isSearching.collectAsState()

            GlanceTheme {
                LockScreenSyncWidgetContent(
                    isSyncEnabled = isSyncEnabled,
                    connectedCount = connectedCount,
                    isSearching = isSearching,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                LockScreenSyncWidgetContent(
                    isSyncEnabled = true,
                    connectedCount = 1,
                    isSearching = false,
                )
            }
        }
    }
}

@Composable
private fun LockScreenSyncWidgetContent(
    isSyncEnabled: Boolean,
    connectedCount: Int,
    isSearching: Boolean,
) {
    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(28.dp)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Top row
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusImage(isSyncEnabled, connectedCount)

                Spacer(modifier = GlanceModifier.defaultWeight())

                RefreshButton()
            }

            Spacer(modifier = GlanceModifier.size(8.dp))

            // Bottom row: status text
            Row(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyncToggle(isSyncEnabled)

                Spacer(modifier = GlanceModifier.width(16.dp))

                val statusTextRes =
                    when {
                        !isSyncEnabled -> R.string.status_disabled
                        connectedCount > 0 -> R.string.status_syncing
                        isSearching -> R.string.status_searching
                        else -> R.string.status_idle
                    }

                Text(
                    text = LocalContext.current.getString(statusTextRes),
                    style =
                        TextDefaults.defaultTextStyle.copy(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
    }
}
