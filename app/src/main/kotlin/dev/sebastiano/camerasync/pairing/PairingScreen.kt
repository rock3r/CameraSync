package dev.sebastiano.camerasync.pairing

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.sebastiano.camerasync.ui.theme.DarkElectricBlue
import dev.sebastiano.camerasync.ui.theme.ElectricBlue
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor

/** Screen for discovering and pairing new camera devices. */
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
        floatingActionButton = {
            val isScanning = (currentState as? PairingScreenState.Scanning)?.isScanning == true
            FloatingActionButton(
                onClick = {
                    if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                }
            ) {
                Icon(
                    painterResource(
                        if (isScanning) R.drawable.ic_stop_24dp else R.drawable.ic_refresh_24dp
                    ),
                    contentDescription = if (isScanning) "Stop scanning" else "Start scanning",
                )
            }
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
                    IdleContent(modifier = Modifier.fillMaxSize())
                }

                is PairingScreenState.Scanning -> {
                    ScanningContent(
                        modifier = Modifier.fillMaxSize(),
                        discoveredDevices = currentState.discoveredDevices,
                        isScanning = currentState.isScanning,
                        onDeviceClick = { camera -> viewModel.pairDevice(camera) },
                        onSystemPairingClick = { viewModel.requestCompanionPairing() },
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
                        onRetry = { viewModel.pairDevice(currentState.camera) },
                        onCancel = { viewModel.cancelPairing() },
                    )
                }
            }

            // Scanning progress indicator at the top (like DevicesListScreen)
            if (currentState is PairingScreenState.Scanning && currentState.isScanning) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            PulsingBluetoothIcon(size = 96.dp, iconSize = 48.dp)

            Spacer(Modifier.height(24.dp))

            Text(
                "Ready to scan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Make sure your camera has Bluetooth pairing enabled, then tap the scan button.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanningContent(
    modifier: Modifier = Modifier,
    discoveredDevices: List<Camera>,
    isScanning: Boolean,
    onDeviceClick: (Camera) -> Unit,
    onSystemPairingClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = onSystemPairingClick, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painterResource(R.drawable.ic_bluetooth_searching_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Pair using Android System")
            }
        }

        if (discoveredDevices.isEmpty()) {
            ScanningEmptyState(modifier = Modifier.weight(1f), isScanning = isScanning)
        } else {
            DiscoveredDevicesList(
                modifier = Modifier.weight(1f),
                devices = discoveredDevices,
                onDeviceClick = onDeviceClick,
            )
        }
    }
}

@Composable
private fun ScanningEmptyState(modifier: Modifier = Modifier, isScanning: Boolean) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isScanning) {
            ScanningActiveState()
        } else {
            ScanningStoppedState()
        }
    }
}

@Composable
private fun ScanningActiveState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        RadioWaveBluetoothIcon(size = 120.dp, iconSize = 64.dp)

        Spacer(Modifier.height(24.dp))

        Text(
            "Looking for cameras...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Make sure your camera is nearby with Bluetooth enabled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanningStoppedState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(
            painterResource(R.drawable.ic_bluetooth_searching_24dp),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Scan stopped",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Tap the refresh button to start scanning again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DiscoveredDevicesList(
    modifier: Modifier = Modifier,
    devices: List<Camera>,
    onDeviceClick: (Camera) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(devices, key = { it.macAddress }) { camera ->
            DiscoveredDeviceCard(camera = camera, onClick = { onDeviceClick(camera) })
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(camera: Camera, onClick: () -> Unit) {
    val pulseAlpha = rememberPulseAlpha(label = "card_pulse")

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingCameraIcon(size = 56.dp, iconSize = 32.dp, alpha = pulseAlpha)

            Spacer(Modifier.width(20.dp))

            DeviceInfoColumn(camera = camera, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceInfoColumn(camera: Camera, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = camera.name ?: "Unknown Camera",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = camera.vendor.vendorName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = camera.macAddress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
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

@Composable
private fun RadioWaveBluetoothIcon(size: Dp, iconSize: Dp) {
    // Note: this same effect can be achieved without recompositions by using drawBehind, but
    // I am lazy.
    val infiniteTransition = rememberInfiniteTransition(label = "radio_wave")
    val animationSpec =
        InfiniteRepeatableSpec<Float>(
            animation = tween(2300, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        )
    val waveScale by
        infiniteTransition.animateFloat(
            initialValue = 0.0f,
            targetValue = 1.5f,
            animationSpec = animationSpec,
            label = "wave",
        )
    val waveAlpha by
        infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = animationSpec,
            label = "wave_alpha",
        )

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        val haloColor = if (isSystemInDarkTheme()) DarkElectricBlue else ElectricBlue

        Box(
            modifier =
                Modifier.size(size)
                    .scale(waveScale)
                    .alpha(waveAlpha)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            .5f to haloColor.copy(alpha = 0.25f),
                            .8f to haloColor.copy(alpha = 0.75f),
                            1f to Color.Transparent,
                        )
                    )
        )

        // Static Bluetooth icon
        Icon(
            painterResource(R.drawable.ic_bluetooth_searching_24dp),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PulsingCameraIcon(size: Dp, iconSize: Dp, alpha: Float) {
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            )
                    )
                )
                .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_photo_camera_24dp),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
    CameraSyncTheme { IdleContent() }
}

@Preview(name = "Scanning Active", showBackground = true)
@Composable
private fun ScanningActiveStatePreview() {
    CameraSyncTheme { ScanningActiveState() }
}

@Preview(name = "Scanning Stopped", showBackground = true)
@Composable
private fun ScanningStoppedStatePreview() {
    CameraSyncTheme { ScanningStoppedState() }
}

@Preview(name = "Discovered Device Card - Named", showBackground = true)
@Composable
private fun DiscoveredDeviceCardNamedPreview() {
    CameraSyncTheme {
        DiscoveredDeviceCard(
            camera =
                Camera(
                    identifier = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    vendor = RicohCameraVendor,
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Discovered Device Card - Unnamed", showBackground = true)
@Composable
private fun DiscoveredDeviceCardUnnamedPreview() {
    CameraSyncTheme {
        DiscoveredDeviceCard(
            camera =
                Camera(
                    identifier = "11:22:33:44:55:66",
                    name = null,
                    macAddress = "11:22:33:44:55:66",
                    vendor = RicohCameraVendor,
                ),
            onClick = {},
        )
    }
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

@Preview(name = "Discovered Devices List", showBackground = true)
@Composable
private fun DiscoveredDevicesListPreview() {
    CameraSyncTheme {
        DiscoveredDevicesList(
            devices =
                listOf(
                    Camera(
                        identifier = "AA:BB:CC:DD:EE:FF",
                        name = "GR IIIx",
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        vendor = RicohCameraVendor,
                    ),
                    Camera(
                        identifier = "11:22:33:44:55:66",
                        name = "GR III",
                        macAddress = "11:22:33:44:55:66",
                        vendor = RicohCameraVendor,
                    ),
                    Camera(
                        identifier = "FF:EE:DD:CC:BB:AA",
                        name = null,
                        macAddress = "FF:EE:DD:CC:BB:AA",
                        vendor = RicohCameraVendor,
                    ),
                ),
            onDeviceClick = {},
        )
    }
}
