package dev.sebastiano.camerasync.ble

import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService

private const val TAG = "ScanReceiver"

class ScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)

        if (errorCode != -1) {
            Log.error(tag = TAG) { "Scan failed with error code: $errorCode" }
            return
        }

        if (callbackType != -1) {
            Log.info(tag = TAG) {
                "Device found via PendingIntent scan (callback type: $callbackType)"
            }

            // Start the service to handle the connection
            // We need to use startForegroundService because we might be in the background
            val serviceIntent = MultiDeviceSyncService.createDeviceFoundIntent(context)
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.error(tag = TAG, throwable = e) { "Failed to start service from ScanReceiver" }
            }
        }
    }
}
