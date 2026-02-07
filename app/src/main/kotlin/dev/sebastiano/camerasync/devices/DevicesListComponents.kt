package dev.sebastiano.camerasync.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
internal fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.loading_devices), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun EmptyContent(modifier: Modifier = Modifier) {
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
internal fun DevicesList(
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
                onEnabledChange = { enabled: Boolean ->
                    onDeviceEnabledChange(deviceWithState, enabled)
                },
                onUnpairClick = { onUnpairClick(deviceWithState) },
                onRetryClick = { onRetryClick(deviceWithState) },
            )
        }
    }
}

@Composable
internal fun UnpairConfirmationDialog(
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

@Preview(name = "Loading Content", showBackground = true)
@Composable
private fun LoadingContentPreview() {
    CameraSyncTheme { LoadingContent() }
}

@Preview(name = "Empty Content", showBackground = true)
@Composable
private fun EmptyContentPreview() {
    CameraSyncTheme { EmptyContent() }
}

@Preview(name = "Unpair Confirmation Dialog", showBackground = true)
@Composable
private fun UnpairConfirmationDialogPreview() {
    CameraSyncTheme {
        UnpairConfirmationDialog(deviceName = "GR IIIx", onConfirm = {}, onDismiss = {})
    }
}
