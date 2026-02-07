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

This section is aligned with the dm-zharov characteristic list and the Image Sync 2.1.17
`definitions/def_ble.decrypted.yaml`. Some earlier UUID mappings were incorrect; use the tables below
as the authoritative mapping for GR III/IIIx.

#### 2.1.1. Service UUIDs

| UUID                                   | Name                         | Purpose                                           |
|:---------------------------------------|:-----------------------------|:--------------------------------------------------|
| `0x180A`                               | Device Information Service   | Standard GATT device info                         |
| `9A5ED1C5-74CC-4C50-B5B6-66A48E7CCFF1` | Camera Information Service   | Ricoh-specific device info                        |
| `4B445988-CAA0-4DD3-941D-37B4F52ACA86` | Camera Service               | Camera state, power, storage, date/time, etc.     |
| `9F00F387-8345-4BBC-8B92-B87B52E3091A` | Shooting Service             | Capture/shutter related settings and notifications |
| `84A0DD62-E8AA-4D0F-91DB-819B6724C69E` | GPS Control Command          | GPS information write                             |
| `F37F568F-9071-445D-A938-5441F2E82399` | WLAN Control Command         | Wi-Fi enable + SSID/passphrase/channel            |
| `0F291746-0C80-4726-87A7-3C501FD3B4B6` | Bluetooth Control Command    | BLE settings (enable condition, paired name)      |

#### 2.1.2. Notification Characteristics (Multiplexed)

| UUID                                   | Service        | Purpose                                                              |
|:---------------------------------------|:---------------|:---------------------------------------------------------------------|
| `FAA0AEAF-1654-4842-A139-F4E1C1E722AC` | Camera Service | Camera Service Notification — multiplexes camera state changes       |
| `671466A5-5535-412E-AC4F-8B2F06AF2237` | Shooting       | Shooting Service Notification — multiplexes shooting state changes   |
| `2AC97991-A78B-4CD4-9AE8-6E030E1D9EDB` | Shooting       | High Frequency Shooting Notification (same payload structure)        |

#### 2.1.3. Camera Information Service Characteristics (Corrected)

These 6 UUIDs map 1:1 to the fields of `CameraInformationServiceModel`:

| UUID                                   | Field                      | Purpose                          |
|:---------------------------------------|:---------------------------|:---------------------------------|
| `F5666A48-6A74-40AE-A817-3C9B3EFB59A6` | `manufacturerNameString`   | Manufacturer name (e.g., "RICOH")|
| `35FE6272-6AA5-44D9-88E1-F09427F51A71` | `modelNumberString`        | Model number                     |
| `0D2FC4D5-5CB3-4CDE-B519-445E599957D8` | `serialNumberString`       | Serial number                    |
| `B4EB8905-7411-40A6-A367-2834C2157EA7` | `firmwareRevisionString`   | Firmware version                 |
| `6FE9D605-3122-4FCE-A0AE-FD9BC08FF879` | `bluetoothDeviceName`      | BLE name (e.g., "RICOH GR IIIx") |
| `97E34DA2-2E1A-405B-B80D-F8F0AA9CC51C` | `bluetoothMacAddressString`| Bluetooth MAC address            |

#### 2.1.4. Camera Service Characteristics

| UUID                                   | Field / Purpose                               |
|:---------------------------------------|:----------------------------------------------|
| `B58CE84C-0666-4DE9-BEC8-2D27B27B3211` | Camera Power (0=off, 1=on, 2=sleep)           |
| `875FC41D-4980-434C-A653-FD4A4D4410C4` | Battery Level + Power Source                  |
| `FA46BBDD-8A8F-4796-8CF3-AA58949B130A` | Date Time (read/write)                        |
| `1452335A-EC7F-4877-B8AB-0F72E18BB295` | Operation Mode (current)                      |
| `430B80A3-CC2E-4EC2-AACD-08610281FF38` | Operation Mode List                           |
| `A36AFDCF-6B67-4046-9BE7-28FB67DBC071` | GEO Tag enable                                |
| `A0C10148-8865-4470-9631-8F36D79A41A5` | Storage Information (list)                    |
| `D9AE1C06-447D-4DEA-8B7D-FC8B19C2CDAE` | File Transfer List                            |
| `BD6725FC-5D16-496A-A48A-F784594C8ECB` | Power Off During File Transfer (behavior/resize) |
| `209F9869-8540-460E-97A6-5C3AC08F2C73` | Grad ND                                       |

