package dev.sebastiano.camerasync.util

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.provider.Settings

/** Provides the device name to be used when identifying this device to cameras. */
interface DeviceNameProvider {
    /** Returns the name of the device. */
    fun getDeviceName(): String
}

/**
 * Android implementation of [DeviceNameProvider] that follows a specific priority for resolving the
 * device name.
 */
class AndroidDeviceNameProvider(private val context: Context) : DeviceNameProvider {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun getDeviceName(): String {
        val contentResolver = context.contentResolver

        // 1. BluetoothAdapter.getName()
        try {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val bluetoothName = bluetoothAdapter?.name
            if (!bluetoothName.isNullOrBlank()) {
                return bluetoothName
            }
        } catch (e: SecurityException) {
            // Ignore if permission is not granted
        }

        // 2. Settings.Global.getString(contentResolver, "device_name")
        try {
            val globalName = Settings.Global.getString(contentResolver, "device_name")
            if (!globalName.isNullOrBlank()) {
                return globalName
            }
        } catch (e: Exception) {
            // Ignore settings access errors
        }

        // 3. Settings.System.getString(contentResolver, "bluetooth_name")
        try {
            val systemBluetoothName = Settings.System.getString(contentResolver, "bluetooth_name")
            if (!systemBluetoothName.isNullOrBlank()) {
                return systemBluetoothName
            }
        } catch (e: Exception) {
            // Ignore settings access errors
        }

        // 4. android.os.Build.MODEL
        // Fallback to "Android Device" if MODEL is somehow null/blank (mostly for tests)
        return if (!Build.MODEL.isNullOrBlank()) Build.MODEL else "Android Device"
    }
}
