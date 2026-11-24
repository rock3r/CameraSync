package dev.sebastiano.camerasync.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

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

@Preview(name = "Error State - Timeout", showBackground = true)
@Composable
private fun ErrorPreview() {
    CameraSyncTheme {
        Surface {
            ErrorContent(error = PairingError.TIMEOUT, canRetry = true, onCancel = {}, onRetry = {})
        }
    }
}

@Preview(name = "Error State - Rejected", showBackground = true)
@Composable
private fun ErrorRejectedPreview() {
    CameraSyncTheme {
        Surface {
            ErrorContent(
                error = PairingError.REJECTED,
                canRetry = true,
                onCancel = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Error State - Unknown", showBackground = true)
@Composable
private fun ErrorUnknownPreview() {
    CameraSyncTheme {
        Surface {
            ErrorContent(
                error = PairingError.UNKNOWN,
                canRetry = false,
                onCancel = {},
                onRetry = {},
            )
        }
    }
}
