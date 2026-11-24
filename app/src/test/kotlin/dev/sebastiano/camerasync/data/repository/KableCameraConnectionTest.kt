package dev.sebastiano.camerasync.data.repository

import com.juul.kable.Peripheral
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.SyncCapabilities
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
 * [`getRemoteControlDelegate()`][KableCameraConnection.getRemoteControlDelegate] caching and retry
 * behavior.
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
                every { getSyncCapabilities() } returns SyncCapabilities()
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
    fun `getRemoteControlDelegate retries after failure and succeeds`() {
        val delegate = FakeRemoteControlDelegate()
        var createCallCount = 0
        val vendor =
            mockk<CameraVendor>(relaxed = true) {
                every { getRemoteControlCapabilities() } returns RemoteControlCapabilities()
                every { getSyncCapabilities() } returns SyncCapabilities()
                every { createRemoteControlDelegate(any(), any()) } answers
                    {
                        createCallCount++
                        if (createCallCount == 1) {
                            error("creation failed")
                        }
                        delegate
                    }
            }
        val connection = createConnection(vendor)

        val first = connection.getRemoteControlDelegate()
        val second = connection.getRemoteControlDelegate()

        assertNull(first)
        assertNotNull(second)
        assertSame(delegate, second)
        verify(exactly = 2) { vendor.createRemoteControlDelegate(any(), any()) }
    }
}
