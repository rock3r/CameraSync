package dev.sebastiano.camerasync.data.repository

import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.fakes.FakeRemoteControlDelegate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for [KableCameraConnection], focusing on
 * [`getRemoteControlDelegate()`][KableCameraConnection.getRemoteControlDelegate] caching and
 * failure-once behavior.
 */
class KableCameraConnectionTest {

    private fun createConnection(vendor: CameraVendor): KableCameraConnection {
        val peripheral =
            mockk<Peripheral>(relaxed = true) {
                every { state } returns MutableStateFlow(mockk(relaxed = true))
                every { services } returns MutableStateFlow(null)
            }
        val connectionDelegate = mockk<VendorConnectionDelegate>(relaxed = true)
        val camera =
            Camera(
                identifier = "test-id",
                name = "Test Camera",
                macAddress = "AA:BB:CC:DD:EE:FF",
                vendor = vendor,
            )
        return KableCameraConnection(camera, peripheral, connectionDelegate)
    }

    @Test
    fun `getRemoteControlDelegate returns delegate from vendor and caches it`() {
        val delegate = FakeRemoteControlDelegate()
        val vendor =
            mockk<CameraVendor>(relaxed = true) {
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                every { createRemoteControlDelegate(any(), any()) } returns delegate
            }
        val connection = createConnection(vendor)

        val first = connection.getRemoteControlDelegate()
        val second = connection.getRemoteControlDelegate()

        assertNotNull(first)
        assertSame(delegate, first)
        assertSame(first, second)
        verify(exactly = 1) { vendor.createRemoteControlDelegate(any(), any()) }
    }

    @Test
    fun `getRemoteControlDelegate returns null when vendor throws and does not retry`() {
        val vendor =
            mockk<CameraVendor>(relaxed = true) {
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                every { createRemoteControlDelegate(any(), any()) } throws
                    RuntimeException("creation failed")
            }
        val connection = createConnection(vendor)

        val first = connection.getRemoteControlDelegate()
        val second = connection.getRemoteControlDelegate()

        assertNull(first)
        assertNull(second)
        verify(exactly = 1) { vendor.createRemoteControlDelegate(any(), any()) }
    }

    @Test
    fun `getRemoteControlDelegate returns null when vendor throws and subsequent calls do not invoke vendor again`() {
        var createCallCount = 0
        val vendor =
            mockk<CameraVendor>(relaxed = true) {
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                every { createRemoteControlDelegate(any(), any()) } answers
                    {
                        createCallCount++
                        error("creation failed")
                    }
            }
        val connection = createConnection(vendor)

        repeat(5) { connection.getRemoteControlDelegate() }

        assertNull(connection.getRemoteControlDelegate())
        assert(createCallCount == 1) {
            "createRemoteControlDelegate should be called exactly once, was $createCallCount"
        }
    }
}
