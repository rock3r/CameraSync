---
description: 
globs: NONE.jola
alwaysApply: false
---

# CameraSync - Claude AI Assistant Guide

## Project Overview

CameraSync is an Android application that synchronizes GPS data and date/time from your Android phone to your camera via Bluetooth Low Energy (BLE). The app supports cameras from multiple vendors (Ricoh, Sony, etc.) and can sync to **multiple cameras simultaneously** in the background.

## Project Structure

This is an Android project built with:
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Target Platform**: Android (tested on Pixel 9 with Android 15)
- **Hardware Target**: BLE-enabled cameras (tested with Ricoh GR IIIx and Sony Alpha cameras)

## Key Technologies & Architecture

- **Multi-Device Architecture**: Supports pairing and syncing multiple cameras simultaneously.
- **Multi-Vendor Architecture**: Uses the Strategy Pattern to support different camera brands (Ricoh, Sony, and extensible to others).
- **Bluetooth Low Energy (BLE)**: Core communication protocol using the Kable library.
- **Android Foreground Services**: Maintains connections when app is backgrounded.
- **Location Services**: Centralized GPS data collection shared across all devices.
- **Proto DataStore**: Persistent storage for paired devices using Protocol Buffers.

## Architecture Overview

### Data Layer
- `PairedDevicesRepository`: Interface for managing paired devices (add, remove, enable/disable) and global sync state
- `DataStorePairedDevicesRepository`: Proto-based persistence implementation
- `CameraRepository`: BLE scanning and connection management
- `LocationRepository`: GPS location updates from Fused Location Provider

### Domain Layer
- `PairedDevice`: Domain model for stored paired cameras
- `DeviceConnectionState`: Sealed interface for device connection states
- `Camera`: Discovered camera model with vendor information
- `CameraVendor`: Strategy interface for vendor-specific protocols
- `VendorConnectionDelegate`: **NEW** Abstraction for handling vendor-specific connection/sync lifecycles
- `DefaultConnectionDelegate`: Standard implementation of the delegate
- `CameraVendorRegistry`: Registry managing all supported camera vendors

### Service Layer
- `MultiDeviceSyncService`: Foreground service managing all device connections
- `MultiDeviceSyncCoordinator`: Core sync logic for multiple concurrent connections
- `LocationCollectionCoordinator`: Centralized location collection with device registration

### UI Layer
- `DevicesListScreen`: Main screen showing paired devices with enable/disable toggles
- `PairingScreen`: BLE scanning and pairing flow for new devices
- Material 3 design with animated connection status indicators

### Utility Layer
- `BatteryOptimizationUtil`: Utility for checking battery optimization status and creating intents to system settings
  - Supports standard Android battery optimization settings
  - Detects and provides intents for OEM-specific battery settings (Xiaomi, Huawei, Oppo, Samsung, etc.)
  - Uses two-step verification to avoid false positives on package detection
- `BatteryOptimizationChecker`: Injectable interface for battery optimization checks (with `AndroidBatteryOptimizationChecker` implementation)
  - Allows mocking in tests via `FakeBatteryOptimizationChecker`

## Multi-Vendor Architecture

CameraSync uses a **Strategy Pattern** to support cameras from multiple manufacturers without modifying core sync logic. This architecture enables adding new camera brands by implementing vendor-specific adapters.

### Key Components

1. **CameraVendor Interface** (`domain/vendor/CameraVendor.kt`)
   - Defines what each vendor must provide: GATT specification, protocol encoding/decoding, device recognition
   - Each vendor implements device identification logic based on service UUIDs, device names, or manufacturer data
   - Vendors declare their capabilities (firmware version support, geo-tagging, etc.)

