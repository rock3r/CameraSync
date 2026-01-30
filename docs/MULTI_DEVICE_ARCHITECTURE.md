# Multi-Device Architecture

This document describes the architecture that enables CameraSync to manage multiple paired cameras simultaneously, with centralized location collection and independent device connection lifecycle.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
├─────────────────────────────────────────────────────────────────┤
│  DevicesListScreen          │  PairingScreen                    │
│  - Shows paired devices     │  - BLE scanning                   │
│  - Enable/disable toggles   │  - Device discovery               │
│  - Connection status        │  - Pairing flow                   │
│  - Unpair action            │  - Error handling                 │
└─────────────────────────────┴───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Service Layer                              │
├─────────────────────────────────────────────────────────────────┤
│  MultiDeviceSyncService (Foreground Service)                    │
│  - Manages service lifecycle                                    │
│  - Observes enabled devices from repository                     │
│  - Updates notifications                                        │
│  - Handles notification actions (Refresh, Stop)                 │
│                                                                 │
│  MultiDeviceSyncCoordinator                                     │
│  - Manages multiple concurrent camera connections               │
│  - Per-device state tracking (Map<MAC, DeviceConnectionState>)  │
│  - Broadcasts location updates to all connected devices         │
│                                                                 │
│  LocationCollectionCoordinator                                  │
│  - Centralized location collection                              │
│  - Device registration for automatic start/stop                 │
│  - 30-second update interval                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Repository Layer                           │
├─────────────────────────────────────────────────────────────────┤
│  PairedDevicesRepository                                        │
│  - Stores paired devices (Proto DataStore)                      │
│  - Enable/disable device state                                  │
│  - Flow<List<PairedDevice>> for reactive updates                │
│                                                                 │
│  CameraRepository                                               │
│  - BLE scanning and discovery                                   │
│  - Device connection management                                 │
│  - Vendor-agnostic camera connections                           │
│                                                                 │
│  LocationRepository                                             │
│  - Fused Location Provider                                      │
│  - High-accuracy GPS updates                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. PairedDevicesRepository

Manages the persistent storage of paired devices.

**Interface:** `domain/repository/PairedDevicesRepository.kt`

```kotlin
interface PairedDevicesRepository {
    val pairedDevices: Flow<List<PairedDevice>>
    val enabledDevices: Flow<List<PairedDevice>>
    val isSyncEnabled: Flow<Boolean>
    
    suspend fun addDevice(camera: Camera, enabled: Boolean = true)
    suspend fun removeDevice(macAddress: String)
    suspend fun setDeviceEnabled(macAddress: String, enabled: Boolean)
    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun isDevicePaired(macAddress: String): Boolean
    // ... more methods
}
```

**Implementation:** `DataStorePairedDevicesRepository` using Proto DataStore.

**Proto Schema:**
```protobuf
message PairedDeviceProto {
  string mac_address = 1;
  optional string name = 2;
  string vendor_id = 3;
  bool enabled = 4;
}

message PairedDevicesProto {
  repeated PairedDeviceProto devices = 1;
  optional bool sync_enabled = 2;
}
```

### 2. LocationCollectionCoordinator

Centralized location collection with automatic lifecycle management.

**Interface:** `devicesync/LocationCollector.kt`

```kotlin
interface LocationCollectionCoordinator : LocationCollector {
    fun registerDevice(deviceId: String)
    fun unregisterDevice(deviceId: String)
    fun getRegisteredDeviceCount(): Int
}
```

**Behavior:**
- Automatically starts collecting when first device registers
- Automatically stops when last device unregisters
- Exposes `StateFlow<GpsLocation?>` for consumers
- 30-second update interval

### 3. MultiDeviceSyncCoordinator

Core coordination logic for multiple device connections.

**Class:** `devicesync/MultiDeviceSyncCoordinator.kt`

**State Management:**
```kotlin
val deviceStates: StateFlow<Map<String, DeviceConnectionState>>
```

**Key Methods:**
```kotlin
fun startDeviceSync(device: PairedDevice)
suspend fun stopDeviceSync(macAddress: String)
suspend fun stopAllDevices()
fun isDeviceConnected(macAddress: String): Boolean
fun getConnectedDeviceCount(): Int
fun startBackgroundMonitoring(enabledDevices: Flow<List<PairedDevice>>)
fun refreshConnections()
```

**Connection Lifecycle:**
1. `startDeviceSync()` establishes the BLE connection
2. **Waits for connection to be fully established** before performing initial setup (prevents GATT write errors)
3. Performs initial setup (firmware read, device name, date/time, geo-tagging) with connection state checks
4. Registers device for location updates
5. Monitors connection state and cleans up on disconnection

