# Ricoh GR Camera Native Android Protocol Specification

**Target Audience:** Clean-room native Android developers (Kotlin, Coroutines, Kable).
**Scope:** Exhaustive specification of BLE discovery, pairing, connection, Wi-Fi handover, image
transfer, remote control, GPS transmission, Image Controls, and firmware updates for Ricoh GR III,
IIIx, and IV cameras.

---

## 1. The "Dual-Transport" Architecture

The Ricoh GR protocol isn't just "Bluetooth" or "Wi-Fi"—it's a choreographed dance between the two.
Understanding this state machine is critical to a stable implementation.

1. **BLE (The "Control Plane"):** Used for low-power discovery, persistent pairing, authentication (
   bonding), "waking" the camera, battery monitoring, GPS coordinate transmission, and triggering
   the remote shutter. It's the lifeline. If BLE drops, the session is dead.
2. **Wi-Fi (The "Data Plane"):** Used for heavy lifting: listing 1000s of photos, transferring RAW
   files, WebSocket-based Live View status, Image Control syncing, camera logs, and Firmware
   updates.

**The "Wi-Fi Handoff":** The app maintains a persistent BLE bond. When the user taps "Import
Photos", the app doesn't just connect to Wi-Fi; it uses BLE to *negotiate* the Wi-Fi credentials,
then programmatically binds the Android network stack to the camera's Access Point (AP), ensuring
traffic goes to the camera and not 4G/5G.

---

## 2. Data Models & Constants

### 2.1. BLE UUIDs

#### 2.1.1. Primary Service UUID (Discovery Filter)

Only one Service UUID matters for discovery:

* **Service:** `ef437870-180a-44ae-affd-99dbd85ade43` (The "Ricoh Service")

#### 2.1.2. Core Characteristics

| UUID                                   | Name             | Operations      | Purpose                                      |
|:---------------------------------------|:-----------------|:----------------|:---------------------------------------------|
| `0F291746-0C80-4726-87A7-3C501FD3B4B6` | Handshake/Notify | Subscribe, Read | "Step 4" liveness check - critical handshake |
| `5f0a7ba9-ae46-4645-abac-58ab2a1f4fe4` | Wi-Fi Config     | Read            | SSID/Password credential exchange            |
| `A3C51525-DE3E-4777-A1C2-699E28736FCF` | Command/Status   | Write, Notify   | Camera control commands, WLAN on/off         |
| `FE3A32F8-A189-42DE-A391-BC81AE4DAA76` | Battery/Info     | Read, Notify    | Battery level, camera info                   |

#### 2.1.3. Extended Characteristics (Full List)

These UUIDs appear in the protocol and may be used for specific features:

| UUID                                   | Suspected Purpose                       |
|:---------------------------------------|:----------------------------------------|
| `84A0DD62-E8AA-4D0F-91DB-819B6724C69E` | Unknown (pairing related)               |
| `28F59D60-8B8E-4FCD-A81F-61BDB46595A9` | Unknown (pairing related)               |
| `A0C10148-8865-4470-9631-8F36D79A41A5` | Unknown                                 |
| `A36AFDCF-6B67-4046-9BE7-28FB67DBC071` | Unknown                                 |
| `e450ed9b-dd61-43f2-bdfb-6af500c910c3` | Unknown                                 |
| `B58CE84C-0666-4DE9-BEC8-2D27B27B3211` | Unknown                                 |
| `BD6725FC-5D16-496A-A48A-F784594C8ECB` | Unknown                                 |
| `D9AE1C06-447D-4DEA-8B7D-FC8B19C2CDAE` | Unknown                                 |
| `1452335A-EC7F-4877-B8AB-0F72E18BB295` | Unknown                                 |
| `875FC41D-4980-434C-A653-FD4A4D4410C4` | Unknown                                 |
| `FA46BBDD-8A8F-4796-8CF3-AA58949B130A` | Unknown                                 |
| `430B80A3-CC2E-4EC2-AACD-08610281FF38` | Unknown                                 |
| `39879aac-0af6-44b5-afbb-ca50053fa575` | Unknown                                 |
| `B4EB8905-7411-40A6-A367-2834C2157EA7` | Unknown                                 |
| `97E34DA2-2E1A-405B-B80D-F8F0AA9CC51C` | Unknown                                 |
| `35FE6272-6AA5-44D9-88E1-F09427F51A71` | Unknown                                 |
| `F5666A48-6A74-40AE-A817-3C9B3EFB59A6` | Unknown                                 |
| `6FE9D605-3122-4FCE-A0AE-FD9BC08FF879` | Unknown                                 |
| `0D2FC4D5-5CB3-4CDE-B519-445E599957D8` | Unknown                                 |
| `460828AC-94EB-4EDF-9BB0-D31E75F2B165` | Unknown                                 |
| `C4B7DFC0-80FD-4223-B132-B7D25A59CF85` | Unknown                                 |
| `0F38279C-FE9E-461B-8596-81287E8C9A81` | Unknown                                 |
| `9111CDD0-9F01-45C4-A2D4-E09E8FB0424D` | Unknown                                 |
| `90638E5A-E77D-409D-B550-78F7E1CA5AB4` | Unknown                                 |
| `63BC8463-228F-4698-B30D-FAF8E3D7BD88` | Unknown                                 |
| `3e0673e0-1c7b-4f97-8ca6-5c2c8bc56680` | Unknown                                 |
| `78009238-AC3D-4370-9B6F-C9CE2F4E3CA8` | Unknown                                 |
| `e799198f-cf3f-4650-9373-b15dda1b618c` | Unknown                                 |
| `30adb439-1bc0-4b8e-9c8b-2bd1892ad6b0` | Unknown                                 |
| `559644B8-E0BC-4011-929B-5CF9199851E7` | Unknown                                 |
| `cd879e7a-ab9f-4c58-90ed-689bae67ef8e` | Unknown                                 |
| `B5589C08-B5FD-46F5-BE7D-AB1B8C074CAA` | Unknown                                 |
| `df77dd09-0a48-44aa-9664-2116d03b6fd7` | Unknown                                 |
| `B29E6DE3-1AEC-48C1-9D05-02CEA57CE664` | Unknown                                 |
| `0936b04c-7269-4cef-9f34-07217d40cc55` | Unknown                                 |
| `009A8E70-B306-4451-B943-7F54392EB971` | Unknown                                 |
| `F37F568F-9071-445D-A938-5441F2E82399` | Unknown                                 |
| `9A5ED1C5-74CC-4C50-B5B6-66A48E7CCFF1` | Unknown (appears twice, different case) |
| `9F00F387-8345-4BBC-8B92-B87B52E3091A` | Unknown                                 |
| `4B445988-CAA0-4DD3-941D-37B4F52ACA86` | Unknown                                 |

**Standard BLE Descriptor:**

* `00002902-0000-1000-8000-00805f9b34fb` - Client Characteristic Configuration Descriptor (CCCD) for
  enabling notifications

### 2.2. Camera Models

Identify the camera using the BLE Advertisement Name or the `/v1/props` HTTP endpoint.

| Model ID  | BLE Name Pattern            | Display Name     | Min FW | Notes                        |
|:----------|:----------------------------|:-----------------|:-------|:-----------------------------|
| `gr2`     | `RICOH GR II`               | GR II            | 1.10   | Original GR II               |
| `gr3`     | `RICOH GR III` (not "IIIx") | RICOH GR III     | 2.00   | 28mm equiv. lens             |
| `gr3Hdf`  | `RICOH GR III HDF`          | GR III HDF       | 2.00   | HDF variant                  |
| `gr3x`    | `RICOH GR IIIx`             | RICOH GR IIIx    | 1.50   | 40mm equiv. lens             |
| `gr3xHdf` | `RICOH GR IIIx HDF`         | GR IIIx HDF      | 1.50   | HDF variant                  |
| `gr4`     | `RICOH GR IV`               | RICOH GR IV      | 1.04*  | Latest gen, in-app FW update |
| `gr4Hdf`  | `RICOH GR IV HDF`           | GR IV HDF        | -      | HDF variant                  |
| `gr4Mono` | `RICOH GR IV Monochrome`    | GR IV Monochrome | -      | Monochrome sensor            |

