package dev.sebastiano.camerasync.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.FocusStatus
import dev.sebastiano.camerasync.domain.model.StorageInfo
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private fun batteryDrawableResId(battery: BatteryInfo?): Int =
    when {
        battery == null -> R.drawable.battery_android_question_24dp
        battery.isCharging -> R.drawable.battery_android_bolt_24dp
        battery.levelPercentage == 0 -> R.drawable.battery_android_alert_24dp
        battery.levelPercentage == 100 -> R.drawable.battery_android_full_24dp
        battery.levelPercentage < 17 -> R.drawable.battery_android_1_24dp
        battery.levelPercentage < 34 -> R.drawable.battery_android_2_24dp
        battery.levelPercentage < 51 -> R.drawable.battery_android_3_24dp
        battery.levelPercentage < 67 -> R.drawable.battery_android_4_24dp
        battery.levelPercentage < 84 -> R.drawable.battery_android_5_24dp
        else -> R.drawable.battery_android_6_24dp
    }

private fun exposureModeDrawableResId(mode: ExposureMode): Int =
    when (mode) {
        ExposureMode.PROGRAM_AUTO -> R.drawable.mode_program_auto
        ExposureMode.APERTURE_PRIORITY -> R.drawable.mode_aperture
        ExposureMode.SHUTTER_PRIORITY -> R.drawable.mode_shutter
        ExposureMode.MANUAL -> R.drawable.mode_manual
        ExposureMode.BULB,
        ExposureMode.TIME,
        ExposureMode.BULB_TIMER -> R.drawable.bulb_24dp
        ExposureMode.AUTO,
        ExposureMode.SNAP_FOCUS_PROGRAM,
        ExposureMode.UNKNOWN -> R.drawable.mode_auto
    }

private fun driveModeDrawableResId(drive: DriveMode): Int =
    when (drive) {
        DriveMode.SINGLE_SHOOTING -> R.drawable.drive_single
        DriveMode.CONTINUOUS_SHOOTING -> R.drawable.drive_continuous
        DriveMode.SELF_TIMER_2S -> R.drawable.drive_timer_2s
        DriveMode.SELF_TIMER_10S -> R.drawable.drive_timer_10s
        DriveMode.BRACKET -> R.drawable.drive_bracket
        DriveMode.INTERVAL,
        DriveMode.MULTI_EXPOSURE,
        DriveMode.UNKNOWN -> R.drawable.drive_single
    }

private fun focusStatusDrawableResId(status: FocusStatus): Int =
    when (status) {
        FocusStatus.MANUAL -> R.drawable.focus_manual
        FocusStatus.LOCKED -> R.drawable.focus_auto_single
        FocusStatus.SEARCHING -> R.drawable.focus_auto_continuous
        FocusStatus.LOST -> R.drawable.focus_auto_auto
    }