**Background Monitoring:**
- `startBackgroundMonitoring()` observes the enabled devices flow
- Periodically checks for enabled but disconnected devices and connects them
- **Automatically disconnects devices that are no longer enabled**
- Runs every 30 seconds and on enabled devices flow changes

**Connection States:**
```kotlin
sealed interface DeviceConnectionState {
    data object Disabled : DeviceConnectionState
    data object Disconnected : DeviceConnectionState
    data object Connecting : DeviceConnectionState
    data class Connected(val firmwareVersion: String?) : DeviceConnectionState
    data class Error(val message: String, val isRecoverable: Boolean) : DeviceConnectionState
    data class Syncing(val firmwareVersion: String?, val lastSyncInfo: LocationSyncInfo?) : DeviceConnectionState
}
```

### 4. MultiDeviceSyncService

Android Foreground Service managing the sync lifecycle.

**Responsibilities:**
- Runs as foreground service with location + connected device types
- Observes `PairedDevicesRepository.enabledDevices`
- Starts/stops device connections based on enabled state
- Updates notification with connection count and sync status
- Handles notification actions:
  - **Refresh**: Sets global `sync_enabled` to true, restarts service, and retries all connections
  - **Stop All**: Disconnects all devices, sets global `sync_enabled` to false, removes notification, and stops service

**Lifecycle:**
1. Service starts when there are enabled devices AND global `sync_enabled` is true
2. Service stops when all devices are disabled/removed OR "Stop All" is clicked
3. Auto-reconnection only occurs when global `sync_enabled` is true
4. Manual refresh via UI or notification restarts the service regardless of current state

## Data Flow

### Device Pairing
```
User selects device in PairingScreen
        │
        ▼
PairingViewModel.pairDevice(camera)
        │
        ▼
PairedDevicesRepository.addDevice(camera, enabled=true)
        │
        ▼
MultiDeviceSyncService observes enabledDevices (if sync_enabled=true)
        │
        ▼
MultiDeviceSyncCoordinator.startDeviceSync(device)
        │
        ▼
Device connects → Initial setup → Register for location
```

### Stop All Sync
```
User clicks "Stop all" in Notification
        │
        ▼
MultiDeviceSyncService.onStartCommand(ACTION_STOP)
        │
        ▼
PairedDevicesRepository.setSyncEnabled(false)
        │
        ▼
MultiDeviceSyncCoordinator.stopAllDevices()
        │
        ▼
stopForeground(REMOVE) & stopSelf()
```

### Manual Refresh / Restart
```
User clicks "Refresh" in UI or Notification
        │
        ▼
DevicesListViewModel.refreshConnections() / ACTION_REFRESH
        │
        ▼
PairedDevicesRepository.setSyncEnabled(true)
        │
        ▼
context.startService(ACTION_REFRESH)
        │
        ▼
Service starts/resumes → startForegroundService() → refreshConnections()
```

### Location Sync
```
LocationRepository emits new GPS location
        │
        ▼
LocationCollectionCoordinator receives location
        │
        ▼
locationUpdates StateFlow emits to subscribers
        │
        ▼
MultiDeviceSyncCoordinator.syncLocationToAllDevices()
        │
        ├──▶ Device 1: CameraConnection.syncLocation()
        ├──▶ Device 2: CameraConnection.syncLocation()
        └──▶ Device N: CameraConnection.syncLocation()
```

### Enable/Disable Device
```
User toggles switch in DevicesListScreen
        │
        ▼
DevicesListViewModel.setDeviceEnabled(mac, enabled)
        │
        ▼
PairedDevicesRepository.setDeviceEnabled(mac, enabled)
        │
        ▼
If (enabled AND sync_enabled) -> context.startService()
        │
        ▼
MultiDeviceSyncService observes change via background monitoring
        │
        ├── If enabled: startDeviceSync(device)
        └── If disabled: checkAndConnectEnabledDevices() detects
            device is no longer enabled and calls stopDeviceSync(mac)
```

**Important:** When a device is disabled, the `checkAndConnectEnabledDevices()` method automatically detects connected devices that are no longer in the enabled list and disconnects them. This ensures devices are properly disconnected when disabled, preventing them from remaining connected.

## Testing

### Fakes Provided

All key interfaces have fake implementations for testing:

