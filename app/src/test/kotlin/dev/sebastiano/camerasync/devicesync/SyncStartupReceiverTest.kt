package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStartupReceiverTest {

    @Test
    fun `registerPresenceForEnabledDevices starts observation when sync enabled`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "00:11:22:33:44:55",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )
        repository.addTestDevice(
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Test Camera 2",
                vendorId = "fake",
                isEnabled = true,
            )
        )

        val observed = mutableListOf<String>()
        val handler =
            SyncStartupHandler(
                repository = repository,
                startObserving = { macAddress -> observed.add(macAddress) },
            )

        handler.registerPresenceForEnabledDevices()

        assertEquals(listOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF"), observed)
    }

    @Test
    fun `registerPresenceForEnabledDevices skips when sync disabled`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "11:22:33:44:55:66",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )
        repository.setSyncEnabled(false)

        val observed = mutableListOf<String>()
        val handler =
            SyncStartupHandler(
                repository = repository,
                startObserving = { macAddress -> observed.add(macAddress) },
            )

        handler.registerPresenceForEnabledDevices()

        assertTrue(observed.isEmpty())
    }

    @Test
    fun `registerPresenceForEnabledDevices skips when no enabled devices`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "22:33:44:55:66:77",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = false,
            )
        )

        val observed = mutableListOf<String>()
        val handler =
            SyncStartupHandler(
                repository = repository,
                startObserving = { macAddress -> observed.add(macAddress) },
            )

        handler.registerPresenceForEnabledDevices()

        assertTrue(observed.isEmpty())
    }
}
