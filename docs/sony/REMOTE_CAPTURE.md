# Remote Capture + Transfer Flow (BLE + Wi-Fi)

This document describes the **full Wi-Fi/PTP/IP** remote capture + media transfer flow derived from
the decompiled Sony Creators' App codebase. USB is intentionally out of scope.

> **Note:** Sony cameras also support a simpler **BLE-only remote shooting** path via the
> `8000FF00` Remote Control Service (shutter with half-press AF, video recording, zoom, manual
> focus, custom buttons). See [`BLE_STATE_MONITORING.md`](BLE_STATE_MONITORING.md) Section 2 for
> details. The BLE remote is sufficient for basic shooting; the Wi-Fi/PTP/IP path described here
> adds live view, touch AF, full device property control, and media transfer.

Key packages/classes referenced:
- BLE: `jp.co.sony.ips.portalapp.btconnection.*`, `jp.co.sony.ips.portalapp.bluetooth.continuous.*`
- Wi-Fi: `jp.co.sony.ips.portalapp.wifi.control.*`, `jp.co.sony.ips.portalapp.wificonnection.*`
- Discovery: `jp.co.sony.ips.portalapp.common.device.SsdpUtil`
- PTP/IP: `jp.co.sony.ips.portalapp.ptpip.*`, `jp.co.sony.ips.portalapp.ptp.remotecontrol.*`
- Orchestration: `jp.co.sony.ips.portalapp.camera.CameraConnector`

-------------------------------------------------------------------------------
## 1) High-level architecture

The remote capture + transfer pipeline is a multi-protocol orchestration:

1. BLE discovery + bonding
2. BLE GATT control to enable camera Wi-Fi + fetch credentials
3. Wi-Fi association to camera AP (or via AP)
4. SSDP discovery to locate the camera HTTP descriptor
5. PTP/IP initialization (command + event channels)
6. Remote control (capture) and/or transfer (push/pull)

The connection state machine is managed by `CameraConnector`:
- `EnumFunction.REMOTE_CONTROL` => PTP/IP in `REMOTE_CONTROL_MODE`
- `EnumFunction.CONTENTS_TRANSFER_*` => PTP/IP in `CONTENTS_TRANSFER_MODE`
- `EnumFunction.REMOTE_CONTROL` can switch to transfer if the camera supports a
  "REMOTE_CONTROL_WITH_TRANSFER_MODE" and you explicitly switch function mode.

-------------------------------------------------------------------------------
## 2) BLE Discovery + Pairing

### 2.1 Scan / device selection
Class: `BluetoothAppScanningState`
- The app scans for the registered camera's BLE MAC address (stored in
  `RegisteredCameraObject`).
- Scan is low-power BLE scan via `BluetoothLeUtil.startLeScanWithLowPower()`.
- The scan checks the MAC address match and parses manufacturer data in the scan
  record to determine camera status and capability.
- If the device is not bonded, the flow halts and surfaces "NotPaired".
- If bonded, it moves to `BluetoothAppConnectingState`.

### 2.2 GATT connection
Class: `BluetoothGattAgent`
- Establishes `BluetoothGatt` connection and performs service discovery.
- Uses an internal operation queue for read/write/notify operations.
- Handles GATT errors (e.g., 133, 62) and disconnection timeouts.
- Requests MTU 158 if possible.

-------------------------------------------------------------------------------
## 3) BLE GATT Service + Characteristics (Camera Control Service)

Service UUID:
- `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF`

Relevant characteristics used for Wi-Fi orchestration:
- `0000CC08` Wi-Fi ON command (write `{0x01}`)
- `0000CC06` SSID (read)
- `0000CC07` Password (read)
- `0000CC0C` BSSID (optional, read)
- `0000CC03` Push transfer notification (notify)
- `0000CC0A` Firmware version (read)
- `0000CC0B` Model name (read)

Parsing rules:
- SSID + Password: US-ASCII string starting at byte index 3
  (`BluetoothGattUtil.getWifiInfo()` strips first 3 bytes).
- BSSID: full US-ASCII string from the response buffer
  (`new String(bArr, StandardCharsets.US_ASCII)`).

-------------------------------------------------------------------------------
## 4) BLE Wi-Fi Activation & Credential Retrieval