@Composable
fun RemoteShootingScreen(viewModel: RemoteShootingViewModel, onBackClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        RemoteShootingUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is RemoteShootingUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Error: ${state.message}")
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(onClick = onBackClick) { Text(stringResource(R.string.action_go_back)) }
                }
            }
        }
        is RemoteShootingUiState.Ready -> {
            RemoteShootingContent(
                deviceName = state.deviceName,
                capabilities = state.capabilities,
                delegate = state.delegate,
                actionState = state.actionState,
                onBackClick = onBackClick,
                onTriggerCapture = { viewModel.triggerCapture() },
                onDisconnectWifi = { viewModel.disconnectWifi() },
                onRetryAction = viewModel::retryAction,
                onResetRemoteShooting = viewModel::resetRemoteShooting,
                onDismissActionError = viewModel::dismissActionError,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteShootingContent(
    deviceName: String,
    capabilities: RemoteControlCapabilities,
    delegate: RemoteControlDelegate,
    actionState: RemoteShootingActionState,
    onBackClick: () -> Unit,
    onTriggerCapture: () -> Unit,
    onDisconnectWifi: () -> Unit,
    onRetryAction: (RemoteShootingAction) -> Unit,
    onResetRemoteShooting: () -> Unit,
    onDismissActionError: () -> Unit,
) {
    val connectionMode by delegate.connectionMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Make scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Connection Banner
            ConnectionBanner(
                mode = connectionMode,
                capabilities = capabilities,
                onDisconnectWifi = onDisconnectWifi,
            )

            Spacer(modifier = Modifier.size(16.dp))

            RemoteShootingActionBanner(
                actionState = actionState,
                onRetryAction = onRetryAction,
                onResetRemoteShooting = onResetRemoteShooting,
                onDismissActionError = onDismissActionError,
            )

            if (actionState !is RemoteShootingActionState.Idle) {
                Spacer(modifier = Modifier.size(16.dp))
            }

            // 2. Status Bar (Battery, Storage, Mode)
            StatusBar(capabilities, delegate)

            Spacer(modifier = Modifier.size(16.dp))

            // 3. Live View (if supported & connected)
            if (capabilities.liveView.supported && connectionMode == ShootingConnectionMode.FULL) {
                LiveViewPanel(capabilities)
                Spacer(modifier = Modifier.size(16.dp))
            }

            // 4. Capture Button (Always visible if supported)
            if (capabilities.remoteCapture.supported) {
                CaptureButton(capabilities, onCapture = onTriggerCapture)
                Spacer(modifier = Modifier.size(16.dp))
            }

            // 5. Focus Status (if supported - works over BLE independently)
            if (
                capabilities.autofocus.supported &&
                    capabilities.autofocus.supportsFocusStatusReading
            ) {
                FocusStatusWidget(delegate)
                Spacer(modifier = Modifier.size(16.dp))
            }

            // 6. Advanced Shooting Controls (if supported)
            if (capabilities.advancedShooting.supported) {
                // Check if current mode supports these controls
                val canShowControls =
                    !capabilities.advancedShooting.requiresWifi ||
                        connectionMode == ShootingConnectionMode.FULL

                if (canShowControls) {
                    AdvancedShootingControls(capabilities, delegate)
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }

            // 7. Image Control (Ricoh specific)
            if (
                capabilities.imageControl.supported && connectionMode == ShootingConnectionMode.FULL
            ) {
                ImageControlPanel()
            }
        }
    }
}

@Composable
internal fun RemoteShootingActionBanner(
    actionState: RemoteShootingActionState,
    onRetryAction: (RemoteShootingAction) -> Unit,
    onResetRemoteShooting: () -> Unit,
    onDismissActionError: () -> Unit,
) {
    when (actionState) {
        is RemoteShootingActionState.Error -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = actionState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (actionState.canRetry) {
                            Button(onClick = { onRetryAction(actionState.action) }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                        if (actionState.canReset) {
                            OutlinedButton(onClick = onResetRemoteShooting) {
                                Text(stringResource(R.string.action_reset_remote_shooting))
                            }
                        }
                        TextButton(onClick = onDismissActionError) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                }
            }
        }
        is RemoteShootingActionState.InProgress -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.remote_action_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        RemoteShootingActionState.Idle -> Unit
    }
}