*GR IV 1.04 required for Image Control feature

**Model Detection:**

- Parse BLE advertisement name to identify camera family
- HDF variants detected by "HDF" suffix
- Monochrome detected by "Monochrome" suffix

**Model-Specific Assets:** The app loads specific visual assets based on the model:

- Home icons: `gr2_home.png`, `gr3_home.png`, `gr4_home.png`, `gr4_mono_home.png`
- Device images: `gr2.png`, `gr3x_black.png`, `gr4.png`
- Pairing guides: `gr3_pairing_*.png`, `gr4_pairing_camera_*.png` (localized)

### 2.3. Connection Constants

| Parameter     | Value                         |
|:--------------|:------------------------------|
| **Camera IP** | `192.168.0.1` (Static AP IP)  |
| **HTTP Port** | `80`                          |
| **WebSocket** | `ws://192.168.0.1/v1/changes` |

---

## 3. Phase 1: BLE Discovery & Connection (The "Standby" State)

This is the default state of the app. It scans, bonds, and waits.

### 3.1. Scanning

**Library:** [Kable](https://github.com/JuulLabs/kable) `Scanner`
**Filter:** Service UUID `ef437870-180a-44ae-affd-99dbd85ade43`

**Scan Result Model:**

```dart
BleScanResultModel(
  advertiseResult: AdvertiseResult,
  name: String,           // e.g., "RICOH GR IIIx"
  remoteId: String,       // MAC address
  txPowerLevel: int       // Signal strength indicator
)
```

**BLE Device Detection UI State:**

```dart
BleDeviceDetectUiState(
  scanResults: List<BleScanResultModel>
)
```

**BLE Device Pairing UI State:**

```dart
BleDevicePairingUiState(
  name: String,
  // ... pairing progress
)
```

**The "Rich" Scan Result:**
Don't just show a MAC address. Parse the `advertisement.name`.

* *User-Friendly:* "RICOH GR IIIx"
* *Iconography:* Load specific assets based on the model string.
* *Signal Strength:* Use `txPowerLevel` for connection quality indicator

### 3.2. Connection & The "Bonding Dance"

**Library:** Kable `Peripheral.connect(transport = Transport.Le)`

**The Trap:** Ricoh cameras are notoriously finicky about bonding.

1. **Connect:** `autoConnect = false` for first time, `true` for reconnections.
2. **Bond:**
    * Check `bondState`.
    * If `NotBonded`: Call `createBond()`. **STOP.** Do not read/write characteristics.
    * **Wait** for state `Bonding` -> `Bonded`.
    * **Sleep:** Wait ~100ms after bonded. If you rush, the camera drops the link.
3. **Discover Services:** Only *after* bonding is stable.

### 3.3. The "Step 4" Handshake (Crucial)

The official app logs mention specific step-based errors. This is the "liveness check". If this
fails, the camera isn't actually listening.

**Procedure:**

1. **Subscribe** to Characteristic `0F291746-0C80-4726-87A7-3C501FD3B4B6`.
2. **Wait** for the *first* notification.
3. **Timeout:** If you don't hear back in **5 seconds**, the connection is zombie. Disconnect and
   retry.
4. **Success:** The camera is now "Connected (Standby)".

**Step-Based Error Codes (for diagnostics):**
| Error Code | Meaning |
| :--- | :--- |
| `step0_o_camera_battery_low` | Camera battery too low to proceed |
| `step0_o_phone_battery_low` | Phone battery too low |
| `step0_o_storage_full` | Storage full |
| `step0_c_timeout_error` | Client-side timeout |
| `step0_c_network_error` | Network error |
| `step0_o_no_internet` | No internet (for cloud features) |
| `step0_o_unknown_error` | Unknown error |
| `step1_s_429_too_many_requests` | Cloud API rate limited |
| `step1_s_500_internal_server_error` | Cloud API server error |
| `step1_s_503_service_unavailable` | Cloud API unavailable |
| `step1_c_timeout_error` | Cloud API timeout |
| `step1_c_network_error` | Cloud API network error |
| `step1_o_no_internet` | No internet for cloud |
| `step1_o_unknown_error` | Unknown cloud error |
| `step2_ble_wlan_on_failure` | Failed to turn on camera WLAN via BLE |
| `step2_ble_connect_failure` | BLE connection failed |
| `step2_wlan_connect_failure` | WLAN connection failed |
| `step2_put_firmware_failure` | Firmware upload failed |
| `step3_ble_connect_failure` | BLE reconnection failed |
| `step4_ble_notify_not_received` | Handshake notification not received |

### 3.4. BLE Characteristic Operations

**Battery Level Characteristic:** `FE3A32F8-A189-42DE-A391-BC81AE4DAA76`

- **Read:** Get current battery level
- **Notify:** Subscribe for battery level changes
- **Model:** `BatteryLevelModel(level: int)` - 0-100 percentage

**Camera Power Operations:**

- Write to command characteristic to control power
- Log: "write camera power: ..."
- Error: "cameraPowerOff Error"

**Remote Shutter (BLE):**
The remote shutter can be triggered via BLE even without WiFi connection:

- Uses command characteristic
- Receives capture status notifications

---

## 4. Phase 2: The Wi-Fi Handoff (The "Transfer" Mode)

User taps "Import Photos" or "Remote Shutter". You need high bandwidth.

### 4.1. Enabling Camera WLAN (BLE Command)

Before connecting to Wi-Fi, you may need to turn on the camera's WLAN function via BLE:

**Command Characteristic:** `A3C51525-DE3E-4777-A1C2-699E28736FCF`

**WLAN Control Command Model:**

```dart
WLANControlCommandModel(
  networkType: String,  // "wifi"
  wlanFreq: int         // 0=2.4GHz, 1=5GHz
)
```

**Write Operation:**

1. Serialize command to bytes
2. Write to characteristic
3. Wait for notification confirming WLAN enabled

**Error Cases:**

- `step2_ble_wlan_on_failure` - Failed to enable WLAN
- WLAN cannot be enabled if camera is in Movie mode
- WLAN cannot be enabled if camera is connected via USB

**Related Logs:**

- "write wlanFrequency"
- "write wifiInfo: ..."

### 4.2. Getting Credentials (BLE)

You need the SSID and Password.

**Wi-Fi Config Characteristic:** `5f0a7ba9-ae46-4645-abac-58ab2a1f4fe4`

**Read Operation:**

1. Read characteristic value
2. Parse response for SSID and password

**Response Format (presumed):**
The characteristic likely returns WiFi credentials in a structured format. Based on the `/v1/props`
endpoint which returns:

```json
{
  "ssid": "RICOH_1234",
  "key": "password123"
}
```

**Fallback Strategy:**

1. If BLE read fails, use HTTP `/v1/props` endpoint (requires existing connection)
2. Standard SSIDs follow pattern: `RICOH_[SerialSegment]`
3. Password can be found in camera menu: Settings → Wi-Fi Info

**Related Logs:**

- "write wifiInfo: ..."

### 4.3. WLAN Frequency Settings

The camera supports different frequency bands:

* **2.4 GHz** - Better compatibility, longer range
* **5 GHz** - Faster speeds, less interference

The `wlan_frequency` parameter is stored in the device database and can be changed via camera
settings.

### 4.4. Android Network Binding

**Library:** `ConnectivityManager`, `WifiNetworkSpecifier`

**The "Pre-Check":**
Before firing up the heavy network requester, try a `GET http://192.168.0.1/v1/props` (Timeout: 3s).
If it returns 200 OK, *you are already connected*. Save 10 seconds of user time.

**The "Request":**

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid(ssid)
    .setWpa2Passphrase(password) // Note: Retry with Wpa3Passphrase if this fails!
    .build()
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)     // = 1
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // = 12, forces traffic to local AP
    .setNetworkSpecifier(specifier)
    .build()