| Interface | Fake Implementation |
|-----------|---------------------|
| `PairedDevicesRepository` | `FakePairedDevicesRepository` |
| `LocationCollectionCoordinator` | `FakeLocationCollector` |
| `CameraRepository` | `FakeCameraRepository` |
| `CameraConnection` | `FakeCameraConnection` |
| `LocationRepository` | `FakeLocationRepository` |
| `CameraVendorRegistry` | `FakeVendorRegistry` |
| `NotificationBuilder` | `FakeNotificationBuilder` |
| `IntentFactory` | `FakeIntentFactory` |
| `PendingIntentFactory` | `FakePendingIntentFactory` |

**Dependency Injection**: The project uses Metro for compile-time DI. Tests use `TestGraphFactory` to access fake dependencies, while production code uses `AppGraphFactory`. This allows for clean separation between test and production implementations without requiring Robolectric or extensive Android framework mocking.

### Test Structure

```
app/src/test/kotlin/dev/sebastiano/camerasync/
├── fakes/
│   ├── FakePairedDevicesRepository.kt
│   ├── FakeLocationCollector.kt
│   ├── FakeCameraRepository.kt
│   ├── FakeCameraConnection.kt
│   ├── FakeLocationRepository.kt
│   ├── FakeVendorRegistry.kt
│   ├── FakeNotificationBuilder.kt
│   ├── FakeIntentFactory.kt
│   └── FakePendingIntentFactory.kt
├── di/
│   └── TestModule.kt (Metro test dependency graph)
├── devicesync/
│   ├── MultiDeviceSyncCoordinatorTest.kt
│   ├── DefaultLocationCollectorTest.kt
│   ├── SyncCoordinatorTest.kt
│   └── NotificationsTest.kt
└── data/repository/
    └── FakePairedDevicesRepositoryTest.kt
```

### Example Test

```kotlin
@Test
fun `multiple devices can be synced simultaneously`() = testScope.runTest {
    val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
    val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

    cameraRepository.connectionToReturn = connection1
    coordinator.startDeviceSync(testDevice1)
    advanceUntilIdle()

    cameraRepository.connectionToReturn = connection2
    coordinator.startDeviceSync(testDevice2)
    advanceUntilIdle()

    assertEquals(2, locationCollector.getRegisteredDeviceCount())
    assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
    assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))
}
```

## Error Handling

### Connection Errors

When a device fails to connect:
1. State transitions to `DeviceConnectionState.Error`
2. Error message indicates cause (pairing rejected, timeout, etc.)
3. Recoverable errors can be retried via `retryDeviceConnection()`
4. Error state is preserved (not overwritten by cleanup)

### GATT Write Errors

To prevent `ProfileServiceNotBound` and other GATT write errors:
1. **Connection establishment is verified** before performing initial setup operations
2. Connection state is checked before each write operation (device name, date/time, geo-tagging)
3. If connection is lost during setup, operations fail gracefully with warnings logged
4. The coordinator waits for `connection.isConnected` to emit `true` before calling `performInitialSetup()`

This ensures that BLE operations only occur when the connection is fully established and active.

### Pairing Errors

The `PairingScreen` handles three error types:
- `REJECTED`: Camera rejected pairing (user needs to enable BT pairing on camera)
- `TIMEOUT`: Connection timed out (camera not nearby or BT disabled)
- `UNKNOWN`: Unexpected error

## Notifications

The foreground service shows:
- **Title**: "Syncing with N devices" or "Searching for N devices..."
- **Content**: Last sync time or connection status
- **Actions**: 
  - "Refresh" - Retry failed connections
  - "Stop all" - Disconnect all devices and stop service

## Scanning Strategy

The app currently uses a **Foreground Service** with periodic checks to maintain connections.

### Current Implementation (Active Check)
- The `MultiDeviceSyncService` runs continuously when "Sync Enabled" is true.
- `MultiDeviceSyncCoordinator` runs a coroutine loop every 30 seconds.
- It checks if any enabled devices are disconnected.
- If disconnected, it triggers an active BLE scan/connect attempt.

### Future Architecture (PendingIntent)
To improve energy efficiency, the architecture is being migrated to:
1. **Passive Scanning**: When no devices are connected, the Foreground Service stops.
2. **PendingIntent**: The app registers a system-level BLE scan via `PendingIntent`.
3. **Wake-up**: When a device is found, the system wakes the app, and the service restarts to handle the connection.
4. **Safety Net**: A `WorkManager` job runs periodically (e.g., every 15m) to ensure the scan is still active if the system kills the app.

## Future Enhancements

Potential improvements to the architecture:
1. Per-device sync intervals (some devices may need more frequent updates)
2. Device priority ordering (which device gets location first)
3. Background scanning for new devices
4. Device-specific notification actions
5. Sync history and statistics per device

---

**Note**: This architecture is designed for testability. All components communicate through interfaces, and the use of `StateFlow` enables reactive UI updates without tight coupling.

