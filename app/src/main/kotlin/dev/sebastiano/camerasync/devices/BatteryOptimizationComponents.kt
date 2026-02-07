package dev.sebastiano.camerasync.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.sebastiano.camerasync.util.BatteryOptimizationUtil

@Composable
internal fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
internal fun BatteryOptimizationWarning(onEnableClick: () -> Unit, modifier: Modifier = Modifier) {
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
internal fun SyncStoppedWarning(onRefreshClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Preview(name = "Battery Optimization Warning", showBackground = true)
@Composable
private fun BatteryOptimizationWarningPreview() {
    CameraSyncTheme {
        Box(Modifier.padding(16.dp)) { BatteryOptimizationWarning(onEnableClick = {}) }
    }
}

@Preview(name = "Sync Stopped Warning", showBackground = true)
@Composable
private fun SyncStoppedWarningPreview() {
    CameraSyncTheme { Box(Modifier.padding(16.dp)) { SyncStoppedWarning(onRefreshClick = {}) } }
}

@Preview(name = "Battery Optimization Dialog", showBackground = true)
@Composable
private fun BatteryOptimizationDialogPreview() {
    CameraSyncTheme { BatteryOptimizationDialog(onConfirm = {}, onDismiss = {}) }
}
