# Ricoh GR Camera Native Android Protocol Specification

**Target Audience:** Clean-room native Android developers (Kotlin, Coroutines, Kable).
**Scope:** Exhaustive specification of BLE discovery, pairing, connection, Wi-Fi handover, image
transfer, remote control, GPS transmission, Image Controls, and firmware updates for Ricoh GR III,
IIIx, and IV cameras.

This folder contains the protocol documentation split by functionality. Start here for architecture,
UUIDs, and BLE flow; see the linked documents for each feature area.

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

The camera exposes **5 BLE services** (1 for discovery, 4 for post-connection operations), each
containing multiple characteristics. This is significantly more complex than a single-service design.

#### 2.1.1. Service UUIDs

| UUID                                   | Name                          | Purpose                                                        |
|:---------------------------------------|:------------------------------|:---------------------------------------------------------------|
| `ef437870-180a-44ae-affd-99dbd85ade43` | Discovery Service             | BLE scan filter — used only for discovering Ricoh cameras      |
| `9A5ED1C5-74CC-4C50-B5B6-66A48E7CCFF1` | Main Camera Service           | Primary post-connection service (`bleServiceProvider`)         |
| `4B445988-CAA0-4DD3-941D-37B4F52ACA86` | Camera State Notification     | Multiplexed notifications for 11 camera state types: CameraPower (`YNa`), BatteryLevel (`XNa`), GeoTag (`cOa`), StorageInformation (`eOa`), CaptureMode (`sPa`), CaptureStatus (`tPa`), ShootingMode (`uPa`), DriveMode (`ZNa`), FileTransferList (`aOa`), PowerOffDuringFileTransfer (`dOa`), FirmwareUpdateResult (`bOa`). Error: `ER_BL_006` "NotifyError" on subscription failure |
| `84A0DD62-E8AA-4D0F-91DB-819B6724C69E` | GeoTag Write Service          | GPS coordinate transmission to camera                          |
| `9F00F387-8345-4BBC-8B92-B87B52E3091A` | Image Control Service         | Reading/writing Image Control presets (custom1/2/3 slots). Error code: `ER_BL_004` on read failure |

#### 2.1.2. Core Characteristics (Well-Understood)

| UUID                                   | Name                   | Operations      | Purpose                                      |
|:---------------------------------------|:-----------------------|:----------------|:---------------------------------------------|
| `0F291746-0C80-4726-87A7-3C501FD3B4B6` | Handshake/Notify       | Subscribe, Read | "Step 4" liveness check — critical handshake |
| `5f0a7ba9-ae46-4645-abac-58ab2a1f4fe4` | Wi-Fi Config           | Read            | SSID/Password credential exchange            |
| `A3C51525-DE3E-4777-A1C2-699E28736FCF` | Drive Mode / Command   | Write, Notify   | Drive mode notifications (`QOa` enum, 16 values — see [HTTP_WEBSOCKET.md §5.2](HTTP_WEBSOCKET.md)); also used for WLAN on/off and remote shutter commands |
| `FE3A32F8-A189-42DE-A391-BC81AE4DAA76` | Battery/Info           | Read, Notify    | Battery level, camera info                   |
| `28F59D60-8B8E-4FCD-A81F-61BDB46595A9` | GeoTag Write           | Write           | GPS coordinate data written to camera        |

#### 2.1.3. Camera Information Service Characteristics

These 6 UUIDs map 1:1 to the fields of `CameraInformationServiceModel`:

| UUID                                   | Field                      | Purpose                          |
|:---------------------------------------|:---------------------------|:---------------------------------|
| `B4EB8905-7411-40A6-A367-2834C2157EA7` | `manufacturerNameString`   | Manufacturer name (e.g., "RICOH")|
| `97E34DA2-2E1A-405B-B80D-F8F0AA9CC51C` | `bluetoothDeviceName`      | BLE name (e.g., "RICOH GR IIIx")|
| `35FE6272-6AA5-44D9-88E1-F09427F51A71` | `bluetoothMacAddressString`| Bluetooth MAC address            |
| `F5666A48-6A74-40AE-A817-3C9B3EFB59A6` | `firmwareRevisionString`   | Firmware version                 |
| `6FE9D605-3122-4FCE-A0AE-FD9BC08FF879` | `modelNumberString`        | Model number                     |
| `0D2FC4D5-5CB3-4CDE-B519-445E599957D8` | `serialNumberString`       | Serial number                    |

