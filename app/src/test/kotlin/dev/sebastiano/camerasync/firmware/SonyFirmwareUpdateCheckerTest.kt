package dev.sebastiano.camerasync.firmware

import android.content.Context
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.firmware.sony.SonyFirmwareUpdateChecker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonyFirmwareUpdateCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: SonyFirmwareUpdateChecker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        val packageInfo = mockk<android.content.pm.PackageInfo>(relaxed = true)
        packageInfo.versionName = "1.0.0"
        every { context.packageName } returns "dev.sebastiano.camerasync"
        every { context.packageManager.getPackageInfo(any<String>(), any<Int>()) } returns
            packageInfo
        checker = SonyFirmwareUpdateChecker(context)
    }

    @Test
    fun `supportsVendor returns true for sony vendor`() {
        assertTrue(checker.supportsVendor("sony"))
    }

    @Test
    fun `supportsVendor returns false for other vendors`() {
        assertFalse(checker.supportsVendor("ricoh"))
        assertFalse(checker.supportsVendor("canon"))
    }

    @Test
    fun `checkForUpdate returns CheckFailed when firmware version is null`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "ILCE-7M4",
                vendorId = "sony",
                isEnabled = true,
            )

        val result = checker.checkForUpdate(device, null)

        assertTrue(result is FirmwareUpdateCheckResult.CheckFailed)
        assertEquals(
            "Firmware version not available",
            (result as FirmwareUpdateCheckResult.CheckFailed).reason,
        )
    }

    @Test
    fun `checkForUpdate returns CheckFailed when model name cannot be extracted`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Unknown Camera",
                vendorId = "sony",
                isEnabled = true,
            )

        val result = checker.checkForUpdate(device, "1.00")

        assertTrue(result is FirmwareUpdateCheckResult.CheckFailed)
    }
}
