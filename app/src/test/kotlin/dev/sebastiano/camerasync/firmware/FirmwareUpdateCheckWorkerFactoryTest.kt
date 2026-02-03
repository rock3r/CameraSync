package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.WorkerParameters
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirmwareUpdateCheckWorkerFactoryTest {

    private lateinit var pairedDevicesRepository: PairedDevicesRepository
    private lateinit var firmwareUpdateCheckers: List<FirmwareUpdateChecker>
    private lateinit var factory: FirmwareUpdateCheckWorkerFactory

    @Before
    fun setUp() {
        pairedDevicesRepository = FakePairedDevicesRepository()
        firmwareUpdateCheckers = emptyList()
        factory = FirmwareUpdateCheckWorkerFactory(pairedDevicesRepository, firmwareUpdateCheckers)
    }

    @Test
    fun `createWorker returns worker for FirmwareUpdateCheckWorker`() {
        val context = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true)

        val worker =
            factory.createWorker(context, FirmwareUpdateCheckWorker::class.java.name, params)

        assertNotNull(worker)
        assertTrue(worker is FirmwareUpdateCheckWorker)
    }

    @Test
    fun `createWorker returns null for unknown worker class`() {
        val context = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true)

        val worker = factory.createWorker(context, "UnknownWorker", params)

        assertNull(worker)
    }
}
