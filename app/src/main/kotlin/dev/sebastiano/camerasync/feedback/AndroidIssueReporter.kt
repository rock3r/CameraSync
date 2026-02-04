package dev.sebastiano.camerasync.feedback

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.FileProvider
import dev.sebastiano.camerasync.domain.repository.CameraConnection
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of [IssueReporter] that collects system and Bluetooth information to help
 * diagnose issues.
 */
class AndroidIssueReporter(private val context: Context) : IssueReporter {

    /**
     * Builds and sends an issue report via email.
     *
     * The report includes system information, Bluetooth adapter state, and if a [connection] is
     * provided, detailed information about the camera connection (bonding state, firmware, etc.).
     * It also attaches a recent logcat dump.
     *
     * @param connection The active camera connection, if any, to include in the report.
     * @param extraInfo Optional additional information from the user.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun sendIssueReport(connection: CameraConnection?, extraInfo: String?) =
        withContext(Dispatchers.IO) {
            val report = buildString {
                appendLine("CameraSync Feedback Report")
                appendLine("========================")
                appendLine("Date: ${ZonedDateTime.now()}")
                appendLine()

                appendLine("System Info")
                appendLine("-----------")
                appendLine("Manufacturer: ${Build.MANUFACTURER}")
                appendLine("Model: ${Build.MODEL}")
                appendLine("Brand: ${Build.BRAND}")
                appendLine("Device: ${Build.DEVICE}")
                appendLine("Product: ${Build.PRODUCT}")
                appendLine(
                    "Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                )
                appendLine()

                appendLine("Bluetooth Info")
                appendLine("--------------")
                val btManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                if (adapter != null) {
                    appendLine("State: ${getBluetoothStateString(adapter.state)}")
                    appendLine("Enabled: ${adapter.isEnabled}")
                } else {
                    appendLine("Bluetooth Adapter not available")
                }
                appendLine()

                if (connection != null) {
                    appendLine("Camera Connection Info")
                    appendLine("---------------------")
                    appendLine("Name: ${connection.camera.name}")
                    appendLine("Mac: ${connection.camera.macAddress}")
                    appendLine("Vendor: ${connection.camera.vendor.vendorName}")

                    try {
                        val device = adapter?.getRemoteDevice(connection.camera.macAddress)
                        if (device != null) {
                            appendLine("Bond State: ${getBondStateString(device.bondState)}")
                            // We can't easily get the current MTU without tracking it or requesting
                            // it
                        }
                    } catch (e: IOException) {
                        appendLine("Error reading bonding info: $e")
                    } catch (e: SecurityException) {
                        appendLine("Error reading bonding info: $e")
                    }

                    try {
                        val fw = connection.readFirmwareVersion()
                        appendLine("Firmware Version: $fw")
                    } catch (e: IOException) {
                        appendLine("Firmware Version: Error ($e)")
                    } catch (e: UnsupportedOperationException) {
                        appendLine("Firmware Version: Error ($e)")
                    }

                    try {
                        val hw = connection.readHardwareRevision()
                        appendLine("Hardware Revision: $hw")
                    } catch (e: IOException) {
                        appendLine("Hardware Revision: Error ($e)")
                    } catch (e: UnsupportedOperationException) {
                        appendLine("Hardware Revision: Error ($e)")
                    }
                } else {
                    appendLine("No active camera connection context provided.")
                }

                if (extraInfo != null) {
                    appendLine()
                    appendLine("Additional Info")
                    appendLine("---------------")
                    appendLine(extraInfo)
                }
            }

            val logFile = collectLogcat()
            val uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("camerasync@sebastiano.dev"))
                    putExtra(Intent.EXTRA_SUBJECT, "CameraSync feedback")
                    putExtra(Intent.EXTRA_TEXT, report)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val chooser = Intent.createChooser(intent, "Send Feedback")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }

    /**
     * Dumps the current logcat to a temporary file in the cache directory.
     *
     * @return The [File] containing the logcat output.
     */
    private fun collectLogcat(): File {
        val logDir = File(context.cacheDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()

        val file = File(logDir, "camerasync_log.txt")
        if (file.exists()) file.delete()

        // Dump logcat to file
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-f", file.absolutePath))
        process.waitFor()
        return file
    }

    private fun getBluetoothStateString(state: Int): String =
        when (state) {
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
            else -> "UNKNOWN ($state)"
        }

    private fun getBondStateString(state: Int): String =
        when (state) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
            else -> "UNKNOWN ($state)"
        }
}
