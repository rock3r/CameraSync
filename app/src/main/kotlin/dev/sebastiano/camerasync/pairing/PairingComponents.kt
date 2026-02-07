package dev.sebastiano.camerasync.pairing

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.sebastiano.camerasync.ui.theme.DarkElectricBlue
import dev.sebastiano.camerasync.ui.theme.ElectricBlue

@Composable
internal fun IdleContent(modifier: Modifier = Modifier, onStartPairing: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            PulsingBluetoothIcon(size = 96.dp, iconSize = 48.dp)

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.ready_to_pair_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.ready_to_pair_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Button(onClick = onStartPairing) {
                Icon(
                    painterResource(R.drawable.ic_bluetooth_searching_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_pair_system))
            }
        }
    }
}

@Composable
internal fun AlreadyBondedContent(
    modifier: Modifier = Modifier,
    removeFailed: Boolean,
    onRemoveBond: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_settings_24dp),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.bonded_dialog_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    if (removeFailed) {
                        stringResource(R.string.bonded_dialog_remove_failed)
                    } else {
                        stringResource(R.string.bonded_dialog_message)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }

                Spacer(Modifier.width(16.dp))

                if (!removeFailed) {
                    Button(onClick = onRemoveBond) {
                        Text(
                            stringResource(R.string.action_remove_pairing),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_ok), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PairingContent(
    modifier: Modifier = Modifier,
    deviceName: String,
    error: PairingError?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error != null) {
            PairingFailed(error, onCancel, onRetry)
        } else {
            PairingInProgress(deviceName, onCancel)
        }
    }
}

@Composable
internal fun PairingFailed(error: PairingError, onCancel: () -> Unit, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        val pulseAlpha = rememberPulseAlpha(initialValue = 0.7f, targetValue = 1f, duration = 1500)

        Box(
            modifier =
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = pulseAlpha * 0.3f)
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

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

        Spacer(Modifier.height(32.dp))

        Row {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }

            Spacer(Modifier.width(16.dp))

            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun PairingInProgress(deviceName: String, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        val infiniteTransition = rememberInfiniteTransition(label = "pairing_animation")
        val rotation by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    InfiniteRepeatableSpec(
                        animation = tween(2000, easing = EaseInOut),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "rotation",
            )
        val pulseScale =
            rememberPulseAlpha(initialValue = 0.95f, targetValue = 1.05f, duration = 1500)

        val haloColor = if (isSystemInDarkTheme()) DarkElectricBlue else ElectricBlue

        Box(
            modifier =
                Modifier.size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(haloColor.copy(alpha = 0.4f), haloColor.copy(alpha = 0.1f))
                        )
                    ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painterResource(R.drawable.ic_bluetooth_searching_24dp),
                contentDescription = null,
                modifier = Modifier.rotate(rotation).size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            stringResource(R.string.pairing_in_progress_title, deviceName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.pairing_in_progress_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
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

@Preview(name = "Idle State", showBackground = true)
@Composable
private fun IdleContentPreview() {
    CameraSyncTheme { IdleContent(onStartPairing = {}) }
}

@Preview(name = "Already Bonded State", showBackground = true)
@Composable
private fun AlreadyBondedContentPreview() {
    CameraSyncTheme { AlreadyBondedContent(removeFailed = false, onRemoveBond = {}, onCancel = {}) }
}

@Preview(name = "Already Bonded State - Remove Failed", showBackground = true)
@Composable
private fun AlreadyBondedContentFailedPreview() {
    CameraSyncTheme { AlreadyBondedContent(removeFailed = true, onRemoveBond = {}, onCancel = {}) }
}

@Preview(name = "Pairing In Progress", showBackground = true)
@Composable
private fun PairingInProgressPreview() {
    CameraSyncTheme { PairingInProgress(deviceName = "GR IIIx", onCancel = {}) }
}

@Preview(name = "Pairing Failed - Rejected", showBackground = true)
@Composable
private fun PairingFailedRejectedPreview() {
    CameraSyncTheme { PairingFailed(error = PairingError.REJECTED, onCancel = {}, onRetry = {}) }
}

@Preview(name = "Pairing Failed - Timeout", showBackground = true)
@Composable
private fun PairingFailedTimeoutPreview() {
    CameraSyncTheme { PairingFailed(error = PairingError.TIMEOUT, onCancel = {}, onRetry = {}) }
}

@Preview(name = "Pairing Failed - Unknown", showBackground = true)
@Composable
private fun PairingFailedUnknownPreview() {
    CameraSyncTheme { PairingFailed(error = PairingError.UNKNOWN, onCancel = {}, onRetry = {}) }
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
