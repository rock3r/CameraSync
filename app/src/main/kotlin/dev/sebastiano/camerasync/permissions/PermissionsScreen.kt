package dev.sebastiano.camerasync.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.PermissionInfo
import dev.sebastiano.camerasync.PermissionsRequester
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val viewModel = remember { PermissionsViewModel() }
    val showSuccessAnimation = viewModel.showSuccessAnimation.value
    val shouldNavigate by viewModel.shouldNavigate.collectAsState()

    // Trigger navigation when ViewModel signals it
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            onPermissionsGranted()
        }
    }

    PermissionsRequester(
        onPermissionsGranted = {
            // Trigger animation when all permissions are granted
            viewModel.onAllPermissionsGranted()
        }
    ) { permissions, requestAll ->
        // Check permissions on each recomposition to catch when they're all granted
        LaunchedEffect(permissions) { viewModel.checkPermissions(permissions) }

        PermissionsScreenContent(
            permissions = permissions,
            showSuccessAnimation = showSuccessAnimation,
            onRequestAll = requestAll,
        )
    }
}

@Composable
private fun PermissionsScreenContent(
    permissions: List<PermissionInfo>,
    showSuccessAnimation: Boolean,
    onRequestAll: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundPermissionName = stringResource(R.string.perm_bg_location)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            // Success animation overlay
            if (showSuccessAnimation) {
                key(showSuccessAnimation) {
                    AnimatedTickMark(
                        modifier = Modifier.size(120.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Main content with fade animation
            val contentAlpha by
                animateFloatAsState(
                    targetValue = if (showSuccessAnimation) 0f else 1f,
                    label = "content_alpha",
                    animationSpec = tween(durationMillis = 500),
                )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.welcome_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Permissions checklist
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        permissions.forEachIndexed { index, permission ->
                            PermissionChecklistItem(
                                permission = permission,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (index < permissions.size - 1) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Request button - only show when there are missing permissions
                val hasMissingPermissions = permissions.any { !it.isGranted }
                val backgroundMissing =
                    permissions.any { it.name == backgroundPermissionName && !it.isGranted }
                AnimatedVisibility(visible = hasMissingPermissions) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ElevatedButton(
                            onClick = { onRequestAll() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_grant_all),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (backgroundMissing) {
                            OutlinedButton(
                                onClick = {
                                    val intent =
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_open_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionChecklistItem(permission: PermissionInfo, modifier: Modifier = Modifier) {
    val iconColor by
        animateColorAsState(
            targetValue =
                if (permission.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            label = "icon_color",
            animationSpec = tween(durationMillis = 300),
        )

    val checkmarkScale by
        animateFloatAsState(
            targetValue = if (permission.isGranted) 1f else 0f,
            label = "checkmark_scale",
            animationSpec = tween(durationMillis = 300),
        )

    val itemAlpha by
        animateFloatAsState(
            targetValue = if (permission.isGranted) 0.7f else 1f,
            label = "item_alpha",
            animationSpec = tween(durationMillis = 300),
        )

    Row(
        modifier = modifier.alpha(itemAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Permission icon
        Box(
            modifier =
                Modifier.size(48.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val icon =
                when {
                    permission.name == stringResource(R.string.perm_location) ->
                        R.drawable.ic_location_on_24dp
                    permission.name == stringResource(R.string.perm_bg_location) ->
                        R.drawable.ic_location_on_24dp
                    permission.name == stringResource(R.string.perm_bt_scan) ||
                        permission.name == stringResource(R.string.perm_bt_connect) ->
                        R.drawable.ic_bluetooth_24dp
                    permission.name == stringResource(R.string.perm_notifications) ->
                        R.drawable.ic_notifications_24dp
                    else -> R.drawable.ic_select_check_box_24dp
                }
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }

        // Permission info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Status indicator
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = permission.isGranted,
                animationSpec = tween(durationMillis = 300),
                label = "permission_status",
            ) { isGranted ->
                if (isGranted) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle_24dp),
                        contentDescription = stringResource(R.string.content_desc_granted),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.scale(checkmarkScale),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedTickMark(
    modifier: Modifier = Modifier,
    color: Color = Color.Green,
    strokeWidth: Float = 8f,
    animationDuration: Int = 600,
) {
    // Start at 0 and animate to 1
    var targetProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) { targetProgress = 1f }

    val pathProgress by
        animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = animationDuration),
            label = "tick_path_progress",
        )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2

        // Define tick mark points
        val startX = centerX - width * 0.25f
        val startY = centerY
        val middleX = centerX
        val middleY = centerY + height * 0.2f
        val endX = centerX + width * 0.3f
        val endY = centerY - height * 0.2f

        // Calculate segment lengths
        val firstSegmentLength =
            kotlin.math.sqrt(
                (middleX - startX) * (middleX - startX) + (middleY - startY) * (middleY - startY)
            )
        val secondSegmentLength =
            kotlin.math.sqrt(
                (endX - middleX) * (endX - middleX) + (endY - middleY) * (endY - middleY)
            )
        val totalLength = firstSegmentLength + secondSegmentLength

        // Draw first segment (bottom-left to center)
        if (pathProgress > 0f) {
            val firstProgress = (pathProgress * totalLength / firstSegmentLength).coerceAtMost(1f)
            val firstEndX = startX + (middleX - startX) * firstProgress
            val firstEndY = startY + (middleY - startY) * firstProgress

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(firstEndX, firstEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // Draw second segment (center to top-right)
        if (pathProgress * totalLength > firstSegmentLength) {
            val secondProgress =
                ((pathProgress * totalLength - firstSegmentLength) / secondSegmentLength)
                    .coerceAtMost(1f)
            val secondEndX = middleX + (endX - middleX) * secondProgress
            val secondEndY = middleY + (endY - middleY) * secondProgress

            drawLine(
                color = color,
                start = Offset(middleX, middleY),
                end = Offset(secondEndX, secondEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(name = "Permissions Screen", showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    CameraSyncTheme {
        PermissionsScreenContent(
            permissions =
                listOf(
                    PermissionInfo("Location", "Required for GPS sync", true),
                    PermissionInfo("Bluetooth", "Required for camera connection", false),
                    PermissionInfo("Notifications", "Required for status updates", false),
                ),
            showSuccessAnimation = false,
            onRequestAll = {},
        )
    }
}

@Preview(name = "Permissions Screen - Success", showBackground = true)
@Composable
private fun PermissionsScreenSuccessPreview() {
    CameraSyncTheme {
        PermissionsScreenContent(
            permissions =
                listOf(
                    PermissionInfo("Location", "Required for GPS sync", true),
                    PermissionInfo("Bluetooth", "Required for camera connection", true),
                    PermissionInfo("Notifications", "Required for status updates", true),
                ),
            showSuccessAnimation = true,
            onRequestAll = {},
        )
    }
}

@Preview(name = "Permission Item - Granted", showBackground = true)
@Composable
private fun PermissionChecklistItemGrantedPreview() {
    CameraSyncTheme {
        Box(Modifier.padding(16.dp)) {
            PermissionChecklistItem(
                permission = PermissionInfo("Location", "Required for GPS sync", true),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Permission Item - Missing", showBackground = true)
@Composable
private fun PermissionChecklistItemMissingPreview() {
    CameraSyncTheme {
        Box(Modifier.padding(16.dp)) {
            PermissionChecklistItem(
                permission = PermissionInfo("Bluetooth", "Required for camera connection", false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
