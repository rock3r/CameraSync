package dev.sebastiano.camerasync.pairing

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.camerasync.TestActivity
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PairingComponentsTest {
    private val registerActivityRule =
        object : ExternalResource() {
            override fun before() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                shadowOf(context.packageManager)
                    .addActivityIfNotPresent(ComponentName(context, TestActivity::class.java))
            }
        }

    private val composeTestRule = createAndroidComposeRule<TestActivity>()

    @get:Rule val ruleChain = RuleChain.outerRule(registerActivityRule).around(composeTestRule)

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
            DiscoveredCameraUi.Detected(camera = camera, make = "Ricoh", model = "GR IIIx")

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
    fun scanningContent_pairInvokesCallback() {
        var clicked = false
        val camera =
            Camera(
                identifier = "12:34:56:78:90:AB",
                name = "Ricoh GR IIIx",
                macAddress = "12:34:56:78:90:AB",
                vendor = RicohCameraVendor,
            )
        val deviceUi =
            DiscoveredCameraUi.Detected(camera = camera, make = "Ricoh", model = "GR IIIx")

        composeTestRule.setContent {
            ScanningContent(devices = listOf(deviceUi), onPairClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Pair").performClick()
        assertTrue(clicked)
    }

    @Test
    fun detectingRow_disablesPairAction() {
        var clicked = false
        val camera =
            Camera(
                identifier = "12:34:56:78:90:AB",
                name = "Ricoh GR IIIx",
                macAddress = "12:34:56:78:90:AB",
                vendor = RicohCameraVendor,
            )
        val deviceUi = DiscoveredCameraUi.Detecting(camera = camera)

        composeTestRule.setContent {
            ScanningContent(devices = listOf(deviceUi), onPairClick = { clicked = true })
        }

        composeTestRule.onAllNodesWithText("Pair").assertCountEquals(0)
        composeTestRule.onNodeWithText("Identifying camera…").performClick()
        assertFalse(clicked)
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
    fun successContent_closeInvokesCallback() {
        var closed = false
        composeTestRule.setContent {
            SuccessContent(deviceName = "My Camera", onClose = { closed = true })
        }

        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(closed)
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

    @Test
    fun errorContent_hidesRetry_whenCannotRetry() {
        composeTestRule.setContent {
            ErrorContent(
                error = PairingError.UNKNOWN,
                canRetry = false,
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Unpair & Restart").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    // ==================== C1: ErrorContent callback invocation tests ====================

    @Test
    fun errorContent_cancelInvokesCallback() {
        var cancelled = false
        composeTestRule.setContent {
            ErrorContent(
                error = PairingError.TIMEOUT,
                canRetry = true,
                onCancel = { cancelled = true },
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun errorContent_retryInvokesCallback() {
        var retried = false
        composeTestRule.setContent {
            ErrorContent(
                error = PairingError.TIMEOUT,
                canRetry = true,
                onCancel = {},
                onRetry = { retried = true },
            )
        }

        composeTestRule.onNodeWithText("Unpair & Restart").performClick()
        assertTrue(retried)
    }

    // ==================== Error variant text tests ====================

    @Test
    fun errorContent_showsRejectedErrorText() {
        composeTestRule.setContent {
            ErrorContent(
                error = PairingError.REJECTED,
                canRetry = true,
                onCancel = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Pairing failed").assertIsDisplayed()
        // Rejected error has specific text from strings.xml
        composeTestRule
            .onNodeWithText("The camera rejected the pairing request.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun errorContent_showsUnknownErrorText() {
        composeTestRule.setContent {
            ErrorContent(error = PairingError.UNKNOWN, canRetry = true, onCancel = {}, onRetry = {})
        }

        composeTestRule.onNodeWithText("Pairing failed").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("An unexpected error occurred", substring = true)
            .assertIsDisplayed()
    }

    // ==================== SuccessContent subtitle test ====================

    @Test
    fun successContent_showsSubtitle() {
        composeTestRule.setContent { SuccessContent(deviceName = "My Camera", onClose = {}) }

        composeTestRule
            .onNodeWithText("Your camera is now ready to sync GPS data.")
            .assertIsDisplayed()
    }

    // ==================== Mixed Detecting/Detected list test ====================

    @Test
    fun scanningContent_showsMixedDetectingAndDetected() {
        val detectedCamera =
            Camera(
                identifier = "12:34:56:78:90:AB",
                name = "Ricoh GR IIIx",
                macAddress = "12:34:56:78:90:AB",
                vendor = RicohCameraVendor,
            )
        val detectingCamera =
            Camera(
                identifier = "AA:BB:CC:DD:EE:FF",
                name = "ILCE-7M4",
                macAddress = "AA:BB:CC:DD:EE:FF",
                vendor = RicohCameraVendor,
            )

        val devices =
            listOf(
                DiscoveredCameraUi.Detected(
                    camera = detectedCamera,
                    make = "Ricoh",
                    model = "GR IIIx",
                ),
                DiscoveredCameraUi.Detecting(camera = detectingCamera),
            )

        var clickedCamera: Camera? = null
        composeTestRule.setContent {
            ScanningContent(devices = devices, onPairClick = { clickedCamera = it })
        }

        // Detected device shows make+model and has Pair button
        composeTestRule.onNodeWithText("Ricoh GR IIIx").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pair").assertIsDisplayed()

        // Detecting device shows identifying text
        composeTestRule.onNodeWithText("Identifying camera…").assertIsDisplayed()

        // Only one "Pair" button (for the detected device)
        composeTestRule.onAllNodesWithText("Pair").assertCountEquals(1)

        // Clicking Pair triggers callback with the detected camera
        composeTestRule.onNodeWithText("Pair").performClick()
        assertTrue(clickedCamera === detectedCamera)
    }

    // ==================== B2: Cancel button on intermediate states ====================

    @Test
    fun associatingContent_showsCancelButton() {
        var cancelled = false
        composeTestRule.setContent { AssociatingContent(onCancel = { cancelled = true }) }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun bondingContent_showsCancelButton() {
        var cancelled = false
        composeTestRule.setContent { BondingContent(onCancel = { cancelled = true }) }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun connectingContent_showsCancelButton() {
        var cancelled = false
        composeTestRule.setContent {
            ConnectingContent(deviceName = "My Camera", onCancel = { cancelled = true })
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun associatingContent_hidesCancelButton_whenNull() {
        composeTestRule.setContent { AssociatingContent(onCancel = null) }

        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    // ==================== C3: Long device name test ====================

    @Test
    fun connectingContent_showsLongDeviceName() {
        val longName = "Ricoh GR IIIx HDF Special Edition Long Name Camera Device"
        composeTestRule.setContent { ConnectingContent(deviceName = longName) }

        composeTestRule
            .onNodeWithText("Connecting to $longName\u2026", substring = true)
            .assertExists()
    }

    // ==================== C6: Scanning pill indicator test ====================

    @Test
    fun scanningContent_showsScanningPillWhenDevicesPresent() {
        val devices =
            listOf(
                DiscoveredCameraUi.Detected(
                    camera = Camera("id1", "GR IIIx", "AA:BB:CC:DD:EE:FF", RicohCameraVendor),
                    make = "Ricoh",
                    model = "GR IIIx",
                )
            )

        composeTestRule.setContent { ScanningContent(devices = devices, onPairClick = {}) }

        composeTestRule.onNodeWithText("Scanning\u2026").assertIsDisplayed()
    }
}
