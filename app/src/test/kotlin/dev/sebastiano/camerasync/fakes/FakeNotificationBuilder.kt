package dev.sebastiano.camerasync.fakes

import android.app.Notification
import androidx.core.app.NotificationCompat
import dev.sebastiano.camerasync.devicesync.NotificationAction
import dev.sebastiano.camerasync.devicesync.NotificationBuilder
import io.mockk.every
import io.mockk.mockk

/**
 * Fake implementation of [NotificationBuilder] for testing. Stores the notification data for
 * verification.
 */
class FakeNotificationBuilder : NotificationBuilder {
    data class BuildCall(
        val channelId: String,
        val title: String,
        val content: String,
        val icon: Int,
        val isOngoing: Boolean,
        val priority: Int,
        val category: String?,
        val isSilent: Boolean,
        val actions: List<NotificationAction>,
        val contentIntent: android.app.PendingIntent?,
    )

    var lastBuildCall: BuildCall? = null
        private set

    /** Reset the builder state between tests. */
    fun reset() {
        lastBuildCall = null
    }

    override fun build(
        channelId: String,
        title: String,
        content: String,
        icon: Int,
        isOngoing: Boolean,
        priority: Int,
        category: String?,
        isSilent: Boolean,
        actions: List<NotificationAction>,
        contentIntent: android.app.PendingIntent?,
    ): Notification {
        lastBuildCall =
            BuildCall(
                channelId = channelId,
                title = title,
                content = content,
                icon = icon,
                isOngoing = isOngoing,
                priority = priority,
                category = category,
                isSilent = isSilent,
                actions = actions,
                contentIntent = contentIntent,
            )

        // Create notification with proper extras for test helpers
        // Use MockK to mock the Bundle to avoid "not mocked" errors
        val bundle = mockk<android.os.Bundle>(relaxed = true)
        val notification = Notification()
        notification.extras = bundle

        // Store data in the bundle using relaxed mocking
        // The relaxed mock will allow any method calls without throwing exceptions
        bundle.putCharSequence(NotificationCompat.EXTRA_TITLE, title)
        bundle.putCharSequence(NotificationCompat.EXTRA_TEXT, content)

        // Store action information in extras for test verification
        // We don't create real Notification.Action objects since we verify setup
        // through the BuildCall and PendingIntentFactory calls
        actions.forEachIndexed { index, action ->
            bundle.putCharSequence("action_${index}_title", action.title)
        }
        bundle.putInt("action_count", actions.size)

        // Also set up the bundle to return values when queried (for test helpers)
        every { bundle.getCharSequence(NotificationCompat.EXTRA_TITLE) } returns title
        every { bundle.getCharSequence(NotificationCompat.EXTRA_TEXT) } returns content
        actions.forEachIndexed { index, action ->
            every { bundle.getCharSequence("action_${index}_title") } returns action.title
        }
        every { bundle.getInt("action_count", 0) } returns actions.size

        return notification
    }
}