#### 2.1.5. Shooting Service Characteristics (Common)

| UUID                                   | Purpose                                           |
|:---------------------------------------|:--------------------------------------------------|
| `A3C51525-DE3E-4777-A1C2-699E28736FCF` | Shooting Mode (P/Av/Tv/M/etc.)                    |
| `78009238-AC3D-4370-9B6F-C9CE2F4E3CA8` | Capture Mode (still=0, movie=2)                   |
| `B29E6DE3-1AEC-48C1-9D05-02CEA57CE664` | Drive Mode (0-65, includes timers/remote modes)   |
| `F4B6C78C-7873-43F0-9748-F4406185224D` | Drive Mode List                                   |
| `B5589C08-B5FD-46F5-BE7D-AB1B8C074CAA` | Capture Status (capturing + countdown state)      |
| `559644B8-E0BC-4011-929B-5CF9199851E7` | Operation Request (remote shutter)               |

#### 2.1.6. WLAN Control Command Characteristics

| UUID                                   | Purpose                            | Details                    |
|:---------------------------------------|:-----------------------------------|:---------------------------|
| `9111CDD0-9F01-45C4-A2D4-E09E8FB0424D` | Network Type                       | 0=OFF, 1=AP mode           |
| `90638E5A-E77D-409D-B550-78F7E1CA5AB4` | SSID                               | UTF-8 string               |
| `0F38279C-FE9E-461B-8596-81287E8C9A81` | Passphrase                         | UTF-8 string               |
| `51DE6EBC-0F22-4357-87E4-B1FA1D385AB8` | Channel                            | 0=Auto, 1-11=channels      |

#### 2.1.7. GPS Control Command Characteristics

| UUID                                   | Purpose                            |
|:---------------------------------------|:-----------------------------------|
| `28F59D60-8B8E-4FCD-A81F-61BDB46595A9` | GPS Information (write/read)       |

#### 2.1.8. Bluetooth Control Command Characteristics

| UUID                                   | Purpose                            | Details                           |
|:---------------------------------------|:-----------------------------------|:----------------------------------|
| `D8676C92-DC4E-4D9E-ACCE-B9E251DDCC0C` | BLE Enable Condition               | 0=disable, 1=anytime, 2=power-on |
| `FE3A32F8-A189-42DE-A391-BC81AE4DAA76` | Paired Device Name                 | UTF-8 string                      |

#### 2.1.9. Standard BLE

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
**Filter:** Prefer **manufacturer data** (Company ID `0x065F`) over a service UUID.

Image Sync advertises a manufacturer data payload with:
- **Model Code** (type `0x01`): `0x01` = GR III, `0x03` = GR IIIx
- **Serial Number** (type `0x02`)
- **Camera Power** (type `0x03`): `0x00` = OFF, `0x01` = ON

Some devices also advertise a Ricoh-specific service UUID, but this is **not** documented in the
public characteristic lists. If you use a service UUID filter, treat it as a best-effort optimization
and fall back to manufacturer data matching.

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

**Procedure (Corrected to public UUIDs):**

Public specs do **not** document a dedicated handshake characteristic. The official app’s "Step 4"
appears to be a liveness check that waits for **any** camera/shooting notification after bonding.

