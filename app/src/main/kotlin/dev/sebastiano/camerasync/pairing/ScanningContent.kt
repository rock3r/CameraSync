package dev.sebastiano.camerasync.pairing

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor

@Composable
internal fun ScanningContent(
    modifier: Modifier = Modifier,
    devices: List<DiscoveredCameraUi>,
    onPairClick: (Camera) -> Unit,
) {
    if (devices.isEmpty()) {
        // Empty state: centered camera icon with scanning message
        PairingStateScaffold(
            modifier = modifier,
            icon = { PulsingCameraIcon(size = 120.dp, iconSize = 64.dp) },
            title = stringResource(R.string.pairing_state_scanning),
            subtitle = stringResource(R.string.scanning_subtitle),
        ) {}
    } else {
        // Devices found: list layout with header
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(32.dp))

            // Header section with title, subtitle, and scanning indicator
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.select_camera_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    ScanningPill()
                }
            }

            Spacer(Modifier.height(24.dp))

            // Device list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices, key = { it.camera.macAddress }) { discoveredCamera ->
                    DiscoveredDeviceRow(discoveredCamera, onPairClick)
                }
            }
        }
    }
}

@Composable
private fun ScanningPill() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.scanning_indicator),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun DiscoveredDeviceRow(deviceUi: DiscoveredCameraUi, onPairClick: (Camera) -> Unit) {
    val isDetecting = deviceUi is DiscoveredCameraUi.Detecting

    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (isDetecting) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isDetecting,
        onClick = { if (!isDetecting) onPairClick(deviceUi.camera) },
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text =
                        when (deviceUi) {
                            is DiscoveredCameraUi.Detecting -> {
                                stringResource(R.string.identifying_camera)
                            }

                            is DiscoveredCameraUi.Detected -> {
                                "${deviceUi.make} ${deviceUi.model}"
                            }
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    text =
                        when (deviceUi) {
                            is DiscoveredCameraUi.Detecting -> {
                                val name = deviceUi.camera.name
                                if (name.isNullOrBlank()) {
                                    deviceUi.camera.macAddress
                                } else {
                                    "$name • ${deviceUi.camera.macAddress}"
                                }
                            }

                            is DiscoveredCameraUi.Detected -> {
                                deviceUi.camera.macAddress
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDetecting) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            trailingContent = {
                if (!isDetecting) {
                    TextButton(onClick = { onPairClick(deviceUi.camera) }) {
                        Text(stringResource(R.string.action_pair))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
internal fun PulsingCameraIcon(size: Dp, iconSize: Dp) {
    // Create multiple expanding radio wave circles with staggered delays
    val waveCount = 3
    val waveDuration = 5000
    val primaryColor = MaterialTheme.colorScheme.primary

    // Calculate scale and alpha for each wave
    val wave0Delay = 0
    val wave0Scale =
        rememberPulseAlphaWithDelay(
            initialValue = 0.3f,
            targetValue = 1.2f,
            duration = waveDuration,
            delay = wave0Delay,
            label = "wave_scale_0",
        )
    val wave0Alpha =
        rememberPulseAlphaWithDelay(
            initialValue = 0.6f,
            targetValue = 0f,
            duration = waveDuration,
            delay = wave0Delay,
            label = "wave_alpha_0",
        )

    val wave1Delay = 1 * (waveDuration / waveCount)
    val wave1Scale =
        rememberPulseAlphaWithDelay(
            initialValue = 0.3f,
            targetValue = 1.2f,
            duration = waveDuration,
            delay = wave1Delay,
            label = "wave_scale_1",
        )
    val wave1Alpha =
        rememberPulseAlphaWithDelay(
            initialValue = 0.6f,
            targetValue = 0f,
            duration = waveDuration,
            delay = wave1Delay,
            label = "wave_alpha_1",
        )

    val wave2Delay = 2 * (waveDuration / waveCount)
    val wave2Scale =
        rememberPulseAlphaWithDelay(
            initialValue = 0.3f,
            targetValue = 1.2f,
            duration = waveDuration,
            delay = wave2Delay,
            label = "wave_scale_2",
        )
    val wave2Alpha =
        rememberPulseAlphaWithDelay(
            initialValue = 0.6f,
            targetValue = 0f,
            duration = waveDuration,
            delay = wave2Delay,
            label = "wave_alpha_2",
        )

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        // Draw all expanding radio wave circles in a single Canvas
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val baseRadius = size.toPx() / 2

            // Draw wave 0
            drawCircle(
                color = primaryColor,
                radius = baseRadius * wave0Scale,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
                alpha = wave0Alpha,
            )

            // Draw wave 1
            drawCircle(
                color = primaryColor,
                radius = baseRadius * wave1Scale,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
                alpha = wave1Alpha,
            )

            // Draw wave 2
            drawCircle(
                color = primaryColor,
                radius = baseRadius * wave2Scale,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
                alpha = wave2Alpha,
            )
        }

        // Static camera icon in the center (not animated, not moving)
        Box(
            Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .padding(16.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_photo_camera_24dp),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun rememberPulseAlphaWithDelay(
    initialValue: Float = 0.6f,
    targetValue: Float = 1f,
    @Suppress("SameParameterValue") duration: Int = 5000,
    delay: Int = 0,
    label: String = "pulse",
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    return infiniteTransition
        .animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec =
                InfiniteRepeatableSpec(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay, StartOffsetType.Delay),
                ),
            label = label,
        )
        .value
}

@Preview(name = "Scanning State - Empty", showBackground = true)
@Composable
private fun ScanningEmptyPreview() {
    CameraSyncTheme { Surface { ScanningContent(devices = emptyList(), onPairClick = {}) } }
}

@Preview(name = "Scanning State - Results", showBackground = true)
@Composable
private fun ScanningResultsPreview() {
    val devices =
        listOf(
            DiscoveredCameraUi.Detected(
                camera = Camera("id1", "GR IIIx", "AA:BB:CC:DD:EE:FF", RicohCameraVendor),
                make = "Ricoh",
                model = "GR IIIx",
            ),
            DiscoveredCameraUi.Detecting(
                camera = Camera("id2", "ILCE-7M4", "BB:CC:DD:EE:FF:00", SonyCameraVendor)
            ),
        )
    CameraSyncTheme { Surface { ScanningContent(devices = devices, onPairClick = {}) } }
}

@Preview(name = "Discovered Device Row - Detected", showBackground = true)
@Composable
private fun DiscoveredDeviceRowPreview() {
    val deviceUi =
        DiscoveredCameraUi.Detected(
            camera = Camera("id1", "GR IIIx", "AA:BB:CC:DD:EE:FF", RicohCameraVendor),
            make = "Ricoh",
            model = "GR IIIx",
        )
    CameraSyncTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                DiscoveredDeviceRow(deviceUi = deviceUi, onPairClick = {})
            }
        }
    }
}

@Preview(name = "Discovered Device Row - Detecting", showBackground = true)
@Composable
private fun DiscoveredDeviceRowDetectingPreview() {
    val deviceUi =
        DiscoveredCameraUi.Detecting(
            camera = Camera("id1", "GR IIIx", "BB:CC:DD:EE:FF:00", RicohCameraVendor)
        )

    CameraSyncTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                DiscoveredDeviceRow(deviceUi = deviceUi, onPairClick = {})
            }
        }
    }
}

@Preview(name = "Scanning Pill", showBackground = true)
@Composable
private fun ScanningPillPreview() {
    CameraSyncTheme { Surface { Box(modifier = Modifier.padding(16.dp)) { ScanningPill() } } }
}

@Preview(name = "Pulsing Camera Icon", showBackground = true)
@Composable
private fun PulsingCameraIconPreview() {
    CameraSyncTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                PulsingCameraIcon(size = 96.dp, iconSize = 48.dp)
            }
        }
    }
}

@Preview(
    name = "Dark Mode - Scanning",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DarkModeScanningPreview() {
    CameraSyncTheme { Surface { ScanningContent(devices = emptyList(), onPairClick = {}) } }
}
