# CameraSync - Claude AI Assistant Guide

## Project Overview

CameraSync is an Android application that synchronizes GPS data and date/time from your Android phone to your camera via Bluetooth Low Energy (BLE). The app supports cameras from multiple vendors (Ricoh, Sony, etc.) and can sync to **multiple cameras simultaneously** in the background.

## Project Structure

This is an Android project built with:
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Target Platform**: Android (tested on Pixel 9 with Android 15)
- **Hardware Target**: BLE-enabled cameras (tested with Ricoh GR IIIx)

## Key Technologies & Architecture

- **Multi-Device Architecture**: Supports pairing and syncing multiple cameras simultaneously.
- **Multi-Vendor Architecture**: Uses the Strategy Pattern to support different camera brands.
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

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions, using `ktfmt` with the `kotlinlang` style
- Run `./gradlew ktfmtFormat` at the end of each task to ensure consistent formatting
- Use Android Architecture Components where applicable
- Maintain compatibility with Android 12+ (backup rules configured)
- All new interfaces should have corresponding fake implementations for testing

### Key Features
1. **Multi-Device Support**: Pair and sync multiple cameras simultaneously.
2. **Camera Discovery**: Vendor-agnostic BLE device scanning.
3. **Auto-reconnection**: Automatic reconnection to enabled devices when in range (requires global sync enabled).
4. **Centralized Location**: Single location collection shared across all connected devices.
5. **Background Sync**: Maintains synchronization via a Foreground Service.
6. **GPS & Time Sync**: Real-time location and timestamp synchronization.
7. **Manual Control**: Notification actions to "Refresh" (restart sync) or "Stop All" (persistent stop).
8. **Battery Optimization Warnings**: Proactive UI warnings when battery optimizations are enabled, with direct links to disable them (including OEM-specific settings).
9. **Issue Reporting**: Integrated feedback system that collects system info, BLE metadata (bonding state, firmware version, hardware revision), and app logs to troubleshoot connectivity issues.

### Testing
- Unit tests use coroutine test dispatchers with `TestScope`
- All repository interfaces have fake implementations in `test/fakes/`
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
- Run tests: `./gradlew test`
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
