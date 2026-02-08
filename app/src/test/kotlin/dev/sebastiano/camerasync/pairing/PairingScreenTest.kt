package dev.sebastiano.camerasync.pairing

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.TestActivity
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.fakes.FakeBluetoothBondingChecker
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeIssueReporter
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric integration tests for [PairingScreen].
 *
 * These tests verify the full screen composable, including the top bar, feedback bottom bar, and
 * correct content rendering for each [PairingScreenState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PairingScreenTest {
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

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var bluetoothBondingChecker: FakeBluetoothBondingChecker
    private lateinit var issueReporter: FakeIssueReporter
    private lateinit var viewModel: PairingViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testCamera =
        Camera(
            identifier = "AA:BB:CC:DD:EE:FF",
            name = "Test Camera",
            macAddress = "AA:BB:CC:DD:EE:FF",
            vendor = FakeCameraVendor,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        cameraRepository = FakeCameraRepository()
        bluetoothBondingChecker = FakeBluetoothBondingChecker()
        issueReporter = FakeIssueReporter()
        viewModel =
            PairingViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                cameraRepository = cameraRepository,
                bluetoothBondingChecker = bluetoothBondingChecker,
                companionDeviceManagerHelper = mockk(relaxed = true),
                issueReporter = issueReporter,
                ioDispatcher = testDispatcher,
                mainDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Scaffold / chrome tests ====================

    @Test
    fun pairingScreen_showsTopBarTitle() {
        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Add Camera").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_showsFeedbackBottomBar() {
        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Having issues? Let us know").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_backButtonInvokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            PairingScreen(
                viewModel = viewModel,
                onNavigateBack = { backClicked = true },
                onDevicePaired = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun pairingScreen_feedbackLinkTriggersSendFeedback() {
        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Having issues? Let us know").performClick()
        composeTestRule.waitForIdle()

        assertTrue(issueReporter.sendIssueReportCalled)
    }

    // ==================== State-to-content mapping tests ====================

    @Test
    fun pairingScreen_scanningState_showsScanningContent() {
        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        // Initial state is Scanning with no devices => empty scanning UI
        composeTestRule.onNodeWithText("Scanning for cameras\u2026").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_scanningState_withDevices_showsDeviceList() = runTest {
        cameraRepository.emitDiscoveredCamera(testCamera)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        // After model probing completes, the headline is "${make} ${model}".
        // FakeCameraVendor.vendorName = "Fake Camera", FakeCameraConnection.modelName = "Test
        // Model"
        composeTestRule.onNodeWithText("Fake Camera Test Model").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_associatingState_showsAssociatingContent() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Requesting system permission\u2026").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_bondingState_showsBondingContent() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        // Trigger association result to move to Bonding
        // createBondAutoBonds=false so it stays in Bonding state
        bluetoothBondingChecker.createBondAutoBonds = false
        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        // Don't advance until idle — the bonding poll loop would run forever.
        // The state transitions synchronously to Bonding before the polling starts.
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Pairing with Android\u2026").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_successState_showsSuccessContent() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Successfully paired with Test Camera!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_errorState_showsErrorContent() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        cameraRepository.connectException = java.io.IOException("Connection failed")

        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Pairing failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unpair & Restart").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_errorState_cancelReturnsToScanning() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        cameraRepository.connectException = java.io.IOException("Connection failed")
        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        // Clear the connect exception so scanning can proceed
        cameraRepository.connectException = null

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // Should be back to scanning
        composeTestRule.onNodeWithText("Scanning for cameras\u2026").assertIsDisplayed()
    }

    // ==================== Chrome persists across states ====================

    @Test
    fun pairingScreen_topBarAndFeedbackPersistInErrorState() = runTest {
        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()

        cameraRepository.connectException = java.io.IOException("Connection failed")
        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        // Top bar still present
        composeTestRule.onNodeWithText("Add Camera").assertIsDisplayed()
        // Feedback still present
        composeTestRule.onNodeWithText("Having issues? Let us know").assertIsDisplayed()
        // Error content also present
        composeTestRule.onNodeWithText("Pairing failed").assertIsDisplayed()
    }

    @Test
    fun pairingScreen_topBarAndFeedbackPersistInSuccessState() = runTest {
        val fakeConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = fakeConnection

        viewModel.requestCompanionPairing(testCamera)
        advanceUntilIdle()
        val intent = mockk<android.content.Intent>(relaxed = true)
        viewModel.onCompanionAssociationResult(intent)
        advanceUntilIdle()

        composeTestRule.setContent {
            PairingScreen(viewModel = viewModel, onNavigateBack = {}, onDevicePaired = {})
        }

        composeTestRule.onNodeWithText("Add Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Having issues? Let us know").assertIsDisplayed()
        composeTestRule.onNodeWithText("Successfully paired with Test Camera!").assertIsDisplayed()
    }
}