// Request network with 60-second timeout
connectivityManager.requestNetwork(request, networkCallback)
handler.postDelayed(timeoutRunnable, 60000L)
```

**The "WPA3 Dance":**
The official app explicitly handles a "WPA3 Transition Mode" issue.

1. Try connection with `securityType` (WPA2 or WPA3).
2. If `onUnavailable` fires, **retry immediately** with the *other* security type.

### 4.5. Verification

Once `onAvailable` fires, **bind the process** (
`connectivityManager.bindProcessToNetwork(network)`). Then, verify the target:

`GET http://192.168.0.1/v1/props`

```json
{
  "errCode": 200,
  "model": "RICOH GR III",
  "serialNo": "12345678",
  "ssid": "RICOH_1234",
  "firmwareVersion": "1.50"
}
```

*Check:* Does `model` contain "RICOH"? If not, you connected to a rogue AP. Abort.

---

## 5. Phase 3: High-Bandwidth Features (HTTP/WebSocket)

### 5.1. HTTP API Endpoints

**Base URL:** `http://192.168.0.1`

#### 5.1.1. Camera Information & Control

| Method | Endpoint            | Purpose                                           | Timeout                   |
|:-------|:--------------------|:--------------------------------------------------|:--------------------------|
| `GET`  | `/v1/props`         | Camera properties (model, serial, SSID, firmware) | 3s (verify), 10s (normal) |
| `GET`  | `/v1/status/device` | Device status (power state, etc.)                 | 10s                       |
| `POST` | `/v1/device/finish` | End session/disconnect (empty body)               | 10s                       |
| `PUT`  | `/v1/params/device` | Set device parameters (WiFi config)               | 10s                       |

**`/v1/props` Response:**

```json
{
  "errCode": 200,
  "model": "RICOH GR III",
  "serialNo": "12345678901234",
  "ssid": "RICOH_1234",
  "key": "password123",
  "bdName": "RICOH GR III",
  "firmwareVersion": "1.50"
}
```

| Field             | Type   | Description                                              |
|:------------------|:-------|:---------------------------------------------------------|
| `errCode`         | int    | 200 = success                                            |
| `model`           | string | Camera model name (e.g., "RICOH GR III")                 |
| `serialNo`        | string | Full serial number (14 chars, first 8 used for matching) |
| `ssid`            | string | WiFi SSID for camera AP                                  |
| `key`             | string | WiFi password                                            |
| `bdName`          | string | Bluetooth device name                                    |
| `firmwareVersion` | string | Firmware version (e.g., "1.50")                          |

**`/v1/params/device` Body (WiFi Configuration):**

```
Content-Type: application/json

channel=0&ssid=RICOH_1234&key=password123
```

#### 5.1.2. Photo Operations

| Method | Endpoint                             | Purpose                     | Timeout |
|:-------|:-------------------------------------|:----------------------------|:--------|
| `GET`  | `/v1/photos/infos?storage=in&after=` | List photos with pagination | 10s     |
| `GET`  | `/v1/photos/<path>?size=thumb`       | Get thumbnail               | 15s     |
| `GET`  | `/v1/photos/<path>?size=view`        | Get preview/view size       | 15s     |
| `GET`  | `/v1/photos/<path>?size=xs`          | Get extra-small preview     | 15s     |
| `GET`  | `/v1/photos/<path>?storage=in`       | Download full image         | 120s    |
| `GET`  | `/v1/photos/<path>/info?storage=in`  | Get single photo info       | 10s     |
| `PUT`  | `/v1/photos/<path>/transfer`         | Prepare file for transfer   | 10s     |
| `GET`  | `/v1/transfers?status=&storage=`     | List transfer queue status  | 10s     |

**Storage Parameter Values:**

- `storage=in` - Internal storage
- `storage=sd1` - SD card slot 1

**`/v1/photos/infos` Response:**

```json
{
  "errCode": 200,
  "dirs": [
    "100RICOH",
    "101RICOH"
  ],
  "files": [
    {
      "memory": 0,
      "dir": "100RICOH",
      "file": "R0001234.DNG",
      "size": 25165824,
      "recorded_size": "24.0MB",
      "datetime": 1704067200,
      "recorded_time": "2024-01-01 12:00:00",
      "orientation": 1,
      "aspect_ratio": "3:2",
      "av": "F2.8",
      "tv": "1/250",
      "sv": "ISO200",
      "xv": "+0.3",
      "lat_lng": "35.6762,139.6503",
      "gps_info": "{...}"
    }
  ]
}
```

| Field           | Type   | Description                                  |
|:----------------|:-------|:---------------------------------------------|
| `memory`        | int    | Storage location (0 = internal, 1 = SD card) |
| `dir`           | string | Directory name (e.g., "100RICOH")            |
| `file`          | string | Filename (e.g., "R0001234.DNG")              |
| `size`          | int    | File size in bytes                           |
| `recorded_size` | string | Human-readable file size                     |
| `datetime`      | int    | Unix timestamp                               |
| `recorded_time` | string | Formatted datetime string                    |
| `orientation`   | int    | EXIF orientation (1-8)                       |
| `aspect_ratio`  | string | "1:1", "3:2", "4:3", "16:9"                  |
| `av`            | string | Aperture value (e.g., "F2.8")                |
| `tv`            | string | Shutter speed (e.g., "1/250")                |
| `sv`            | string | ISO sensitivity (e.g., "ISO200")             |
| `xv`            | string | Exposure compensation (e.g., "+0.3")         |
| `lat_lng`       | string | GPS coordinates "lat,lng"                    |
| `gps_info`      | string | Full GPS info JSON                           |

**Transfer Status Response (`/v1/transfers`):**

```json
{
  "errCode": 200,
  "transfers": [
    {
      "filepath": "/100RICOH/R0001234.DNG",
      "status": "transferred"
    }
  ]
}
```

| Status          | Meaning                  |
|:----------------|:-------------------------|
| `transferred`   | File transfer complete   |
| `untransferred` | File not yet transferred |

**File Transfer Models:**

**FileTransferListModel:**

```dart
FileTransferListModel(isNotEmpty: bool)
```

**PowerOffDuringFileTransferModel:**

```dart
PowerOffDuringFileTransferModel(behavior: PowerOffBehavior)
```

- Controls camera behavior if power-off requested during transfer
- Error: "PowerOffDuringFileTransferNotify error: ..."

**Transfer PUT Body (`/v1/photos/<path>/transfer`):**

```
Content-Type: application/json

storage=in
```

**Aspect Ratios:**

- `1:1` - Square crop
- `3:2` - Native sensor ratio
- `4:3` - Compact camera ratio
- `16:9` - Widescreen

#### 5.1.3. Remote Shooting

| Method     | Endpoint                | Purpose                     |
|:-----------|:------------------------|:----------------------------|
| `POST`     | `/v1/camera/shoot`      | Trigger shutter             |
| `GET/POST` | `/v1/photos?storage=in` | Photo capture/list endpoint |

#### 5.1.4. Image Control

| Method | Endpoint              | Purpose                            |
|:-------|:----------------------|:-----------------------------------|
| `GET`  | `/imgctrl?storage=in` | Get Image Control data from camera |

#### 5.1.5. Camera Logs (GR Log Feature)

| Method | Endpoint                                            | Purpose                    |
|:-------|:----------------------------------------------------|:---------------------------|
| `GET`  | `/v1/logs/camera?type=monthly_capture&date=YYYY-MM` | Get monthly shooting stats |

**Log Data Includes:**

- `still_count`, `video_count` - Shot counts
- `exposure_p`, `exposure_tv`, `exposure_av`, `exposure_m`, `exposure_b`, `exposure_t`,
  `exposure_bt`, `exposure_sfp` - Shots per exposure mode
- `effect_*` - Shots per Image Control preset
- `aspect_ratio_*` - Shots per aspect ratio

#### 5.1.6. Firmware Operations

