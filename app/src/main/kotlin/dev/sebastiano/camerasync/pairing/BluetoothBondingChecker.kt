package dev.sebastiano.camerasync.pairing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.juul.khronicle.Log
import java.io.IOException

private const val TAG = "BluetoothBondingChecker"

/**
 * Interface for checking and managing Bluetooth bonding state at the Android system level.
 *
 * This is separate from the app's internal pairing state stored in PairedDevicesRepository.
 * System-level bonding can interfere with BLE connections if not properly handled.
 */
interface BluetoothBondingChecker {
    /**
     * Checks if a device with the given MAC address is already bonded at the system level.
     *
     * @param macAddress The MAC address of the device to check (format: "AA:BB:CC:DD:EE:FF")
     * @return true if the device is bonded, false otherwise
     */
    fun isDeviceBonded(macAddress: String): Boolean

    /**
     * Gets the bonded BluetoothDevice for the given MAC address, if it exists.
     *
     * @param macAddress The MAC address of the device
     * @return The BluetoothDevice if bonded, null otherwise
     */
    fun getBondedDevice(macAddress: String): BluetoothDevice?

    /**
     * Attempts to remove the bond for a device with the given MAC address.
     *
     * Note: This requires BLUETOOTH_CONNECT permission and may not work on all Android versions. On
     * some devices, users may need to manually remove the pairing from system settings.
     *
     * @param macAddress The MAC address of the device to unbond
     * @return true if the unbond operation was initiated successfully, false otherwise
     */
    fun removeBond(macAddress: String): Boolean

    /**
     * Initiates bonding (pairing) with a device at the OS level.
     *
     * This will trigger the system pairing dialog if the device is discoverable.
     *
     * @param macAddress The MAC address of the device to bond with
     * @return true if bonding was initiated successfully, false otherwise
     */
    fun createBond(macAddress: String): Boolean

    /**
     * Gets the current bond state of a device.
     *
     * @param macAddress The MAC address of the device
     * @return The bond state (BOND_NONE, BOND_BONDING, or BOND_BONDED), or null if device cannot be
     *   accessed
     */
    fun getBondState(macAddress: String): Int?
}

/**
 * Android implementation of [BluetoothBondingChecker].
 *
 * @property context The application context used to access Bluetooth services and check
 *   permissions.
 */
class AndroidBluetoothBondingChecker(private val context: Context) : BluetoothBondingChecker {

    /** The [BluetoothAdapter] used for bonding operations, if available on the device. */
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }

    override fun isDeviceBonded(macAddress: String): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.warn(tag = TAG) { "Bluetooth adapter not available" }
            return false
        }

        if (!adapter.isEnabled) {
            Log.debug(tag = TAG) { "Bluetooth not enabled" }
            return false
        }

        // Check for BLUETOOTH_CONNECT permission
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            Log.warn(tag = TAG) { "BLUETOOTH_CONNECT permission not granted" }
            return false
        }

        return try {
            val bondedDevices = adapter.bondedDevices
            val isBonded =
                bondedDevices.any { device ->
                    // Compare MAC addresses (normalize to uppercase for comparison)
                    device.address.equals(macAddress, ignoreCase = true)
                }

            if (isBonded) {
                Log.info(tag = TAG) { "Device $macAddress is already bonded at system level" }
            }

            isBonded
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) {
                "SecurityException accessing bonded devices (permission may have been revoked)"
            }
            false
        }
    }

    override fun getBondedDevice(macAddress: String): BluetoothDevice? {
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled) return null

        // Check for BLUETOOTH_CONNECT permission
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            Log.warn(tag = TAG) { "BLUETOOTH_CONNECT permission not granted" }
            return null
        }

        return try {
            adapter.bondedDevices.firstOrNull { device ->
                device.address.equals(macAddress, ignoreCase = true)
            }
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) {
                "SecurityException accessing bonded devices (permission may have been revoked)"
            }
            null
        }
    }

    override fun removeBond(macAddress: String): Boolean {
        val device = getBondedDevice(macAddress) ?: return false

        return try {
            // Use reflection to call removeBond() as it's a hidden API
            val method = BluetoothDevice::class.java.getMethod("removeBond")
            val result = method.invoke(device) as? Boolean ?: false

            if (result) {
                Log.info(tag = TAG) { "Successfully initiated unbond for $macAddress" }
            } else {
                Log.warn(tag = TAG) { "Failed to unbond $macAddress" }
            }

            result
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Error removing bond for $macAddress" }
            false
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) { "Error removing bond for $macAddress" }
            false
        }
    }

    override fun createBond(macAddress: String): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.warn(tag = TAG) { "Bluetooth adapter not available" }
            return false
        }

        if (!adapter.isEnabled) {
            Log.debug(tag = TAG) { "Bluetooth not enabled" }
            return false
        }

        // Check for BLUETOOTH_CONNECT permission
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            Log.warn(tag = TAG) { "BLUETOOTH_CONNECT permission not granted" }
            return false
        }

        return try {
            // Get remote device by MAC address
            val device = adapter.getRemoteDevice(macAddress)
            if (device == null) {
                Log.warn(tag = TAG) { "Could not get remote device for $macAddress" }
                return false
            }

            // Check if already bonded
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                Log.info(tag = TAG) { "Device $macAddress is already bonded" }
                return true
            }

            // Initiate bonding
            val result = device.createBond()
            if (result) {
                Log.info(tag = TAG) { "Successfully initiated bonding for $macAddress" }
            } else {
                Log.warn(tag = TAG) { "Failed to initiate bonding for $macAddress" }
            }

            result
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) {
                "SecurityException creating bond (permission may have been revoked)"
            }
            false
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Error creating bond for $macAddress" }
            false
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) { "Error creating bond for $macAddress" }
            false
        }
    }

    override fun getBondState(macAddress: String): Int? {
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled) return null

        // Check for BLUETOOTH_CONNECT permission
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            Log.warn(tag = TAG) { "BLUETOOTH_CONNECT permission not granted" }
            return null
        }

        return try {
            val device = adapter.getRemoteDevice(macAddress)
            device.bondState
        } catch (e: SecurityException) {
            Log.error(tag = TAG, throwable = e) {
                "SecurityException getting bond state (permission may have been revoked)"
            }
            null
        } catch (e: IOException) {
            Log.error(tag = TAG, throwable = e) { "Error getting bond state for $macAddress" }
            null
        } catch (e: IllegalArgumentException) {
            Log.error(tag = TAG, throwable = e) { "Error getting bond state for $macAddress" }
            null
        }
    }
}