Two BLE state machine steps are used:

### 4.1 Turn Wi-Fi on
Class: `TurningWifiOnState`
- If Wi-Fi is already launched, transitions to next state.
- Otherwise writes `{0x01}` to `0000CC08`.
- Listens for Wi-Fi status updates and errors via
  `BluetoothCameraInfo$WifiStatus`.
- Timeouts are 30 seconds.

Failure cases:
- Command timeout => `EnumBluetoothWifiInfoError.TimeOut`
- GATT write errors => `CommandFailure`
- Camera reports `NoMedia` => `CommandFailureForNoMedia`

### 4.2 Read SSID/Password/BSSID
Class: `GettingWifiInfoAfterWifiOnState`
- Read `0000CC06` (SSID)
- Read `0000CC07` (password)
- Read `0000CC0C` (BSSID; optional)
- Timeouts are 15 seconds.

If BSSID read fails, it logs a warning and still completes with SSID/password.

### 4.3 Push Transfer BLE notification
Characteristic: `0000CC03`
- Enable notifications by writing `0x01, 0x00` to CCCD (`0x2902`).
- Notification payload is 4 bytes:
  - Byte index 3: `0x02` => Ready for transfer (Push Transfer)
  - Byte index 3: `0x01` => Not ready / idle

-------------------------------------------------------------------------------
## 5) Wi-Fi Connection (Camera AP or Via AP)

Orchestrator: `CameraConnector.connectPtpIp()`

### 5.1 Topologies
`EnumTopology`:
- `CAMERA_AP` (typical BLE-based AP activation)
- `VIA_AP` (connect via existing AP; not detailed here)

### 5.2 Connection phases
`CameraConnector.EnumConnectPhase`:
`GET_NETWORK_SETTING` → `SET_SMARTPHONE_CONNECT_SETTING` → `GET_UUID`
→ `SEARCH_NETWORK` → `FETCH_SSH_INFO` → `CONNECT_NETWORK` → `CONNECT_CAMERA`

### 5.3 Wi-Fi association
Class: `WifiControlUtil` + `WifiConnection`
- Creates `WifiNetworkSpecifier` with SSID + WPA2 passphrase.
- Optional BSSID constraint if valid.
- Uses `ConnectivityManager.requestNetwork()` with timeout.
- If already connected to SSID, moves directly to `Connected` state.

Key error types (`EnumWifiControlErrorType`):
- `ConnectionTimeOut`
- `WifiOff`
- `NoWifiConfiguration`
- `ErrorAuthenticating`

### 5.4 App-only routing (Android 12/13+)
This app uses `requestNetwork()` + `bindProcessToNetwork()` patterns to
route app sockets to the camera AP without hijacking global routing
(`ConnectivityManager.bindProcessToNetwork()`).

-------------------------------------------------------------------------------
## 6) Camera Discovery (SSDP / UPnP)

Class: `SsdpUtil`

- Uses Sony Scalar Web API device finder:
  `com.sony.scalar.webapi.lib.devicefinder.*`
- Sends M-SEARCH for:
  `urn:schemas-sony-com:service:ScalarWebAPI:1`
- Multicast: `239.255.255.250:1900`
- Expects `LOCATION` header to point to `http://<camera-ip>:8080/description.xml`
- Filters out devices that fail discovery 3 times (blacklist).

Discovery provides the IP address needed for PTP/IP TCP socket connections.

-------------------------------------------------------------------------------
## 7) PTP/IP Session (Command + Event Channels)

PTP/IP is used for both remote control and media transfer.
Port: TCP `15740`

### 7.1 Initialization handshake
Classes: `CommandInitializer`, `EventInitializer`

Two connections to the same port:
1. Command channel:
   - Send `InitCommandRequest` (Type `0x01`)
     - Client GUID (16 bytes)
     - Friendly name (UTF-16LE, null-terminated)
     - Protocol Version: `0x00010000` (65536)
   - Receive `InitCommandAck` (Type `0x02`)
   - Receive connection number (used for event channel)
2. Event channel:
   - Send `InitEventRequest` (Type `0x03`) with connection number
   - Receive `InitEventAck` (Type `0x04`)

