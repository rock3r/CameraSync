package dev.sebastiano.camerasync

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsRequester(
    onPermissionsGranted: () -> Unit,
    content:
        @Composable
        (permissions: List<PermissionInfo>, onRequestAllPermissions: () -> Unit) -> Unit,
) {
    val basePermissions =
        listOf(ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS)
    val allPermissions = basePermissions + ACCESS_BACKGROUND_LOCATION

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions = basePermissions)
    val backgroundPermissionState = rememberPermissionState(permission = ACCESS_BACKGROUND_LOCATION)

    val permissionNames =
        mapOf(
            ACCESS_FINE_LOCATION to stringResource(R.string.perm_location),
            ACCESS_BACKGROUND_LOCATION to stringResource(R.string.perm_bg_location),
            BLUETOOTH_SCAN to stringResource(R.string.perm_bt_scan),
            BLUETOOTH_CONNECT to stringResource(R.string.perm_bt_connect),
            POST_NOTIFICATIONS to stringResource(R.string.perm_notifications),
        )

    val permissionDescriptions =
        mapOf(
            ACCESS_FINE_LOCATION to stringResource(R.string.perm_location_desc),
            ACCESS_BACKGROUND_LOCATION to stringResource(R.string.perm_bg_location_desc),
            BLUETOOTH_SCAN to stringResource(R.string.perm_bt_scan_desc),
            BLUETOOTH_CONNECT to stringResource(R.string.perm_bt_connect_desc),
            POST_NOTIFICATIONS to stringResource(R.string.perm_notifications_desc),
        )

    val permissions =
        allPermissions.map { permission ->
            val permissionState =
                if (permission == ACCESS_BACKGROUND_LOCATION) {
                    backgroundPermissionState
                } else {
                    multiplePermissionsState.permissions.find { it.permission == permission }
                }
            PermissionInfo(
                name = permissionNames[permission] ?: permission,
                description = permissionDescriptions[permission] ?: "",
                isGranted = permissionState?.status?.isGranted ?: false,
            )
        }

    LaunchedEffect(multiplePermissionsState) {
        val backgroundGranted = backgroundPermissionState.status.isGranted
        if (multiplePermissionsState.allPermissionsGranted && backgroundGranted) {
            onPermissionsGranted()
        }
    }

    content(
        permissions,
        {
            val missingPermissions =
                multiplePermissionsState.permissions
                    .filter { !it.status.isGranted }
                    .map { it.permission }
            val backgroundMissing = !backgroundPermissionState.status.isGranted

            if (missingPermissions.isNotEmpty()) {
                multiplePermissionsState.launchMultiplePermissionRequest()
            } else if (backgroundMissing) {
                backgroundPermissionState.launchPermissionRequest()
            }
        },
    )
}