@Composable
fun ConnectionBanner(
    mode: ShootingConnectionMode,
    capabilities: RemoteControlCapabilities,
    onDisconnectWifi: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text =
                    when (mode) {
                        ShootingConnectionMode.BLE_ONLY ->
                            stringResource(R.string.remote_connection_ble)
                        ShootingConnectionMode.FULL ->
                            stringResource(R.string.remote_connection_wifi)
                    },
                style = MaterialTheme.typography.titleMedium,
            )

            // Full mode (Wi‑Fi) switch is not implemented yet; do not show Connect Wi‑Fi button.
            if (capabilities.connectionModeSupport.wifiAddsFeatures) {
                Spacer(modifier = Modifier.size(8.dp))
                if (mode == ShootingConnectionMode.BLE_ONLY) {
                    Text(
                        stringResource(R.string.remote_wifi_coming_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(onClick = onDisconnectWifi) {
                        Text(stringResource(R.string.remote_disconnect_wifi))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBar(capabilities: RemoteControlCapabilities, delegate: RemoteControlDelegate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (capabilities.batteryMonitoring.supported) {
                val batteryFlow =
                    remember(delegate) {
                        delegate
                            .observeBatteryLevel()
                            .map<BatteryInfo, BatteryInfo?> { it }
                            .catch { emit(null) }
                    }
                val battery by batteryFlow.collectAsState(initial = null)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(batteryDrawableResId(battery)),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (battery != null) {
                            stringResource(
                                R.string.remote_status_battery,
                                battery!!.levelPercentage,
                            )
                        } else {
                            stringResource(R.string.remote_status_battery_unknown)
                        }
                    )
                }
            }
            if (capabilities.storageMonitoring.supported) {
                val storageFlow =
                    remember(delegate) {
                        delegate
                            .observeStorageStatus()
                            .map<StorageInfo, StorageInfo?> { it }
                            .catch { emit(null) }
                    }
                val storage by storageFlow.collectAsState(initial = null)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sd_card_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        when {
                            storage?.isPresent == true ->
                                stringResource(
                                    R.string.remote_status_storage,
                                    storage?.remainingShots?.toString() ?: "--",
                                )
                            else -> stringResource(R.string.remote_status_storage_no_card)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LiveViewPanel(capabilities: RemoteControlCapabilities) {
    Card(
        modifier = Modifier.fillMaxWidth().size(200.dp) // Aspect ratio placeholder
    ) {
        // Live view stream rendering would go here
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.remote_live_view_placeholder))
            if (capabilities.remoteCapture.supportsTouchAF) {
                Text(
                    stringResource(R.string.remote_live_view_touch_af),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun CaptureButton(capabilities: RemoteControlCapabilities, onCapture: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onCapture, modifier = Modifier.size(88.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_photo_camera_48dp),
                contentDescription = stringResource(R.string.content_desc_capture),
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.remote_capture_button),
            style = MaterialTheme.typography.labelMedium,
        )
        if (capabilities.remoteCapture.supportsHalfPressAF) {
            Text(
                stringResource(R.string.remote_half_press_supported),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AdvancedShootingControls(
    capabilities: RemoteControlCapabilities,
    delegate: RemoteControlDelegate,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.remote_shooting_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (capabilities.advancedShooting.supportsExposureModeReading) {
                    val exposureModeFlow =
                        remember(delegate) {
                            delegate.observeExposureMode().catch { emit(ExposureMode.UNKNOWN) }
                        }
                    val mode by exposureModeFlow.collectAsState(initial = ExposureMode.UNKNOWN)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(exposureModeDrawableResId(mode)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Column {
                            Text(
                                stringResource(R.string.remote_label_mode),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(mode.toString(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (capabilities.advancedShooting.supportsDriveModeReading) {
                    val driveModeFlow =
                        remember(delegate) {
                            delegate.observeDriveMode().catch { emit(DriveMode.UNKNOWN) }
                        }
                    val drive by driveModeFlow.collectAsState(initial = DriveMode.UNKNOWN)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(driveModeDrawableResId(drive)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Column {
                            Text(
                                stringResource(R.string.remote_label_drive),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(drive.toString(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusStatusWidget(delegate: RemoteControlDelegate) {
    val focusFlow =
        remember(delegate) { delegate.observeFocusStatus()?.catch { emit(FocusStatus.LOST) } }
    if (focusFlow != null) {
        val focusStatus by focusFlow.collectAsState(initial = FocusStatus.LOST)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(focusStatusDrawableResId(focusStatus)),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Column {
                    Text(
                        stringResource(R.string.remote_label_focus),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(focusStatus.toString(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun ImageControlPanel() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.remote_image_control),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.remote_image_control_standard),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Preset list, parameters
        }
    }
}
