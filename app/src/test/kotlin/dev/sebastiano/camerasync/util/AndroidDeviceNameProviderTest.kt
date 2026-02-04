package dev.sebastiano.camerasync.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AndroidDeviceNameProviderTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val bluetoothManager = mockk<BluetoothManager>()
    private val bluetoothAdapter = mockk<BluetoothAdapter>()
    private lateinit var provider: AndroidDeviceNameProvider

    @Before
    fun setup() {
        provider = AndroidDeviceNameProvider(context)
        every { context.contentResolver } returns contentResolver
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter

        mockkStatic(Settings.Global::class)
        mockkStatic(Settings.System::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should return bluetooth name if available`() {
        every { bluetoothAdapter.name } returns "Bluetooth Name"

        val result = provider.getDeviceName()

        assertEquals("Bluetooth Name", result)
    }

    @Test
    fun `should return global device name if bluetooth name is blank`() {
        every { bluetoothAdapter.name } returns ""
        every { Settings.Global.getString(any(), eq("device_name")) } returns "Global Name"

        val result = provider.getDeviceName()

        assertEquals("Global Name", result)
    }

    @Test
    fun `should return system bluetooth name if bluetooth and global names are blank`() {
        every { bluetoothAdapter.name } returns null
        every { Settings.Global.getString(any(), any()) } returns null
        every { Settings.System.getString(any(), eq("bluetooth_name")) } returns "System Name"

        val result = provider.getDeviceName()

        assertEquals("System Name", result)
    }

    private fun getExpectedFallbackName(): String =
        if (!Build.MODEL.isNullOrBlank()) Build.MODEL else "Android Device"

    @Test
    fun `should return build model as fallback`() {
        every { bluetoothAdapter.name } returns null
        every { Settings.Global.getString(any(), any()) } returns null
        every { Settings.System.getString(any(), any()) } returns null

        val result = provider.getDeviceName()

        assertEquals(getExpectedFallbackName(), result)
    }

    @Test
    fun `should return build model if security exception occurs accessing bluetooth`() {
        every { bluetoothAdapter.name } throws SecurityException("No permission")
        every { Settings.Global.getString(any(), any()) } returns null
        every { Settings.System.getString(any(), any()) } returns null

        val result = provider.getDeviceName()

        assertEquals(getExpectedFallbackName(), result)
    }
}
