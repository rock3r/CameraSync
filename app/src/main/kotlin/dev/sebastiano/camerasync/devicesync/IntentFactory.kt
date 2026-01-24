package dev.sebastiano.camerasync.devicesync

import android.content.Context
import android.content.Intent

/** Factory for creating Intents, allowing testability by providing a fake implementation. */
interface IntentFactory {
    fun createRefreshIntent(context: Context): Intent

    fun createStopIntent(context: Context): Intent

    fun createStartIntent(context: Context): Intent

    fun createMainActivityIntent(context: Context): Intent
}
