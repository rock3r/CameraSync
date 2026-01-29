# Firmware Updates

This document describes how the app detects the camera firmware version over BLE, how it checks
for available firmware updates, and how it performs the firmware update flow. Details are based
on the current implementation in `app/src/main/java/...` (see references in each section).

## 1) Detect firmware version from BLE

### BLE services/characteristics used

- **Camera Control Service**: `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF`
- **Firmware version characteristic**: `0000CC0A` (US-ASCII string)
- **Model name characteristic**: `0000CC0B` (US-ASCII string)
- **Pairing info characteristic** (pairing flow): `0000EE04` (payload includes firmware version)

The camera-control service and characteristic lookup are handled via
`BluetoothGattUtil.findCameraControlCharacteristic()` / `BluetoothGattUtil.findCharacteristic()`
which target the `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF` service.

### Continuous connection read (primary path)

During continuous connection, `GettingCameraDeviceInfoState` initiates a characteristic read for
`0000CC0A` and decodes the bytes as a US-ASCII firmware string. If valid, it immediately reads
`0000CC0B` for the model name and stores both in `BluetoothCameraInfoStore`:

- `GettingCameraDeviceInfoState.onEnter()` starts by reading `0000CC0A`.
- `onGattCharacteristicRead()` for `0000CC0A` → `new String(bArr, StandardCharsets.US_ASCII)`
  → assigns `mCameraFwVersion`.
- It then reads `0000CC0B` and assigns `mCameraModel`.
- `updateCameraDeviceInfo()` packages these values into `BluetoothCameraInfo$Device` and
  notifies listeners.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/btconnection/internal/state/GettingCameraDeviceInfoState.java`

### Pairing flow read (secondary path)

During pairing, firmware version is also read as part of the pairing camera information packet:

- Characteristic `0000EE04` is read.
- The payload includes firmware version and model name as US-ASCII strings.
- A `ReceivedPairingCameraInformation` is built and returned via callback.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/btconnection/internal/state/PairingState.java`

### Persisting firmware version

When device info updates arrive, `BluetoothAppConnectedState.saveCameraDeviceInfo()` stores the
firmware version on the `RegisteredCameraObject` and triggers workers to refresh firmware info:

- If the version changes, `FIRMWARE_INFO_UPDATE` is enqueued.
- Firmware update notifications are also raised when a change is detected.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/bluetooth/continuous/BluetoothAppConnectedState.java`

## 2) Check if firmware updates are available

There are two layers of checks:

### A) Local DB check (fast path)

`CameraFirmwareClient.isUpdateAvailable()` compares the current camera firmware version against
the latest version recorded in the local `CameraDb` firmware table.

- Uses `CameraDb.getFirmwareInfo(modelName, currentVersion)`
- Compares versions by multiplying by 100 and converting to `int`:
  `((int) (Float.parseFloat(currentVersion) * 100)) < ((int) (Float.parseFloat(firmwareInfo.firmwareVersion) * 100))`
- Returns `true` if the DB has a newer version

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/CameraFirmwareClient.java`

### B) On-camera update readiness check (PTP/IP)

Before uploading firmware, the uploader confirms that the camera supports the firmware update
command set and explicitly requests a firmware update check over PTP/IP:

1. `CameraFirmwareUploader.checkCommandVersion()` reads device property
   `FirmwareUpdateCommandVersion` (`0xD21B`) and requires it to be `>= 100`.
2. If supported, `requestFirmwareUpdateCheck()` is called.
3. `BasePtpManager.requestFirmwareUpdateCheck()` sends operation
   `SDIO_RequestFirmwareUpdateCheck` (`0x940B`) with the firmware file size.
4. `FirmwareUpdateCheckRequester` listens for the event
   `SDIE_FirmwareUpdateCheckResult` (`0xC40B`) and maps the result to an upload outcome.
5. After a successful check, the app monitors the `FirmwareUpdateStatus` (`0xD21C`) property. If it transitions to `Off`, the upload is canceled from the camera side.
6. The app also reads `MaximumNumberOfBytesForUploadPartialData` (`0xD21D`) to determine the maximum chunk size for data transfer (calculated as `value - 12`).

