package dev.sebastiano.camerasync.devicesync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Factory for creating PendingIntents, allowing testability by providing a fake implementation. */
interface PendingIntentFactory {
    fun createServicePendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent

    fun createActivityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent
}