Notes:
- The client GUID is a stable UUID per install (stored in SharedPrefs).
- PTP/IP packets have an 8-byte header (4 bytes length, 4 bytes type). The length includes the header itself.
- Probe Responder: The app MUST respond to `ProbeRequest` (Type `0x0D` / 13) on the event channel with a `ProbeResponse` (Type `0x0E` / 14).

### 7.2 Session and function modes
`EnumFunctionMode`:
- `REMOTE_CONTROL_MODE`
- `CONTENTS_TRANSFER_MODE`
- `REMOTE_CONTROL_WITH_TRANSFER_MODE` (when supported)

If already in `REMOTE_CONTROL_WITH_TRANSFER_MODE`, the app can switch modes
without reopening the session; otherwise it tears down and re-initializes.

### 7.3 Transport error handling
Class: `PtpIpManager`
- app MUST respond to ProbeRequest (Type `0x0D` / 13) on the event channel.
- Retries command connection up to 3 times.
- On socket closed during initialization, sets status
  `ConnectionLimit` or `TransportErrorOccurred`.
- On probe failure: `TransactionTimedOut` and teardown.

-------------------------------------------------------------------------------
## 8) Remote Capture (PTP/IP Control Codes)

### 8.1 Control code transport
Classes: `EnumControlCode`, `EnumButton`, `BaseCamera.sendControlCode()`

PTP control codes are sent via PTP/IP transactions to simulate buttons and
camera UI events. Common codes used for shooting:

- `S1Button` (half-press AF)
- `S2Button` (full press shutter)
- `RequestOneShooting`
- `MovieRecButton`
- `RemoteTouchOperation` / `CancelRemoteTouchOperation`
- `SetLiveViewEnable`, `SetPostViewEnable`

### 8.2 Shooting controller
Class: `ShootingController`
- Maintains current shooting mode (Still/Movie/Continuous/Bulb/HFR/etc).
- Sends S1/S2 press/release events via `pressButton()` / `releaseButton()`.
- Uses device property updates to enforce restrictions.

Typical still capture:
1. `pressButton(S1)` to focus (optional)
2. `pressButton(S2)` to shoot (or `RequestOneShooting`)
3. `releaseButton(S2)` then `releaseButton(S1)`

Typical movie capture:
1. `pressButton(MovieRec)` -> start recording
2. `pressButton(MovieRec)` -> stop recording

### 8.3 Mode-specific behavior
Modes in `ShootingController` (`EnumShootingMode`):
- `StillMode` — standard still capture (S1/S2 or RequestOneShooting)
- `MovieMode` — video recording (MovieRec toggle)
- `ContinuousShootMode` — burst shooting
- `SpotBoostShootMode` — continuous shooting with spot-boost
- `BulbShootMode` — manual bulb (S2 hold)
- `BulbTimerShootMode` — timed bulb (`BulbTimerSetting`/`BulbExposureTimeSetting`)
- `NoiseReductionMode` — long-exposure noise reduction state
- `SlowAndQuickMode` — slow & quick motion video
- `HighFrameRateMode` — HFR recording (HFRStandby → record)
- `PanoramaShootMode` — panorama sweep

Each mode is mapped to different UI + event handling, but the control layer
relies on `EnumControlCode` + `EnumButton`.

### 8.4 Full Control Codes (`EnumControlCode`, `SDIO_ControlDevice`)

All PTP control codes sent via `SDIO_ControlDevice` transactions:

