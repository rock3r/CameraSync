package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FirmwareUpdateSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun `triggerOneTimeCheck schedules work with network constraint`() {
        val workRequestSlot = slot<OneTimeWorkRequest>()

        FirmwareUpdateScheduler.triggerOneTimeCheck(context)

        verify {
            workManager.enqueueUniqueWork(
                "firmware_update_check_one_time",
                ExistingWorkPolicy.REPLACE,
                capture(workRequestSlot),
            )
        }

        val constraints = workRequestSlot.captured.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun `scheduleDailyCheck schedules periodic work with network constraint`() {
        val workRequestSlot = slot<PeriodicWorkRequest>()

        FirmwareUpdateScheduler.scheduleDailyCheck(context)

        verify {
            workManager.enqueueUniquePeriodicWork(
                "firmware_update_check",
                ExistingPeriodicWorkPolicy.KEEP,
                capture(workRequestSlot),
            )
        }

        val constraints = workRequestSlot.captured.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun `cancelCheck cancels both periodic and one-time work`() {
        FirmwareUpdateScheduler.cancelCheck(context)

        verify {
            workManager.cancelUniqueWork("firmware_update_check")
            workManager.cancelUniqueWork("firmware_update_check_one_time")
        }
    }

    @Test
    fun `triggerOneTimeCheck handles WorkManager initialization failure`() {
        every { WorkManager.getInstance(any()) } throws IllegalStateException("Not initialized")

        // Should not crash
        FirmwareUpdateScheduler.triggerOneTimeCheck(context)
    }

    @Test(expected = IllegalStateException::class)
    fun `scheduleDailyCheck rethrows WorkManager initialization failure`() {
        every { WorkManager.getInstance(any()) } throws IllegalStateException("Not initialized")

        FirmwareUpdateScheduler.scheduleDailyCheck(context)
    }

    @Test
    fun `cancelCheck handles WorkManager initialization failure`() {
        every { WorkManager.getInstance(any()) } throws IllegalStateException("Not initialized")

        // Should not crash
        FirmwareUpdateScheduler.cancelCheck(context)
    }
}
