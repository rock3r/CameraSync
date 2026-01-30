package dev.sebastiano.camerasync

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.sebastiano.camerasync.devices.DevicesListScreen
import dev.sebastiano.camerasync.devices.DevicesListViewModel
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import dev.sebastiano.camerasync.devicesync.registerNotificationChannel
import dev.sebastiano.camerasync.di.AppGraph
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.logging.LogViewerScreen
import dev.sebastiano.camerasync.logging.LogViewerViewModel
import dev.sebastiano.camerasync.pairing.BluetoothBondingChecker
import dev.sebastiano.camerasync.pairing.PairingScreen
import dev.sebastiano.camerasync.pairing.PairingViewModel
import dev.sebastiano.camerasync.permissions.PermissionsScreen
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import dev.zacsweers.metro.Inject

@Inject
class MainActivity(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val locationRepository: LocationRepository,
    private val vendorRegistry: CameraVendorRegistry,
    private val bluetoothBondingChecker: BluetoothBondingChecker,
    private val cameraRepository: CameraRepository,
    private val issueReporter: IssueReporter,
) : ComponentActivity() {

    private val appGraph: AppGraph by lazy { (application as CameraSyncApp).appGraph }
    private var shouldShowPermissionsState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        registerNotificationChannel(this)

        shouldShowPermissionsState =
            intent.getBooleanExtra(MultiDeviceSyncService.EXTRA_SHOW_PERMISSIONS, false)

        setContent {
            RootComposable(
                viewModelFactory = appGraph.viewModelFactory(),
                shouldShowPermissions = shouldShowPermissionsState,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shouldShowPermissionsState =
            intent.getBooleanExtra(MultiDeviceSyncService.EXTRA_SHOW_PERMISSIONS, false)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RootComposable(
    viewModelFactory: ViewModelProvider.Factory,
    shouldShowPermissions: Boolean = false,
) {
    CameraSyncTheme {
        val basePermissions =
            listOf(ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS)
        val multiplePermissionsState =
            rememberMultiplePermissionsState(permissions = basePermissions)
        val backgroundPermissionState =
            rememberPermissionState(permission = ACCESS_BACKGROUND_LOCATION)

        // Initialize backStack - validate saved state against current permissions
        val backStack =
            rememberSaveable(
                saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
            ) {
                mutableStateListOf<NavRoute>(NavRoute.NeedsPermissions)
            }

        // Validate saved backStack state: if we saved DevicesList but permissions are now missing,
        // reset to NeedsPermissions
        LaunchedEffect(
            multiplePermissionsState.allPermissionsGranted,
            backgroundPermissionState.status.isGranted,
        ) {
            val allPermissionsGranted =
                multiplePermissionsState.allPermissionsGranted &&
                    backgroundPermissionState.status.isGranted
            val currentRoute = backStack.firstOrNull()

            if (!allPermissionsGranted && currentRoute == NavRoute.DevicesList) {
                // Permissions were revoked or background location is missing - go back to
                // permissions screen
                backStack.clear()
                backStack.add(NavRoute.NeedsPermissions)
            } else if (allPermissionsGranted && currentRoute == NavRoute.NeedsPermissions) {
                // All permissions granted - navigate to DevicesList
                backStack[0] = NavRoute.DevicesList
            }
        }

        // Navigate to permissions screen if requested from notification
        LaunchedEffect(shouldShowPermissions) {
            if (shouldShowPermissions) {
                // Clear back stack and show permissions screen
                backStack.clear()
                backStack.add(NavRoute.NeedsPermissions)
            }
        }

        // Check if ALL permissions (including background location) are granted and navigate to
        // DevicesList
        // This handles both startup (when permissions are already granted) and runtime (when user
        // grants permissions)
        LaunchedEffect(
            multiplePermissionsState.allPermissionsGranted,
            backgroundPermissionState.status.isGranted,
        ) {
            val allPermissionsGranted =
                multiplePermissionsState.allPermissionsGranted &&
                    backgroundPermissionState.status.isGranted
            if (allPermissionsGranted && backStack.contains(NavRoute.NeedsPermissions)) {
                // Replace NeedsPermissions with DevicesList
                val needsPermissionsIndex = backStack.indexOf(NavRoute.NeedsPermissions)
                backStack[needsPermissionsIndex] = NavRoute.DevicesList
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut())
            },
            popTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut())
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut())
            },
        ) { key ->
            NavEntry(key) {
                when (key) {
                    NavRoute.NeedsPermissions -> {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                backStack.add(NavRoute.DevicesList)
                                backStack.remove(NavRoute.NeedsPermissions)
                            }
                        )
                    }

                    NavRoute.DevicesList -> {
                        val devicesListViewModel: DevicesListViewModel =
                            viewModel(factory = viewModelFactory)

                        DevicesListScreen(
                            viewModel = devicesListViewModel,
                            onAddDeviceClick = { backStack.add(NavRoute.Pairing) },
                            onViewLogsClick = { backStack.add(NavRoute.LogViewer) },
                        )
                    }

                    NavRoute.LogViewer -> {
                        val logViewerViewModel: LogViewerViewModel =
                            viewModel(factory = viewModelFactory)

                        LogViewerScreen(
                            viewModel = logViewerViewModel,
                            onNavigateBack = {
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            },
                        )
                    }

                    NavRoute.Pairing -> {
                        val pairingViewModel: PairingViewModel =
                            viewModel(factory = viewModelFactory)
                        val context = LocalContext.current

                        PairingScreen(
                            viewModel = pairingViewModel,
                            onNavigateBack = {
                                // Can't use removeLast before API 35
                                // Ensure we don't remove the last item (must keep at least one
                                // route)
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            },
                            onDevicePaired = {
                                // Can't use removeLast before API 35
                                // Ensure we don't remove the last item (must keep at least one
                                // route)
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                // Trigger a refresh so the newly paired device connects
                                // immediately.
                                androidx.core.content.ContextCompat.startForegroundService(
                                    context,
                                    MultiDeviceSyncService.createRefreshIntent(context),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
