package dev.sebastiano.camerasync.firmware.ricoh

import android.content.Context
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.firmware.FirmwareUpdateCheckResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RicohFirmwareUpdateCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: RicohFirmwareUpdateChecker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        checker = RicohFirmwareUpdateChecker(context)
    }

    @Test
    fun `supportsVendor returns true for ricoh vendor`() {
        assertTrue(checker.supportsVendor("ricoh"))
    }

    @Test
    fun `supportsVendor returns false for other vendors`() {
        assertFalse(checker.supportsVendor("sony"))
        assertFalse(checker.supportsVendor("canon"))
    }

    @Test
    fun `isLegacyModel identifies legacy models correctly`() {
        assertTrue(checker.isLegacyModel("RICOH GR II"))
        assertTrue(checker.isLegacyModel("RICOH GR III"))
        assertTrue(checker.isLegacyModel("RICOH GR IIIx"))
        assertTrue(checker.isLegacyModel("GR III HDF"))
        assertTrue(checker.isLegacyModel("GR IIIx HDF"))

        assertFalse(checker.isLegacyModel("RICOH GR IV"))
        assertFalse(checker.isLegacyModel("RICOH GR IV HDF"))
        assertFalse(checker.isLegacyModel("Unknown Camera"))
    }

    @Test
    fun `mapModelToApiCode maps modern models correctly`() {
        assertEquals("gr4", checker.mapModelToApiCode("RICOH GR IV"))
        assertEquals("gr4", checker.mapModelToApiCode("RICOH GR IV HDF"))
        assertEquals("gr4", checker.mapModelToApiCode("GR IV"))

        assertEquals(null, checker.mapModelToApiCode("RICOH GR III"))
        assertEquals(null, checker.mapModelToApiCode("Unknown"))
    }

    @Test
    fun `isNewerVersion compares versions correctly`() {
        assertTrue(checker.isNewerVersion("2.00", "1.91"))
        assertTrue(checker.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(checker.isNewerVersion("2.0", "1.9.9"))

        assertFalse(checker.isNewerVersion("1.91", "2.00"))
        assertFalse(checker.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(checker.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `checkForUpdate returns CheckFailed when firmware version is null`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "RICOH GR III",
                vendorId = "ricoh",
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
    fun `findBestMatchingCameraKey prefers longer key so GR IIIx matches GR IIIx not GR III`() {
        val cameras = mapOf("GR III" to "1.91", "GR IIIx" to "2.00")
        val match = checker.findBestMatchingCameraKey(cameras, "RICOH GR IIIx")
        assertNotNull(match)
        assertEquals("GR IIIx", match!!.first)
        assertEquals("2.00", match.second)
    }

    @Test
    fun `findBestMatchingCameraKey returns GR III for RICOH GR III`() {
        val cameras = mapOf("GR III" to "1.91", "GR IIIx" to "2.00")
        val match = checker.findBestMatchingCameraKey(cameras, "RICOH GR III")
        assertNotNull(match)
        assertEquals("GR III", match!!.first)
        assertEquals("1.91", match.second)
    }

    @Test
    fun `findBestMatchingCameraKey returns null when no key matches`() {
        val cameras = mapOf("GR III" to "1.91")
        val match = checker.findBestMatchingCameraKey(cameras, "Unknown Camera")
        assertNull(match)
    }

    @Test
    fun `checkForUpdate returns CheckFailed when device name is null`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = null,
                vendorId = "ricoh",
                isEnabled = true,
            )

        val result = checker.checkForUpdate(device, "1.00")

        assertTrue(result is FirmwareUpdateCheckResult.CheckFailed)
        assertEquals(
            "Device name not available",
            (result as FirmwareUpdateCheckResult.CheckFailed).reason,
        )
    }

    @Test
    fun `checkForUpdate returns CheckFailed with parsing message when AWS API returns malformed JSON`() =
        runTest {
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse().setResponseCode(200).setBody("not valid json"))
            val checkerWithMockServer =
                RicohFirmwareUpdateChecker(context, awsApiUrl = server.url("/").toString())
            val device =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "RICOH GR IV",
                    vendorId = "ricoh",
                    isEnabled = true,
                )

            val result = checkerWithMockServer.checkForUpdate(device, "1.00")

            server.shutdown()
            assertTrue(result is FirmwareUpdateCheckResult.CheckFailed)
            val reason = (result as FirmwareUpdateCheckResult.CheckFailed).reason
            assertTrue(
                "Reason should indicate parsing error, not network: $reason",
                reason.startsWith("Could not parse firmware response:"),
            )
            assertFalse(
                "Reason must not say network error for serialization failure: $reason",
                reason.contains("Network error"),
            )
        }
}
