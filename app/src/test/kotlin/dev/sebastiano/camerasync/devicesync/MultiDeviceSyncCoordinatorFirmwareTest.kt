package dev.sebastiano.camerasync.devicesync

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
import dev.sebastiano.camerasync.domain.vendor.DefaultConnectionDelegate
import dev.sebastiano.camerasync.domain.vendor.RemoteControlCapabilities
import dev.sebastiano.camerasync.domain.vendor.RemoteControlDelegate
import dev.sebastiano.camerasync.domain.vendor.SyncCapabilities
import dev.sebastiano.camerasync.domain.vendor.VendorConnectionDelegate
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeCameraVendor
import dev.sebastiano.camerasync.fakes.FakeDeviceNameProvider
import dev.sebastiano.camerasync.fakes.FakeGattSpec
import dev.sebastiano.camerasync.fakes.FakeIntentFactory
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeLocationCollector
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.fakes.FakePendingIntentFactory
import dev.sebastiano.camerasync.fakes.FakeProtocol
import dev.sebastiano.camerasync.fakes.FakeRemoteControlDelegate
import dev.sebastiano.camerasync.fakes.FakeVendorRegistry
import dev.sebastiano.camerasync.firmware.FirmwareUpdateScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class MultiDeviceSyncCoordinatorFirmwareTest {

    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var locationCollector: FakeLocationCollector
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var pendingIntentFactory: FakePendingIntentFactory
    private lateinit var intentFactory: FakeIntentFactory
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var testScope: TestScope
    private lateinit var connectionManager: DeviceConnectionManager
    private lateinit var firmwareManager: DeviceFirmwareManager
    private lateinit var coordinator: MultiDeviceSyncCoordinator

    private val testDevice1 =
        PairedDevice(
            macAddress = "00:11:22:33:44:55",
            name = "Test Camera 1",
            vendorId = "fake",
            isEnabled = true,
            lastSyncedAt = 1L,
        )

    private fun PairedDevice.toTestCamera() =
        Camera(
            identifier = macAddress,
            name = name,
            macAddress = macAddress,
            vendor = FakeCameraVendor,
        )

    @Before
    fun setUp() {
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        cameraRepository = FakeCameraRepository()
        locationCollector = FakeLocationCollector()
        vendorRegistry = FakeVendorRegistry()
        pairedDevicesRepository = FakePairedDevicesRepository()
        pendingIntentFactory = FakePendingIntentFactory()
        intentFactory = FakeIntentFactory()
        notificationManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.packageName } returns "dev.sebastiano.camerasync"
        every { context.applicationContext } returns context
        mockkStatic(NotificationManagerCompat::class)
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        every { NotificationManagerCompat.from(context) } returns notificationManagerCompat

        testScope = TestScope(UnconfinedTestDispatcher())

        connectionManager = DeviceConnectionManager()

        val notificationBuilder =
            object : NotificationBuilder {
                override fun build(
                    channelId: String,
                    title: String,
                    content: String,
                    icon: Int,
                    isOngoing: Boolean,
                    priority: Int,
                    category: String?,
                    isSilent: Boolean,
                    actions: List<NotificationAction>,
                    contentIntent: PendingIntent?,
                ): Notification = mockk(relaxed = true)
            }
        firmwareManager =
            DeviceFirmwareManager(
                context = context,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                intentFactory = intentFactory,
                notificationBuilder = notificationBuilder,
            )

        coordinator =
            MultiDeviceSyncCoordinator(
                context = context,
                cameraRepository = cameraRepository,
                vendorRegistry = vendorRegistry,
                pairedDevicesRepository = pairedDevicesRepository,
                pendingIntentFactory = pendingIntentFactory,
                connectionManager = connectionManager,
                firmwareManager = firmwareManager,
                deviceNameProvider = FakeDeviceNameProvider(),
                locationCollector = locationCollector,
                coroutineScope = testScope.backgroundScope,
            )
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
    }

    @Test
    fun `firmware update check runs when device connects with firmware version`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.firmwareVersion = "2.01"
            cameraRepository.connectionToReturn = connection

            val deviceWithUpdate =
                testDevice1.copy(
                    firmwareVersion = "2.01",
                    latestFirmwareVersion = "2.02",
                    firmwareUpdateNotificationShown = false,
                )
            pairedDevicesRepository.addTestDevice(deviceWithUpdate)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            assertTrue(connection.readFirmwareVersionCalled)
        }

    @Test
    fun `firmware update notification not shown when already notified`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.firmwareVersion = "2.01"
            cameraRepository.connectionToReturn = connection

            val deviceAlreadyNotified =
                testDevice1.copy(
                    firmwareVersion = "2.01",
                    latestFirmwareVersion = "2.02",
                    firmwareUpdateNotificationShown = true,
                )
            pairedDevicesRepository.addTestDevice(deviceAlreadyNotified)

            val notificationManagerCompat = NotificationManagerCompat.from(context)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            verify(exactly = 0) { notificationManagerCompat.notify(any(), any<Notification>()) }
        }

    @Test
    fun `firmware update notification not shown when no update available`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.firmwareVersion = "2.01"
            cameraRepository.connectionToReturn = connection

            val deviceNoUpdate =
                testDevice1.copy(firmwareVersion = "2.01", latestFirmwareVersion = null)
            pairedDevicesRepository.addTestDevice(deviceNoUpdate)

            val notificationManagerCompat = NotificationManagerCompat.from(context)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            verify(exactly = 0) { notificationManagerCompat.notify(any(), any<Notification>()) }
        }

    @Test
    fun `firmware update notification not shown when firmware version is null`() =
        testScope.runTest {
            val noFirmwareVendor =
                object : CameraVendor {
                    override val vendorId: String = "no-fw"
                    override val vendorName: String = "No FW"
                    override val gattSpec = FakeGattSpec
                    override val protocol = FakeProtocol

                    override fun recognizesDevice(
                        deviceName: String?,
                        serviceUuids: List<kotlin.uuid.Uuid>,
                        manufacturerData: Map<Int, ByteArray>,
                    ): Boolean = false

                    override fun createConnectionDelegate(): VendorConnectionDelegate =
                        DefaultConnectionDelegate()

                    override fun getRemoteControlCapabilities() = RemoteControlCapabilities()

                    override fun getSyncCapabilities() =
                        SyncCapabilities(
                            supportsFirmwareVersion = false,
                            supportsDeviceName = true,
                            supportsDateTimeSync = true,
                            supportsGeoTagging = true,
                            supportsLocationSync = true,
                        )

                    override fun extractModelFromPairingName(pairingName: String?) =
                        pairingName ?: "No FW"

                    override fun createRemoteControlDelegate(
                        peripheral: com.juul.kable.Peripheral,
                        camera: Camera,
                    ): RemoteControlDelegate = FakeRemoteControlDelegate()
                }

            vendorRegistry.addVendor(noFirmwareVendor)

            val noFwDevice = testDevice1.copy(vendorId = "no-fw", macAddress = "CC:DD:EE:FF:00:11")

            val connection =
                FakeCameraConnection(
                    Camera(
                        identifier = noFwDevice.macAddress,
                        name = noFwDevice.name,
                        macAddress = noFwDevice.macAddress,
                        vendor = noFirmwareVendor,
                    )
                )
            cameraRepository.connectionToReturn = connection

            pairedDevicesRepository.addTestDevice(noFwDevice)

            val notificationManagerCompat = NotificationManagerCompat.from(context)

            coordinator.startDeviceSync(noFwDevice)
            advanceUntilIdle()

            verify(exactly = 0) { notificationManagerCompat.notify(any(), any<Notification>()) }
        }

    @Test
    fun `firmware update notification flag cleared when new update found`() =
        testScope.runTest {
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            connection.firmwareVersion = "2.01"
            cameraRepository.connectionToReturn = connection

            val deviceWithOldUpdate =
                testDevice1.copy(
                    firmwareVersion = "2.01",
                    latestFirmwareVersion = "2.02",
                    firmwareUpdateNotificationShown = true,
                )
            pairedDevicesRepository.addTestDevice(deviceWithOldUpdate)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            var updatedDevice = pairedDevicesRepository.getDevice(testDevice1.macAddress)
            assertTrue(
                "Notification flag should remain true when already shown",
                updatedDevice?.firmwareUpdateNotificationShown == true,
            )

            pairedDevicesRepository.setFirmwareUpdateInfo(
                testDevice1.macAddress,
                "2.03",
                System.currentTimeMillis(),
            )
            advanceUntilIdle()

            updatedDevice = pairedDevicesRepository.getDevice(testDevice1.macAddress)
            assertTrue(
                "Notification flag should be cleared when new update is found",
                updatedDevice?.firmwareUpdateNotificationShown == false,
            )
            assertEquals("2.03", updatedDevice?.latestFirmwareVersion)
        }

    @Test
    fun `initial setup respects capabilities`() =
        testScope.runTest {
            val limitedVendor =
                object : CameraVendor {
                    override val vendorId: String = "limited"
                    override val vendorName: String = "Limited"
                    override val gattSpec = FakeGattSpec
                    override val protocol = FakeProtocol

                    override fun recognizesDevice(
                        deviceName: String?,
                        serviceUuids: List<kotlin.uuid.Uuid>,
                        manufacturerData: Map<Int, ByteArray>,
                    ): Boolean = false

                    override fun createConnectionDelegate(): VendorConnectionDelegate =
                        DefaultConnectionDelegate()

                    override fun getRemoteControlCapabilities() = RemoteControlCapabilities()

                    override fun getSyncCapabilities() =
                        SyncCapabilities(
                            supportsFirmwareVersion = false,
                            supportsDeviceName = false,
                            supportsDateTimeSync = false,
                            supportsGeoTagging = false,
                            supportsLocationSync = false,
                        )

                    override fun extractModelFromPairingName(pairingName: String?) =
                        pairingName ?: "Limited"

                    override fun createRemoteControlDelegate(
                        peripheral: com.juul.kable.Peripheral,
                        camera: Camera,
                    ): RemoteControlDelegate = FakeRemoteControlDelegate()
                }

            vendorRegistry.addVendor(limitedVendor)

            val limitedDevice =
                testDevice1.copy(vendorId = "limited", macAddress = "FF:FF:FF:FF:FF:FF")
            val connection =
                FakeCameraConnection(
                    Camera(
                        identifier = limitedDevice.macAddress,
                        name = limitedDevice.name,
                        macAddress = limitedDevice.macAddress,
                        vendor = limitedVendor,
                    )
                )
            cameraRepository.connectionToReturn = connection

            coordinator.startDeviceSync(limitedDevice)
            advanceUntilIdle()

            assertFalse(
                "Should not set device name if not supported",
                connection.pairedDeviceName != null,
            )
            assertFalse(
                "Should not sync date time if not supported",
                connection.syncedDateTime != null,
            )
            assertFalse("Should not set geo tagging if not supported", connection.geoTaggingEnabled)
            assertFalse(
                "Should not read firmware version if not supported",
                connection.readFirmwareVersionCalled,
            )
        }

    @Test
    fun `firmware update check is triggered on connect when last check was long ago`() =
        testScope.runTest {
            mockkObject(FirmwareUpdateScheduler)
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            val oldCheckTime = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 hours ago
            val deviceWithOldCheck = testDevice1.copy(lastFirmwareCheckedAt = oldCheckTime)
            pairedDevicesRepository.addTestDevice(deviceWithOldCheck)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            verify { FirmwareUpdateScheduler.triggerOneTimeCheck(any()) }
            unmockkObject(FirmwareUpdateScheduler)
        }

    @Test
    fun `firmware update check is triggered on connect when last check is missing`() =
        testScope.runTest {
            mockkObject(FirmwareUpdateScheduler)
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            val deviceWithNoCheck = testDevice1.copy(lastFirmwareCheckedAt = null)
            pairedDevicesRepository.addTestDevice(deviceWithNoCheck)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            verify { FirmwareUpdateScheduler.triggerOneTimeCheck(any()) }
            unmockkObject(FirmwareUpdateScheduler)
        }

    @Test
    fun `firmware update check is NOT triggered on connect when last check was recent`() =
        testScope.runTest {
            mockkObject(FirmwareUpdateScheduler)
            val connection = FakeCameraConnection(testDevice1.toTestCamera())
            cameraRepository.connectionToReturn = connection

            val recentCheckTime = System.currentTimeMillis() - (1 * 60 * 60 * 1000L) // 1 hour ago
            val deviceWithRecentCheck = testDevice1.copy(lastFirmwareCheckedAt = recentCheckTime)
            pairedDevicesRepository.addTestDevice(deviceWithRecentCheck)

            coordinator.startDeviceSync(testDevice1)
            advanceUntilIdle()

            verify(exactly = 0) { FirmwareUpdateScheduler.triggerOneTimeCheck(any()) }
            unmockkObject(FirmwareUpdateScheduler)
        }
}
