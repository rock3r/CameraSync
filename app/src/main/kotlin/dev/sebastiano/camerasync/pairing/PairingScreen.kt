package dev.sebastiano.camerasync.pairing

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.sebastiano.camerasync.ui.theme.DarkElectricBlue
import dev.sebastiano.camerasync.ui.theme.ElectricBlue

/** Screen for pairing new camera devices using the system flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onNavigateBack: () -> Unit,
    onDevicePaired: () -> Unit,
) {
    val state by viewModel.state
    val currentState = state

    // Observe navigation events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is PairingNavigationEvent.DevicePaired -> onDevicePaired()
            }
        }
    }

    val intentSenderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onCompanionAssociationResult(result.data)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.associationRequest.collect { intentSender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Camera",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), "Back")
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Having issues? Let us know",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { viewModel.sendFeedback() },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentState) {
                is PairingScreenState.Idle -> {
                    IdleContent(
                        modifier = Modifier.fillMaxSize(),
                        onStartPairing = { viewModel.requestCompanionPairing() },
                    )
                }

                is PairingScreenState.AlreadyBonded -> {
                    AlreadyBondedContent(
                        modifier = Modifier.fillMaxSize(),
                        removeFailed = currentState.removeFailed,
                        onRemoveBond = { viewModel.removeBondAndRetry(currentState.camera) },
                        onCancel = { viewModel.cancelPairing() },
                    )
                }

                is PairingScreenState.Pairing -> {
                    PairingContent(
                        modifier = Modifier.fillMaxSize(),
                        deviceName = currentState.camera.name ?: currentState.camera.macAddress,
                        error = currentState.error,
                        onRetry = {
                            viewModel.pairDevice(currentState.camera, allowExistingBond = true)
                        },
                        onCancel = { viewModel.cancelPairing() },
                    )
                }
            }

        }
    }
}

@Composable
private fun IdleContent(modifier: Modifier = Modifier, onStartPairing: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            PulsingBluetoothIcon(size = 96.dp, iconSize = 48.dp)

            Spacer(Modifier.height(24.dp))

            Text(
            "Ready to pair",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Text(
            "Use Android's pairing flow to select a camera from the system list.",
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
            Text("Pair using Android System")
        }
        }
    }
}

@Composable
private fun AlreadyBondedContent(
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
                "Camera Already Paired",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    if (removeFailed) {
                        "This camera is already paired to your phone at the system level. " +
                            "Please remove the existing pairing from your phone's Bluetooth settings, " +
                            "then try again."
                    } else {
                        "This camera is already paired to your phone at the system level. " +
                            "This can interfere with the app's connection. " +
                            "Would you like to remove the existing pairing?"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }

                Spacer(Modifier.width(16.dp))

                if (!removeFailed) {
                    Button(onClick = onRemoveBond) {
                        Text("Remove Pairing", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = onCancel) { Text("OK", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun PairingContent(
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
private fun PairingFailed(error: PairingError, onCancel: () -> Unit, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        val infiniteTransition = rememberInfiniteTransition(label = "error_pulse")
        val pulseAlpha by
            infiniteTransition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec =
                    InfiniteRepeatableSpec(
                        animation = tween(1500, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulse",
            )

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
            "Pairing failed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text =
                when (error) {
                    PairingError.REJECTED ->
                        "The camera rejected the pairing request. Make sure Bluetooth pairing is enabled on your camera."

                    PairingError.TIMEOUT ->
                        "Connection timed out. Make sure the camera is nearby and Bluetooth is enabled."

                    PairingError.UNKNOWN -> "An unexpected error occurred. Please try again."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }

            Spacer(Modifier.width(16.dp))

            TextButton(onClick = onRetry) { Text("Retry", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PairingInProgress(deviceName: String, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        val infiniteTransition = rememberInfiniteTransition(label = "pairing_animation")
        val animationSpec =
            InfiniteRepeatableSpec<Float>(
                animation = tween(2000, easing = EaseInOut),
                repeatMode = RepeatMode.Restart,
            )
        val rotation by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = animationSpec,
                label = "rotation",
            )
        val pulseScale by
            infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec =
                    InfiniteRepeatableSpec(
                        animation = tween(1500, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "scale",
            )

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
            "Pairing with $deviceName...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Please wait while we connect to your camera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

// Animation helper functions

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

/** Errors that can occur during pairing. */
enum class PairingError {
    /** Camera rejected the pairing request. */
    REJECTED,

    /** Connection timed out. */
    TIMEOUT,

    /** Unknown error. */
    UNKNOWN,
}

// Preview functions

@Preview(name = "Idle State", showBackground = true)
@Composable
private fun IdleContentPreview() {
    CameraSyncTheme { IdleContent(onStartPairing = {}) }
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