#### 2.1.4. Camera Mode & Shooting Characteristics

| UUID                                   | Purpose                                         | Enum/Type Values                                     |
|:---------------------------------------|:------------------------------------------------|:-----------------------------------------------------|
| `BD6725FC-5D16-496A-A48A-F784594C8ECB` | Operation mode list (available modes)            | `capture`, `playback`, `bleStartup`, `other`, `powerOffTransfer` |
| `D9AE1C06-447D-4DEA-8B7D-FC8B19C2CDAE` | Current operation mode                           | Same enum as above                                   |
| `63BC8463-228F-4698-B30D-FAF8E3D7BD88` | User mode / Shooting mode / Drive mode           | UserMode, ShootingMode (`still`/`movie`), DriveMode enums |
| `3e0673e0-1c7b-4f97-8ca6-5c2c8bc56680` | Capture type (still vs. video)                   | `image` (0), `video` (1→2)                           |
| `009A8E70-B306-4451-B943-7F54392EB971` | Capture mode                                     | CaptureMode enum (`single`, `continuous`, `interval`, `multiExposure`) |
| `B5589C08-B5FD-46F5-BE7D-AB1B8C074CAA` | Exposure mode (primary)                          | `P`, `Av`, `Tv`, `M`, `B`, `BT`, `T`, `SFP`        |
| `df77dd09-0a48-44aa-9664-2116d03b6fd7` | Exposure mode (companion)                        | Same enum as above                                   |

#### 2.1.5. WLAN Configuration Characteristics

These 5 UUIDs appear together in the WLAN configuration builder block, directly after the
`WLANControlCommandModel(networkType, passphrase, wlanFreq)` definition.

| UUID                                   | Purpose                                         | Details                                              |
|:---------------------------------------|:------------------------------------------------|:-----------------------------------------------------|
| `460828AC-94EB-4EDF-9BB0-D31E75F2B165` | WLAN control command                             | Reads `WLANControlCommandModel` (networkType, passphrase, wlanFreq). Associated with `CLa` enum (`read`/`write`) |
| `C4B7DFC0-80FD-4223-B132-B7D25A59CF85` | WLAN passphrase or frequency                     | Individual WLAN config field — likely passphrase or network type (adjacent to WLAN control in builder) |
| `0F38279C-FE9E-461B-8596-81287E8C9A81` | WLAN passphrase or frequency                     | Individual WLAN config field — the remaining field not covered by C4B7DFC0 |
| `9111CDD0-9F01-45C4-A2D4-E09E8FB0424D` | WLAN security type (primary)                     | `TPa` enum: `wpa2` (0), `wpa3` (1), `transition` (2) |
| `90638E5A-E77D-409D-B550-78F7E1CA5AB4` | WLAN security type (companion)                   | Same `TPa` enum. Paired with `9111CDD0` — likely one is read, one is notify |

#### 2.1.6. Camera Service Model Characteristics

These characteristics populate the `CameraServiceModel` which has 9 fields: `cameraPower`,
`operationModeList`, `operationMode`, `geoTag`, `storageInformation`, `fileTransferList`,
`powerOffDuringTransfer`, `gradNd`, `cameraName`.

The builder registers 13 UUIDs total. Two are known service-level UUIDs (`5f0a7ba9` Wi-Fi Config,
`ef437870` Discovery) referenced for cross-service data. The remaining 11 map to the 9 fields:

