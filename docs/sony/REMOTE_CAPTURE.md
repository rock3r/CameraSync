# Remote Capture + Transfer Flow (BLE + Wi-Fi)

This document describes the full camera remote capture + media transfer flow used by this app.
It is intended for clean-room reimplementation and is derived from the decompiled codebase.
USB is intentionally out of scope.

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
Modes in `ShootingController`:
- Still, Movie, Continuous, Bulb, High Frame Rate, Panorama, etc.
- Each mode is mapped to different UI + event handling, but the control layer
  relies on `EnumControlCode` + `EnumButton`.

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

