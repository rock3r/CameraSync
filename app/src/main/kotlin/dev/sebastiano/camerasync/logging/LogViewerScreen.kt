package dev.sebastiano.camerasync.logging

import android.content.res.Configuration
import androidx.compose.foundation.ScrollIndicatorFactory
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.scrollIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme

/** Screen for viewing system and app logs. */
@Composable
fun LogViewerScreen(viewModel: LogViewerViewModel, onNavigateBack: () -> Unit) {
    val logs by viewModel.logs.collectAsState()
    val filterText by viewModel.filterText.collectAsState()
    val filterLevel by viewModel.filterLevel.collectAsState()
    val isRefreshing by viewModel.isRefreshing

    LogViewerScreenContent(
        logs = logs,
        filterText = filterText,
        filterLevel = filterLevel,
        isRefreshing = isRefreshing,
        onNavigateBack = onNavigateBack,
        onFilterTextChange = viewModel::setFilterText,
        onFilterLevelChange = viewModel::setFilterLevel,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerScreenContent(
    logs: List<LogEntry>,
    filterText: String,
    filterLevel: LogLevel?,
    isRefreshing: Boolean,
    onNavigateBack: () -> Unit,
    onFilterTextChange: (String) -> Unit,
    onFilterLevelChange: (LogLevel?) -> Unit,
    onRefresh: () -> Unit,
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(filterText.isNotEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchField = !showSearchField }) {
                        Icon(
                            painterResource(
                                if (showSearchField) R.drawable.ic_close_24dp
                                else R.drawable.ic_search_24dp
                            ),
                            contentDescription =
                                if (showSearchField)
                                    stringResource(R.string.content_desc_close_search)
                                else stringResource(R.string.content_desc_search),
                        )
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_filter_list_24dp),
                                contentDescription = stringResource(R.string.content_desc_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_all_levels)) },
                                onClick = {
                                    onFilterLevelChange(null)
                                    showFilterMenu = false
                                },
                            )
                            LogLevel.entries
                                .filter { it != LogLevel.UNKNOWN }
                                .forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.name) },
                                        onClick = {
                                            onFilterLevelChange(level)
                                            showFilterMenu = false
                                        },
                                    )
                                }
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_refresh_24dp),
                                contentDescription = stringResource(R.string.content_desc_refresh),
                            )
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showSearchField) {
                TextField(
                    value = filterText,
                    onValueChange = onFilterTextChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.placeholder_filter_logs)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = null)
                    },
                    trailingIcon = {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { onFilterTextChange("") }) {
                                Icon(
                                    painterResource(R.drawable.ic_close_24dp),
                                    contentDescription = stringResource(R.string.content_desc_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                )
            }

            if (filterLevel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.label_level_filter, filterLevel.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = getLogLevelColor(filterLevel),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onFilterLevelChange(null) },
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_close_24dp),
                            contentDescription =
                                stringResource(R.string.content_desc_clear_level_filter),
                        )
                    }
                }
            }

            val listState = rememberLazyListState()
            val hasScrolledToEnd = rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty() && !hasScrolledToEnd.value) {
                    listState.animateScrollToItem(logs.size - 1)
                    hasScrolledToEnd.value = true
                }
            }

            val scrollbarFactory = remember { LogScrollbarFactory() }
            val scrollbarModifier =
                listState.scrollIndicatorState?.let {
                    Modifier.scrollIndicator(
                        factory = scrollbarFactory,
                        state = it,
                        orientation = Orientation.Vertical,
                    )
                } ?: Modifier

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(scrollbarModifier),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(logs) { entry ->
                    LogEntryItem(entry)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LogEntryLandscape(entry)
    } else {
        LogEntryPortrait(entry)
    }
}

@Composable
private fun LogEntryPortrait(entry: LogEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogLevelBadge(entry.level)
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = getLogLevelColor(entry.level),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.message,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LogEntryLandscape(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(120.dp)) {
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogLevelBadge(entry.level)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = getLogLevelColor(entry.level),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = entry.message,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogLevelBadge(level: LogLevel) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(getLogLevelColor(level).copy(alpha = 0.2f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.name.take(1),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = getLogLevelColor(level),
        )
    }
}

@Composable
private fun getLogLevelColor(level: LogLevel): Color =
    when (level) {
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.DEBUG -> Color(0xFF2196F3)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.WARN -> Color(0xFFFFC107)
        LogLevel.ERROR -> Color(0xFFF44336)
        LogLevel.ASSERT -> Color(0xFF9C27B0)
        LogLevel.UNKNOWN -> Color.Gray
    }

private data class LogScrollbarFactory(
    val thumbThickness: Dp = 4.dp,
    val padding: Dp = 2.dp,
    val thumbColor: Color = Color.Gray,
    val thumbAlpha: Float = 0.5f,
) : ScrollIndicatorFactory {
    override fun createNode(
        state: ScrollIndicatorState,
        orientation: Orientation,
    ): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()

                if (state.contentSize <= state.viewportSize || state.viewportSize == 0) return

                val visibleContentRatio = state.viewportSize.toFloat() / state.contentSize
                val thumbLength = state.viewportSize * visibleContentRatio
                val thumbPosition = state.scrollOffset * visibleContentRatio

                val thumbThicknessPx = thumbThickness.toPx()
                val paddingPx = padding.toPx()

                val (topLeft, size) =
                    when (orientation) {
                        Orientation.Vertical -> {
                            val x = size.width - thumbThicknessPx - paddingPx
                            Offset(x, thumbPosition) to Size(thumbThicknessPx, thumbLength)
                        }
                        Orientation.Horizontal -> {
                            val y = size.height - thumbThicknessPx - paddingPx
                            Offset(thumbPosition, y) to Size(thumbLength, thumbThicknessPx)
                        }
                    }

                drawRect(color = thumbColor, topLeft = topLeft, size = size, alpha = thumbAlpha)
            }
        }
}

@Preview(name = "Log Viewer Screen", showBackground = true)
@Composable
private fun LogViewerScreenPreview() {
    CameraSyncTheme {
        LogViewerScreenContent(
            logs =
                listOf(
                    LogEntry("12:00:01", LogLevel.INFO, "MainActivity", "App started"),
                    LogEntry("12:00:05", LogLevel.DEBUG, "BLE", "Scanning for devices..."),
                    LogEntry("12:00:10", LogLevel.WARN, "Sync", "Connection weak, retrying in 5s"),
                    LogEntry("12:00:15", LogLevel.ERROR, "Sync", "Failed to connect to GR IIIx"),
                ),
            filterText = "",
            filterLevel = null,
            isRefreshing = false,
            onNavigateBack = {},
            onFilterTextChange = {},
            onFilterLevelChange = {},
            onRefresh = {},
        )
    }
}

@Preview(name = "Log Entry - Portrait", showBackground = true)
@Composable
private fun LogEntryPortraitPreview() {
    CameraSyncTheme {
        LogEntryPortrait(
            LogEntry(
                timestamp = "12:00:00.000",
                level = LogLevel.DEBUG,
                tag = "CameraRepository",
                message = "Searching for cameras with service UUID 0000... ",
            )
        )
    }
}
