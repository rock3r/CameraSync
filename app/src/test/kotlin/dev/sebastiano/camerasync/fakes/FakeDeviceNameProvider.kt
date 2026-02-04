package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.util.DeviceNameProvider

class FakeDeviceNameProvider(var name: String = "Test Device") : DeviceNameProvider {
    override fun getDeviceName(): String = name
}
