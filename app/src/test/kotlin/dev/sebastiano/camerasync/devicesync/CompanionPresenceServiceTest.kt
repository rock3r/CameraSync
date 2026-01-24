package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionPresenceServiceTest {

    @Test
    fun `presence handler starts sync when device appears and is enabled`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "00:11:22:33:44:55",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )

        val actions = mutableListOf<Pair<String, Boolean>>()
        val handler =
            PresenceSyncHandler(
                repository = repository,
                isServiceRunning = { false },
                startPresenceSync = { mac, shouldStart -> actions.add(mac to shouldStart) },
            )

        handler.handlePresence("00:11:22:33:44:55", isPresent = true)

        assertEquals(listOf("00:11:22:33:44:55" to true), actions)
    }

    @Test
    fun `presence handler stops sync when device disappears and service running`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )

        val actions = mutableListOf<Pair<String, Boolean>>()
        val handler =
            PresenceSyncHandler(
                repository = repository,
                isServiceRunning = { true },
                startPresenceSync = { mac, shouldStart -> actions.add(mac to shouldStart) },
            )

        handler.handlePresence("AA:BB:CC:DD:EE:FF", isPresent = false)

        assertEquals(listOf("AA:BB:CC:DD:EE:FF" to false), actions)
    }

    @Test
    fun `presence handler ignores disabled device`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "11:22:33:44:55:66",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = false,
            )
        )

        val actions = mutableListOf<Pair<String, Boolean>>()
        val handler =
            PresenceSyncHandler(
                repository = repository,
                isServiceRunning = { true },
                startPresenceSync = { mac, shouldStart -> actions.add(mac to shouldStart) },
            )

        handler.handlePresence("11:22:33:44:55:66", isPresent = true)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `presence handler ignores when sync disabled`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "66:55:44:33:22:11",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )
        repository.setSyncEnabled(false)

        val actions = mutableListOf<Pair<String, Boolean>>()
        val handler =
            PresenceSyncHandler(
                repository = repository,
                isServiceRunning = { true },
                startPresenceSync = { mac, shouldStart -> actions.add(mac to shouldStart) },
            )

        handler.handlePresence("66:55:44:33:22:11", isPresent = true)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `presence handler does not stop sync when service not running`() = runTest {
        val repository = FakePairedDevicesRepository()
        repository.addTestDevice(
            PairedDevice(
                macAddress = "FF:EE:DD:CC:BB:AA",
                name = "Test Camera",
                vendorId = "fake",
                isEnabled = true,
            )
        )

        val actions = mutableListOf<Pair<String, Boolean>>()
        val handler =
            PresenceSyncHandler(
                repository = repository,
                isServiceRunning = { false },
                startPresenceSync = { mac, shouldStart -> actions.add(mac to shouldStart) },
            )

        handler.handlePresence("FF:EE:DD:CC:BB:AA", isPresent = false)

        assertTrue(actions.isEmpty())
    }
}
