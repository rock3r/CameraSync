package dev.sebastiano.camerasync.logging

import dev.sebastiano.camerasync.fakes.FakeLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private lateinit var fakeRepository: FakeLogRepository
    private lateinit var viewModel: LogViewerViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLogRepository()
        viewModel = LogViewerViewModel(fakeRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh calls repository refresh`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(fakeRepository.refreshCalled)
    }

    @Test
    fun `logs are filtered by text`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.logs.collect {} }

        val entry1 = LogEntry("01-01 12:00:00", LogLevel.INFO, "Tag1", "Hello world")
        val entry2 = LogEntry("01-01 12:01:00", LogLevel.DEBUG, "Tag2", "Something else")
        fakeRepository.setLogs(listOf(entry1, entry2))
        advanceUntilIdle()

        viewModel.setFilterText("world")
        advanceUntilIdle()
        assertEquals(listOf(entry1), viewModel.logs.value)

        viewModel.setFilterText("Tag2")
        advanceUntilIdle()
        assertEquals(listOf(entry2), viewModel.logs.value)

        viewModel.setFilterText("nonexistent")
        advanceUntilIdle()
        assertTrue(viewModel.logs.value.isEmpty())
    }

    @Test
    fun `logs are filtered by level`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.logs.collect {} }

        val entry1 = LogEntry("01-01 12:00:00", LogLevel.INFO, "Tag1", "Message 1")
        val entry2 = LogEntry("01-01 12:01:00", LogLevel.DEBUG, "Tag2", "Message 2")
        fakeRepository.setLogs(listOf(entry1, entry2))
        advanceUntilIdle()

        viewModel.setFilterLevel(LogLevel.INFO)
        advanceUntilIdle()
        assertEquals(listOf(entry1), viewModel.logs.value)

        viewModel.setFilterLevel(LogLevel.DEBUG)
        advanceUntilIdle()
        assertEquals(listOf(entry2), viewModel.logs.value)

        viewModel.setFilterLevel(null)
        advanceUntilIdle()
        assertEquals(listOf(entry1, entry2), viewModel.logs.value)
    }
}
