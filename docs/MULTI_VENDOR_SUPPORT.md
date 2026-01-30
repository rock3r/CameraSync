# Multi-Vendor Camera Support

This document describes the refactoring that enables CameraSync to support cameras from multiple manufacturers, not just Ricoh.

## Architecture Overview

The refactoring introduces a vendor abstraction layer using the **Strategy Pattern**, allowing easy extension to support new camera brands.

### Key Components

1. **CameraVendor Interface** (`domain/vendor/CameraVendor.kt`)
   - Defines what each vendor must provide
   - Contains GATT specification, protocol encoder/decoder, and device recognition logic

2. **CameraVendorRegistry** (`domain/vendor/CameraVendorRegistry.kt`)
   - Manages all registered camera vendors
   - Identifies which vendor a discovered BLE device belongs to
   - Provides scan filter UUIDs for all vendors

3. **Vendor Implementations** (`vendors/` package)
   - Each vendor (Ricoh, Canon, Nikon, etc.) has its own sub-package
   - Contains vendor-specific GATT specs, protocol encoding, and capabilities

4. **Generic Domain Model**
   - `Camera` (replaces `RicohCamera`) with vendor property
   - Backward compatibility maintained via type alias

## Directory Structure

```
app/src/main/kotlin/dev/sebastiano/camerasync/
├── domain/
│   ├── model/
│   │   ├── Camera.kt (formerly RicohCamera.kt)
│   │   └── ...
│   ├── vendor/
│   │   ├── CameraVendor.kt
│   │   ├── CameraVendorRegistry.kt
│   │   └── CameraGattSpec.kt
│   └── repository/
│       └── CameraRepository.kt (vendor-agnostic)
├── vendors/
│   └── ricoh/
│       ├── RicohCameraVendor.kt
│       ├── RicohGattSpec.kt (moved from ble/)
│       └── RicohProtocol.kt (moved from data/encoding/)
├── data/
│   └── repository/
│       └── KableCameraRepository.kt (vendor-agnostic)
└── CameraSyncApp.kt (vendor registry configuration)
```

## Adding Support for a New Camera Vendor

Follow these steps to add support for a new camera brand (e.g., Canon, Nikon, Sony):

### Step 1: Create Vendor Package

Create a new package: `app/src/main/kotlin/dev/sebastiano/camerasync/vendors/[vendor-name]/`

### Step 2: Implement GATT Specification

Create `[VendorName]GattSpec.kt` implementing `CameraGattSpec`:

```kotlin
@OptIn(ExperimentalUuidApi::class)
object CanonGattSpec : CameraGattSpec {
    // Scan filter UUIDs
    override val scanFilterServiceUuids: List<Uuid> = listOf(
        Uuid.parse("YOUR-CANON-SERVICE-UUID")
    )

    // Firmware service
    override val firmwareServiceUuid: Uuid? = Uuid.parse("...")
    override val firmwareVersionCharacteristicUuid: Uuid? = Uuid.parse("...")

    // Device name service
    override val deviceNameServiceUuid: Uuid? = Uuid.parse("...")
    override val deviceNameCharacteristicUuid: Uuid? = Uuid.parse("...")

    // DateTime service
    override val dateTimeServiceUuid: Uuid? = Uuid.parse("...")
    override val dateTimeCharacteristicUuid: Uuid? = Uuid.parse("...")

    // Geo-tagging
    override val geoTaggingCharacteristicUuid: Uuid? = Uuid.parse("...")

    // Location service
    override val locationServiceUuid: Uuid? = Uuid.parse("...")
    override val locationCharacteristicUuid: Uuid? = Uuid.parse("...")
}
```

### Step 3: Implement Protocol Encoder/Decoder

Create `[VendorName]Protocol.kt` implementing `CameraProtocol`:

```kotlin
object CanonProtocol : CameraProtocol {
    override fun encodeDateTime(dateTime: ZonedDateTime): ByteArray {
        // Implement Canon's specific date/time encoding
        // This will vary by vendor!
    }

    override fun decodeDateTime(bytes: ByteArray): String {
        // Decode Canon's date/time format
    }

    override fun encodeLocation(location: GpsLocation): ByteArray {
        // Implement Canon's specific GPS encoding
    }

    override fun decodeLocation(bytes: ByteArray): String {
        // Decode Canon's location format
    }

    override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray {
        // Encode geo-tagging state
    }

    override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean {
        // Decode geo-tagging state
    }
}
```

### Step 4: Implement Camera Vendor

Create `[VendorName]CameraVendor.kt` implementing `CameraVendor`:

