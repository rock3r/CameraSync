package dev.sebastiano.camerasync.ui.remote

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.camerasync.domain.model.BatteryInfo
import dev.sebastiano.camerasync.domain.model.CameraMode
import dev.sebastiano.camerasync.domain.model.CaptureStatus
import dev.sebastiano.camerasync.domain.model.DriveMode
import dev.sebastiano.camerasync.domain.model.ExposureMode
import dev.sebastiano.camerasync.domain.model.StorageInfo
import dev.sebastiano.camerasync.domain.vendor.BatteryMonitoringCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.ShootingConnectionMode
import dev.sebastiano.camerasync.domain.vendor.StorageMonitoringCapabilities
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteShootingStatusBarTest {

    @get:Rule val composeTestRule = createComposeRule()

    private class ThrowingStatusDelegate : RemoteControlDelegate {
        private val _connectionMode = MutableStateFlow(ShootingConnectionMode.BLE_ONLY)
        override val connectionMode: StateFlow<ShootingConnectionMode> =
            _connectionMode.asStateFlow()

        override suspend fun triggerCapture() = Unit

        override suspend fun startBulbExposure() = Unit

        override suspend fun stopBulbExposure() = Unit

        override fun observeBatteryLevel(): Flow<BatteryInfo> = flow {
            throw IllegalStateException("Boom")
        }

        override fun observeStorageStatus(): Flow<StorageInfo> = flow {
            throw IllegalStateException("Boom")
        }

        override fun observeCameraMode(): Flow<CameraMode> = emptyFlow()

        override fun observeCaptureStatus(): Flow<CaptureStatus> = emptyFlow()

        override fun observeExposureMode(): Flow<ExposureMode> = emptyFlow()

        override fun observeDriveMode(): Flow<DriveMode> = emptyFlow()

        override suspend fun connectWifi() = Unit

        override suspend fun disconnectWifi() = Unit
    }

    @Test
    fun statusBar_handlesFlowFailures() {
        val capabilities =
            RemoteControlCapabilities(
                batteryMonitoring = BatteryMonitoringCapabilities(supported = true),
                storageMonitoring = StorageMonitoringCapabilities(supported = true),
            )

        composeTestRule.setContent {
            CameraSyncTheme { StatusBar(capabilities, ThrowingStatusDelegate()) }
        }

        composeTestRule.onNodeWithText("Battery: --").assertExists()
        composeTestRule.onNodeWithText("Storage: No card").assertExists()
    }
}
