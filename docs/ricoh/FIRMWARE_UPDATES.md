# Ricoh Firmware Updates

Firmware updates combine **cloud** (version check and download) with **camera HTTP** (prepare, upload, apply). Only GR IV currently supports in-app firmware updates; other models use manual or scraped version info.

**Cloud API:** See [CLOUD_SERVICES.md](CLOUD_SERVICES.md) for headers and `POST /v1/fwDownload`.  
**Camera HTTP:** See [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) §5.1.6 for endpoints.

---

## Firmware Update Flow

1. **Check Version:** Call cloud `POST /v1/fwDownload` with `model` (e.g. `gr4`), omit `target_firmware_version` to get latest.
2. **Compare:** Get camera firmware from HTTP `GET /v1/props` (field: `firmwareVersion`). If cloud `version` > camera version → update available.
3. **Download:** If update needed, include `target_firmware_version` in `POST /v1/fwDownload`, then GET the `presignedUrl` to download the firmware zip.
4. **Prepare:** Call `GET /v1/configs/firmware/prepare` on camera.
5. **Upload:** Stream firmware binary to `PUT /v1/configs/firmware`.
6. **Apply:** Call `GET /v1/configs/firmware?storage=in&reboot=true`.
7. **Wait:** Camera reboots and applies update.

**Cancel:** `GET /v1/configs/firmware/cancel`

---

## Supported Models for In-App Firmware Update

Only **GR IV** (`gr4`) currently supports in-app firmware update.

| Camera      | Model ID  | Display Name       | In-App FW Update | Internal FW Name |
|:------------|:----------|:-------------------|:-----------------|:-----------------|
| GR II       | `gr2`     | "GR II"            | No               | -                |
| GR III      | `gr3`     | "RICOH GR III"     | No               | -                |
| GR III HDF  | `gr3Hdf`  | "GR III HDF"       | No               | -                |
| GR IIIx     | `gr3x`    | "RICOH GR IIIx"    | No               | -                |
| GR IIIx HDF | `gr3xHdf` | "GR IIIx HDF"      | No               | -                |
| **GR IV**   | `gr4`     | "RICOH GR IV"      | **Yes**          | `eg-1`           |
| GR IV HDF   | `gr4Hdf`  | "GR IV HDF"        | No (not yet)     | -                |
| GR IV Mono  | `gr4Mono` | "GR IV Monochrome" | No (not yet)     | -                |

**Implementation note:** CameraSync uses a hybrid strategy — AWS API for newer models (e.g. GR IV) and scraped data from `ricoh_firmware.json` (e.g. GitHub Pages) for legacy models (GR II, GR III/IIIx).

---

## Minimum App-Compatible Firmware Versions (Hardcoded)

- **GR III:** Ver. 2.00 or later  
- **GR IIIx:** Ver. 1.50 or later  
- **GR IV (Image Control):** Ver. 1.04 or later  

---

## Data Models (from official app)

**FirmwareUpdateModel:** `code: int`, `result: FirmwareUpdateResult`  
**FirmwareUpdateInfo:** `version: String`, `targetFirmwareVersion: String`  
**FirmwareUpdateResultModel:** `result: FirmwareUpdateResult`  
**FirmwareUpdateResult enum (`FMa`):** `invalid` (0), `success` (1), `failure` (2)  
**FirmwareUpdateCheckData:** `batteryLevel: int`, `cameraBatteryLevel: int`

**Pre-Update Checks:**

- Camera battery sufficient  
- Phone battery sufficient  
- Phone storage space available  
- Camera not in shooting mode  

BLE firmware-update cancel characteristics: see [README.md](README.md) §2.1.7 (`B29E6DE3`, `0936b04c`). Notification type `bOa` for firmware update result.