1. **Subscribe** to **Camera Service Notification** `FAA0AEAF-1654-4842-A139-F4E1C1E722AC`.
2. **Subscribe** to **Shooting Service Notification** `671466A5-5535-412E-AC4F-8B2F06AF2237`.
3. **Wait** for the *first* notification from either channel.
4. **Timeout:** If nothing arrives in **5 seconds**, the connection is zombie. Disconnect and retry.
5. **Success:** The camera is now "Connected (Standby)".

**Important:** `0F291746-0C80-4726-87A7-3C501FD3B4B6` is the **Bluetooth Control Command service** (e.g.,
BLE Enable Condition / Paired Device Name). It is **not** a notification channel.

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

**1. Notifications (Camera + Shooting State):**
Subscribe to the **Camera Service Notification** (`FAA0AEAF`) and **Shooting Service Notification**
(`671466A5`). Each notification includes a changed-value UUID; parse that UUID and decode using the
matching characteristic definition.

Common notify/read characteristics:
- **Battery Level:** `875FC41D` (0-100, plus power source)
- **Capture Status:** `B5589C08` (capturing + countdown state)
- **Capture Mode:** `78009238` (still vs movie)
- **Shooting Mode:** `A3C51525` (P/Av/Tv/M/etc.)
- **Drive Mode:** `B29E6DE3` (full 0-65 enum)
- **Storage Info:** `A0C10148` (storage list)
- **File Transfer List:** `D9AE1C06`
- **Geo Tag Enable:** `A36AFDCF`

**2. Commands (Write):**
- **Remote Shutter:** **Operation Request** `559644B8` (Shooting service). Payload: 2 bytes
  `[OperationCode, Parameter]` — Start=1, Stop=2; Parameter: No AF=0, AF=1.
- **WLAN Control:** **Network Type** `9111CDD0` (service `F37F568F`), value 0=OFF, 1=AP.
- **Camera Power:** **Camera Power** `B58CE84C` (Camera service), value 0=Off.
- **Date/Time:** **Date Time** `FA46BBDD` (Camera service), write local time.

**3. Information (Read):**
- **Device Info:** Read standard GATT characteristics (Model, Serial, Firmware)
- **Camera Info Service:** Read custom Ricoh info fields (§2.1.3)

**4. Settings (Write):**
- **GPS Location:** Write **GPS Information** `28F59D60` (GeoTag service) — see [GPS_LOCATION.md](GPS_LOCATION.md).
- **Date/Time:** Sync phone time via **Date Time** `FA46BBDD`.


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

- **BLE:** Scan via manufacturer data (Company ID `0x065F`); handle bonding; Step 4 handshake (5s timeout); battery/GPS/power; disconnect recovery. See [README](README.md) §3.
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
| **Live View stream**    | Not confirmed           | OpenAPI defines `/v1/liveview` (MJPEG), but GR III/IIIx behavior not confirmed in tests |
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
| [EXTERNAL_REFERENCES.md](EXTERNAL_REFERENCES.md) | Community BLE/Wi‑Fi specs (dm-zharov, CursedHardware, GRsync, etc.) and how we use them |
| [WIFI_HANDOFF.md](WIFI_HANDOFF.md) | Phase 2: Enabling WLAN, credentials, Android network binding |
| [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) | HTTP API, WebSocket status, photo ops, remote shooting, exposure/drive/self-timer |
| [IMAGE_CONTROL.md](IMAGE_CONTROL.md) | Image Control presets, parameters, applying to camera |
| [GPS_LOCATION.md](GPS_LOCATION.md) | GPS/location transmission over BLE, EXIF, permissions |
| [BATTERY.md](BATTERY.md) | Battery monitoring and constraints |
| [CLOUD_SERVICES.md](CLOUD_SERVICES.md) | AWS API: app info, GR Gallery, error codes |
| [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md) | Camera firmware prepare/upload/apply; cloud version check & download |
| [DATA_MODELS.md](DATA_MODELS.md) | App state models and notifier providers |
| [ERROR_HANDLING.md](ERROR_HANDLING.md) | HTTP handling, user-facing errors, connection recovery |
