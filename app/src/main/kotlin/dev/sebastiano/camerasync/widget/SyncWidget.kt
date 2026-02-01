package dev.sebastiano.camerasync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.MainActivity
import dev.sebastiano.camerasync.widget.ui.RefreshButton
import dev.sebastiano.camerasync.widget.ui.StatusImage
import dev.sebastiano.camerasync.widget.ui.TextSyncToggle

class SyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyncWidget()
}

internal class SyncWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(DpSize(245.dp, 56.dp), DpSize(245.dp, 250.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.info(javaClass.simpleName) { "Updating lockscreen widget $id..." }
        val appGraph = (context.applicationContext as CameraSyncApp).appGraph
        val repository = appGraph.pairedDevicesRepository()
        val syncStatusRepository = appGraph.syncStatusRepository()

        provideContent {
            val isSyncEnabled by repository.isSyncEnabled.collectAsState(initial = false)
            val connectedCount by syncStatusRepository.connectedDevicesCount.collectAsState()
            val isSearching by syncStatusRepository.isSearching.collectAsState()

            GlanceTheme { WidgetContent(isSyncEnabled, connectedCount, isSearching) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            val size = DpSize(LocalSize.current.width, LocalSize.current.height)
            Log.info("!!!!!") { "Updating lockscreen widget preview... size: $size dp" }
            GlanceTheme {
                WidgetContent(isSyncEnabled = true, connectedCount = 1, isSearching = false)
            }
        }
    }
}

@Composable
private fun WidgetContent(isSyncEnabled: Boolean, connectedCount: Int, isSearching: Boolean) {
    key(LocalSize.current) {
        Column(GlanceModifier.fillMaxSize()) {
            val width = LocalSize.current.width
            val height = LocalSize.current.height
            val showSecondRow = height >= (56.dp * 2)
            val rowHeight = if (showSecondRow) (height - 8.dp) / 2 else height
            val unitWidth = min(rowHeight, (width - 8.dp) / 3)
            val cornerRadius = max(unitWidth, rowHeight)
            Log.info("!!!!!") { "New size: $width x $height dp" }

            FirstRow(unitWidth, cornerRadius, isSyncEnabled, connectedCount)

            if (showSecondRow) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                SecondRow(connectedCount, isSearching)
            }
        }
    }
}

@Composable
private fun ColumnScope.FirstRow(
    unitWidth: Dp,
    cornerRadius: Dp,
    isSyncEnabled: Boolean,
    connectedCount: Int,
) {
    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
        Box(
            GlanceModifier.width(unitWidth)
                .fillMaxHeight()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity(MainActivity::class.java))
                .cornerRadius(cornerRadius),
            contentAlignment = Alignment.Center,
        ) {
            StatusImage(isSyncEnabled, connectedCount)
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Row(
            GlanceModifier.width(unitWidth * 2)
                .fillMaxHeight()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(cornerRadius)
        ) {
            Box(
                GlanceModifier.defaultWeight()
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextSyncToggle(
                    isSyncEnabled,
                    GlanceModifier.fillMaxSize()
                        .background(
                            if (isSyncEnabled) {
                                GlanceTheme.colors.primaryContainer
                            } else {
                                GlanceTheme.colors.widgetBackground
                            }
                        )
                        .cornerRadius(cornerRadius),
                )
            }

            Box(
                GlanceModifier.defaultWeight()
                    .fillMaxHeight()
                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                RefreshButton(
                    GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.tertiary)
                        .cornerRadius(unitWidth / 2)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SecondRow(connectedCount: Int, isSearching: Boolean) {
    Box(
        GlanceModifier.fillMaxWidth()
            .defaultWeight()
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(
                LocalContext.current.resources
                    .getDimension(android.R.dimen.system_app_widget_background_radius)
                    .dp
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildString {
                append("$connectedCount connected device(s)")
                if (isSearching) append(". Searching\u2026")
            }
        )
    }
}
