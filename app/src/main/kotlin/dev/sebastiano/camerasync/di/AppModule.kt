package dev.sebastiano.camerasync.di

import android.app.Application
import android.content.Context
import dev.sebastiano.camerasync.MainActivity
import dev.sebastiano.camerasync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.camerasync.data.repository.FusedLocationRepository
import dev.sebastiano.camerasync.data.repository.KableCameraRepository
import dev.sebastiano.camerasync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.camerasync.devicesync.AndroidIntentFactory
import dev.sebastiano.camerasync.devicesync.AndroidNotificationBuilder
import dev.sebastiano.camerasync.devicesync.AndroidPendingIntentFactory
import dev.sebastiano.camerasync.devicesync.IntentFactory
import dev.sebastiano.camerasync.devicesync.MultiDeviceSyncService
import dev.sebastiano.camerasync.devicesync.NotificationBuilder
import dev.sebastiano.camerasync.devicesync.PendingIntentFactory
import dev.sebastiano.camerasync.domain.repository.CameraRepository
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import dev.sebastiano.camerasync.domain.repository.PairedDevicesRepository
import dev.sebastiano.camerasync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.camerasync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.camerasync.feedback.AndroidIssueReporter
import dev.sebastiano.camerasync.feedback.IssueReporter
import dev.sebastiano.camerasync.pairing.AndroidBluetoothBondingChecker
import dev.sebastiano.camerasync.pairing.BluetoothBondingChecker
import dev.sebastiano.camerasync.pairing.CompanionDeviceManagerHelper
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.camerasync.vendors.sony.SonyCameraVendor
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

/**
 * Metro dependency graph for production dependencies.
 *
 * The graph is created via the Factory interface using createGraphFactory, which allows passing
 * external dependencies like Application. Context is then provided from Application and injected
 * into @Provides methods that need it.
 */
@DependencyGraph
interface AppGraph {
    fun inject(service: MultiDeviceSyncService)

    fun inject(activity: MainActivity)

    @Provides fun provideApplicationContext(application: Application): Context = application

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
    fun providePairedDevicesRepository(context: Context): PairedDevicesRepository =
        DataStorePairedDevicesRepository(context.pairedDevicesDataStoreV2)

    @Provides
    fun provideLocationRepository(context: Context): LocationRepository =
        FusedLocationRepository(context)

    @Provides
    fun provideCameraRepository(vendorRegistry: CameraVendorRegistry): CameraRepository =
        KableCameraRepository(vendorRegistry = vendorRegistry)

    @Provides
    fun provideNotificationBuilder(context: Context): NotificationBuilder =
        AndroidNotificationBuilder(context)

    @Provides
    fun provideIntentFactory(): IntentFactory =
        AndroidIntentFactory(MultiDeviceSyncService::class.java)

    @Provides
    fun providePendingIntentFactory(): PendingIntentFactory = AndroidPendingIntentFactory()

    @Provides
    fun provideBluetoothBondingChecker(context: Context): BluetoothBondingChecker =
        AndroidBluetoothBondingChecker(context)

    @Provides
    fun provideCompanionDeviceManagerHelper(
        context: Context,
        vendorRegistry: CameraVendorRegistry,
    ): CompanionDeviceManagerHelper = CompanionDeviceManagerHelper(context, vendorRegistry)

    @Provides
    fun provideIssueReporter(context: Context): IssueReporter = AndroidIssueReporter(context)

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
