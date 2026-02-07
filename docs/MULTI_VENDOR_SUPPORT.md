# Multi-Vendor Camera Support

This document describes the architecture that enables CameraSync to support cameras from multiple
manufacturers, currently supporting Ricoh and Sony.

## Architecture Overview

The app uses a vendor abstraction layer based on the **Strategy Pattern**, allowing easy extension
to support new camera brands.

### Key Components

1. **CameraVendor Interface** (`domain/vendor/CameraVendor.kt`)
    - Defines what each vendor must provide
    - Contains GATT specification, protocol encoder/decoder, and device recognition logic
    - **New**: Creates a `VendorConnectionDelegate` to handle connection lifecycle

2. **VendorConnectionDelegate** (`domain/vendor/VendorConnectionDelegate.kt`)
    - **Primary Abstraction Point**: Encapsulates the entire connection and sync lifecycle
    - Handles vendor-specific connection setup (MTU, handshake), sync logic (retries, locking), and disconnection cleanup
    - `DefaultConnectionDelegate`: Base implementation for standard GATT write flows
    - `SonyConnectionDelegate`: Complex implementation with locking, notifications, and retries

3. **CameraVendorRegistry** (`domain/vendor/CameraVendorRegistry.kt`)
    - Manages all registered camera vendors
    - Identifies which vendor a discovered BLE device belongs to
    - Aggregates scan filter UUIDs and name prefixes for all vendors

4. **Vendor Implementations** (`vendors/` package)
    - Each vendor (Ricoh, Sony, etc.) has its own sub-package
    - Contains vendor-specific GATT specs, protocol encoding, capabilities, and connection delegates

5. **Generic Domain Model**
    - `Camera` (replaces `RicohCamera`) with vendor property and generic `vendorMetadata` map

## Connection Delegates

The `VendorConnectionDelegate` is the core of the multi-vendor architecture. It allows vendors to take full control of how data is synchronized to the camera, rather than just providing data for a generic sync loop.

### Why Delegates?
Different vendors have vastly different connection requirements:
- **Ricoh**: Simple GATT writes. Connect -> Write -> Done.
- **Sony**: Complex state machine. Connect -> Request MTU 158 -> Subscribe to Notifications -> Write "Lock" command -> Write "Enable" command -> Read Capabilities -> Write Data -> Disconnect.

The delegate pattern encapsulates this complexity within the vendor's implementation, keeping the core repository clean.

### Standard Delegate Flow
1. `KableCameraRepository` connects to the device
2. Calls `delegate.onConnected()` (Sony uses this to enable services)
3. Delegates `syncLocation()` and `syncDateTime()` calls directly to the delegate implementation
4. Calls `delegate.onDisconnecting()` for cleanup

## Directory Structure

```
app/src/main/kotlin/dev/sebastiano/camerasync/
├── domain/
│   ├── model/
│   │   ├── Camera.kt (formerly RicohCamera.kt)
│   │   └── ...
│   ├── vendor/
│   │   ├── CameraVendor.kt
│   │   ├── VendorConnectionDelegate.kt  <-- New abstraction
│   │   ├── DefaultConnectionDelegate.kt <-- Standard impl
│   │   ├── CameraVendorRegistry.kt
│   │   └── CameraGattSpec.kt
│   └── repository/
│       └── CameraRepository.kt (vendor-agnostic)
├── vendors/
│   ├── ricoh/
│   │   ├── RicohCameraVendor.kt
│   │   ├── RicohGattSpec.kt
│   │   └── RicohProtocol.kt
│   └── sony/
│       ├── SonyCameraVendor.kt
│       ├── SonyConnectionDelegate.kt    <-- Complex logic here
│       ├── SonyGattSpec.kt
│       └── SonyProtocol.kt
├── data/
│   └── repository/
│       └── KableCameraRepository.kt (vendor-agnostic)
└── di/
    └── AppGraph.kt (vendor registry configuration)
```

## Adding Support for a New Camera Vendor

Follow these steps to add support for a new camera brand (e.g., Canon, Nikon):

### Step 1: Create Vendor Package
Create a new package: `app/src/main/kotlin/dev/sebastiano/camerasync/vendors/[vendor-name]/`

### Step 2: Implement GATT Specification
Create `[VendorName]GattSpec.kt` implementing `CameraGattSpec` with service/characteristic UUIDs.

### Step 3: Implement Protocol Encoder/Decoder
Create `[VendorName]Protocol.kt` implementing `CameraProtocol` to handle binary data formats.

### Step 4: Implement Connection Delegate
- If the camera uses standard GATT writes, you can use `DefaultConnectionDelegate`.
- If the camera requires complex setup (auth, specific MTU, flow control), implement `VendorConnectionDelegate` or extend `DefaultConnectionDelegate`.

```kotlin
class CanonConnectionDelegate : DefaultConnectionDelegate() {
    // Optional: Override setup/teardown or sync logic if needed
    override suspend fun onConnected(peripheral: Peripheral, camera: Camera) {
        // Perform vendor-specific handshake
    }
}
```

### Step 5: Implement Camera Vendor
Create `[VendorName]CameraVendor.kt` implementing `CameraVendor`. Return your delegate in `createConnectionDelegate()`.

```kotlin
@OptIn(ExperimentalUuidApi::class)
object CanonCameraVendor : CameraVendor {
    // ... metadata ...
    
    override fun createConnectionDelegate(): VendorConnectionDelegate = CanonConnectionDelegate()

    // ... recognition logic ...
}
```

### Step 6: Register Vendor
Update `AppGraph.kt` to register the new vendor in the `provideVendorRegistry` method.

## How It Works

### BLE Scanning
1. `KableCameraRepository` asks `CameraVendorRegistry` for all scan filter UUIDs
2. Scanner is configured to listen for all registered vendor UUIDs
3. When a device is discovered, the registry identifies which vendor it belongs to
4. A `Camera` object is created with the appropriate vendor

### Connection & Communication
1. `KableCameraRepository.connect()` creates a `KableCameraConnection`
2. `KableCameraConnection` asks the vendor to create a `VendorConnectionDelegate`
3. All sync operations (`syncLocation`, `syncDateTime`) are passed to the delegate
4. The delegate handles the specific BLE interactions (encoding, writing, retrying)
5. Unsupported features throw `UnsupportedOperationException` based on vendor capabilities

### Vendor Capabilities
Different vendors support different features. The `CameraCapabilities` class defines what each vendor supports:
- Firmware version and hardware revision reading
- Setting paired device name
- Date/time synchronization
- Geo-tagging enable/disable
- GPS location synchronization
- Vendor-specific pairing initialization (e.g., Sony's EE01 write)

## Benefits
✅ **Total Isolation**: Sony's complex locking logic stays in `SonyConnectionDelegate`
✅ **Clean Core**: `KableCameraRepository` is ~300 lines lighter and simpler
✅ **Flexibility**: Delegates can handle any weird connection flow (auth, keep-alives, split packets)
✅ **Testability**: Delegates can be unit tested in isolation
