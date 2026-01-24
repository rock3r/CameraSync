package dev.sebastiano.camerasync.logging

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel for the Log Viewer screen. */
class LogViewerViewModel(
    private val logRepository: LogRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText

    private val _filterLevel = MutableStateFlow<LogLevel?>(null)
    val filterLevel: StateFlow<LogLevel?> = _filterLevel

    private val _isRefreshing = mutableStateOf(false)
    val isRefreshing: State<Boolean> = _isRefreshing

    /** The filtered list of log entries. */
    val logs: StateFlow<List<LogEntry>> =
        combine(logRepository.getLogs(), _filterText, _filterLevel) { allLogs, text, level ->
                allLogs.filter { entry ->
                    val matchesText =
                        text.isBlank() ||
                            entry.message.contains(text, ignoreCase = true) ||
                            entry.tag.contains(text, ignoreCase = true)
                    val matchesLevel = level == null || entry.level == level
                    matchesText && matchesLevel
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        refresh()
    }

    /** Refreshes the logs from the repository. */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            _isRefreshing.value = true
            try {
                logRepository.refresh()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Sets the text filter for the logs. */
    fun setFilterText(text: String) {
        _filterText.value = text
    }

    /** Sets the level filter for the logs. */
    fun setFilterLevel(level: LogLevel?) {
        _filterLevel.value = level
    }
}
