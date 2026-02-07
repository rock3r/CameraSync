package dev.sebastiano.camerasync.ui.remote

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.camerasync.ui.theme.CameraSyncTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteShootingActionBannerTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun actionBanner_showsRetryResetAndDismiss() {
        val errorState =
            RemoteShootingActionState.Error(
                action = RemoteShootingAction.TriggerCapture,
                message = "Action failed. Please try again.",
                canRetry = true,
                canReset = true,
            )

        composeTestRule.setContent {
            CameraSyncTheme {
                RemoteShootingActionBanner(
                    actionState = errorState,
                    onRetryAction = {},
                    onResetRemoteShooting = {},
                    onDismissActionError = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Action failed. Please try again.").assertExists()
        composeTestRule.onNodeWithText("Retry").assertExists()
        composeTestRule.onNodeWithText("Reset remote shooting").assertExists()
        composeTestRule.onNodeWithText("OK").assertExists()
    }
}
