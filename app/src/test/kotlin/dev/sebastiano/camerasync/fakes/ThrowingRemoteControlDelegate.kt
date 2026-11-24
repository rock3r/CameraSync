package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate

/**
 * A [RemoteControlDelegate] that throws from [triggerCapture] and [disconnectWifi] to simulate BLE
 * failures (e.g. camera disconnected). Used for regression tests that verify the ViewModel catches
 * these exceptions and does not crash.
 */
class ThrowingRemoteControlDelegate(
    private val triggerCaptureError: Throwable =
        java.io.IOException("BLE write failed (disconnected)"),
    private val disconnectWifiError: Throwable =
        java.io.IOException("BLE write failed (disconnected)"),
) : RemoteControlDelegate by FakeRemoteControlDelegate() {

    override suspend fun triggerCapture() {
        throw triggerCaptureError
    }

    override suspend fun disconnectWifi() {
        throw disconnectWifiError
    }
}