```kotlin
@OptIn(ExperimentalUuidApi::class)
object CanonCameraVendor : CameraVendor {
    override val vendorId: String = "canon"
    override val vendorName: String = "Canon"
    override val gattSpec: CameraGattSpec = CanonGattSpec
    override val protocol: CameraProtocol = CanonProtocol

    override fun recognizesDevice(deviceName: String?, serviceUuids: List<Uuid>): Boolean {
        // Identify Canon cameras by service UUID or device name
        return serviceUuids.any { it in CanonGattSpec.scanFilterServiceUuids }
            || deviceName?.startsWith("EOS", ignoreCase = true) == true
    }

    override fun getCapabilities(): CameraCapabilities {
        return CameraCapabilities(
            supportsFirmwareVersion = true,
            supportsDeviceName = true,
            supportsDateTimeSync = true,
            supportsGeoTagging = true, // Set based on what Canon supports
            supportsLocationSync = true,
        )
    }
}
```

### Step 5: Register Vendor

Update `CameraSyncApp.kt` to register the new vendor:

> **Important**: Registering a new vendor automatically updates the global BLE scan filters. The `KableCameraRepository` queries the registry for all vendor UUIDs at startup. Ensure your vendor's `scanFilterServiceUuids` are correct, or the scanner will filter out your devices.

```kotlin
fun createVendorRegistry(): CameraVendorRegistry {
    return DefaultCameraVendorRegistry(
        vendors = listOf(
            RicohCameraVendor,
            SonyCameraVendor,
            // Add more vendors here:
            // CanonCameraVendor,
            // NikonCameraVendor,
        ),
    )
}
```

That's it! The app will now automatically discover and sync with Canon cameras.

## How It Works

### BLE Scanning

1. `KableCameraRepository` asks `CameraVendorRegistry` for all scan filter UUIDs
2. Scanner is configured to listen for all registered vendor UUIDs
3. When a device is discovered, the registry identifies which vendor it belongs to
4. A `Camera` object is created with the appropriate vendor

### Connection & Communication

1. `KableCameraRepository.connect()` returns a vendor-agnostic `CameraConnection`
2. `KableCameraConnection` uses the camera's vendor GATT spec to find services/characteristics
3. Data is encoded/decoded using the vendor's protocol implementation
4. Unsupported features throw `UnsupportedOperationException` based on vendor capabilities

### Vendor Capabilities

Different vendors support different features. The `CameraCapabilities` class defines what each vendor supports:

- Firmware version reading
- Setting paired device name
- Date/time synchronization
- Geo-tagging enable/disable
- GPS location synchronization

## Backward Compatibility

- `RicohCamera` is now a deprecated type alias for `Camera`
- Old `RicohGattSpec` and `RicohProtocol` classes are deprecated wrappers
- Existing code continues to work with deprecation warnings
- Tests remain functional with backward-compatible API

## Benefits

✅ **Easy Extension**: Add new camera vendors by implementing 3 classes
✅ **Vendor Isolation**: Each vendor's protocol is self-contained
✅ **No Core Changes**: Adding vendors doesn't touch core sync logic
✅ **Type Safety**: Compile-time checks ensure vendor implementations are complete
✅ **Testability**: Each vendor can be tested independently
✅ **Backward Compatible**: Existing Ricoh camera support unchanged

## Testing a New Vendor

1. Implement the vendor classes as described above
2. Add unit tests in `app/src/test/kotlin/dev/sebastiano/camerasync/vendors/[vendor-name]/`
3. Test protocol encoding/decoding with known byte sequences
4. Test device recognition with various device names and service UUIDs
5. Test with actual hardware if available

## Example: Testing Canon Protocol

```kotlin
class CanonProtocolTest {
    @Test
    fun `encodeDateTime produces correct format`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = CanonProtocol.encodeDateTime(dateTime)

        // Verify Canon's specific byte format
        // (This will vary based on Canon's actual protocol!)
        assertEquals(expectedSize, encoded.size)
        // ... more assertions
    }
}
```

## Common Issues

### Device Not Recognized

- Verify the scan filter UUID is correct for your camera
- Check that `recognizesDevice()` logic matches your camera's advertisement
- Use BLE scanner apps to inspect actual advertisement data

### Wrong Data Format

- Carefully reverse-engineer the vendor's protocol
- Use a BLE sniffer to capture actual communication
- Test encoding/decoding with known values

### Missing Features

- Some cameras may not support all features
- Set capabilities correctly to avoid runtime errors
- Handle `UnsupportedOperationException` gracefully in UI

## Additional Resources

- BLE GATT Specification: https://www.bluetooth.com/specifications/specs/
- nRF Connect (BLE scanner): https://www.nordicsemi.com/Products/Development-tools/nrf-connect-for-mobile
- Wireshark BLE plugin for protocol analysis

---

**Note**: This refactoring maintains full backward compatibility with existing Ricoh camera support while enabling future expansion to any BLE-enabled camera brand.
