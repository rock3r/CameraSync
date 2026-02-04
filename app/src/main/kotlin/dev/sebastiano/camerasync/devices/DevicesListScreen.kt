package dev.sebastiano.camerasync.devices

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState

/** Main screen showing the list of paired devices with their sync status. */
@Composable
fun DevicesListScreen(
    viewModel: DevicesListViewModel,
    onAddDeviceClick: () -> Unit,
    onViewLogsClick: () -> Unit,
) {
    val state by viewModel.state
    var deviceToUnpair by remember { mutableStateOf<PairedDeviceWithState?>(null) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

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
            DevicesListTopAppBar(
                state = state,
                onSyncEnabledChange = viewModel::setSyncEnabled,
                onRefreshClick = viewModel::refreshConnections,
                onViewLogsClick = onViewLogsClick,
                onSendFeedbackClick = viewModel::sendFeedback,
            )
        },
        floatingActionButton = {
            DevicesListFloatingActionButton(
                isSyncEnabled = state.isSyncEnabled,
                onClick = onAddDeviceClick,
            )
        },
    ) { innerPadding ->
        DevicesListContent(
            state = state,
            onBatteryOptimizationClick = { showBatteryOptimizationDialog = true },
            onEnableSyncClick = { viewModel.setSyncEnabled(true) },
            onDeviceEnabledChange = { device, enabled ->
                viewModel.setDeviceEnabled(device.device.macAddress, enabled)
            },
            onUnpairClick = { device -> deviceToUnpair = device },
            onRetryClick = { device ->
                @SuppressLint("MissingPermission")
                viewModel.retryConnection(device.device.macAddress)
            },
            modifier = Modifier.padding(innerPadding),
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesListTopAppBar(
    state: DevicesListState,
    onSyncEnabledChange: (Boolean) -> Unit,
    onRefreshClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onSendFeedbackClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            val isSyncEnabled = state.isSyncEnabled

            // Sync toggle switch
            Switch(
                checked = isSyncEnabled,
                onCheckedChange = onSyncEnabledChange,
                modifier = Modifier.padding(end = 8.dp),
            )

            if (state is DevicesListState.HasDevices) {
                val hasEnabledCameras = state.devices.any { it.device.isEnabled }
                IconButton(onClick = onRefreshClick, enabled = hasEnabledCameras && isSyncEnabled) {
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
                            onSendFeedbackClick()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesListFloatingActionButton(isSyncEnabled: Boolean, onClick: () -> Unit) {
    if (isSyncEnabled) {
        FloatingActionButton(onClick = onClick) {
            Icon(
                painterResource(R.drawable.ic_add_camera_24dp),
                contentDescription = stringResource(R.string.content_desc_add_device),
            )
        }
    } else {
        // Wrap in tooltip when disabled
        TooltipBox(
            positionProvider =
                rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 8.dp,
                ),
            tooltip = {
                PlainTooltip { Text(stringResource(R.string.tooltip_sync_disabled_pairing)) }
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
}

@Composable
private fun DevicesListContent(
    state: DevicesListState,
    onBatteryOptimizationClick: () -> Unit,
    onEnableSyncClick: () -> Unit,
    onDeviceEnabledChange: (PairedDeviceWithState, Boolean) -> Unit,
    onUnpairClick: (PairedDeviceWithState) -> Unit,
    onRetryClick: (PairedDeviceWithState) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is DevicesListState.Loading -> {
            LoadingContent(modifier)
        }

        is DevicesListState.Empty -> {
            EmptyContent(modifier)
        }

        is DevicesListState.HasDevices -> {
            Box(modifier = modifier) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LocationCard(
                        location = state.currentLocation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )

                    if (!state.isIgnoringBatteryOptimizations) {
                        BatteryOptimizationWarning(
                            onEnableClick = onBatteryOptimizationClick,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }

                    if (!state.isSyncEnabled) {
                        SyncStoppedWarning(
                            onRefreshClick = onEnableSyncClick,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }

                    DevicesList(
                        devices = state.devices,
                        displayInfoMap = state.displayInfoMap,
                        onDeviceEnabledChange = onDeviceEnabledChange,
                        onUnpairClick = onUnpairClick,
                        onRetryClick = onRetryClick,
                    )
                }

                AnimatedVisibility(state.isScanning, enter = fadeIn(), exit = fadeOut()) {
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    )
                }
            }
        }
    }
}