| UUID                                   | Field / Purpose                                 | Confidence |
|:---------------------------------------|:------------------------------------------------|:-----------|
| `A0C10148-8865-4470-9631-8F36D79A41A5` | **fileTransferList** — `FileTransferListModel(isNotEmpty)` | High (immediately follows `FileTransferListModel` definition in pool) |
| `BD6725FC-5D16-496A-A48A-F784594C8ECB` | **operationModeList** — Available operation modes | High (`YLa` type confirmed: `capture`, `playback`, `bleStartup`, `other`, `powerOffTransfer`) |
| `D9AE1C06-447D-4DEA-8B7D-FC8B19C2CDAE` | **operationMode** — Current operation mode      | High (paired with `BD6725FC`, same `YLa` enum) |
| `A36AFDCF-6B67-4046-9BE7-28FB67DBC071` | One of: cameraPower, geoTag, storageInformation, gradNd, cameraName | Medium (single UUID, no associated type in pool — likely a simple value field) |
| `e450ed9b-dd61-43f2-bdfb-6af500c910c3` | One of: cameraPower, geoTag, storageInformation, gradNd, cameraName | Medium (same pattern as `A36AFDCF`) |
| `B58CE84C-0666-4DE9-BEC8-2D27B27B3211` | One of: cameraPower, geoTag, storageInformation, gradNd, cameraName | Medium (same pattern as above) |
| `1452335A-EC7F-4877-B8AB-0F72E18BB295` | Likely **powerOffDuringTransfer** or **storageInformation** (primary) | Medium (paired with `875FC41D` — the dual-field nature fits `PowerOffDuringFileTransferModel(behavior, autoResize)`) |
| `875FC41D-4980-434C-A653-FD4A4D4410C4` | Likely **powerOffDuringTransfer** or **storageInformation** (companion) | Medium (paired with `1452335A`) |
| `FA46BBDD-8A8F-4796-8CF3-AA58949B130A` | CameraServiceModel construction — possibly the camera state service UUID | Medium (trio terminates with `_cMa` CameraServiceModel type) |
| `430B80A3-CC2E-4EC2-AACD-08610281FF38` | CameraServiceModel construction — read or notify characteristic | Medium (part of trio) |
| `39879aac-0af6-44b5-afbb-ca50053fa575` | CameraServiceModel construction — the third in the trio | Medium (last UUID before `_cMa` type in pool) |

#### 2.1.7. Other Characteristics

These characteristics appear in the camera state/notification registration builder but do not
belong to a specific named service model:

| UUID                                   | Purpose                                         | Confidence |
|:---------------------------------------|:------------------------------------------------|:-----------|
| `B29E6DE3-1AEC-48C1-9D05-02CEA57CE664` | **Firmware update cancel** (primary) — `xPa` enum with value `cancel` (0) | High |
| `0936b04c-7269-4cef-9f34-07217d40cc55` | **Firmware update cancel** (companion) — paired with `B29E6DE3` | High |
| `F37F568F-9071-445D-A938-5441F2E82399` | **Device Information bridge** — reads standard GATT short UUIDs (`2A26` Firmware Rev, `2A24` Model Number, `2A28` Software Rev, `2A29` Manufacturer, `2A25` Serial Number) | High |
| `e799198f-cf3f-4650-9373-b15dda1b618c` | **Storage information list** (primary) — `List<ka>` type, returns list of `StorageInformationModel` entries | High |
| `30adb439-1bc0-4b8e-9c8b-2bd1892ad6b0` | **Storage information list** (companion) — paired with `e799198f`, likely one read / one notify | High |
| `78009238-AC3D-4370-9B6F-C9CE2F4E3CA8` | **Camera power or GeoTag status** — positioned between capture type and storage list in builder. One of the remaining notification-readable states (cameraPower, geoTag, batteryLevel) | Medium |
| `559644B8-E0BC-4011-929B-5CF9199851E7` | **Camera power or battery level** (primary) — positioned between storage list and exposure mode. Paired with `cd879e7a` | Medium |
| `cd879e7a-ab9f-4c58-90ed-689bae67ef8e` | **Camera power or battery level** (companion) — paired with `559644B8` | Medium |

#### 2.1.8. Standard BLE

**Descriptor:**

* `00002902-0000-1000-8000-00805f9b34fb` - Client Characteristic Configuration Descriptor (CCCD) for
  enabling notifications

**Standard GATT Short UUIDs (Device Information Service):**

| Short UUID | Name                  | Purpose                       |
|:-----------|:----------------------|:------------------------------|
| `2A24`     | Model Number String   | Camera model identifier       |
| `2A25`     | Serial Number String  | Camera serial number          |
| `2A26`     | Firmware Revision     | Current firmware version      |
| `2A28`     | Software Revision     | Software version              |
| `2A29`     | Manufacturer Name     | Manufacturer ("RICOH")        |

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

**Alternative Model ID Conventions:**

The binary uses two naming conventions for GR IV models:

| Primary ID | Alternative ID   | Firmware Suffix |
|:-----------|:-----------------|:----------------|
| `gr4`      | `grIV`           | *(none)*        |
| `gr4Hdf`   | `grIVHDF`        | `_h`            |
| `gr4Mono`  | `grIVMonochrome` | `_m`            |

