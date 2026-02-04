package dev.sebastiano.camerasync.devices

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
internal fun ConnectionStatusIcon(state: DeviceConnectionState, modifier: Modifier = Modifier) {
    val (icon, color) =
        when (state) {
            is DeviceConnectionState.Disabled ->
                R.drawable.ic_photo_camera_24dp to
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

            is DeviceConnectionState.Disconnected ->
                R.drawable.ic_bluetooth_disabled_24dp to MaterialTheme.colorScheme.onSurfaceVariant

            is DeviceConnectionState.Searching ->
                R.drawable.ic_search_24dp to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Connecting ->
                R.drawable.ic_bluetooth_searching_24dp to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Unreachable ->
                R.drawable.ic_bluetooth_disabled_24dp to
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

            is DeviceConnectionState.Connected ->
                R.drawable.ic_bluetooth_connected_24dp to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Syncing ->
                R.drawable.ic_linked_camera_24dp to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Error ->
                R.drawable.ic_error_24dp to MaterialTheme.colorScheme.error
        }

    val animatedColor by animateColorAsState(targetValue = color, label = "status_color")

    Crossfade(state, label = "status_crossfade", modifier = modifier) {
        Box(
            modifier =
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(animatedColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")

            @SuppressLint("MissingPermission")
            when (it) {
                is DeviceConnectionState.Searching ->
                    SearchingAnimatedIcon(infiniteTransition, icon, animatedColor)

                is DeviceConnectionState.Connecting ->
                    ConnectingAnimatedIcon(infiniteTransition, icon, animatedColor)

                is DeviceConnectionState.Syncing -> SyncingAnimatedIcon(animatedColor)

                else -> Icon(painterResource(icon), contentDescription = null, tint = animatedColor)
            }
        }
    }
}

@Composable
private fun SearchingAnimatedIcon(
    infiniteTransition: InfiniteTransition,
    icon: Int,
    animatedColor: Color,
) {
    val angle by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                InfiniteRepeatableSpec(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "searching_rotation",
        )

    // Move in a small circle
    val radius = 2.dp
    val x = radius * cos(angle.toDouble() * (PI / 180.0)).toFloat()
    val y = radius * sin(angle.toDouble() * (PI / 180.0)).toFloat()

    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = animatedColor,
        modifier = Modifier.offset(x = x, y = y),
    )
}

@Composable
private fun ConnectingAnimatedIcon(
    infiniteTransition: InfiniteTransition,
    icon: Int,
    animatedColor: Color,
) {
    val alpha by
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec =
                InfiniteRepeatableSpec(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "connecting_blink",
        )

    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = animatedColor,
        modifier = Modifier.alpha(alpha),
    )
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun SyncingAnimatedIcon(animatedColor: Color, modifier: Modifier = Modifier) {
    // Use the animated vector drawable for wave animation
    // Since Compose AVD API suck, and we can't have an infinite loop, we hack around it
    // by alternating two identical animations; the visible one plays forward while the
    // invisible one plays backwards.
    val image1 = AnimatedImageVector.animatedVectorResource(R.drawable.avd_syncing_waves)
    val image2 = AnimatedImageVector.animatedVectorResource(R.drawable.avd_syncing_waves)
    var useFirstImage by remember { mutableStateOf(false) }

    // Automatically toggle the animation state to keep it looping
    LaunchedEffect(Unit) {
        useFirstImage = true
        while (true) {
            delay(2000.milliseconds) // Total animation duration
            useFirstImage = !useFirstImage
        }
    }

    val avd1 = rememberAnimatedVectorPainter(image1, useFirstImage)
    val avd2 = rememberAnimatedVectorPainter(image2, !useFirstImage)

    Box(modifier) {
        // We need to keep both in composition or the animation will not run
        Icon(
            painter = avd1,
            contentDescription = null,
            tint = animatedColor,
            modifier = Modifier.alpha(if (useFirstImage) 1f else 0f),
        )
        Icon(
            painter = avd2,
            contentDescription = null,
            tint = animatedColor,
            modifier = Modifier.alpha(if (useFirstImage) 0f else 1f),
        )
    }
}

@Composable
internal fun ConnectionStatusText(state: DeviceConnectionState, modifier: Modifier = Modifier) {
    val (text, color) =
        when (state) {
            is DeviceConnectionState.Disabled ->
                stringResource(R.string.status_disabled) to
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            is DeviceConnectionState.Disconnected ->
                stringResource(R.string.status_disconnected) to
                    MaterialTheme.colorScheme.onSurfaceVariant

            is DeviceConnectionState.Searching ->
                stringResource(R.string.status_searching) to MaterialTheme.colorScheme.primary
            is DeviceConnectionState.Connecting ->
                stringResource(R.string.status_connecting) to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Unreachable ->
                stringResource(R.string.status_unreachable) to
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

            is DeviceConnectionState.Connected ->
                stringResource(R.string.status_connected) to MaterialTheme.colorScheme.primary
            is DeviceConnectionState.Error -> state.message to MaterialTheme.colorScheme.error
            is DeviceConnectionState.Syncing ->
                stringResource(R.string.status_syncing) to MaterialTheme.colorScheme.primary
        }

    @SuppressLint("MissingPermission")
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
