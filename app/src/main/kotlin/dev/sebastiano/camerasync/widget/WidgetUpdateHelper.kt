package dev.sebastiano.camerasync.widget

import android.content.Context
import com.juul.khronicle.Log
import androidx.glance.appwidget.updateAll

/**
 * Interface for triggering updates for the sync widget.
 */
interface WidgetUpdateHelper {
    /**
     * Updates all instances of [SyncWidget].
     */
    suspend fun updateWidgets()
}

/**
 * Production implementation of [WidgetUpdateHelper] that actually updates Glance widgets.
 */
class GlanceWidgetUpdateHelper(private val context: Context) : WidgetUpdateHelper {

    override suspend fun updateWidgets() {
        Log.debug(tag = TAG) { "Updating all sync widgets" }
        try {
            SyncWidget().updateAll(context)
        } catch (e: Exception) {
            Log.warn(tag = TAG, throwable = e) { "Failed to update widgets" }
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateHelper"
    }
}
