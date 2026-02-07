package dev.sebastiano.camerasync.fakes

import android.content.Context
import android.content.Intent
import dev.sebastiano.camerasync.devicesync.IntentFactory
import io.mockk.mockk

/** Fake implementation of [IntentFactory] for testing. */
class FakeIntentFactory : IntentFactory {
    var lastRefreshIntent: Intent? = null
        private set

    var lastRefreshDeviceIntent: Intent? = null
        private set

    var lastRefreshDeviceMacAddress: String? = null
        private set

    var lastStopIntent: Intent? = null
        private set

    var lastStartIntent: Intent? = null
        private set

    var lastMainActivityIntent: Intent? = null
        private set

    /** Reset the factory state between tests. */
    fun reset() {
        lastRefreshIntent = null
        lastRefreshDeviceIntent = null
        lastRefreshDeviceMacAddress = null
        lastStopIntent = null
        lastStartIntent = null
        lastMainActivityIntent = null
    }

    override fun createRefreshIntent(context: Context): Intent {
        // Context is passed but not used in fake - just track the call
        val intent = mockk<Intent>(relaxed = true)
        lastRefreshIntent = intent
        return intent
    }

    override fun createRefreshDeviceIntent(context: Context, macAddress: String): Intent {
        // Context is passed but not used in fake - just track the call
        val intent = mockk<Intent>(relaxed = true)
        lastRefreshDeviceIntent = intent
        lastRefreshDeviceMacAddress = macAddress
        return intent
    }

    override fun createStopIntent(context: Context): Intent {
        // Context is passed but not used in fake - just track the call
        val intent = mockk<Intent>(relaxed = true)
        lastStopIntent = intent
        return intent
    }

    override fun createStartIntent(context: Context): Intent {
        // Context is passed but not used in fake - just track the call
        val intent = mockk<Intent>(relaxed = true)
        lastStartIntent = intent
        return intent
    }

    override fun createMainActivityIntent(context: Context): Intent {
        // Context is passed but not used in fake - just track the call
        val intent = mockk<Intent>(relaxed = true)
        lastMainActivityIntent = intent
        return intent
    }
}
