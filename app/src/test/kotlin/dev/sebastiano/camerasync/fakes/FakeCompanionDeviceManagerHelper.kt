package dev.sebastiano.camerasync.fakes

import android.companion.CompanionDeviceManager
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper

/** Fake implementation of [CompanionDeviceManagerHelper] for testing. */
class FakeCompanionDeviceManagerHelper : CompanionDeviceManagerHelper {
    var requestAssociationCalled = false
        private set

    var lastCallback: CompanionDeviceManager.Callback? = null
        private set

    var lastMacAddress: String? = null
        private set

    override fun requestAssociation(
        callback: CompanionDeviceManager.Callback,
        macAddress: String?,
    ) {
        requestAssociationCalled = true
        lastCallback = callback
        lastMacAddress = macAddress
    }

    /** Simulates a failure by invoking the stored callback's onFailure. */
    fun simulateFailure(error: CharSequence? = "Test failure") {
        lastCallback?.onFailure(error)
    }
}
