package dev.sebastiano.camerasync.devicesync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Android implementation of [PendingIntentFactory] using PendingIntent.getService. */
class AndroidPendingIntentFactory : PendingIntentFactory {
    override fun createServicePendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent = PendingIntent.getService(context, requestCode, intent, flags)

    override fun createActivityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int,
    ): PendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags)
}
