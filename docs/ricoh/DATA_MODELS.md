# Ricoh App Data Models (State Management)

The official app uses Riverpod-style state management. This document lists the main data models and
notifier providers inferred from the binary. Implementation details (e.g. Kotlin/Flow) may differ in
CameraSync.

---

## 10.1. Camera State Models

**CameraServiceModel:**

```dart
CameraServiceModel(
  cameraPower: CameraPowerModel,
  operationModeList: List<OperationMode>,  // Available modes
  operationMode: OperationMode,            // Current mode
  geoTag: GeoTagModel,
  storageInformation: List<StorageInformationModel>,
  fileTransferList: FileTransferListModel,
  powerOffDuringTransfer: PowerOffDuringFileTransferModel,
  gradNd: bool?,                           // Grad ND filter state
  cameraName: String                       // Camera display name
)
```

**OperationMode enum:** `capture`, `playback`, `bleStartup`, `other`, `powerOffTransfer`

**CameraPowerModel:** `CameraPowerModel(isPower: bool)`

**BatteryLevelModel:** `BatteryLevelModel(level: int)` — 0–100 percentage

**StorageInformationModel:**

```dart
StorageInformationModel(
  type: StorageType,              // internal, sd1
  isExistence: bool,
  isLocked: bool,
  isAvailable: bool,
  isFormatted: bool,
  remainingPictures: int,
  remainingVideoSeconds: int,
  fileType: String
)
```

---

## 10.2. Shooting State Models

**CaptureStatusModel:** `CaptureStatusModel(countdown: int, status: String)` — status "idle" | "capture"

**ShootingModeModel:** `ShootingModeModel(mode: String)` — "still" | "movie"

**CaptureModeModel:** `CaptureModeModel(mode: String)` — "single", "continuous", etc.

**DriveModeModel:** `DriveModeModel(driveMode: DriveMode)`

**DriveMode BLE enum (`QOa`)** — 16 values:

| Value | Name                                  |
|:------|:--------------------------------------|
| 0     | `oneFrame`                            |
| 1     | `tenSecondFrame`                      |
| 2     | `twoSecondFrame`                      |
| 3     | `continuousShootingFrame`             |
| 4     | `bracketFrame`                        |
| 5–9   | bracket/multi-exposure + timer variants |
| 10–15 | interval / interval composition + timer variants |

**CountdownStatus enum (`uOa`):** `notInCountdown` (0), `selfTimerCountdown` (1), `scheduledTimeWaiting` (2)

**CapturingStatus enum (`tOa`):** `notInShootingProcess` (0), `inShootingProcess` (1)

**UserMode enum (`zPa`):** `Other` (0), `U1` (1), `U2` (2), `U3` (3)

**ExposureModel:** `ExposureModel(p, tv, av, m, b, t, bt, sfp: int)` — shots per exposure mode

---

## 10.3. Device Models

**DeviceModel:** Aggregates BLE/HTTP data: `deviceInfo`, `isBleConnected`, `isWifiConnected`, `isLocationExisted`, `storageType`, `batteryLevel`, `inMemoryRemainingPictures`, `sdCardRemainingPictures`, `cameraPower`, lock/available/formatted flags for memory and SD, `isValidFirmwareVersion`.

**CurrentDeviceModel:** `CurrentDeviceModel(currentDevice: DeviceModel)`

**DeviceInfoModel:**

```dart
DeviceInfoModel(
  id: int,
  modelName: String,
  modelType: int,
  deviceName: String,
  bdName: String,
  remoteId: String,
  serialNo: String,
  firmwareVersion: String,
  ssid: String,
  bssid: String,
  security: String,
  password: String,
  wlanFrequency: int,  // 0=2.4GHz, 1=5GHz
  isTimeSync: bool
)
```

**CameraInformationServiceModel:** Read via the 6 custom UUIDs in [README.md](README.md) §2.1.3: `manufacturerNameString`, `bluetoothDeviceName`, `bluetoothMacAddressString`, `firmwareRevisionString`, `modelNumberString`, `serialNumberString`.

---

## 10.4. Photo Models

**PhotoInfoModel:** `id`, `cameraModel`, `serialNo`, `memory`, `dir`, `file`, `recordedSize`, `size`, `datetime`, `recordedTime`, `orientation`, `aspectRatio`, `av`, `tv`, `sv`, `xv`, `latLng`, `gpsInfo`, `thumbImagePath`, `viewImagePath`, `fullImagePath`, `fullImageId`, `xsImagePath`

**GalleryPhotoInfoModel:** Same as PhotoInfoModel plus `isFavorite`

---

## 10.5. Image Control Models

**ImageControlModel:** `id`, `raw: Uint8List`, `name`, `thumbPath`, `viewPath`, `imageControlType`, `imageControlCameraType`, `customImageType`, `customImageId`, `customImageVersion`, `isFavorite`, `isGrGallery`, `aspectRatio`, `orientation`

**ImageControlType Enum:** Standard, Vivid, Monotone, … Custom1/2/3; GR IV Mono: MonoStandard, MonoSoft, etc.

---

## 10.6. State Notifier Providers

| Provider                                     | Notification Type | Purpose                       |
|:---------------------------------------------|:------------------|:------------------------------|
| `cameraPowerNotifierProvider`                | `YNa`             | Camera power — `CameraPowerModel(isPower)` |
| `batteryLevelNotifierProvider`               | `XNa`             | Battery — `BatteryLevelModel(level)` |
| `captureStatusNotifierProvider`              | `tPa`             | Capture state — countdown + capturing status |
| `shootingModeNotifierProvider`               | `uPa`             | Exposure + user mode         |
| `captureModeNotifierProvider`                | `sPa`             | Capture mode (image/video)   |
| `driveModeNotifierProvider`                  | `ZNa`             | Drive mode — `QOa` enum      |
| `storageInformationNotifierProvider`         | `eOa`             | Storage — `List<StorageInformationModel>` |
| `geoTagNotifierProvider`                     | `cOa`             | GeoTag — `GeoTagModel(isEnabled)` |
| `powerOffDuringFileTransferNotifierProvider` | `dOa`             | Transfer behavior            |
| `fileTransferListNotifierProvider`           | `aOa`             | File transfer state          |
| `firmwareUpdateResultNotifierProvider`       | `bOa`             | Firmware result — `FMa` enum |
| `bleConnectionStateNotifierProvider`         | —                 | BLE connection state         |
| `websocketConnectionStateNotifierProvider`   | —                 | WebSocket connection state   |
| `imageControlViewModelProvider`              | —                 | Image Control state          |
| `cameraLogViewModelProvider`                 | —                 | GR Log state                 |
| `cameraInfoViewModelProvider`                | —                 | Camera info                  |
| `cameraSettingsViewModelProvider`            | —                 | Settings state               |

**FileTransferListModel:** `FileTransferListModel(isNotEmpty: bool)`  
**PowerOffDuringFileTransferModel:** `PowerOffDuringFileTransferModel(behavior: PowerOffBehavior)`
