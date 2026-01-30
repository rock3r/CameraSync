package dev.sebastiano.camerasync.fakes

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.sebastiano.camerasync.devicesync.PendingIntentFactory
import io.mockk.mockk

/**
 * Fake implementation of [PendingIntentFactory] for testing.
 *
 * Tracks all PendingIntent creation calls so tests can verify they were set up correctly. Returns a
 * mock PendingIntent since we can't create real ones without a real Android Context.
 */
class FakePendingIntentFactory : PendingIntentFactory {
    data class PendingIntentCall(val requestCode: Int, val intent: Intent, val flags: Int)

    private val _calls = mutableListOf<PendingIntentCall>()
    val calls: List<PendingIntentCall>
        get() = _calls.toList()

    var lastRequestCode: Int? = null
        private set

    var lastIntent: Intent? = null
        private set

    /** Reset the factory state between tests. */
    fun reset() {
        _calls.clear()
        lastRequestCode = null
        lastIntent = null
    }

    override fun createServicePendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent {
        // Context is passed but not used in fake - just track the call
        // We don't try to create real PendingIntents which would require a real Android Context
        val call = PendingIntentCall(requestCode, intent, flags)
        _calls.add(call)
        lastRequestCode = requestCode
        lastIntent = intent
        // Return a mock PendingIntent - we verify setup by checking the calls, not the
        // PendingIntent itself
        return mockk<PendingIntent>(relaxed = true)
    }

    override fun createActivityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent {
        // Context is passed but not used in fake - just track the call
        val call = PendingIntentCall(requestCode, intent, flags)
        _calls.add(call)
        lastRequestCode = requestCode
        lastIntent = intent
        return mockk<PendingIntent>(relaxed = true)
    }

    override fun createBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent {
        val call = PendingIntentCall(requestCode, intent, flags)
        _calls.add(call)
        lastRequestCode = requestCode
        lastIntent = intent
        return mockk<PendingIntent>(relaxed = true)
    }
}