**References:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/update/CameraFirmwareUploader.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/ptpip/BasePtpManager.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/ptpip/mtp/FirmwareUpdateCheckRequester.java`

## 3) Perform the firmware update

The firmware update flow has three main phases: metadata download, firmware file download, and
firmware upload to the camera. This is orchestrated by `FirmwareUpdateActivity` with controllers
for each phase.

### Flow diagram

```mermaid
flowchart TD
    A[BLE read fw/model] --> B[Save device info]
    B --> C[CameraDb firmware info]
    C --> D[FirmwareUpdateActivity]
    D --> E{Signed in?}
    E -- yes --> F[Firmware EULA]
    E -- no --> G[Sign-in request UI]
    F --> H[FirmwareWebApiController]
    H --> I[Firmware metadata download]
    I --> J[Firmware file download (HTTP)]
    J --> K[PTP/IP update check + upload]
    K --> L[Firmware update complete]
```

### Phase A: Start update flow + metadata lookup

`FirmwareUpdateActivity` validates the target camera and cached firmware info:

- It looks up `FirmwareInfoObject` from `CameraDb` using model name + current firmware version.
- If cached firmware metadata is missing, the flow is terminated.
- If a firmware file is already downloaded, it prompts to resume or discard.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/FirmwareUpdateActivity.java`

### Phase B: Firmware metadata download (Sony Web API)

`FirmwareWebApiController.downloadFirmwareInfo()` downloads firmware metadata (including the
firmware file URL) via `CameraFirmwareInfoClient`:

- Requires network connectivity.
- Uses the firmware info API endpoint stored in `EnumSharedPreference.FirmwareInfoApi`.
- Uses `firmwareId` from the local DB to fetch update metadata from Sony servers.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/controller/FirmwareWebApiController.java`

### Phase C: Firmware file download

`DownloadPhaseController.start()` kicks off the firmware file download after metadata is fetched:

- Retrieves the `firmwareUrl` from the `FirmwareInfo` response.
- Uses `CameraFirmwareDownloader` to download the binary to local storage.
- Persists download state and shows progress UI.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/controller/DownloadPhaseController.java`

### Phase D: Firmware upload (PTP/IP)

`UploadPhaseController` manages the transfer of the firmware binary to the camera:

- `CameraFirmwareUploader.checkCommandVersion()` ensures the camera supports updates.
- `requestFirmwareUpdateCheck()` requests the camera to verify readiness.
- The uploader switches firmware update mode ON, queries max chunk size, and streams the file.
- On completion or error, firmware update mode is turned OFF.

**References:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/controller/UploadPhaseController.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/update/CameraFirmwareUploader.java`

## Firmware update check results and upload errors

During the PTP/IP check and update mode transitions, the app maps firmware update results into
`EnumCameraFirmwareUploadResult` values. The mapping is handled in
`FirmwareUpdateCheckRequester` and the firmware update mode callbacks.

### Firmware update check result mapping (PTP/IP)

When the event `SDIE_FirmwareUpdateCheckResult` is received, the result is mapped as:

- `OK` -> `Success`
- `LowBattery` -> `LowBatteryError`
- `NoMedia` -> `NoMediaError`
- `MediaNoWritable` -> `MediaNoWritableError`
- `OverFileSize` -> `OverFileSizeError`
- `OverHeating` -> `OverHeatingError`
- `OperationLock` -> `OperationLockError`
- `Capturing` -> `CapturingError`
- `Busy` -> `BusyError`
- Anything else -> `OtherError`

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/ptpip/mtp/FirmwareUpdateCheckRequester.java`

### Firmware update mode ON event mapping

After a successful update check, the app enables firmware update mode and maps any error event:

- `OK` -> continue to upload
- `LowBattery` -> `LowBatteryError`
- `NoMedia` -> `NoMediaError`
- `MediaNoWritable` -> `MediaNoWritableError`
- `OverFileSize` -> `OverFileSizeError`
- `OverHeating` -> `OverHeatingError`
- `OperationLock` -> `OperationLockError`
- `Capturing` -> `CapturingError`
- `Busy` -> `BusyError`
- Anything else -> `OtherError`

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/ptpip/mtp/FirmwareUpdateCheckRequester.java`

## Sony sign-in requirement (cannot be bypassed in current implementation)

The existing flow explicitly requires a signed-in Sony account before proceeding to firmware
download/updates:

- `SignInRequestFragment` checks `AuthUtil.checkSignIn()` in `onResume()`.
- If not signed in, the UI presents the sign-in request; if signed in, it proceeds to the EULA.

There is no in-repo logic to bypass Sony authentication. The firmware metadata and downloads are
retrieved through Sony-hosted APIs that are assumed to be protected by authentication and
associated request headers. Any bypass would require changing the app’s authentication flow or
calling private endpoints without authorization, which is not supported by the current code.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/SignInRequestFragment.java`

## Firmware metadata + download URLs (with headers)

The firmware update flow uses three HTTP endpoints: a firmware list API, a firmware info API, and
the firmware binary URL. These are the concrete URLs and headers used by the app.

