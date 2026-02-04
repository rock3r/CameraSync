package dev.sebastiano.camerasync.pairing

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R

/** Screen for pairing new camera devices using the system flow. */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onNavigateBack: () -> Unit,
    onDevicePaired: () -> Unit,
) {
    val state by viewModel.state

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
                        stringResource(R.string.pairing_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back_24dp),
                            stringResource(R.string.content_desc_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.pairing_feedback),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { viewModel.sendFeedback() },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val currentState = state) {
                is PairingScreenState.Idle -> {
                    IdleContent(
                        modifier = Modifier.fillMaxSize(),
                        onStartPairing = { viewModel.requestCompanionPairing() },
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
                        onRetry = {
                            viewModel.pairDevice(currentState.camera, allowExistingBond = true)
                        },
                        onCancel = { viewModel.cancelPairing() },
                    )
                }
            }
        }
    }
}
