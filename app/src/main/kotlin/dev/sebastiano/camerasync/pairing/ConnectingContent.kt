package dev.sebastiano.camerasync.pairing

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
internal fun ConnectingContent(
    modifier: Modifier = Modifier,
    deviceName: String,
    onCancel: (() -> Unit)? = null,
) {
    PairingStateScaffold(
        modifier = modifier,
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.pairing_state_connecting, deviceName),
    ) {
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Preview(name = "Connecting State", showBackground = true)
@Composable
private fun ConnectingPreview() {
    CameraSyncTheme { Surface { ConnectingContent(deviceName = "GR IIIx") } }
}