| Method | Endpoint                                      | Purpose                              |
|:-------|:----------------------------------------------|:-------------------------------------|
| `GET`  | `/v1/configs/firmware/prepare`                | Prepare for firmware update          |
| `PUT`  | `/v1/configs/firmware`                        | Upload firmware binary (stream body) |
| `GET`  | `/v1/configs/firmware?storage=in&reboot=true` | Apply firmware and reboot            |
| `GET`  | `/v1/configs/firmware/cancel`                 | Cancel firmware update               |

**Firmware Update Flow:**

1. **Check Version:** Call cloud API `/v1/getInformation` to get latest versions
2. **Compare:** Compare with camera's `firmwareVersion` from `/v1/props`
3. **Download:** If update needed, download from `/v1/fwDownload`
4. **Prepare:** Call `/v1/configs/firmware/prepare` on camera
5. **Upload:** Stream firmware binary to `PUT /v1/configs/firmware`
6. **Apply:** Call `/v1/configs/firmware?storage=in&reboot=true`
7. **Wait:** Camera reboots and applies update

**FirmwareUpdateModel:**

```dart
FirmwareUpdateModel(
  code: int,
  result: FirmwareUpdateResult
)
```

**FirmwareUpdateInfo:**

```dart
FirmwareUpdateInfo(
  version: String,
  targetFirmwareVersion: String
)
```

**FirmwareUpdateCheckData:**

```dart
FirmwareUpdateCheckData(
  batteryLevel: int,
  cameraBatteryLevel: int
)
```

**Pre-Update Checks:**

- Camera battery sufficient
- Phone battery sufficient
- Phone storage space available
- Camera not in shooting mode

### 5.2. WebSocket: Real-Time Status

**Endpoint:** `ws://192.168.0.1/v1/changes`

**Connection:**

```
GET /v1/changes HTTP/1.1
Host: 192.168.0.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: <base64-key>
Sec-WebSocket-Version: 13
```

**The Flow:**

1. **Connect:** Open the WebSocket.
2. **Listen:** The camera pushes JSON state updates whenever state changes.
3. **No client messages required:** This is a receive-only channel for status updates.

**Status Payload Structure:**

```json
{
  "captureStatus": "idle",
  "storageStatus": "ready",
  "shootingMode": "still",
  "captureMode": "single",
  "driveMode": "single",
  "selfTimerCountdown": 0,
  "batteryLevel": 75
}
```

**Capture Status Values:**
| Value | Meaning | UI Action |
| :--- | :--- | :--- |
| `idle` | Ready to shoot | Enable Shutter Button |
| `capture` | Currently capturing | Disable Shutter Button, show spinner |

**Shooting Mode Values:**
| Value | Meaning |
| :--- | :--- |
| `still` | Still image mode |
| `movie` | Movie/video mode |

**Capture Mode Values:**
| Value | Meaning |
| :--- | :--- |
| `single` | Single shot |
| `continuous` | Continuous shooting |
| `interval` | Interval timer |
| `multiExposure` | Multiple exposure |

**Drive Mode Values:**
| Value | Asset | Description |
| :--- | :--- | :--- |
| `single` | `drive_single.png` | Single shot |
| `continuous` | `drive_continuous.png` | Continuous shooting |
| `auto_bkt` | `drive_auto_bkt.png` | Auto bracket |
| `multi_exp` | `drive_multi_exp.png` | Multiple exposure |
| `interval` | `drive_interval.png` | Interval timer |
| `multi_exp_interval` | `drive_multi_exp_interval.png` | Multi-exp + interval |

**Storage Status Values:**
| Value | Meaning |
| :--- | :--- |
| `ready` | Storage media ready |
| (other) | Check media - "Please make sure the storage media is ready for use" |

**Self Timer Countdown:**

- `selfTimerCountdown`: int - Seconds remaining (0 when not active)
- Related frames: `intervalFrame`, `intervalTenSecondFrame`, `intervalTwoSecondFrame`,
  `intervalCompositionFrame`

### 5.3. Exposure Modes

The camera supports these exposure modes (shown in remote shutter UI):

| Mode      | Internal ID | Asset                   | Description                            |
|:----------|:------------|:------------------------|:---------------------------------------|
| Program   | `p`         | `exp_mode_program.png`  | Auto exposure                          |
| Av        | `av`        | `exp_mode_av.png`       | Aperture priority                      |
| Tv        | `tv`        | `exp_mode_tv.png`       | Shutter priority                       |
| Manual    | `m`         | `exp_mode_manual.png`   | Full manual                            |
| Bulb      | `b`         | `exp_mode_bulb.png`     | Long exposure (manual shutter release) |
| Time      | `t`         | `exp_mode_time.png`     | Long exposure (timed)                  |
| Bulb/Time | `bt`        | `exp_mode_bulbtime.png` | Combined bulb-time mode (Bulb Timer)   |
| Snap FP   | `sfp`       | `exp_mode_snap.png`     | Snap Focus Program                     |

**ExposureMode EXIF Field:**
The EXIF tag `ExposureMode` (34850) contains the exposure program:

- 1 = Manual
- 2 = Normal program
- 3 = Aperture priority
- 4 = Shutter priority

### 5.4. Drive Modes

| Mode               | Internal ID          | Asset                          | Description                        |
|:-------------------|:---------------------|:-------------------------------|:-----------------------------------|
| Single             | `single`             | `drive_single.png`             | Single shot                        |
| Continuous         | `continuous`         | `drive_continuous.png`         | Continuous shooting                |
| Auto Bracket       | `auto_bkt`           | `drive_auto_bkt.png`           | Exposure bracketing                |
| Multi-exposure     | `multi_exp`          | `drive_multi_exp.png`          | Multiple exposures combined        |
| Interval           | `interval`           | `drive_interval.png`           | Interval timer shooting            |
| Multi-exp Interval | `multi_exp_interval` | `drive_multi_exp_interval.png` | Combined interval + multi-exposure |

### 5.5. Self-Timer

**SelfTimer Enum Values:**
| Value | Description |
| :--- | :--- |
| `off` | No self-timer (`timer_off.svg`) |
| `2sec` | 2-second delay |
| `10sec` | 10-second delay |

**Timer Frames (for composition modes):**

- `intervalFrame` - Basic interval
- `intervalTenSecondFrame` - 10-second interval
- `intervalTwoSecondFrame` - 2-second interval
- `intervalCompositionFrame` - Interval composition
- `intervalCompositionTenSecondFrame` - 10-second composition
- `intervalCompositionTwoSecondFrame` - 2-second composition

**Timer State:**

- `timerDuration`: Duration in seconds
- `selfTimerCountdown`: Current countdown value (from WebSocket)

---

## 6. Image Control Feature

Image Controls are camera picture profiles/presets that can be synced between camera and app.

### 6.1. Built-in Presets

| Preset        | Asset               |
|:--------------|:--------------------|
| Standard      | `standard.png`      |
| Vivid         | `vivid.png`         |
| Monotone      | `monotone.png`      |
| Soft Monotone | `soft_monotone.png` |
| Hard Monotone | `hard_monotone.png` |
| Hi-Contrast   | `hi_contrast.png`   |
| Positive Film | `posi_film.png`     |
| Negative Film | `nega_film.png`     |
| Bleach Bypass | `bleach_bypass.png` |
| Retro         | `retro.png`         |
| HDR Tone      | `hdr.png`           |
| Cross Process | `cross_process.png` |
| Cinema Yellow | `cinema_yellow.png` |
| Cinema Green  | `cinema_green.png`  |
| Grainy        | `grainy.png`        |
| Hi            | `hi.png`            |
| Soft          | `soft.png`          |
| Solid         | `solid.png`         |

**GR IV Monochrome-specific:**
| Preset | Description |
| :--- | :--- |
| Mono Standard | Standard monochrome |
| Mono Soft | Soft monochrome |
| Mono High-Contrast | High contrast monochrome |
| Mono Grainy | Grainy film look |
| Mono Solid | Solid blacks |
| Mono HDR Tone | HDR-style monochrome |

### 6.2. Image Control Parameters

These parameters can be adjusted for each preset:

| Parameter                | Asset                        | Description          |
|:-------------------------|:-----------------------------|:---------------------|
| Saturation               | `saturation.png`             | Color intensity      |
| Hue                      | `hue.png`                    | Color shift          |
| Key                      | `key.png`                    | Overall brightness   |
| Contrast                 | `contrast.png`               | Overall contrast     |
| Contrast (Highlight)     | `contrast_highlight.png`     | Highlight contrast   |
| Contrast (Shadow)        | `contrast_shadow.png`        | Shadow contrast      |
| Sharpness                | `sharpness.png`              | Edge sharpness       |
| Shading                  | `shading.png`                | Vignette effect      |
| Clarity                  | `clarity.png`                | Local contrast       |
| Toning                   | `toning.png`                 | Color toning         |
| Toning (Monotone)        | `toning_monotone.png`        | Sepia/toning for B&W |
| Filter Effect (Monotone) | `filter_effect_monotone.png` | Color filter for B&W |
| Granular                 | `granular.png`               | Film grain           |
| Granular Size            | `granular_size.png`          | Grain size           |
| Granular Strength        | `granular_strength.png`      | Grain intensity      |
| HDR Tone                 | `hdrtone.png`                | HDR intensity        |

### 6.3. Image Control Storage (Local Database)

```sql
CREATE TABLE image_control(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  raw BLOB NOT NULL,              -- Raw binary data
  name TEXT NOT NULL,
  thumb_path TEXT NOT NULL,
  view_path TEXT NOT NULL,
  image_control_type INTEGER NOT NULL,
  image_control_camera_type INTEGER NOT NULL,
  custom_image_id INTEGER NOT NULL,
  custom_image_version INTEGER NOT NULL,
  is_favorite INTEGER NOT NULL,
  is_gr_gallery INTEGER NOT NULL,
  aspect_ratio TEXT DEFAULT "4:3",
  orientation INTEGER DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### 6.4. Applying Image Control to Camera

1. Camera must be in still image mode (not movie mode)
2. Use the `/imageControlWrite` endpoint/flow
3. Can write to Custom 1, Custom 2, or Custom 3 slots
4. **Warning:** "Please do not operate the camera's mode dial while the Image Control is being set."

**GR IV Firmware Requirement:**
Image Control setting requires GR IV firmware version 1.04 or later.

---

## 7. GPS/Location Transmission

The app can transmit smartphone GPS coordinates to the camera for geotagging photos.

### 7.1. GeoTag Model

```dart
GeoTagModel(
  isEnabled: bool,
  isLocationExisted: bool,
  isLocationRecordEnabled: bool
)
```

### 7.2. Location Settings

| Setting                 | Internal Key      | Description                               |
|:------------------------|:------------------|:------------------------------------------|
| Location Transmission   | `isEnabled`       | Enable/disable GPS transmission           |
| Frequency               | `locationFreqNum` | "High" (accurate) or "Low" (power-saving) |
| Background Transmission | `bgLocationLimit` | Continue when app backgrounded            |
| Background Time Limit   | -                 | Auto-stop after set duration              |

**LocationFreqButtonState:**

- `high` - Accurate location, higher battery consumption
- `low` - Power-saving mode, less accurate

**BgLocationLimitButtonState:**

- Configurable timeout for background transmission

### 7.3. GPS Data Fields (EXIF)

The following GPS fields are written to image EXIF (IFD GPS):

| Tag    | Name            | Description              |
|:-------|:----------------|:-------------------------|
| 0x0000 | GPSVersionID    | GPS tag version          |
| 0x0001 | GPSLatitudeRef  | "N" or "S"               |
| 0x0002 | GPSLatitude     | Latitude in degrees      |
| 0x0003 | GPSLongitudeRef | "E" or "W"               |
| 0x0004 | GPSLongitude    | Longitude in degrees     |
| 0x0005 | GPSAltitudeRef  | 0 = above sea level      |
| 0x0006 | GPSAltitude     | Altitude in meters       |
| 0x0007 | GPSTimeStamp    | UTC time                 |
| 0x0008 | GPSSatellites   | Satellites used          |
| 0x0009 | GPSStatus       | "A" = measurement active |
| 0x000A | GPSMeasureMode  | 2D or 3D                 |
| 0x000B | GPSDOP          | Precision                |
| 0x001D | GPSDate         | UTC date                 |

### 7.4. Location Transmission Protocol

Location data is transmitted via BLE to maintain low-latency updates even when Wi-Fi is not
connected.

**Transmission Flow:**

1. App acquires GPS fix from smartphone
2. Coordinates serialized and written to BLE characteristic
3. Camera stores location for next shot
4. Location embedded in EXIF when photo taken

**Messages:**

- "The app is acquiring location information."
- "Location information transmission is complete."

### 7.5. Permissions Required

Android permissions needed:

- `location` - Basic location
- `locationAlways` - Background location
- `locationWhenInUse` - Foreground location
- `android_uses_fine_location` - High accuracy GPS

---

## 8. Battery Monitoring

### 8.1. Battery Level States

| State   | Asset                 | Description         |
|:--------|:----------------------|:--------------------|
| Full    | `battery_full.svg`    | Full charge         |
| Half    | `battery_half.svg`    | ~50% charge         |
| Low     | `battery_low.svg`     | Low battery warning |
| Empty   | `battery_empty.svg`   | Critical battery    |
| Unknown | `battery_unknown.svg` | Cannot read battery |

### 8.2. Battery-Related Constraints

- Firmware update requires sufficient battery level
- File transfer may be blocked on low battery
- Error: `step0_o_camera_battery_low`

---

## 9. Cloud Services (AWS)

**Base URL:** `https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com`

**Required Headers (All Endpoints):**

```http
x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera
x-secret-key: CySfvt88t8
Content-Type: application/json
```

**Common Request Body Fields (Phone Telemetry):**
Most endpoints require device information in the request body:

```json
{
  "phone_model": "Device Name",
  "phone_os": "Android",
  "phone_os_version": "14",
  "phone_language": "en",
  "phone_app_ver": "1.0.0"
}
```

### 9.1. App Information & Notifications

**Endpoint:** `POST /v1/getInformation`

**Full cURL Example:**

```bash
curl -X POST "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/getInformation" \
  -H "x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera" \
  -H "x-secret-key: CySfvt88t8" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_model": "Pixel 8",
    "phone_os": "Android",
    "phone_os_version": "14",
    "phone_language": "en",
    "phone_app_ver": "1.0.0"
  }'
```

**Actual Response (trimmed):**

```json
{
  "informationList": {
    "ja": [
      /* Japanese notifications */
    ],
    "en": [
      {
        "No": "1",
        "Title": "GR WORLD V1.2.0 is released.",
        "Date": "2026-01-13",
        "Message": "<Enhanced features>\n• RICOH GR IV HDF is now supported.\n• You can check notifications in App Settings.\n• GR Gallery added to Home screen...\n\n<GR IV only>\n• You can update the camera firmware from the app.\n• You can save image controls from images on your camera..."
      },
      {
        "No": "2",
        "Title": "The Android version of GR WORLD V1.2.1 is released.",
        "Date": "2026-01-22",
        "Message": "<Improved Contents>\n• Bug fixed about view."
      }
    ],
    "fr": [
      /* French */
    ],
    "de": [
      /* German */
    ],
    "ko": [
      /* Korean */
    ],
    "zh-TW": [
      /* Traditional Chinese */
    ],
    "zh-CN": [
      /* Simplified Chinese */
    ]
  }
}
```

**Supported Languages:** `ja`, `en`, `fr`, `de`, `ko`, `zh-TW`, `zh-CN`

**Minimum Firmware Requirements:**

- GR IV Image Control: Version 1.04 or later

### 9.2. Firmware Version Check & Download

**Endpoint:** `POST /v1/fwDownload`

This single endpoint serves two purposes:

1. **Check latest version:** Omit `target_firmware_version` to get the latest available version
2. **Download specific version:** Include `target_firmware_version` to get that specific version

#### Step 1: Check Latest Firmware Version

**cURL Example (check latest):**

```bash
curl -X POST "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/fwDownload" \
  -H "x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera" \
  -H "x-secret-key: CySfvt88t8" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_model": "Pixel 8",
    "phone_os": "Android",
    "phone_os_version": "14",
    "phone_language": "en",
    "phone_app_ver": "1.0.0",
    "model": "gr4"
  }'