| Code  | Name                                     | Data   | Description                                        |
|-------|------------------------------------------|--------|----------------------------------------------------|
| 53953 | `S1Button`                               | 2 byte | Half-press AF (press=1 / release=2)                |
| 53954 | `S2Button`                               | 2 byte | Full-press shutter (press=1 / release=2)           |
| 53955 | `AELButton`                              | 2 byte | AE Lock toggle                                     |
| 53956 | `AFLButton`                              | 2 byte | AF Lock toggle                                     |
| 53959 | `RequestOneShooting`                     | 2 byte | Single-shot capture (no separate S1/S2)            |
| 53960 | `MovieRecButton`                         | 2 byte | Movie record start/stop toggle                     |
| 53961 | `FELButton`                              | 2 byte | Flash Exposure Lock toggle                         |
| 53965 | `RemoteKeyUp`                            | 2 byte | D-pad up                                           |
| 53966 | `RemoteKeyDown`                          | 2 byte | D-pad down                                         |
| 53967 | `RemoteKeyLeft`                          | 2 byte | D-pad left                                         |
| 53968 | `RemoteKeyRight`                         | 2 byte | D-pad right                                        |
| 53969 | `NearFar`                                | 2 byte | Manual focus near/far (minus=near, plus=far)       |
| 53970 | `AFMFHold`                               | 2 byte | AF/MF Hold button                                  |
| 53971 | `CancelPixelShiftShooting`               | 2 byte | Cancel pixel shift shooting                        |
| 53972 | `PixelShiftShootingMode`                 | 2 byte | Enter pixel shift shooting mode                    |
| 53973 | `HFRStandby`                             | 2 byte | HFR standby start/stop                             |
| 53974 | `HFRRecordingCancel`                     | 2 byte | HFR recording cancel                               |
| 53975 | `FocusStepNear`                          | 2 byte | Focus step near (fine)                             |
| 53976 | `FocusStepFar`                           | 2 byte | Focus step far (fine)                              |
| 53977 | `AWBLButton`                             | 2 byte | Auto White Balance Lock toggle                     |
| 53978 | `ProgramShift`                           | 1 byte | Program shift increment (+1) / decrement (-1)      |
| 53979 | `WhiteBalanceInitialization`             | 2 byte | Reset white balance                                |
| 53980 | `AFAreaPosition`                         | 4 byte | Set AF area position (x, y encoded)                |
| 53981 | `ZoomOperation`                          | 1 byte | Zoom in (+) / out (-)                              |
| 53987 | `HighResolutionSSAdjust`                 | 2 byte | Hi-Res shutter speed adjust (+/-)                  |
| 53988 | `RemoteTouchOperation`                   | 4 byte | Touch AF at (x, y) coordinate                     |
| 53989 | `CancelRemoteTouchOperation`             | 2 byte | Cancel touch AF                                    |
| 53992 | `WiFiPowerOff`                           | 2 byte | Turn off camera Wi-Fi                              |
| 54000 | `HighResolutionSSAdjustInIntegralMultiples` | 2 byte | Hi-Res SS adjust by integral steps               |
| 54001 | `FlickerScan`                            | 2 byte | Initiate flicker scan                              |
| 54006 | `ContShootSpotBoostButton`               | 2 byte | Continuous shoot spot boost toggle                 |
| 54024 | `WiFiDirectModeOff`                      | 2 byte | Turn off Wi-Fi Direct mode                         |
| 54031 | `SetSelectOnCameraTransferEnable`        | 2 byte | Enable/disable select-on-camera transfer           |
| 54032 | `SetSelectOnCameraTransferMode`          | 2 byte | Set transfer mode                                  |
| 54033 | `CancelSelectOnCameraTransfer`           | 2 byte | Cancel select-on-camera transfer                   |
| 54034 | `SetPostViewEnable`                      | 2 byte | Enable/disable post-capture preview                |
| 54035 | `SetLiveViewEnable`                      | 2 byte | Enable/disable live view stream                    |

### 8.5 Device Properties (PTP/IP `DevicePropCode`)

Sony cameras expose ~150+ readable/observable properties via PTP/IP. The camera
pushes property-change events on the PTP event channel, enabling real-time UI
updates. Key shooting-related properties:

**Exposure & Metering:**

| Code  | Name                         | Description                                    |
|-------|------------------------------|------------------------------------------------|
| 20494 | `ExposureProgramMode`        | P / A / S / M / Bulb / etc.                    |
| 20487 | `FNumber`                    | Aperture (F-number)                            |
| 53773 | `ShutterSpeed`               | Shutter speed                                  |
| 53663 | `ExtendedShutterSpeed`       | Extended shutter speed (longer exposures)      |
| 53790 | `ISOSensitivity`             | ISO value                                      |
| 20496 | `ExposureBiasCompensation`   | Exposure compensation (+/- EV)                 |
| 20491 | `ExposureMeteringMode`       | Metering mode (matrix, center, spot)           |
| 53787 | `PictureEffect`              | Creative style / picture effect                |
| 53824 | `CreativeStyle`              | Creative style preset                          |
| 53823 | `PictureProfile`             | Picture profile (S-Log, HLG, etc.)             |

