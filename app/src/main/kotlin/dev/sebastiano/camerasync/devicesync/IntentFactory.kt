package dev.sebastiano.camerasync.devicesync

import android.content.Context
import android.content.Intent

/** Factory for creating Intents, allowing testability by providing a fake implementation. */
interface IntentFactory {
    /** Creates an [Intent] to trigger a connection refresh. */
    fun createRefreshIntent(context: Context): Intent

    /** Creates an [Intent] to stop all synchronizations. */
    fun createStopIntent(context: Context): Intent

    /** Creates an [Intent] to start the synchronization service. */
    fun createStartIntent(context: Context): Intent

    /** Creates an [Intent] to launch the main activity. */
    fun createMainActivityIntent(context: Context): Intent
}
