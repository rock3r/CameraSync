package dev.sebastiano.camerasync.pairing

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
internal fun ScanningContent(
    modifier: Modifier = Modifier,
    devices: List<DiscoveredCameraUi>,
    onPairClick: (Camera) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (devices.isEmpty()) {
            EmptyScanningState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(devices, key = { it.camera.macAddress }) { deviceUi ->
                    DiscoveredDeviceRow(deviceUi, onPairClick)
                }
            }
        }
    }
}

@Composable
private fun EmptyScanningState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        PulsingBluetoothIcon(size = 96.dp, iconSize = 48.dp)

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.pairing_state_scanning),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.ready_to_pair_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DiscoveredDeviceRow(deviceUi: DiscoveredCameraUi, onPairClick: (Camera) -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = "${deviceUi.make} ${deviceUi.model}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(text = deviceUi.camera.macAddress, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                painterResource(
                    R.drawable.ic_photo_camera_24dp
                ), // Ensure this drawable exists or use fallback
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            if (deviceUi.isProbingModel) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = { onPairClick(deviceUi.camera) }) {
                    Text(stringResource(R.string.action_pair))
                }
            }
        },
    )
}

@Composable
internal fun AssociatingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.pairing_state_associating),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun BondingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.pairing_state_bonding),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun ConnectingContent(modifier: Modifier = Modifier, deviceName: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.pairing_state_connecting, deviceName),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun SuccessContent(
    modifier: Modifier = Modifier,
    deviceName: String,
    onClose: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_check_circle_24dp), // Ensure this exists
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.pairing_state_success, deviceName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.pairing_state_success_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Button(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
internal fun ErrorContent(
    modifier: Modifier = Modifier,
    error: PairingError,
    canRetry: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.pairing_failed_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(12.dp))

            val errorText =
                when (error) {
                    PairingError.REJECTED -> stringResource(R.string.error_pairing_rejected)
                    PairingError.TIMEOUT -> stringResource(R.string.error_pairing_timeout)
                    PairingError.UNKNOWN -> stringResource(R.string.error_pairing_unknown)
                }
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.pairing_error_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }

                if (canRetry) {
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.action_unpair_and_restart))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPulseAlpha(
    initialValue: Float = 0.6f,
    targetValue: Float = 1f,
    duration: Int = 2000,
    label: String = "pulse",
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    return infiniteTransition
        .animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec =
                InfiniteRepeatableSpec(
                    animation = tween(duration, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = label,
        )
        .value
}

@Composable
private fun PulsingBluetoothIcon(size: Dp, iconSize: Dp) {
    val scale =
        rememberPulseAlpha(
            initialValue = 1f,
            targetValue = 1.15f,
            duration = 1500,
            label = "pulse_scale",
        )
    val alpha =
        rememberPulseAlpha(
            initialValue = 0.6f,
            targetValue = 1f,
            duration = 1500,
            label = "pulse_alpha",
        )

    Box(
        modifier =
            Modifier.size(size)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            )
                    )
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_bluetooth_searching_24dp),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// Previews

@Preview(name = "Scanning State - Empty", showBackground = true)
@Composable
private fun ScanningEmptyPreview() {
    CameraSyncTheme { ScanningContent(devices = emptyList(), onPairClick = {}) }
}

@Preview(name = "Scanning State - Results", showBackground = true)
@Composable
private fun ScanningResultsPreview() {
    val devices =
        listOf(
            DiscoveredCameraUi(
                camera =
                    Camera(
                        "id1",
                        "GR IIIx",
                        "mac1",
                        dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor,
                    ),
                make = "Ricoh",
                model = "GR IIIx",
            ),
            DiscoveredCameraUi(
                camera =
                    Camera(
                        "id2",
                        "ILCE-7M4",
                        "mac2",
                        dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor,
                    ),
                make = "Sony",
                model = "ILCE-7M4",
                isProbingModel = true,
            ),
        )
    CameraSyncTheme { ScanningContent(devices = devices, onPairClick = {}) }
}

@Preview(name = "Associating State", showBackground = true)
@Composable
private fun AssociatingPreview() {
    CameraSyncTheme { AssociatingContent() }
}

@Preview(name = "Bonding State", showBackground = true)
@Composable
private fun BondingPreview() {
    CameraSyncTheme { BondingContent() }
}

@Preview(name = "Connecting State", showBackground = true)
@Composable
private fun ConnectingPreview() {
    CameraSyncTheme { ConnectingContent(deviceName = "GR IIIx") }
}

@Preview(name = "Success State", showBackground = true)
@Composable
private fun SuccessPreview() {
    CameraSyncTheme { SuccessContent(deviceName = "GR IIIx", onClose = {}) }
}

@Preview(name = "Error State", showBackground = true)
@Composable
private fun ErrorPreview() {
    CameraSyncTheme {
        ErrorContent(error = PairingError.TIMEOUT, canRetry = true, onCancel = {}, onRetry = {})
    }
}

@Preview(name = "Pulsing Bluetooth Icon", showBackground = true)
@Composable
private fun PulsingBluetoothIconPreview() {
    CameraSyncTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PulsingBluetoothIcon(size = 96.dp, iconSize = 48.dp)
        }
    }
}
