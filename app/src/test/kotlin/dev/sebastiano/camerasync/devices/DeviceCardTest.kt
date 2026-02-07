package dev.sebastiano.camerasync.devices

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.camerasync.domain.model.DeviceConnectionState
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.model.PairedDeviceWithState
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI tests for [DeviceCard], focusing on Remote Control button visibility based on connection
 * state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceCardTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun remoteControlButton_visibleWhenConnected() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "GR IIIx",
                                    vendorId = "ricoh",
                                    isEnabled = true,
                                ),
                            connectionState = DeviceConnectionState.Connected(),
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Ricoh",
                            "GR IIIx",
                            null,
                            supportsRemoteControl = true,
                            showPairingName = false,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Ricoh GR IIIx").performClick()
        composeTestRule.onNodeWithText("Remote control").assertExists()
    }

    @Test
    fun remoteControlButton_hiddenWhenConnectedButUnsupported() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "GR IIIx",
                                    vendorId = "ricoh",
                                    isEnabled = true,
                                ),
                            connectionState = DeviceConnectionState.Connected(),
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Ricoh",
                            "GR IIIx",
                            null,
                            supportsRemoteControl = false,
                            showPairingName = false,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Ricoh GR IIIx").performClick()
        composeTestRule.onNodeWithText("Remote control").assertDoesNotExist()
    }

    @Test
    fun remoteControlButton_visibleWhenSyncing() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "GR IIIx",
                                    vendorId = "ricoh",
                                    isEnabled = true,
                                ),
                            connectionState =
                                DeviceConnectionState.Syncing(firmwareVersion = "1.10"),
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Ricoh",
                            "GR IIIx",
                            null,
                            supportsRemoteControl = true,
                            showPairingName = false,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Ricoh GR IIIx").performClick()
        composeTestRule.onNodeWithText("Remote control").assertExists()
    }

    @Test
    fun remoteControlButton_hiddenWhenDisconnected() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "GR IIIx",
                                    vendorId = "ricoh",
                                    isEnabled = true,
                                ),
                            connectionState = DeviceConnectionState.Disconnected,
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Ricoh",
                            "GR IIIx",
                            "My Camera",
                            supportsRemoteControl = true,
                            showPairingName = true,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Ricoh GR IIIx (My Camera)").performClick()
        composeTestRule.onNodeWithText("Remote control").assertDoesNotExist()
    }

    @Test
    fun remoteControlButton_hiddenWhenUnreachable() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "Alpha 7 IV",
                                    vendorId = "sony",
                                    isEnabled = true,
                                ),
                            connectionState = DeviceConnectionState.Unreachable,
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Sony",
                            "Alpha 7 IV",
                            "Studio A",
                            supportsRemoteControl = true,
                            showPairingName = true,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Sony Alpha 7 IV (Studio A)").performClick()
        composeTestRule.onNodeWithText("Remote control").assertDoesNotExist()
    }

    @Test
    fun remoteControlButton_hiddenWhenDisabled() {
        composeTestRule.setContent {
            CameraSyncTheme {
                DeviceCard(
                    deviceWithState =
                        PairedDeviceWithState(
                            device =
                                PairedDevice(
                                    macAddress = "00:11:22:33:44:55",
                                    name = "GR IIIx",
                                    vendorId = "ricoh",
                                    isEnabled = false,
                                ),
                            connectionState = DeviceConnectionState.Disabled,
                        ),
                    displayInfo =
                        DeviceDisplayInfo(
                            "Ricoh",
                            "GR IIIx",
                            null,
                            supportsRemoteControl = true,
                            showPairingName = false,
                        ),
                    onEnabledChange = {},
                    onUnpairClick = {},
                    onRetryClick = {},
                    onRemoteControlClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Ricoh GR IIIx").performClick()
        composeTestRule.onNodeWithText("Remote control").assertDoesNotExist()
    }
}