**Focus:**

| Code  | Name                                    | Description                                    |
|-------|-----------------------------------------|------------------------------------------------|
| 20490 | `FocusMode`                             | AF-S / AF-C / DMF / MF                        |
| 53779 | `FocusIndication`                       | Focus status (in-focus, not-focused, etc.)     |
| 53804 | `FocusArea`                             | AF area mode (wide, zone, center, etc.)        |
| 53810 | `AFAreaPosition`                        | AF area position (x, y)                        |
| 53836 | `FocalPosition`                         | Current focal position (for MF)                |
| 53844 | `FocusMagnifierSetting`                 | Focus magnifier on/off                         |
| 53805 | `FocusMagnifierStatus`                  | Focus magnifier active state                   |
| 53806 | `FocusMagnifierRatio`                   | Focus magnifier zoom ratio                     |
| 53808 | `FocusMagnifierPosition`                | Focus magnifier position (x, y)                |
| 53813 | `NearFarEnableStatus`                   | NearFar (MF) control enable/disable            |
| 53814 | `AFMFHoldButtonEnableStatus`            | AF/MF Hold button enable/disable               |
| 53891 | `FunctionOfTouchOperation`              | Touch operation function (AF, tracking, etc.)  |
| 53892 | `RemoteTouchOperationEnableStatus`      | Remote touch AF enable/disable                 |
| 53861 | `OnePushAFExecutionState`               | One-push AF execution state                    |

**Shooting State:**

| Code  | Name                         | Description                                    |
|-------|------------------------------|------------------------------------------------|
| 53789 | `MovieRecordingState`        | Movie recording state (idle/recording)         |
| 53802 | `HFRRecordingState`          | HFR recording state                            |
| 20499 | `StillCaptureMode`           | Still capture mode (single, continuous, timer)  |
| 53793 | `LiveViewStatus`             | Live view enable/disable state                 |
| 53786 | `DisableIndication`          | Controls currently disabled by camera          |
| 53860 | `RemoteControlRestrictionStatus` | Remote control restrictions active          |

**Bulb & Long Exposure:**

| Code  | Name                              | Description                                    |
|-------|-----------------------------------|------------------------------------------------|
| 53924 | `BulbTimerSetting`                | Bulb timer on/off                              |
| 53925 | `BulbExposureTimeSetting`         | Bulb exposure time setting (seconds)           |
| 53926 | `ElapsedBulbExposureTime`         | Current elapsed bulb time                      |
| 53927 | `RemainingBulbExposureTime`       | Remaining bulb exposure time                   |
| 53928 | `RemainingNoiseReductionTime`     | Remaining NR processing time                   |

**Battery & Storage:**

| Code  | Name                              | Description                                    |
|-------|-----------------------------------|------------------------------------------------|
| 53774 | `BatteryLevelIndicator`           | Battery level (bar indicator)                  |
| 53784 | `BatteryRemaining`                | Battery remaining percentage                   |
| 53549 | `SecondBatteryRemaining`          | Vertical grip battery remaining                |
| 53832 | `MediaSLOT1Status`                | Slot 1 media status                            |
| 53833 | `MediaSLOT1RemainingNumberShots`  | Slot 1 remaining still shots                   |
| 53834 | `MediaSLOT1RemainingShootingTime` | Slot 1 remaining video time (seconds)          |
| 53846 | `MediaSLOT2Status`                | Slot 2 media status                            |
| 53847 | `MediaSLOT2RemainingNumberShots`  | Slot 2 remaining still shots                   |
| 53848 | `MediaSLOT2RemainingShootingTime` | Slot 2 remaining video time (seconds)          |

**Zoom:**

| Code  | Name                         | Description                                    |
|-------|------------------------------|------------------------------------------------|
| 53851 | `ZoomOperationEnableStatus`  | Zoom control available                         |
| 53852 | `ZoomScale`                  | Current zoom level                             |
| 53853 | `ZoomBarInformation`         | Zoom bar position info                         |
| 53854 | `ZoomSpeedRange`             | Min/max zoom speed                             |

