package dev.sebastiano.camerasync.pairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import org.junit.Rule
import org.junit.Test

class PairingComponentsTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun scanningContent_showsDiscoveredDevices() {
        val camera =
            Camera(
                identifier = "12:34:56:78:90:AB",
                name = "Ricoh GR IIIx",
                macAddress = "12:34:56:78:90:AB",
                vendor = RicohCameraVendor,
            )
        val deviceUi =
            DiscoveredCameraUi(
                camera = camera,
                make = "Ricoh",
                model = "GR IIIx",
                isProbingModel = false,
            )

        composeTestRule.setContent { ScanningContent(devices = listOf(deviceUi), onPairClick = {}) }

        composeTestRule.onNodeWithText("Ricoh GR IIIx").assertIsDisplayed()
        composeTestRule.onNodeWithText("12:34:56:78:90:AB").assertIsDisplayed()
    }

    @Test
    fun scanningContent_showsEmptyState_whenNoDevices() {
        composeTestRule.setContent { ScanningContent(devices = emptyList(), onPairClick = {}) }

        composeTestRule.onNodeWithText("Scanning for cameras…").assertIsDisplayed()
    }

    @Test
    fun associatingContent_showsCorrectText() {
        composeTestRule.setContent { AssociatingContent() }

        composeTestRule.onNodeWithText("Requesting system permission…").assertIsDisplayed()
    }

    @Test
    fun bondingContent_showsCorrectText() {
        composeTestRule.setContent { BondingContent() }

        composeTestRule.onNodeWithText("Pairing with Android…").assertIsDisplayed()
    }

    @Test
    fun connectingContent_showsCorrectText() {
        composeTestRule.setContent { ConnectingContent(deviceName = "My Camera") }

        composeTestRule.onNodeWithText("Connecting to My Camera…").assertIsDisplayed()
    }

    @Test
    fun successContent_showsCorrectText() {
        composeTestRule.setContent { SuccessContent(deviceName = "My Camera", onClose = {}) }

        composeTestRule.onNodeWithText("Successfully paired with My Camera!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun errorContent_showsErrorTextAndButtons() {
        composeTestRule.setContent {
            ErrorContent(error = PairingError.TIMEOUT, canRetry = true, onCancel = {}, onRetry = {})
        }

        composeTestRule.onNodeWithText("Pairing failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unpair & Restart").assertIsDisplayed()
    }
}
