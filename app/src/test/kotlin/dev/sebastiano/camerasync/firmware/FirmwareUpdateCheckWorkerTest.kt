package dev.sebastiano.camerasync.firmware

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FirmwareUpdateCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var firmwareUpdateChecker: FirmwareUpdateChecker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        pairedDevicesRepository = FakePairedDevicesRepository()
        firmwareUpdateChecker = mockk(relaxed = true)
    }

    @Test
    fun `doWork returns success when no devices are paired`() = runTest {
        pairedDevicesRepository.setPairedDevices(emptyList())

        val worker =
            FirmwareUpdateCheckWorker(
                context,
                workerParams,
                pairedDevicesRepository,
                listOf(firmwareUpdateChecker),
            )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork checks devices with matching vendor checker`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "ILCE-7M4",
                vendorId = "sony",
                isEnabled = true,
                firmwareVersion = "1.00",
            )
        pairedDevicesRepository.setPairedDevices(listOf(device))

        every { firmwareUpdateChecker.supportsVendor("sony") } returns true
        coEvery { firmwareUpdateChecker.checkForUpdate(device, "1.00") } returns
            FirmwareUpdateCheckResult.NoUpdateAvailable

        val worker =
            FirmwareUpdateCheckWorker(
                context,
                workerParams,
                pairedDevicesRepository,
                listOf(firmwareUpdateChecker),
            )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork sets update available flag when update is found`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "ILCE-7M4",
                vendorId = "sony",
                isEnabled = true,
                firmwareVersion = "1.00",
            )
        pairedDevicesRepository.setPairedDevices(listOf(device))

        every { firmwareUpdateChecker.supportsVendor("sony") } returns true
        coEvery { firmwareUpdateChecker.checkForUpdate(device, "1.00") } returns
            FirmwareUpdateCheckResult.UpdateAvailable(
                currentVersion = "1.00",
                latestVersion = "2.00",
                modelName = "ILCE-7M4",
            )

        val worker =
            FirmwareUpdateCheckWorker(
                context,
                workerParams,
                pairedDevicesRepository,
                listOf(firmwareUpdateChecker),
            )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Verify latest version was set and notification flag was cleared
        val updatedDevice = pairedDevicesRepository.getDevice(device.macAddress)
        assertEquals("2.00", updatedDevice?.latestFirmwareVersion)
        assertEquals(false, updatedDevice?.firmwareUpdateNotificationShown)
    }
}