**White Balance:**

| Code  | Name                                    | Description                                    |
|-------|-----------------------------------------|------------------------------------------------|
| 20485 | `WhiteBalance`                          | White balance preset                           |
| 53775 | `ColorTemperature`                      | Color temperature (K)                          |
| 53776 | `BiaxialFineTuningGMDirection`          | WB fine-tune G-M axis                          |
| 53788 | `BiaxialFineTuningABDirection`          | WB fine-tune A-B axis                          |
| 53838 | `AWBLockIndication`                     | AWB Lock active                                |

**Other:**

| Code  | Name                         | Description                                    |
|-------|------------------------------|------------------------------------------------|
| 53777 | `AspectRatio`                | Image aspect ratio                             |
| 53763 | `ImageSize`                  | Image resolution setting                       |
| 53842 | `JPEGQuality`                | JPEG quality / compression                     |
| 53843 | `FileFormatStill`            | Still file format (RAW, JPEG, RAW+JPEG)        |
| 53825 | `FileFormatMovie`            | Movie file format (XAVC, AVCHD, etc.)          |
| 53826 | `RecordingSettingMovie`      | Movie resolution/frame rate setting             |
| 53841 | `DeviceOverheatingState`     | Camera overheating warning                     |
| 53857 | `RecordingTime`              | Current movie recording time elapsed           |

### 8.6 Live View Stream

Live view is an HTTP-based continuous JPEG stream. When `SetLiveViewEnable` is
sent via PTP/IP, the camera exposes a `LiveViewUrl` device property with the
stream URL.

Classes: `LiveViewStream`, `LiveViewGetter`, `LiveViewDownloader`,
`ChunkedPermanentEeImageDownloader`

The live view `LiveViewDataset` includes:
- **Header**: coordinate system, frame dimensions
- **JPEG frame**: viewfinder image
- **AF frame overlay**: AF point rectangles with lock status (`EnumAfLockStatus`)
- **Face detection frames**: face rectangles with type and status
- **Tracking frames**: subject tracking rectangles
- **EFraming frames**: electronic framing guides
- **AF range frames**: AF area range indicators
- **Level indicator**: camera pitch/roll
- **Arrow indicators**: directional hints

The app uses `LiveviewScreenController` to render the viewfinder with
overlaid AF frames (`AfFrameDrawer`), aspect markers (`AspectMarkerDrawer`),
grid lines (`GridlineDrawer`), and touch operations (`TouchOperationController`).

Liveview quality can be adjusted via the `LiveviewImageQuality` device property.

-------------------------------------------------------------------------------
## 9) Media Transfer (PTP/IP)

Media transfer uses Sony-specific PTP operations:

Operation codes:
- `0x923C` SDIO_GetContentsInfoList
- `0x923F` SDIO_GetSelectOnCameraTransferContentsList
- `0x923D` SDIO_GetContentsData (full-size)
- `0x923E` SDIO_GetContentsCompressedData (thumb/screennail)

### 9.1 Push transfer (camera-initiated)
Triggers:
- BLE notification on `0000CC03` (value `0x02` in byte index 3)
- Or PTP event `SDIE_SelectOnCameraSendEvent` (event code `0xC235`)

Flow:
1. Receive push notification/event.
2. Issue `0x923F` to fetch list of selected items.
3. Parse each item metadata:
   - slotNumber (int)
   - contentId (int)
   - fileId (short)
   - filePath length (int) + UTF-8 path
   - fileFormat (int enum)
   - fileSize (long)
   - umid (32 bytes)
   - imageType (byte): `0x00` original, `0x01` 2M
   - shotMarks (count + bool list)
   - shortVideoLength (byte)
   - videoParam flag (int)
4. Build `uniqueId`:
   - `uniqueId = (((slotNumber << 24) | fileId) << 32) | contentId`
5. Download based on `imageType`:
   - original => `0x923D`
   - 2M => `0x923E` with type `0x02`

### 9.2 Pull transfer (app-initiated browsing)
Flow:
1. `0x923C` list all contents.
2. Request previews:
   - thumbnail => `0x923E` type `0x01`
   - screennail (2M) => `0x923E` type `0x02`
