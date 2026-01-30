package dev.sebastiano.camerasync.devices

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.devicesync.formatElapsedTimeSince
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState
import dev.sebastiano.camerasync.util.BatteryOptimizationUtil
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.material3.CompassButton
import org.maplibre.compose.material3.LocationPuckDefaults
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position

/** Main screen showing the list of paired devices with their sync status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesListScreen(
    viewModel: DevicesListViewModel,
    onAddDeviceClick: () -> Unit,
    onViewLogsClick: () -> Unit,
) {
    val state by viewModel.state
    var deviceToUnpair by remember { mutableStateOf<PairedDeviceWithState?>(null) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Refresh battery optimization status when returning from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    val currentState = state
                    val isSyncEnabled =
                        currentState is DevicesListState.HasDevices && currentState.isSyncEnabled

                    // Sync toggle switch
                    Switch(
                        checked = isSyncEnabled,
                        onCheckedChange = { viewModel.setSyncEnabled(it) },
                        modifier = Modifier.padding(end = 8.dp),
                    )

                    if (currentState is DevicesListState.HasDevices) {
                        val hasEnabledCameras = currentState.devices.any { it.device.isEnabled }
                        IconButton(
                            onClick = { viewModel.refreshConnections() },
                            enabled = hasEnabledCameras && isSyncEnabled,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_refresh_24dp),
                                contentDescription = stringResource(R.string.content_desc_refresh),
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = stringResource(R.string.content_desc_settings),
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_view_logs)) },
                                onClick = {
                                    showMenu = false
                                    onViewLogsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_send_feedback)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.sendFeedback()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val currentState = state
            val isSyncEnabled =
                currentState is DevicesListState.HasDevices && currentState.isSyncEnabled

            if (isSyncEnabled) {
                FloatingActionButton(onClick = onAddDeviceClick) {
                    Icon(
                        painterResource(R.drawable.ic_add_camera_24dp),
                        contentDescription = stringResource(R.string.content_desc_add_device),
                    )
                }
            } else {
                // Wrap in tooltip when disabled
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text(stringResource(R.string.tooltip_sync_disabled_pairing))
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    FloatingActionButton(
                        onClick = {}, // No-op when disabled
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add_camera_24dp),
                            contentDescription = stringResource(R.string.content_desc_add_device_disabled),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (val currentState = state) {
            is DevicesListState.Loading -> {
                LoadingContent(Modifier.padding(innerPadding))
            }

            is DevicesListState.Empty -> {
                EmptyContent(modifier = Modifier.padding(innerPadding))
            }

            is DevicesListState.HasDevices -> {
                Box(modifier = Modifier.padding(innerPadding)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LocationCard(
                            location = currentState.currentLocation,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )

                        if (!currentState.isIgnoringBatteryOptimizations) {
                            BatteryOptimizationWarning(
                                onEnableClick = { showBatteryOptimizationDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }

                        if (!currentState.isSyncEnabled) {
                            SyncStoppedWarning(
                                onRefreshClick = { viewModel.setSyncEnabled(true) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }

                        DevicesList(
                            devices = currentState.devices,
                            displayInfoMap = currentState.displayInfoMap,
                            onDeviceEnabledChange = { device, enabled ->
                                viewModel.setDeviceEnabled(device.device.macAddress, enabled)
                            },
                            onUnpairClick = { device -> deviceToUnpair = device },
                            onRetryClick = { device ->
                                viewModel.retryConnection(device.device.macAddress)
                            },
                        )
                    }

                    AnimatedVisibility(
                        currentState.isScanning,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Unpair confirmation dialog
    deviceToUnpair?.let { device ->
        UnpairConfirmationDialog(
            deviceName = device.device.name ?: device.device.macAddress,
            onConfirm = {
                viewModel.unpairDevice(device.device.macAddress)
                deviceToUnpair = null
            },
            onDismiss = { deviceToUnpair = null },
        )
    }

    // Battery optimization dialog
    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onConfirm = { showBatteryOptimizationDialog = false },
            onDismiss = { showBatteryOptimizationDialog = false },
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.loading_devices), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_photo_camera_24dp),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.empty_devices_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.empty_devices_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DevicesList(
    devices: List<PairedDeviceWithState>,
    displayInfoMap: Map<String, DeviceDisplayInfo>,
    onDeviceEnabledChange: (PairedDeviceWithState, Boolean) -> Unit,
    onUnpairClick: (PairedDeviceWithState) -> Unit,
    onRetryClick: (PairedDeviceWithState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = 80.dp, // Room for FAB
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(devices, key = { it.device.macAddress }) { deviceWithState ->
            val displayInfo = displayInfoMap[deviceWithState.device.macAddress]
            DeviceCard(
                deviceWithState = deviceWithState,
                displayInfo = displayInfo,
                onEnabledChange = { enabled -> onDeviceEnabledChange(deviceWithState, enabled) },
                onUnpairClick = { onUnpairClick(deviceWithState) },
                onRetryClick = { onRetryClick(deviceWithState) },
            )
        }
    }
}

@Composable
private fun DeviceCard(
    deviceWithState: PairedDeviceWithState,
    displayInfo: DeviceDisplayInfo?,
    onEnabledChange: (Boolean) -> Unit,
    onUnpairClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val device = deviceWithState.device
    val connectionState = deviceWithState.connectionState
    val unknownString = stringResource(R.string.label_unknown)
    val info = displayInfo ?: DeviceDisplayInfo(unknownString, unknownString, device.name, false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
    ) {
        Column {
            // Main row
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator
                ConnectionStatusIcon(connectionState)

                Spacer(Modifier.width(16.dp))

                // Device info
                Column(modifier = Modifier.weight(1f)) {
                    // Show make and model, with pairing name if needed
                    val titleText =
                        if (info.showPairingName && info.pairingName != null) {
                            "${info.make} ${info.model} (${info.pairingName})"
                        } else {
                            "${info.make} ${info.model}"
                        }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionStatusText(connectionState)

                        if (
                            device.lastSyncedAt != null &&
                                connectionState is DeviceConnectionState.Syncing
                        ) {
                            val context = LocalContext.current
                            Text(
                                text = " • ${formatElapsedTimeSince(context, device.lastSyncedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Enable/disable switch
                Switch(checked = device.isEnabled, onCheckedChange = onEnabledChange)

                Spacer(Modifier.width(8.dp))

                // Expand indicator
                Icon(
                    painterResource(
                        if (isExpanded) R.drawable.ic_collapse_24dp else R.drawable.ic_expand_24dp
                    ),
                    contentDescription =
                        if (isExpanded) stringResource(R.string.content_desc_collapse)
                        else stringResource(R.string.content_desc_expand),
                    modifier = Modifier.alpha(0.6f),
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    // Device details
                    DeviceDetailRow(stringResource(R.string.label_make), info.make)
                    DeviceDetailRow(stringResource(R.string.label_model), info.model)
                    if (info.pairingName != null) {
                        DeviceDetailRow(
                            stringResource(R.string.label_pairing_name),
                            info.pairingName,
                        )
                    }
                    DeviceDetailRow(stringResource(R.string.label_mac_address), device.macAddress)

                    if (device.lastSyncedAt != null) {
                        val context = LocalContext.current
                        DeviceDetailRow(
                            stringResource(R.string.label_last_sync),
                            formatElapsedTimeSince(context, device.lastSyncedAt),
                        )
                    }

                    if (connectionState is DeviceConnectionState.Syncing) {
                        connectionState.firmwareVersion?.let { version ->
                            DeviceDetailRow(stringResource(R.string.label_firmware), version)
                        }
                    }

                    if (connectionState is DeviceConnectionState.Unreachable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.unreachable_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRetryClick) {
                            Text(stringResource(R.string.retry_connection))
                        }
                    }

                    if (connectionState is DeviceConnectionState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                        if (connectionState.isRecoverable) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRetryClick) {
                                Text(stringResource(R.string.retry_connection))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Unpair action
                    TextButton(onClick = onUnpairClick, modifier = Modifier.align(Alignment.End)) {
                        Text(
                            stringResource(R.string.unpair_device),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusIcon(state: DeviceConnectionState) {
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

    Crossfade(state) {
        Box(
            modifier =
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(animatedColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")

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
private fun ConnectionStatusText(state: DeviceConnectionState) {
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

    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp).alignByBaseline(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun UnpairConfirmationDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_unpair_title)) },
        text = { Text(stringResource(R.string.dialog_unpair_message, deviceName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_unpair),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showError by remember { mutableStateOf(false) }
    val hasOemSettings = remember {
        BatteryOptimizationUtil.hasOemBatteryOptimizationSettings(context)
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = {
                showError = false
                onDismiss()
            },
            title = { Text(stringResource(R.string.dialog_settings_error_title)) },
            text = { Text(stringResource(R.string.dialog_settings_error_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showError = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_battery_opt_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_battery_opt_message))

                    if (hasOemSettings) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.dialog_battery_opt_oem_message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.dialog_battery_opt_instructions))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent =
                                BatteryOptimizationUtil.createBatteryOptimizationSettingsIntent(
                                    context
                                )
                            context.startActivity(intent)
                            onConfirm()
                        } catch (_: Exception) {
                            // If launching the intent fails, show an error dialog with manual
                            // instructions
                            showError = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_open_settings))
                }
            },
            dismissButton = {
                if (hasOemSettings) {
                    TextButton(
                        onClick = {
                            try {
                                val oemIntent =
                                    BatteryOptimizationUtil.getOemBatteryOptimizationIntent(context)
                                if (oemIntent != null) {
                                    context.startActivity(oemIntent)
                                    onConfirm()
                                } else {
                                    showError = true
                                }
                            } catch (_: Exception) {
                                showError = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_oem_settings))
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_not_now))
                    }
                }
            },
        )
    }
}

@Composable
private fun BatteryOptimizationWarning(onEnableClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.warning_battery_opt_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.warning_battery_opt_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onEnableClick) { Text(stringResource(R.string.action_disable)) }
        }
    }
}

@Composable
private fun SyncStoppedWarning(
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_bluetooth_disabled_24dp),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.warning_sync_stopped_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.warning_sync_stopped_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onRefreshClick) {
                Text(stringResource(R.string.action_enable_sync))
            }
        }
    }
}

@Composable
private fun LocationCard(location: GpsLocation?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().height(200.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (location == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.acquiring_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val isDarkTheme = isSystemInDarkTheme()
            val mapStyle =
                if (isDarkTheme) "https://tiles.openfreemap.org/styles/dark"
                else "https://tiles.openfreemap.org/styles/positron"

            val cameraState =
                rememberCameraState(
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = 14.0,
                    )
                )

            // Update camera when location changes
            val locationFlow = remember { MutableStateFlow(location.toMapBoxLocation()) }

            val userState =
                rememberUserLocationState(
                    object : LocationProvider {
                        override val location: StateFlow<Location?> = locationFlow
                    }
                )
            LocationTrackingEffect(userState) {
                cameraState.animateTo(
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = cameraState.position.zoom, // Keep current zoom
                    )
                )
                locationFlow.value = location.toMapBoxLocation()
            }

            val styleState = rememberStyleState()
            Box(modifier = Modifier.fillMaxSize()) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Uri(mapStyle),
                    styleState = styleState,
                    cameraState = cameraState,
                    options =
                        MapOptions(
                            ornamentOptions =
                                OrnamentOptions(
                                    padding = PaddingValues(8.dp),
                                    isScaleBarEnabled = false,
                                    isLogoEnabled = false,
                                    isCompassEnabled = false,
                                )
                        ),
                ) {
                    LocationPuck(
                        idPrefix = "user-location",
                        cameraState = cameraState,
                        locationState = userState,
                        colors = LocationPuckDefaults.colors(),
                    )
                }

                Column(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    CompassButton(cameraState)
                    UserLocationButton {
                        cameraState.position =
                            CameraPosition(
                                target = Position(location.longitude, location.latitude),
                                zoom = cameraState.position.zoom, // Keep current zoom
                            )
                    }
                }
            }
        }
    }
}

private fun GpsLocation.toMapBoxLocation() =
    Location(
        Position(longitude, latitude),
        accuracy.toDouble(),
        bearing = null,
        bearingAccuracy = null,
        speed = null,
        speedAccuracy = null,
        timestamp.toTimeMark(),
    )

private fun ZonedDateTime.toTimeMark(): TimeMark {
    val now = ZonedDateTime.now()
    // Calculate the difference in milliseconds
    val diffMillis = toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

    // Create a mark that is 'diff' away from the current monotonic 'now'
    return TimeSource.Monotonic.markNow() + diffMillis.milliseconds
}

@Composable
private fun UserLocationButton(
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    puckColors: LocationPuckColors = LocationPuckDefaults.colors(),
    puckSizes: LocationPuckSizes = LocationPuckSizes(),
    contentDescription: String = stringResource(R.string.content_desc_user_location),
    size: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(size / 6),
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = modifier.requiredSize(size).aspectRatio(1f),
        onClick = onClick,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
    ) {
        Canvas(modifier = Modifier.semantics { this.contentDescription = contentDescription }) {
            drawCircle(
                puckColors.dotStrokeColor,
                puckSizes.dotRadius.toPx(),
                style = Stroke(puckSizes.dotStrokeWidth.toPx()),
            )
            drawCircle(puckColors.dotFillColorCurrentLocation, puckSizes.dotRadius.toPx())
        }
    }
}
