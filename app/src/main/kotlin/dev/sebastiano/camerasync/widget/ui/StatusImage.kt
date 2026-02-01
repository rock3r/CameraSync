package dev.sebastiano.camerasync.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import dev.sebastiano.camerasync.MainActivity
import dev.sebastiano.camerasync.R

@Composable
internal fun StatusImage(isSyncEnabled: Boolean, connectedCount: Int) {
    Box(
        GlanceModifier.size(56.dp).clickable(actionStartActivity(MainActivity::class.java)),
        contentAlignment = Alignment.Center,
    ) {
        val icon =
            if (isSyncEnabled && connectedCount > 0) {
                R.drawable.ic_linked_camera_48dp
            } else {
                R.drawable.ic_photo_camera_48dp
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
}
