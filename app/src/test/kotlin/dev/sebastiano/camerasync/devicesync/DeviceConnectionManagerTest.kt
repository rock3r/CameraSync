package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.repository.CameraConnection
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceConnectionManagerTest {

    @Test
    fun `removeConnectionIfMatches keeps newer connection`() {
        val manager = DeviceConnectionManager()
        val macAddress = "AA:BB:CC:DD:EE:FF"
        val initialConnection = mockk<CameraConnection>(relaxed = true)
        val replacementConnection = mockk<CameraConnection>(relaxed = true)
        val initialJob = Job()
        val replacementJob = Job()

        try {
            manager.addConnection(macAddress, initialConnection, initialJob)
            manager.addConnection(macAddress, replacementConnection, replacementJob)

            val (removedConnection, removedJob) =
                manager.removeConnectionIfMatches(macAddress, initialJob)

            assertNull(removedConnection)
            assertNull(removedJob)
            assertEquals(replacementConnection, manager.getConnection(macAddress))
        } finally {
            initialJob.cancel()
            replacementJob.cancel()
        }
    }

    @Test
    fun `removeConnectionIfMatches removes when job matches`() {
        val manager = DeviceConnectionManager()
        val macAddress = "AA:BB:CC:DD:EE:FF"
        val connection = mockk<CameraConnection>(relaxed = true)
        val job = Job()

        try {
            manager.addConnection(macAddress, connection, job)

            val (removedConnection, removedJob) = manager.removeConnectionIfMatches(macAddress, job)

            assertEquals(connection, removedConnection)
            assertEquals(job, removedJob)
            assertNull(manager.getConnection(macAddress))
        } finally {
            job.cancel()
        }
    }
}
