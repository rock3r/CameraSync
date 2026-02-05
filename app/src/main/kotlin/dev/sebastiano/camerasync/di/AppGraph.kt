package dev.sebastiano.camerasync.di

import android.app.Application
import android.content.Context
import dev.sebastiano.camerasync.MainActivity
import dev.sebastiano.camerasync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.camerasync.data.repository.FusedLocationRepository
import dev.sebastiano.camerasync.data.repository.InMemorySyncStatusRepository
import dev.sebastiano.camerasync.data.repository.KableCameraRepository
import dev.sebastiano.camerasync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.camerasync.devices.DevicesListViewModel
import dev.sebastiano.camerasync.devicesync.AndroidIntentFactory
import dev.sebastiano.camerasync.devicesync.AndroidNotificationBuilder
import dev.sebastiano.camerasync.devicesync.AndroidPendingIntentFactory
import dev.sebastiano.camerasync.devicesync.DefaultLocationCollector
import dev.sebastiano.camerasync.devicesync.IntentFactory
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import dev.sebastiano.camerasync.devicesync.NotificationBuilder
import dev.sebastiano.camerasync.devicesync.PendingIntentFactory
import dev.sebastiano.camerasync.devicesync.SyncTileService
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.repository.SyncStatusRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.camerasync.feedback.AndroidIssueReporter
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.firmware.FirmwareUpdateChecker
import dev.sebastiano.camerasync.firmware.ricoh.RicohFirmwareUpdateChecker
import dev.sebastiano.camerasync.firmware.sony.SonyFirmwareUpdateChecker
import dev.sebastiano.camerasync.logging.LogRepository
import dev.sebastiano.camerasync.logging.LogViewerViewModel
import dev.sebastiano.camerasync.logging.LogcatLogRepository
import dev.sebastiano.camerasync.pairing.AndroidBluetoothBondingChecker
import dev.sebastiano.camerasync.pairing.BluetoothBondingChecker
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import dev.sebastiano.camerasync.pairing.PairingViewModel
import dev.sebastiano.camerasync.util.AndroidBatteryOptimizationChecker
import dev.sebastiano.camerasync.util.AndroidDeviceNameProvider
import dev.sebastiano.camerasync.util.BatteryOptimizationChecker
import dev.sebastiano.camerasync.util.DeviceNameProvider
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.sebastiano.camerasync.widget.GlanceWidgetUpdateHelper
import dev.sebastiano.camerasync.widget.WidgetUpdateHelper
import dev.sebastiano.camerasync.widget.WidgetUpdateManager
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Metro dependency graph for production dependencies.
 *
 * The graph is created via the Factory interface using createGraphFactory, which allows passing
 * external dependencies like Application. Context is then provided from Application and injected
 * into @Provides methods that need it.
 */
@DependencyGraph
@SingleIn(AppGraph::class)
interface AppGraph {
    fun mainActivity(): MainActivity

    fun multiDeviceSyncService(): MultiDeviceSyncService

    fun provideFirmwareUpdateCheckers(): List<FirmwareUpdateChecker>

    fun syncTileService(): SyncTileService

    fun devicesListViewModel(): DevicesListViewModel

    fun pairingViewModel(): PairingViewModel

    fun logViewerViewModel(): LogViewerViewModel

    fun pairedDevicesRepository(): PairedDevicesRepository

    fun syncStatusRepository(): SyncStatusRepository

    fun widgetUpdateManager(): WidgetUpdateManager

    fun widgetUpdateHelper(): WidgetUpdateHelper

    fun viewModelFactory(): MetroViewModelFactory = MetroViewModelFactory(this)

    fun locationCollectorFactory(): DefaultLocationCollector.Factory

    @Provides
    @SingleIn(AppGraph::class)
    fun provideApplicationContext(application: Application): Context = application

    @Provides
    @SingleIn(AppGraph::class)
    fun provideBatteryOptimizationChecker(): BatteryOptimizationChecker =
        AndroidBatteryOptimizationChecker()

    @Provides
    @SingleIn(AppGraph::class)
    fun provideDeviceNameProvider(context: Context): DeviceNameProvider =
        AndroidDeviceNameProvider(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Creates the default camera vendor registry with all supported vendors.
     *
     * Currently supports:
     * - Ricoh cameras (GR IIIx, GR III, etc.)
     * - Sony Alpha cameras (via DI Remote Control protocol)
     *
     * To add support for additional camera vendors:
     * 1. Create a new vendor package (e.g., vendors/canon/)
     * 2. Implement CameraVendor, CameraGattSpec, and CameraProtocol
     * 3. Add the vendor to this list
     */
    @Provides
    @SingleIn(AppGraph::class)
    fun provideVendorRegistry(): CameraVendorRegistry =
        DefaultCameraVendorRegistry(
            vendors =
                listOf(
                    RicohCameraVendor,
                    SonyCameraVendor,
                    // Add more vendors here:
                    // CanonCameraVendor,
                    // NikonCameraVendor,
                )
        )

    @Provides
    @SingleIn(AppGraph::class)
    fun providePairedDevicesRepository(context: Context): PairedDevicesRepository =
        DataStorePairedDevicesRepository(context.pairedDevicesDataStoreV2)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideSyncStatusRepository(): SyncStatusRepository = InMemorySyncStatusRepository()

    @Provides
    @SingleIn(AppGraph::class)
    fun provideLocationRepository(context: Context): LocationRepository =
        FusedLocationRepository(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideCameraRepository(
        vendorRegistry: CameraVendorRegistry,
        context: Context,
    ): CameraRepository = KableCameraRepository(vendorRegistry = vendorRegistry, context = context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideNotificationBuilder(context: Context): NotificationBuilder =
        AndroidNotificationBuilder(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideIntentFactory(): IntentFactory =
        AndroidIntentFactory(MultiDeviceSyncService::class.java)

    @Provides
    @SingleIn(AppGraph::class)
    fun providePendingIntentFactory(): PendingIntentFactory = AndroidPendingIntentFactory()

    @Provides
    @SingleIn(AppGraph::class)
    fun provideBluetoothBondingChecker(context: Context): BluetoothBondingChecker =
        AndroidBluetoothBondingChecker(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideCompanionDeviceManagerHelper(
        context: Context,
        vendorRegistry: CameraVendorRegistry,
    ): CompanionDeviceManagerHelper = CompanionDeviceManagerHelper(context, vendorRegistry)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideIssueReporter(context: Context): IssueReporter = AndroidIssueReporter(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideLogRepository(context: Context): LogRepository = LogcatLogRepository(context)

    @Provides
    fun provideLogViewerViewModel(
        logRepository: LogRepository,
        ioDispatcher: CoroutineDispatcher,
    ): LogViewerViewModel = LogViewerViewModel(logRepository, ioDispatcher)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideWidgetUpdateHelper(context: Context): WidgetUpdateHelper =
        GlanceWidgetUpdateHelper(context)

    @Provides
    @SingleIn(AppGraph::class)
    fun provideFirmwareUpdateCheckers(context: Context): List<FirmwareUpdateChecker> =
        listOf(SonyFirmwareUpdateChecker(context), RicohFirmwareUpdateChecker(context))

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