3. Download selection:
   - original => `0x923D` (full size)

### 9.3 Chunking and resume
Downloads use `SDIO_GetContentsData` in chunks:
- Chunk size: 3,145,728 bytes (3 MB)
- Parameters:
  - uniqueId low32 (contentId)
  - uniqueId high32: `(slotNumber << 24) | fileId`
    - Bit 16 of high32 is set if the response should include file size (`isResponseFileSize`).
  - offset low32/high32
  - chunkSize
- Non-zero offset allows resume after interruption.

-------------------------------------------------------------------------------
## 10) Error Handling / Edge Cases

### 10.1 BLE errors (`EnumBluetoothWifiInfoError`)
- `TimeOut`: camera did not respond
- `CommandFailure`: generic failure
- `CommandFailureForNoMedia`: no SD card
- `CommandFailureForNoContentsInMedia`: no contents
- `CommandFailureForOther`: other failure (low battery, etc.)

### 10.2 Wi-Fi errors (`EnumWifiControlErrorType`)
- `ConnectionTimeOut`
- `WifiOff`
- `NoWifiConfiguration`
- `ErrorAuthenticating`

### 10.3 PTP errors (`EnumResponseCode`)
Common handling:
- `OK` (0x2001) success
- `GeneralError` (0x2002)
- `DeviceBusy` (0x2019)
- `IncompleteTransfer` (0x2007) indicates network loss
- `TransactionCanceled` (0x201F)

### 10.4 Transport errors
`PtpIpManager` handles:
- Socket closed during init -> ConnectionLimit / TransportError
- Probe failures -> TransactionTimedOut
- Automatic retries (3 max)

-------------------------------------------------------------------------------
## 11) Handovers / Session Switching

1. BLE remains connected for notifications and status
2. Data transfers happen over Wi-Fi PTP/IP
3. Function mode can switch between remote control and transfer:
   - If camera supports `REMOTE_CONTROL_WITH_TRANSFER_MODE`, it can switch
     without reconnecting; otherwise it tears down and reconnects.

-------------------------------------------------------------------------------
## 12) Minimal Clean-room Implementation Checklist

### Path A: BLE-Only Remote Shooting (simpler, no Wi-Fi)

BLE:
- Pair & bond with camera (required; unpaired devices are disconnected)
- Enable "Bluetooth Rmt Ctrl" in camera settings
- Connect GATT, discover service `8000FF00`
- Subscribe to `FF02` notifications (focus/shutter/recording status)
- Write commands to `FF01`:
  - Still capture: `0x0107` → `0x0109` → `0x0108` → `0x0106`
  - Video toggle: `0x010E`
  - Zoom: `[0x02, 0x6D, speed]` / `[0x02, 0x6C, 0x00]`
  - Focus: `[0x02, 0x47, speed]` / `[0x02, 0x46, 0x00]`
- **Always** send matched Down/Up pairs; skipping Up leaves camera stuck

Monitoring (optional, via `8000CC00` if accessible alongside `8000FF00`):
- Subscribe to `CC10` (battery), `CC09` (status), `CC0F` (storage)

### Path B: Full Wi-Fi/PTP/IP Remote Control (advanced features)

BLE:
- Scan for bonded camera BLE MAC
- Connect GATT, discover services
- Write `{0x01}` to `0000CC08`
- Read SSID (0000CC06), password (0000CC07), optional BSSID (0000CC0C)
- Enable notifications on `0000CC03` (CCCD 0x2902)

Wi-Fi:
- Connect to camera AP using `WifiNetworkSpecifier`
- Bind app routing to the camera network

Discovery:
- SSDP M-SEARCH to `239.255.255.250:1900`
- Target `urn:schemas-sony-com:service:ScalarWebAPI:1`
- Parse `LOCATION` URL for IP address

PTP/IP:
- Open command + event TCP connections to `15740`
- Perform `InitCommandRequest` + `InitEventRequest`

Remote capture:
- Use PTP control codes (S1/S2, RequestOneShooting, MovieRec)
- Handle liveview/postview enable if required

Transfer:
- Push transfer via BLE notification / PTP event
- Pull transfer via `0x923C` list and `0x923D`/`0x923E` download
- Chunked download (3 MB) + resume support

