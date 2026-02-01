package dev.sebastiano.camerasync.widget.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService

@Composable
internal fun RefreshButton() {
    Box(
        modifier =
            GlanceModifier.size(48.dp)
                .background(GlanceTheme.colors.tertiary)
                .cornerRadius(24.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh_24dp),
            contentDescription = "Refresh",
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary),
        )
    }
}

internal class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        Log.info(javaClass.name) { "Forcing refresh" }
        context.startForegroundService(MultiDeviceSyncService.createRefreshIntent(context))
    }
}