```

**Actual Response (latest version):**

```json
{
  "version": "1.04",
  "presignedUrl": "https://rim-grapp-firmware-prod.s3-accelerate.amazonaws.com/eg-1/eg-1_v104.zip?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
  "expirationDate": "2026-02-04T05:45:40.590Z"
}
```

#### Step 2: Compare with Camera Version

1. Get camera's current firmware from HTTP `/v1/props` endpoint (field: `firmwareVersion`)
2. Compare with `version` from cloud response
3. If cloud version > camera version → update available

#### Step 3: Download Firmware (if needed)

**cURL Example (download specific version):**

```bash
curl -X POST "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/fwDownload" \
  -H "x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera" \
  -H "x-secret-key: CySfvt88t8" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_model": "Pixel 8",
    "phone_os": "Android",
    "phone_os_version": "14",
    "phone_language": "en",
    "phone_app_ver": "1.0.0",
    "model": "gr4",
    "target_firmware_version": "1.04"
  }'
```

**Then download the actual firmware file:**

```bash
curl -o firmware.zip "https://rim-grapp-firmware-prod.s3-accelerate.amazonaws.com/eg-1/eg-1_v104.zip?..."
```

#### Supported Models for In-App Firmware Update

**Important:** Only GR IV currently supports in-app firmware updates.

| Camera      | Model ID  | Display Name       | Suffix | In-App FW Update | Internal FW Name |
|:------------|:----------|:-------------------|:-------|:-----------------|:-----------------|
| GR II       | `gr2`     | "GR II"            | -      | No               | -                |
| GR III      | `gr3`     | "RICOH GR III"     | -      | No               | -                |
| GR III HDF  | `gr3Hdf`  | "GR III HDF"       | -      | No               | -                |
| GR IIIx     | `gr3x`    | "RICOH GR IIIx"    | -      | No               | -                |
| GR IIIx HDF | `gr3xHdf` | "GR IIIx HDF"      | -      | No               | -                |
| **GR IV**   | `gr4`     | "RICOH GR IV"      | -      | **Yes**          | `eg-1`           |
| GR IV HDF   | `gr4Hdf`  | "GR IV HDF"        | `_h`   | No (not yet)     | -                |
| GR IV Mono  | `gr4Mono` | "GR IV Monochrome" | `_m`   | No (not yet)     | -                |

**BLE Advertisement Name Patterns:**

- "RICOH GR IV" or "RICOH GR IV HDF" → model code for GR IV family
- "RICOH GR III" or "RICOH GR III HDF" → model code for GR III family
- "RICOH GR IIIx" or "RICOH GR IIIx HDF" → model code for GR IIIx family

**Error Response (unsupported model):**

```json
{
  "errorCode": "E001-002",
  "errorMessage": "指定の機種は登録されていません。"
}
```

Translation: "The specified model is not registered."

#### Minimum App-Compatible Firmware Versions (Hardcoded)

The app has minimum firmware versions hardcoded for registration:

- **GR III:** Ver. 2.00 or later
- **GR IIIx:** Ver. 1.50 or later
- **GR IV (Image Control):** Ver. 1.04 or later

#### Response Fields

| Field            | Type   | Description                        |
|:-----------------|:-------|:-----------------------------------|
| `version`        | string | Firmware version (e.g., "1.04")    |
| `presignedUrl`   | string | S3 presigned URL for download      |
| `expirationDate` | string | URL expiration (ISO 8601, ~1 hour) |

### 9.3. GR Gallery (Photographer Presets)

**List Photographer Albums:**
**Endpoint:** `POST /v1/album/present/getList`

**Full cURL Example:**

```bash
curl -X POST "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/album/present/getList" \
  -H "x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera" \
  -H "x-secret-key: CySfvt88t8" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_model": "Pixel 8",
    "phone_os": "Android",
    "phone_os_version": "14",
    "phone_language": "en",
    "phone_app_ver": "1.0.0"
  }'
```

**Actual Response:**

```json
{
  "list": [
    {
      "No": "1",
      "displayFlag": "1",
      "photographerName": "大門美奈／Mina Daimon",
      "albumId": "MinaDaimon_1",
      "albumTitle": "GR IV",
      "expireDate": "2030-01-01",
      "albumJacketPath": "https://dxlmtdbo51.execute-api.ap-northeast-1.amazonaws.com/v1/rim-grapp-album-present-prod/MinaDaimon_1/R0000911.JPG",
      "photographerIconPath": "https://dxlmtdbo51.execute-api.ap-northeast-1.amazonaws.com/v1/rim-grapp-album-present-prod/MinaDaimon_1/大門美奈／Mina Daimon.jpeg"
    }
  ]
}
```

**Get Album Image Controls:**
**Endpoint:** `POST /v1/album/present/getAlbumData`

**Full cURL Example:**

```bash
curl -X POST "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/album/present/getAlbumData" \
  -H "x-api-key: B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera" \
  -H "x-secret-key: CySfvt88t8" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_model": "Pixel 8",
    "phone_os": "Android",
    "phone_os_version": "14",
    "phone_language": "en",
    "phone_app_ver": "1.0.0",
    "albumId": "MinaDaimon_1"
  }'