### 1) Firmware list API (POST)

- **Purpose:** Fetch firmware list for all registered cameras and the firmware info API URL
- **URL:** `https://support.d-imaging.sony.co.jp/FSErFHP8Je0xINMFVv9P/api/firmwarelist_api.php`
  (`R.string.camera_firmware_info_url`)
- **HTTP method:** `POST`
- **Headers:**
  - `User-Agent: Creators App/<appVersion> ( Android <osVersion> )`
  - `Content-Type: application/json; charset=utf-8` (Retrofit JSON converter)
- **Body (JSON):**
  - `locale`: `"pmm"` (hard-coded in `FirmwareModelList`)
  - `language`: `getCurrentLocaleInfoBasedUserProfile()` (locale string)
  - `modelList`: array of `{ modelName, currentVersion }`

**References:**  
`app/src/main/res/values/strings.xml`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareService2.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/info/CameraFirmwareInfoDownloader3.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareModelList.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareModelList2.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/UserAgentInterceptor.java`

### 2) Firmware info API (GET)

- **Purpose:** Fetch firmware metadata for a specific firmware ID
- **URL:** From `FirmwareList.firmwareInfoApi` (saved to `EnumSharedPreference.FirmwareInfoApi`)
- **HTTP method:** `GET`
- **Query parameters:**
  - `fwid`: firmware ID from the list response
  - `area`: `"pmm"`
  - `lang`: `<region>_<lang>` (with `zh` mapped to `cs` for `cn` or `ct` for `hk`/`tw`)
- **Headers:**
  - `User-Agent: Creators App/<appVersion> ( Android <osVersion> )`

**References:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareService2.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/info/CameraFirmwareInfoDownloader2.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareList.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/UserAgentInterceptor.java`

### 3) Firmware binary download (GET)

- **Purpose:** Download the firmware binary file
- **URL:** `FirmwareInfo.firmwareUrl` (from the firmware info response)
- **HTTP method:** `GET`
- **Headers:**
  - `Connection: close`
  - `Accept-Encoding: ` (empty string to disable compression)

**References:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareInfo.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/firmware/http/CameraFirmwareHttpConnection.java`

### Notes about auth headers

Firmware list/info calls are made via `FirmwareService` which is constructed without an
`AuthService`, so the `Authorization` header is **not** injected for these requests. The app
still enforces Sony sign-in in the UI flow, but the firmware metadata endpoints shown above are
called without auth headers in this codebase.

**Reference:**  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/firmware/FirmwareService.java`  
`app/src/main/java/jp/p006co/sony/ips/portalapp/imagingedgeapi/AbstractImagingEdgeApiService.java`

## Practical step-by-step summary

1. **Read firmware version over BLE**
   - Read `0000CC0A` (firmware) and `0000CC0B` (model) from the camera control service.
2. **Persist device info**
   - Store firmware version and model in `RegisteredCameraObject`.
3. **Check update availability**
   - Compare current version to `CameraDb` firmware info via `CameraFirmwareClient`.
4. **Start update flow**
   - Launch `FirmwareUpdateActivity` and confirm cached firmware info.
5. **Ensure user is signed in**
   - `SignInRequestFragment` enforces Sony login before proceeding.
6. **Download metadata + binary**
   - `FirmwareWebApiController` fetches metadata; `DownloadPhaseController` downloads firmware.
7. **Upload firmware**
   - `UploadPhaseController` / `CameraFirmwareUploader` run PTP/IP checks and upload.

## Compatible Models

The following camera models are supported by the app for general connectivity and, provided they support `FirmwareUpdateCommandVersion` >= 100, for firmware updates as well. This list is based on `app/src/main/base.apk/assets/camera_guide.json`.

### ALPHA
- **α1 II** (ILCE-1M2)
- **α1** (ILCE-1)
- **α9 III** (ILCE-9M3)
- **α7R V** (ILCE-7RM5)
- **α7S III** (ILCE-7SM3)
- **α7 IV** (ILCE-7M4)
- **α7CR** (ILCE-7CR)
- **α7C II** (ILCE-7CM2)
- **α6700** (ILCE-6700)

### VLOGCAM
- **ZV-E1**
- **ZV-E10 II** (ZV-E10M2)
- **ZV-1 II** (ZV-1M2)
- **ZV-1F**

### PROCAM
- **FX2** (ILME-FX2)
- **FX3** (ILME-FX3, ILME-FX3A)
- **FX30** (ILME-FX30)

### RX
- **RX1R III** (DSC-RX1RM3)

### OTHER
- **ILX-LR1**