The primary IDs (`gr4`, `gr4Hdf`, `gr4Mono`) are used in cloud API requests (e.g., `fwDownload`).
The alternative IDs appear in internal routing/navigation. Firmware suffixes are appended to the
internal firmware name (e.g., `eg-1_h` for HDF).

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

**Note on User-Friendly Names:**
While scanning provides a basic model name (e.g., "RICOH GR IIIx"), you can retrieve more detailed information *after* bonding but *before* Wi-Fi connection by reading the standard **Device Information Service** (`0x180A`) characteristics:
- **Manufacturer Name String** (`0x2A29`): "RICOH IMAGING COMPANY, LTD."
- **Model Number String** (`0x2A24`): "RICOH GR IIIx"
- **Serial Number String** (`0x2A25`)
- **Firmware Revision String** (`0x2A26`)

See §2.1.8 for standard UUIDs. This allows the UI to show detailed device info during the "Connected (Standby)" state.

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

> **Note on Cloud Errors:** Codes like `step0_s_429` imply the official app performs a cloud check (likely for device registration or firmware status) during this handshake phase. For a clean-room implementation, this cloud check is likely **not required** for the BLE protocol itself to function, but purely an app-level requirement of the official implementation.

| Error Code | Meaning |
| :--- | :--- |
| `step0_o_camera_battery_low` | Camera battery too low to proceed |
| `step0_o_phone_battery_low` | Phone battery too low |
| `step0_o_storage_full` | Storage full |
| `step0_c_timeout_error` | Client-side timeout |
| `step0_c_network_error` | Network error |
| `step0_o_no_internet` | No internet (for cloud features) |
| `step0_o_unknown_error` | Unknown error |
| `step0_s_429_too_many_requests` | Cloud API rate limited (step 0) |
| `step0_s_500_internal_server_error` | Cloud API server error (step 0) |
| `step0_s_503_service_unavailable` | Cloud API unavailable (step 0) |
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

### 3.4. BLE Capabilities & Operations

This section summarizes the key operations available over BLE. See §2 for full UUID tables.

**1. Notifications (Camera State):**
Subscribe to **Camera State Notification** service characteristics to receive real-time updates:
- **Battery Level:** (`FE3A32F8`) - 0-100%
- **Capture Status:** (`tPa`) - `CaptureStatusModel` with two sub-states:
  - `countdown`: `notInCountdown` (0) / `selfTimerCountdown` (1)
  - `capturing`: `notInShootingProcess` (0) / `inShootingProcess` (1)
- **Shooting Mode:** (`uPa`) - Still/Movie, Exposure Mode (P/Av/Tv/M)
- **Drive Mode:** (`A3C51525` notify, `QOa` enum) - 16-value enum combining drive mode + self-timer
  (see [HTTP_WEBSOCKET.md §5.2](HTTP_WEBSOCKET.md) for full mapping)
- **Storage Info:** (`eOa`) - SD card status, remaining shots

**2. Commands (Write):**
- **Remote Shutter:** Trigger capture via Command characteristic (`A3C51525`). This is a
  single-step fire command — no half-press/S1/AF step exists. The camera handles autofocus
  internally. For Bulb/Time modes, first write starts exposure, second write stops it
  (`TimeShootingState` tracks this).
- **WLAN Control:** Enable/Disable camera Wi-Fi (`A3C51525`)
- **Camera Power:** Turn camera off

**3. Information (Read):**
- **Device Info:** Read standard GATT characteristics (Model, Serial, Firmware)
- **Camera Info Service:** Read custom Ricoh info fields (§2.1.3)

**4. Settings (Write):**
- **GPS Location:** Write coordinates to GeoTag service (§7)
- **Date/Time:** Sync phone time to camera (see Appendix B)


---

## State Machine Summary

See [ERROR_HANDLING.md](ERROR_HANDLING.md) for user-facing errors and recovery. High-level state flow:

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

## Implementation Checklist (High-Level)

- **BLE:** Scan with service UUID filter; handle bonding; Step 4 handshake (5s timeout); battery/GPS/power; disconnect recovery. See [README](README.md) §3.
- **Wi-Fi:** [WIFI_HANDOFF.md](WIFI_HANDOFF.md)
- **HTTP/WebSocket:** [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) (photos, remote shooting, logs)
- **Firmware:** [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md)
- **Image Control:** [IMAGE_CONTROL.md](IMAGE_CONTROL.md)
- **UI/UX:** Model-specific assets, battery/exposure/drive indicators, error guidance per [ERROR_HANDLING.md](ERROR_HANDLING.md)

