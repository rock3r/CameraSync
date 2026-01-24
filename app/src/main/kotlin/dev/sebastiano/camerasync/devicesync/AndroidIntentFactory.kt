package dev.sebastiano.camerasync.devicesync

import android.content.Context
import android.content.Intent
import dev.sebastiano.camerasync.MainActivity

/** Android implementation of [IntentFactory] for MultiDeviceSyncService. */
class AndroidIntentFactory(private val serviceClass: Class<*>) : IntentFactory {
    override fun createRefreshIntent(context: Context): Intent =
        Intent(context, serviceClass).apply { action = MultiDeviceSyncService.ACTION_REFRESH }

    override fun createStopIntent(context: Context): Intent =
        Intent(context, serviceClass).apply { action = MultiDeviceSyncService.ACTION_STOP }

    override fun createStartIntent(context: Context): Intent = Intent(context, serviceClass)

    override fun createMainActivityIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
