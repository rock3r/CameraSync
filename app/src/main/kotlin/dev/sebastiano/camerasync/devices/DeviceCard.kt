package dev.sebastiano.camerasync.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.devicesync.formatElapsedTimeSince
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
internal fun DeviceCard(
    deviceWithState: PairedDeviceWithState,
    displayInfo: DeviceDisplayInfo?,
    onEnabledChange: (Boolean) -> Unit,
    onUnpairClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRemoteControlClick: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val device = deviceWithState.device
    val connectionState = deviceWithState.connectionState
    val unknownString = stringResource(R.string.label_unknown)
    val info =
        displayInfo
            ?: DeviceDisplayInfo(
                unknownString,
                unknownString,
                device.name,
                supportsRemoteControl = false,
                showPairingName = false,
            )

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
                            DeviceDetailRowWithBadge(
                                label = stringResource(R.string.label_firmware),
                                value = version,
                                latestVersion = device.latestFirmwareVersion,
                            )
                        }
                    } else if (device.firmwareVersion != null) {
                        // Show firmware version even when not syncing if we have it stored
                        DeviceDetailRowWithBadge(
                            label = stringResource(R.string.label_firmware),
                            value = device.firmwareVersion,
                            latestVersion = device.latestFirmwareVersion,
                        )
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Remote Control action - visible only when connected
                        if (
                            info.supportsRemoteControl &&
                                (connectionState is DeviceConnectionState.Connected ||
                                    connectionState is DeviceConnectionState.Syncing)
                        ) {
                            FilledTonalButton(onClick = onRemoteControlClick) {
                                Icon(
                                    painterResource(R.drawable.ic_photo_camera_24dp),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.remote_shooting_title))
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        // Unpair action
                        TextButton(onClick = onUnpairClick) {
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
private fun DeviceDetailRowWithBadge(label: String, value: String, latestVersion: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp).alignByBaseline(),
        )
        Row(
            modifier = Modifier.alignByBaseline(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            if (latestVersion != null) {
                Badge(
                    modifier = Modifier.alignByBaseline(),
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text =
                            stringResource(R.string.badge_firmware_update_available, latestVersion),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Preview(name = "Disconnected", showBackground = true)
@Composable
private fun DeviceCardDisconnectedPreview() {
    CameraSyncTheme {
        DeviceCard(
            deviceWithState =
                PairedDeviceWithState(
                    device =
                        PairedDevice(
                            macAddress = "00:11:22:33:44:55",
                            name = "GR IIIx",
                            vendorId = "ricoh",
                            isEnabled = true,
                        ),
                    connectionState = DeviceConnectionState.Disconnected,
                ),
            displayInfo =
                DeviceDisplayInfo(
                    "Ricoh",
                    "GR IIIx",
                    "My Camera",
                    supportsRemoteControl = true,
                    showPairingName = true,
                ),
            onEnabledChange = {},
            onUnpairClick = {},
            onRetryClick = {},
            onRemoteControlClick = {},
        )
    }
}

@Preview(name = "Syncing with Firmware Update", showBackground = true)
@Composable
private fun DeviceCardSyncingPreview() {
    CameraSyncTheme {
        DeviceCard(
            deviceWithState =
                PairedDeviceWithState(
                    device =
                        PairedDevice(
                            macAddress = "00:11:22:33:44:55",
                            name = "GR IIIx",
                            vendorId = "ricoh",
                            isEnabled = true,
                            lastSyncedAt = System.currentTimeMillis() - 30000,
                            firmwareVersion = "1.10",
                            latestFirmwareVersion = "1.20",
                        ),
                    connectionState = DeviceConnectionState.Syncing(firmwareVersion = "1.10"),
                ),
            displayInfo =
                DeviceDisplayInfo(
                    "Ricoh",
                    "GR IIIx",
                    null,
                    supportsRemoteControl = true,
                    showPairingName = false,
                ),
            onEnabledChange = {},
            onUnpairClick = {},
            onRetryClick = {},
            onRemoteControlClick = {},
        )
    }
}

@Preview(name = "Unreachable", showBackground = true)
@Composable
private fun DeviceCardUnreachablePreview() {
    CameraSyncTheme {
        DeviceCard(
            deviceWithState =
                PairedDeviceWithState(
                    device =
                        PairedDevice(
                            macAddress = "00:11:22:33:44:55",
                            name = "Alpha 7 IV",
                            vendorId = "sony",
                            isEnabled = true,
                        ),
                    connectionState = DeviceConnectionState.Unreachable,
                ),
            displayInfo =
                DeviceDisplayInfo(
                    "Sony",
                    "Alpha 7 IV",
                    "Studio A",
                    supportsRemoteControl = true,
                    showPairingName = true,
                ),
            onEnabledChange = {},
            onUnpairClick = {},
            onRetryClick = {},
            onRemoteControlClick = {},
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun DeviceCardErrorPreview() {
    CameraSyncTheme {
        DeviceCard(
            deviceWithState =
                PairedDeviceWithState(
                    device =
                        PairedDevice(
                            macAddress = "00:11:22:33:44:55",
                            name = "GR IIIx",
                            vendorId = "ricoh",
                            isEnabled = true,
                        ),
                    connectionState =
                        DeviceConnectionState.Error(
                            message = "Bluetooth connection lost unexpectedly",
                            isRecoverable = true,
                        ),
                ),
            displayInfo =
                DeviceDisplayInfo(
                    "Ricoh",
                    "GR IIIx",
                    null,
                    supportsRemoteControl = true,
                    showPairingName = false,
                ),
            onEnabledChange = {},
            onUnpairClick = {},
            onRetryClick = {},
            onRemoteControlClick = {},
        )
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun DeviceCardDisabledPreview() {
    CameraSyncTheme {
        DeviceCard(
            deviceWithState =
                PairedDeviceWithState(
                    device =
                        PairedDevice(
                            macAddress = "00:11:22:33:44:55",
                            name = "GR IIIx",
                            vendorId = "ricoh",
                            isEnabled = false,
                        ),
                    connectionState = DeviceConnectionState.Disabled,
                ),
            displayInfo =
                DeviceDisplayInfo(
                    "Ricoh",
                    "GR IIIx",
                    null,
                    supportsRemoteControl = true,
                    showPairingName = false,
                ),
            onEnabledChange = {},
            onUnpairClick = {},
            onRetryClick = {},
            onRemoteControlClick = {},
        )
    }
}
