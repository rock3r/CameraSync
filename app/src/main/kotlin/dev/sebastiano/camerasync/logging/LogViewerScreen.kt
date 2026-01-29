package dev.sebastiano.camerasync.logging

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.camerasync.R

/** Screen for viewing system and app logs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(viewModel: LogViewerViewModel, onNavigateBack: () -> Unit) {
    val logs by viewModel.logs.collectAsState()
    val filterText by viewModel.filterText.collectAsState()
    val filterLevel by viewModel.filterLevel.collectAsState()
    val isRefreshing by viewModel.isRefreshing

    var showFilterMenu by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

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
                                    viewModel.setFilterLevel(null)
                                    showFilterMenu = false
                                },
                            )
                            LogLevel.entries
                                .filter { it != LogLevel.UNKNOWN }
                                .forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.name) },
                                        onClick = {
                                            viewModel.setFilterLevel(level)
                                            showFilterMenu = false
                                        },
                                    )
                                }
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
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
                    onValueChange = { viewModel.setFilterText(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.placeholder_filter_logs)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = null)
                    },
                    trailingIcon = {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setFilterText("") }) {
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
                        stringResource(R.string.label_level_filter, filterLevel!!.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = getLogLevelColor(filterLevel!!),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.setFilterLevel(null) },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