2. **VendorConnectionDelegate** (`domain/vendor/VendorConnectionDelegate.kt`)
   - **New**: Encapsulates the entire connection and sync lifecycle
   - Allows vendors to implement complex handshakes (like Sony's DD30/DD31 locking) in isolation
   - `KableCameraRepository` delegates sync operations to this interface

3. **CameraVendorRegistry** (`domain/vendor/CameraVendorRegistry.kt`)
   - Central registry managing all supported vendors
   - Identifies which vendor a discovered BLE device belongs to
   - Aggregates scan filter UUIDs and device name prefixes from all vendors for efficient BLE scanning

4. **Vendor Implementations** (`vendors/` package)
   - **Ricoh**: `vendors/ricoh/` - Supports GR IIIx, GR III, and other Ricoh cameras
   - **Sony**: `vendors/sony/` - Supports Alpha series cameras (ILCE-7M4, etc.)
   - Each vendor package contains:
     - `[Vendor]GattSpec`: BLE service and characteristic UUIDs
     - `[Vendor]Protocol`: Encoding/decoding logic for date/time and GPS data
     - `[Vendor]CameraVendor`: Device recognition and capabilities
     - `[Vendor]ConnectionDelegate`: Connection lifecycle implementation

5. **Vendor-Agnostic Core**
   - `Camera` domain model (replaces legacy `RicohCamera`) contains a `vendor` property
   - `CameraRepository` and `CameraConnection` work with any vendor through the abstraction layer
   - Sync logic in `MultiDeviceSyncCoordinator` is completely vendor-agnostic

### Adding New Vendors

To add support for a new camera brand (e.g., Canon, Nikon):

1. Create vendor package: `app/src/main/kotlin/dev/sebastiano/camerasync/vendors/[vendor-name]/`
2. Implement `CameraGattSpec` with BLE UUIDs
3. Implement `CameraProtocol` with encoding/decoding logic
4. Implement `VendorConnectionDelegate` (or use `DefaultConnectionDelegate`)
5. Implement `CameraVendor` with device recognition logic and delegate creation
6. Register vendor in `AppGraph.kt`'s `provideVendorRegistry()` method

**Important**: Registering a new vendor automatically updates global BLE scan filters. The `KableCameraRepository` queries the registry for all vendor UUIDs at startup.

### Testing Multi-Vendor Code

- Each vendor has comprehensive tests: `RicohCameraVendorTest.kt`, `SonyCameraVendorTest.kt`
- GATT specifications have dedicated tests: `RicohGattSpecTest.kt`, `SonyGattSpecTest.kt`
- Registry logic is tested in `CameraVendorRegistryTest.kt`
- Protocol encoding/decoding has dedicated test classes
- `FakeVendorRegistry` available for integration testing

For complete details on the multi-vendor architecture, see [`docs/MULTI_VENDOR_SUPPORT.md`](docs/MULTI_VENDOR_SUPPORT.md).

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions, using `ktfmt` with the `kotlinlang` style
- Run `./gradlew ktfmtFormat` at the end of each task to ensure consistent formatting
- Use Android Architecture Components where applicable
- Maintain compatibility with Android 12+ (backup rules configured)
- All new interfaces should have corresponding fake implementations for testing

### Key Features
1. **Multi-Device Support**: Pair and sync multiple cameras simultaneously.
2. **Multi-Vendor Support**: Works with Ricoh, Sony, and extensible to other brands.
3. **Camera Discovery**: Vendor-agnostic BLE device scanning.
4. **Auto-reconnection**: Automatic reconnection to enabled devices when in range (requires global sync enabled).
5. **Centralized Location**: Single location collection shared across all connected devices.
6. **Background Sync**: Maintains synchronization via a Foreground Service.
7. **GPS & Time Sync**: Real-time location and timestamp synchronization.
8. **Manual Control**: Notification actions to "Refresh" (restart sync) or "Stop All" (persistent stop).
9. **Battery Optimization Warnings**: Proactive UI warnings when battery optimizations are enabled, with direct links to disable them (including OEM-specific settings).
10. **Issue Reporting**: Integrated feedback system that collects system info, BLE metadata (bonding state, firmware version, hardware revision), and app logs to troubleshoot connectivity issues.
11. **Firmware Update Notifications**: Automatic detection and notification of available firmware updates for paired cameras. Daily background checks via WorkManager, with notifications shown when devices connect. UI displays firmware version with badge indicating available updates.

### Testing
- Unit tests use coroutine test dispatchers with `TestScope`
- All repository interfaces have fake implementations in `test/fakes/`
- Each vendor has comprehensive test coverage for device recognition and protocol logic
- Use Khronicle for logging throughout the app (initialized in MainActivity)
- Kable logging uses KhronicleLogEngine adapter
- Tests use `TestGraphFactory` to get fake dependencies instead of production implementations

### Dispatcher Injection for Testability

**Always inject dispatchers** into ViewModels and other classes that launch coroutines on `Dispatchers.IO` or `Dispatchers.Default`. This allows tests to control time advancement with `runTest` and `advanceUntilIdle()`.

#### Pattern

```kotlin
// In the ViewModel/class
class MyViewModel(
    private val repository: MyRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO, // Injectable with default
) : ViewModel() {
    
    fun doSomething() {
        viewModelScope.launch(ioDispatcher) {  // Use injected dispatcher
            repository.fetchData()
        }
    }
}

// In tests
@Test
fun `my test`() = runTest {
    val testDispatcher = UnconfinedTestDispatcher()
    val viewModel = MyViewModel(
        repository = fakeRepository,
        ioDispatcher = testDispatcher,  // Inject test dispatcher
    )
    
    viewModel.doSomething()
    advanceUntilIdle()  // Now properly advances virtual time
    
    // Assertions...
}
```

#### Why This Matters

When using `runTest`, time is virtual. However, coroutines launched on `Dispatchers.IO` use **real time**, causing:
- `advanceUntilIdle()` to not wait for IO operations
- Tests to be flaky or require `Thread.sleep()` (which is slow and unreliable)

By injecting the dispatcher, tests can pass `UnconfinedTestDispatcher()` or `StandardTestDispatcher()`, making all coroutines use virtual time.

#### Examples in This Codebase

- `DevicesListViewModel`: Accepts `ioDispatcher` parameter for all `Dispatchers.IO` usages
- `PairingViewModel`: Same pattern for pairing operations
- `MultiDeviceSyncCoordinator`: Accepts `CoroutineScope` for complete control in tests

### Testing Notes
- Primary test configuration: Pixel 9 + Android 15 + Ricoh GR IIIx
- ALWAYS run tests: `./gradlew test`
- Run specific test class: `./gradlew test --tests "fully.qualified.TestClassName"`

## Common Tasks

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Installing Debug Build
```bash
./gradlew installDebug
```

### Formatting Code
```bash
./gradlew ktfmtFormat
```

## Important Considerations

- Devices must have Bluetooth pairing enabled on the camera side
- Background operation requires proper battery optimization exemptions
- Location permissions are critical for GPS sync functionality
- BLE permissions required for camera communication
- Location collection runs at 60-second intervals when devices are connected
- Each camera vendor may have different capabilities and requirements

### Battery Optimization

The app displays a warning card when battery optimizations are active, as they can interfere with:
- Background BLE connections to cameras
- Foreground service reliability
- Location updates

**Implementation Details:**
- `DevicesListViewModel` monitors battery optimization status via a reactive flow
- Status is automatically refreshed when the app resumes (using lifecycle observers)
- UI shows a warning card with a button to open system settings
- Supports both standard Android settings and OEM-specific battery management screens
- OEM detection uses package verification to avoid false positives (checks package existence + activity resolution)
- Multi-layer fallback: Direct request → General settings → OEM settings → Manual instructions

**Supported OEM Battery Settings:**
- Xiaomi (MIUI)
- Huawei
- Oppo/ColorOS (multiple versions)
- Samsung (China & Global)
- iQOO, Vivo, HTC, Asus, Meizu, ZTE, Lenovo, Coolpad, LeTV, Gionee

**Required Permissions:**
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in AndroidManifest.xml
- Corresponding `<queries>` declarations for package visibility (Android 11+)

## License

Apache License 2.0 - See LICENSE file for details.

---

*This document is maintained to help Claude AI understand the project context and provide relevant assistance.*