```

**Actual Response (trimmed to 3 items):**

```json
{
  "list": [
    {
      "No": "1",
      "displayNumber": "2",
      "displayFlag": "1",
      "viewPath": "https://dxlmtdbo51.execute-api.ap-northeast-1.amazonaws.com/v1/rim-grapp-album-present-prod/MinaDaimon_1/R0001207.JPG",
      "imageControlPath": "https://dxlmtdbo51.execute-api.ap-northeast-1.amazonaws.com/v1/rim-grapp-album-present-prod/MinaDaimon_1/R0001207.BIN",
      "imageControlName": "Negative Film",
      "cameraModel": "gr4",
      "aspectRatio": "3:2",
      "orientation": "1"
    },
    {
      "No": "2",
      "displayNumber": "3",
      "displayFlag": "1",
      "viewPath": "https://.../.../R0001205.JPG",
      "imageControlPath": "https://.../.../R0001205.BIN",
      "imageControlName": "Positive Film",
      "cameraModel": "gr4",
      "aspectRatio": "3:2",
      "orientation": "1"
    },
    {
      "No": "3",
      "displayNumber": "4",
      "displayFlag": "1",
      "viewPath": "https://.../.../R0001208.JPG",
      "imageControlPath": "https://.../.../R0001208.BIN",
      "imageControlName": "Positive Film",
      "cameraModel": "gr4",
      "aspectRatio": "3:2",
      "orientation": "1"
    }
  ]
}
```

**Image Control Names Found:**

- Standard, Vivid, Negative Film, Positive Film
- Retro, Hi-Contrast B&W, Cinema (Yellow), Cinema (Green)

**To download an Image Control preset (.BIN file):**

```bash
curl -o preset.bin "https://dxlmtdbo51.execute-api.ap-northeast-1.amazonaws.com/v1/rim-grapp-album-present-prod/MinaDaimon_1/R0001207.BIN"
```

### 9.4. Error Codes

| Code  | Meaning                                                     |
|:------|:------------------------------------------------------------|
| `200` | Success                                                     |
| `429` | Too Many Requests (`step1_s_429_too_many_requests`)         |
| `500` | Internal Server Error (`step1_s_500_internal_server_error`) |
| `503` | Service Unavailable (`step1_s_503_service_unavailable`)     |

---

## 10. App Data Models (State Management)

The app uses Riverpod-style state management with the following models and notifier providers:

### 10.1. Camera State Models

**CameraServiceModel:**

```dart
CameraServiceModel(
  cameraPower: CameraPowerModel,
  storageInformation: StorageInformationModel,
  powerOffDuringTransfer: PowerOffDuringFileTransferModel
)
```

**CameraPowerModel:**

```dart
CameraPowerModel(isPower: bool)
```

**BatteryLevelModel:**

```dart
BatteryLevelModel(level: int)  // 0-100 percentage
```

**StorageInformationModel:**

```dart
StorageInformationModel(
  type: StorageType,  // internal, sd1
  isLocationExisted: bool
)
```

### 10.2. Shooting State Models

**CaptureStatusModel:**

```dart
CaptureStatusModel(
  countdown: int,      // Self-timer countdown
  status: String       // "idle", "capture"
)
```

**ShootingModeModel:**

```dart
ShootingModeModel(mode: String)  // "still", "movie"
```

**CaptureModeModel:**

```dart
CaptureModeModel(mode: String)  // "single", "continuous", etc.
```

**DriveModeModel:**

```dart
DriveModeModel(driveMode: DriveMode)
// DriveMode: single, continuous, auto_bkt, multi_exp, interval, multi_exp_interval
```

**ExposureModel:**

```dart
ExposureModel(
  p: int,    // Program mode shots
  tv: int,   // Shutter priority shots
  av: int,   // Aperture priority shots
  m: int,    // Manual shots
  b: int,    // Bulb shots
  t: int,    // Time shots
  bt: int,   // Bulb-Time shots
  sfp: int   // Snap Focus Program shots
)
```

### 10.3. Device Models

**DeviceModel:**

```dart
DeviceModel(
  deviceInfo: DeviceInfoModel,
  cameraModel: String,
  cameraName: String,
  cameraType: CameraType
)
```

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

**CameraInformationServiceModel:**

```dart
CameraInformationServiceModel(
  manufacturerNameString: String,
  modelNumberString: String,
  batteryLevel: int,
  txPowerLevel: int
)
```

### 10.4. Photo Models

**PhotoInfoModel:**

```dart
PhotoInfoModel(
  id: int,
  cameraModel: String,
  serialNo: String,
  memory: int,
  dir: String,
  file: String,
  recordedSize: String,
  size: int,
  datetime: int,
  recordedTime: String,
  orientation: int,
  aspectRatio: String,
  av: String,
  tv: String,
  sv: String,
  xv: String,
  latLng: String,
  gpsInfo: String,
  thumbImagePath: String,
  viewImagePath: String,
  fullImagePath: String,
  fullImageId: String,
  xsImagePath: String
)
```

**GalleryPhotoInfoModel:**

```dart
GalleryPhotoInfoModel(
  // Same as PhotoInfoModel plus:
  isFavorite: bool
)
```

### 10.5. Image Control Models

**ImageControlModel:**

```dart
ImageControlModel(
  id: int,
  raw: Uint8List,                    // Binary control data
  name: String,
  thumbPath: String,
  viewPath: String,
  imageControlType: ImageControlType,
  imageControlCameraType: ImageControlCameraType,
  customImageId: int,
  customImageVersion: int,
  isFavorite: bool,
  isGrGallery: bool,
  aspectRatio: String,
  orientation: int
)
```

**ImageControlType Enum:**

- Standard, Vivid, Monotone, SoftMonotone, HardMonotone
- HiContrast, PositiveFilm, NegativeFilm, BleachBypass
- Retro, HDRTone, CrossProcess, CinemaYellow, CinemaGreen
- Grainy, Hi, Soft, Solid
- Custom1, Custom2, Custom3
- (GR IV Mono) MonoStandard, MonoSoft, MonoHighContrast, MonoGrainy, MonoSolid, MonoHDRTone

### 10.6. State Notifier Providers

The app uses these Riverpod providers for reactive state:

| Provider                                     | Purpose                       |
|:---------------------------------------------|:------------------------------|
| `cameraPowerNotifierProvider`                | Camera power on/off state     |
| `batteryLevelNotifierProvider`               | Battery level updates         |
| `captureStatusNotifierProvider`              | Capture state (idle/shooting) |
| `shootingModeNotifierProvider`               | Still/Movie mode              |
| `captureModeNotifierProvider`                | Single/Continuous/etc.        |
| `driveModeNotifierProvider`                  | Drive mode setting            |
| `storageInformationNotifierProvider`         | Storage status                |
| `powerOffDuringFileTransferNotifierProvider` | Transfer behavior             |
| `imageControlViewModelProvider`              | Image Control state           |
| `cameraLogViewModelProvider`                 | GR Log state                  |
| `cameraInfoViewModelProvider`                | Camera info                   |
| `cameraSettingsViewModelProvider`            | Settings state                |

---

## 11. Local Database Schema

### 11.1. Device Information

```sql
CREATE TABLE device_info(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  model_name TEXT,
  model_type INTEGER,
  device_name TEXT,           -- User-editable camera name
  bd_name TEXT,               -- Bluetooth device name
  remote_id TEXT,
  serial_no TEXT,
  firmware_version TEXT,
  ssid TEXT,
  bssid TEXT,
  security TEXT,
  password TEXT,
  wlan_frequency INTEGER DEFAULT 0,  -- 0=2.4GHz, 1=5GHz
  is_time_sync INTEGER DEFAULT 0,
  created_at INTEGER,
  updated_at INTEGER
);
```

### 11.2. Photo Information

```sql
CREATE TABLE photo_info(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  camera_model TEXT,
  serial_no TEXT,
  memory INTEGER,             -- Storage slot
  dir TEXT,                   -- Directory (e.g., "100RICOH")
  file TEXT,                  -- Filename (e.g., "R0001234.DNG")
  recorded_size TEXT,
  size INTEGER,               -- File size in bytes
  datetime INTEGER,           -- Unix timestamp
  recorded_time TEXT,
  orientation INTEGER,        -- EXIF orientation
  aspect_ratio TEXT,          -- "3:2", "4:3", "1:1", "16:9"
  av TEXT,                    -- Aperture (e.g., "F2.8")
  tv TEXT,                    -- Shutter speed (e.g., "1/250")
  sv TEXT,                    -- ISO (e.g., "ISO200")
  xv TEXT,                    -- Exposure compensation
  lat_lng TEXT,               -- GPS coordinates
  gps_info TEXT,              -- Full GPS data
  thumb_last_access_datetime INTEGER,
  thumb_image_path TEXT,
  view_last_access_datetime INTEGER,
  view_image_path TEXT,
  full_image_path TEXT,
  full_image_id TEXT,
  xs_image_path TEXT,         -- Extra-small preview
  created_at INTEGER,
  updated_at INTEGER
);
```

### 11.3. Camera Log (GR Log)

```sql
CREATE TABLE monthly_camera_log(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_info_id INTEGER NOT NULL,
  date TEXT NOT NULL,         -- "YYYY-MM"
  completed INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER,
  updated_at INTEGER,
  FOREIGN KEY(device_info_id) REFERENCES device_info(id) ON DELETE CASCADE
);

