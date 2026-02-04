package dev.sebastiano.camerasync.devicesync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Factory for creating PendingIntents, allowing testability by providing a fake implementation. */
interface PendingIntentFactory {
    /**
     * Creates a [PendingIntent] that starts a foreground service.
     *
     * @param context The context.
     * @param requestCode The request code for the [PendingIntent].
     * @param intent The [Intent] to be triggered.
     * @param flags The [PendingIntent] flags.
     * @return The created [PendingIntent].
     */
    fun createServicePendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent

    /**
     * Creates a [PendingIntent] that starts an activity.
     *
     * @param context The context.
     * @param requestCode The request code for the [PendingIntent].
     * @param intent The [Intent] to be triggered.
     * @param flags The [PendingIntent] flags.
     * @return The created [PendingIntent].
     */
    fun createActivityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent

    /**
     * Creates a [PendingIntent] that sends a broadcast.
     *
     * @param context The context.
     * @param requestCode The request code for the [PendingIntent].
     * @param intent The [Intent] to be triggered.
     * @param flags The [PendingIntent] flags.
     * @return The created [PendingIntent].
     */
    fun createBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent
}