---

## Security Considerations

### API Keys

The AWS API keys documented in [CLOUD_SERVICES.md](CLOUD_SERVICES.md) are extracted from the official app. For a clean-room implementation:

- Consider if these keys should be used directly
- The keys may be rotated by Ricoh
- Monitor for authentication failures

### Network Security

- Camera AP uses WPA2/WPA3
- No HTTPS (HTTP only over local network)
- Ensure network binding to prevent data leakage to internet

### Bluetooth Security

- BLE bonding provides encryption
- Store bond information securely
- Handle re-pairing gracefully

---

## Confirmed Absent Capabilities

The following capabilities were investigated via binary analysis of the official GR WORLD app
(libapp.so Dart object pool + Ghidra decompilation) and are **confirmed not present** in the Ricoh
GR protocol:

| Capability              | Status                  | Evidence                                                           |
|:------------------------|:------------------------|:-------------------------------------------------------------------|
| **Live View stream**    | Not supported           | WebSocket is status-only JSON; no video stream endpoint exists; zero references to liveView/viewfinder in binary |
| **Post-capture preview**| Not supported           | No postView/postCapture references in binary                       |
| **Half-press AF (S1)**  | Not supported           | Remote shutter is single-step "shoot" command; no focusing/AF state in CaptureStatusModel; no S1/half-press concept |
| **Touch AF**            | Not supported           | No touch AF/touch operation references in binary                   |
| **Focus status reading**| Not supported           | No AF result/focus confirmation states; CaptureStatusModel only has countdown + capturing |
| **Download resume**     | Not supported           | No HTTP Range headers, byte-offset parameters, or partial download mechanism; simple full-file GET only |

---

## Known Limitations & Quirks

1. **Movie Mode Restriction:** WLAN cannot be enabled when camera is in movie mode
2. **USB Connection:** WLAN cannot be enabled when camera is connected via USB
3. **Bonding Sensitivity:** Camera drops connection if characteristics accessed too quickly after bonding
4. **WPA3 Transition:** Some Android versions need explicit WPA2/WPA3 fallback
5. **GR IV Image Control:** Requires firmware 1.04+ for Image Control writing
6. **Mode Dial Warning:** Don't operate mode dial during Image Control application
7. **RAW File Sizes:** DNG files can be 20-30MB; handle partial downloads and large memory buffers

---

## Appendices

### Appendix A: File Path Conventions

Photos are organized as:

```
/<storage>/DCIM/<directory>/<filename>
```

- **storage:** `in` for internal
- **directory:** `100RICOH`, `101RICOH`, etc.
- **filename:** `R0001234.JPG`, `R0001234.DNG`

### Appendix B: Time Synchronization

The app can sync time from smartphone to camera:

- `is_time_sync` flag in device database
- Useful for accurate timestamps when GPS not available

### Appendix C: Camera Naming

Users can set custom names for their cameras:

- Stored in `device_name` field
- Displayed in app instead of default BLE name
- Endpoint/method for setting name TBD

---

## See also

| Document | Contents |
|:---------|:---------|
| [WIFI_HANDOFF.md](WIFI_HANDOFF.md) | Phase 2: Enabling WLAN, credentials, Android network binding |
| [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) | HTTP API, WebSocket status, photo ops, remote shooting, exposure/drive/self-timer |
| [IMAGE_CONTROL.md](IMAGE_CONTROL.md) | Image Control presets, parameters, applying to camera |
| [GPS_LOCATION.md](GPS_LOCATION.md) | GPS/location transmission over BLE, EXIF, permissions |
| [BATTERY.md](BATTERY.md) | Battery monitoring and constraints |
| [CLOUD_SERVICES.md](CLOUD_SERVICES.md) | AWS API: app info, GR Gallery, error codes |
| [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md) | Camera firmware prepare/upload/apply; cloud version check & download |
| [DATA_MODELS.md](DATA_MODELS.md) | App state models and notifier providers |
| [ERROR_HANDLING.md](ERROR_HANDLING.md) | HTTP handling, user-facing errors, connection recovery |
