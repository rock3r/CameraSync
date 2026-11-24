package dev.sebastiano.camerasync.pairing

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
internal fun SuccessContent(
    modifier: Modifier = Modifier,
    deviceName: String,
    onClose: () -> Unit,
) {
    PairingStateScaffold(
        modifier = modifier,
        icon = {
            Icon(
                painterResource(R.drawable.ic_check_circle_24dp),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.pairing_state_success, deviceName),
        subtitle = stringResource(R.string.pairing_state_success_subtitle),
    ) {
        Button(onClick = onClose) { Text(stringResource(R.string.action_close)) }
    }
}

@Preview(name = "Success State", showBackground = true)
@Composable
private fun SuccessPreview() {
    CameraSyncTheme { Surface { SuccessContent(deviceName = "GR IIIx", onClose = {}) } }
}
