package dev.sebastiano.camerasync.domain.vendor

import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class RemoteControlDelegateTest {

    private class MinimalDelegate : RemoteControlDelegate {
        private val _connectionMode = MutableStateFlow(ShootingConnectionMode.BLE_ONLY)
        override val connectionMode: StateFlow<ShootingConnectionMode> =
            _connectionMode.asStateFlow()

        override suspend fun triggerCapture() = Unit

        override suspend fun startBulbExposure() = Unit

        override suspend fun stopBulbExposure() = Unit

        override fun observeBatteryLevel(): Flow<BatteryInfo> = emptyFlow()

        override fun observeStorageStatus(): Flow<StorageInfo> = emptyFlow()

        override fun observeCameraMode(): Flow<CameraMode> = emptyFlow()

        override fun observeCaptureStatus(): Flow<CaptureStatus> = emptyFlow()

        override fun observeExposureMode(): Flow<ExposureMode> = emptyFlow()

        override fun observeDriveMode(): Flow<DriveMode> = emptyFlow()

        override suspend fun connectWifi() = Unit

        override suspend fun disconnectWifi() = Unit
    }

    @Test
    fun `default optional methods throw UnsupportedOperationException`() = runTest {
        val delegate = MinimalDelegate()

        assertThrowsUnsupported { delegate.toggleAELock() }
        assertThrowsUnsupported { delegate.toggleFELock() }
        assertThrowsUnsupported { delegate.toggleAWBLock() }
        assertThrowsUnsupported { delegate.focusNear() }
        assertThrowsUnsupported { delegate.focusFar() }
        assertThrowsUnsupported { delegate.zoomIn() }
        assertThrowsUnsupported { delegate.zoomOut() }
        assertThrowsUnsupported { delegate.touchAF(0.5f, 0.5f) }
        assertThrowsUnsupported { delegate.toggleVideoRecording() }
    }

    private suspend fun assertThrowsUnsupported(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // expected
        }
    }
}
