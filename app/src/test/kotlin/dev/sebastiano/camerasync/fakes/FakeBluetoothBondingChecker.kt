package dev.sebastiano.camerasync.fakes

import android.bluetooth.BluetoothDevice
import dev.sebastiano.camerasync.pairing.BluetoothBondingChecker

/** Fake implementation of [BluetoothBondingChecker] for testing. */
class FakeBluetoothBondingChecker : BluetoothBondingChecker {
    private val bondedDevices = mutableSetOf<String>()

    /** Controls whether createBond will succeed. */
    var createBondSucceeds: Boolean = true

    /** Controls whether createBond will automatically mark the device as bonded. */
    var createBondAutoBonds: Boolean = true

    /** Sets whether a device is bonded. */
    fun setBonded(macAddress: String, bonded: Boolean) {
        if (bonded) {
            bondedDevices.add(macAddress.uppercase())
        } else {
            bondedDevices.remove(macAddress.uppercase())
        }
    }

    override fun isDeviceBonded(macAddress: String): Boolean {
        return bondedDevices.contains(macAddress.uppercase())
    }

    override fun getBondedDevice(macAddress: String): BluetoothDevice? {
        // Return null as we can't create a real BluetoothDevice in tests
        // This is fine since tests don't need the actual device object
        // The removeBond method will still work via isDeviceBonded check
        return null
    }

    override fun removeBond(macAddress: String): Boolean {
        val wasBonded = isDeviceBonded(macAddress)
        if (wasBonded) {
            bondedDevices.remove(macAddress.uppercase())
        }
        return wasBonded
    }

    override fun createBond(macAddress: String): Boolean {
        if (!createBondSucceeds) {
            return false
        }
        if (createBondAutoBonds) {
            bondedDevices.add(macAddress.uppercase())
        }
        return true
    }

    override fun getBondState(macAddress: String): Int? {
        return if (isDeviceBonded(macAddress)) {
            android.bluetooth.BluetoothDevice.BOND_BONDED
        } else {
            android.bluetooth.BluetoothDevice.BOND_NONE
        }
    }
}