CREATE TABLE daily_camera_log(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  monthly_camera_log_id INTEGER NOT NULL,
  day TEXT NOT NULL,
  still_count INTEGER NOT NULL,
  video_count INTEGER NOT NULL,
  -- Exposure mode counts
  exposure_p INTEGER NOT NULL,
  exposure_tv INTEGER NOT NULL,
  exposure_av INTEGER NOT NULL,
  exposure_m INTEGER NOT NULL,
  exposure_b INTEGER NOT NULL,
  exposure_t INTEGER NOT NULL,
  exposure_bt INTEGER NOT NULL,
  exposure_sfp INTEGER NOT NULL,
  -- Image Control/Effect counts
  effect_off INTEGER NOT NULL,
  effect_col_vivid INTEGER NOT NULL,
  effect_efc_monochrome INTEGER NOT NULL,
  effect_efc_soft_monochrome INTEGER NOT NULL,
  effect_efc_hard_monochrome INTEGER NOT NULL,
  effect_efc_high_contrast INTEGER NOT NULL,
  effect_efc_posi_film INTEGER NOT NULL,
  effect_efc_bleach_bypass INTEGER NOT NULL,
  effect_efc_retro INTEGER NOT NULL,
  effect_efc_hdr_tone INTEGER NOT NULL,
  effect_col_custom1 INTEGER NOT NULL,
  effect_col_custom2 INTEGER NOT NULL,
  effect_col_custom3 INTEGER NOT NULL,
  -- GR IV Mono-specific effects
  effect_efc_mono_standard INTEGER DEFAULT 0,
  effect_efc_mono_soft INTEGER DEFAULT 0,
  effect_efc_mono_high_contrast INTEGER DEFAULT 0,
  effect_efc_mono_grainy INTEGER DEFAULT 0,
  effect_efc_mono_solid INTEGER DEFAULT 0,
  effect_efc_mono_hdr_tone INTEGER DEFAULT 0,
  -- Aspect ratio counts
  aspect_ratio_one_to_one INTEGER,
  aspect_ratio_three_to_two INTEGER,
  aspect_ratio_four_to_three INTEGER,
  aspect_ratio_sixteen_to_nine INTEGER,
  FOREIGN KEY(monthly_camera_log_id) REFERENCES monthly_camera_log(id)
);
```

---

## 12. Error Handling Strategy

### 12.1. HTTP Response Handling

| Code  | Meaning      | Action                                                          |
|:------|:-------------|:----------------------------------------------------------------|
| `200` | Success      | Process response body                                           |
| `404` | Not Found    | For pagination: end of list; For single resource: doesn't exist |
| `401` | Unauthorized | Re-run Wi-Fi Handoff                                            |
| Other | Error        | Display "GET/PUT error" with response code                      |

### 12.2. User-Facing Errors

| Scenario                        | User Message / Action                                                             |
|:--------------------------------|:----------------------------------------------------------------------------------|
| **BLE Scan Empty**              | "Camera not found. Is Bluetooth enabled on the camera?"                           |
| **Step 4 Timeout**              | "Connection handshake failed. Please restart camera."                             |
| **Wi-Fi Timeout**               | "Could not join camera network. Please verify Android Settings."                  |
| **Wi-Fi Connection Failed**     | Try: Turn Wi-Fi off/on, restart phone, forget saved network                       |
| **WLAN Enable Failed**          | "The WLAN function cannot be enabled. Set camera to Photo/Playback mode."         |
| **HTTP 401**                    | "Authentication failed." -> Re-run Wi-Fi Handoff.                                 |
| **Firmware Mismatch**           | "Firmware update required. Please update camera manually."                        |
| **Camera Model Incorrect**      | "The camera model is incorrect."                                                  |
| **Battery Too Low**             | "The battery level is low." / "The battery will run out soon."                    |
| **Storage Full**                | Check available storage                                                           |
| **Storage Not Ready**           | "Please make sure the storage media is ready for use, then try again."            |
| **Shooting Mode Error**         | "The camera could not be switched to the shooting mode."                          |
| **Image Control Not Supported** | "The camera does not support the Image Control setting feature. Update firmware." |

### 12.3. Connection Recovery

If WLAN connection fails:

1. Turn smartphone Wi-Fi off, wait a few seconds, turn on again
2. Restart smartphone
3. If camera network is saved as "My Network", delete it and reconnect
4. Verify camera WLAN is enabled (check camera menu)

---

## 13. State Machine Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                          DISCONNECTED                            │
└─────────────────────────────────────────────────────────────────┘
                                │
                    BLE Scan (Service UUID filter)
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         BLE DISCOVERED                           │
│              (Camera found in scan results)                      │
└─────────────────────────────────────────────────────────────────┘
                                │
                      connect() + createBond()
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                           BLE BONDED                             │
│                 (Waiting for Step 4 handshake)                   │
└─────────────────────────────────────────────────────────────────┘
                                │
               Subscribe + receive notification (5s timeout)
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        BLE CONNECTED                             │
│              (Standby - can trigger shutter, GPS)                │
└─────────────────────────────────────────────────────────────────┘
                                │
                  User requests high-bandwidth feature
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       WLAN CONNECTING                            │
│            (Read credentials, request network)                   │
└─────────────────────────────────────────────────────────────────┘
                                │
                    onAvailable + bindProcessToNetwork
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        WLAN CONNECTED                            │
│         (Full features: photos, logs, Image Control)             │
└─────────────────────────────────────────────────────────────────┘
                                │
                    Idle timeout / user disconnect
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        BLE CONNECTED                             │
│                   (Return to standby state)                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 14. Implementation Checklist

### 14.1. BLE Layer

- [ ] Scan with service UUID filter
- [ ] Handle bonding state machine
- [ ] Implement Step 4 handshake with 5s timeout
- [ ] Subscribe to battery notifications
- [ ] Implement GPS coordinate transmission
- [ ] Handle camera power state changes
- [ ] Handle disconnect/reconnection gracefully

### 14.2. Wi-Fi Layer

- [ ] Read credentials from BLE characteristic
- [ ] Implement WPA2/WPA3 fallback
- [ ] Bind network to process
- [ ] Verify camera with `/v1/props`
- [ ] Handle timeout and retry logic

### 14.3. HTTP/WebSocket Layer

- [ ] Implement photo listing with pagination
- [ ] Handle thumbnail/preview/full download
- [ ] Implement remote shutter trigger
- [ ] WebSocket connection for live status
- [ ] Camera log sync

### 14.4. Firmware Update

- [x] Check cloud for latest version (Hybrid strategy)
    - **AWS API**: Used for newer models (e.g., GR IV).
    - **Scraped Data**: Used for legacy models (GR II, GR III/IIIx). Fetched from `ricoh_firmware.json` hosted on GitHub Pages.
- [ ] Compare with camera version
- [ ] Download firmware binary
- [ ] Upload to camera
- [ ] Trigger reboot

### 14.5. Image Control

- [ ] Read Image Controls from photos
- [ ] Store in local database
- [ ] Apply to camera Custom slots
- [ ] Handle GR IV Mono-specific presets

### 14.6. UI/UX

- [ ] Model-specific iconography
- [ ] Battery level indicators
- [ ] Exposure/drive mode display
- [ ] Self-timer countdown
- [ ] Error messages with actionable guidance

---

## 15. Security Considerations

### 15.1. API Keys

The AWS API keys documented above are extracted from the official app. For a clean-room
implementation:

- Consider if these keys should be used directly
- The keys may be rotated by Ricoh
- Monitor for authentication failures

### 15.2. Network Security

- Camera AP uses WPA2/WPA3
- No HTTPS (HTTP only over local network)
- Ensure network binding to prevent data leakage to internet

### 15.3. Bluetooth Security

- BLE bonding provides encryption
- Store bond information securely
- Handle re-pairing gracefully

---

## 16. Known Limitations & Quirks

1. **Movie Mode Restriction:** WLAN cannot be enabled when camera is in movie mode
2. **USB Connection:** WLAN cannot be enabled when camera is connected via USB
3. **Bonding Sensitivity:** Camera drops connection if characteristics accessed too quickly after
   bonding
4. **WPA3 Transition:** Some Android versions need explicit WPA2/WPA3 fallback
5. **GR IV Image Control:** Requires firmware 1.04+ for Image Control writing
6. **Mode Dial Warning:** Don't operate mode dial during Image Control application
7. **RAW File Sizes:** DNG files can be 20-30MB; handle partial downloads and large memory buffers

---

## Appendix A: File Path Conventions

Photos are organized as:

```
/<storage>/DCIM/<directory>/<filename>
```

- **storage:** `in` for internal
- **directory:** `100RICOH`, `101RICOH`, etc.
- **filename:** `R0001234.JPG`, `R0001234.DNG`

---

## Appendix B: Time Synchronization

The app can sync time from smartphone to camera:

- `is_time_sync` flag in device database
- Useful for accurate timestamps when GPS not available

---

## Appendix C: Camera Naming

Users can set custom names for their cameras:

- Stored in `device_name` field
- Displayed in app instead of default BLE name
- Endpoint/method for setting name TBD
