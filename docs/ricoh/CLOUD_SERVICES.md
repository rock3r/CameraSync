# Ricoh Cloud Services (AWS)

The official app uses an AWS API in `ap-northeast-1` for app information, notifications, firmware
version/download, and GR Gallery (photographer presets). This document describes the endpoints and
error codes. Firmware update flow (camera-side + cloud) is summarized in [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md).

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

---

## 9.1. App Information & Notifications

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

**Response:** `informationList` keyed by language (`ja`, `en`, `fr`, `de`, `ko`, `zh-TW`, `zh-CN`), each an array of notifications with `No`, `Title`, `Date`, `Message`.

**Supported Languages:** `ja`, `en`, `fr`, `de`, `ko`, `zh-TW`, `zh-CN`

**Minimum Firmware Requirements:** GR IV Image Control: Version 1.04 or later

---

## 9.2. Firmware Version Check & Download

**Endpoint:** `POST /v1/fwDownload`

This single endpoint serves two purposes:

1. **Check latest version:** Omit `target_firmware_version` to get the latest available version.
2. **Download specific version:** Include `target_firmware_version` to get that specific version.

See [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md) for the full flow (cloud check → compare with camera → download → camera prepare/upload/apply).

**Response Fields:**

| Field            | Type   | Description                        |
|:-----------------|:-------|:-----------------------------------|
| `version`        | string | Firmware version (e.g., "1.04")    |
| `presignedUrl`   | string | S3 presigned URL for download      |
| `expirationDate` | string | URL expiration (ISO 8601, ~1 hour) |

**Error Response (unsupported model):**

```json
{
  "errorCode": "E001-002",
  "errorMessage": "指定の機種は登録されていません。"
}
```

Translation: "The specified model is not registered."

---

## 9.3. GR Gallery (Photographer Presets)

**List Photographer Albums:** `POST /v1/album/present/getList`

**Get Album Image Controls:** `POST /v1/album/present/getAlbumData`

Request body includes common phone fields; getAlbumData also requires `albumId` (e.g. `MinaDaimon_1`).

**List response:** `list` of albums with `No`, `displayFlag`, `photographerName`, `albumId`, `albumTitle`, `expireDate`, `albumJacketPath`, `photographerIconPath`.

**Album data response:** `list` of items with `No`, `displayNumber`, `displayFlag`, `viewPath`, `imageControlPath` (`.BIN` URL), `imageControlName`, `cameraModel`, `aspectRatio`, `orientation`. Download preset via the `imageControlPath` URL.

**Image Control Names Found:** Standard, Vivid, Negative Film, Positive Film, Retro, Hi-Contrast B&W, Cinema (Yellow), Cinema (Green).

---

## 9.4. Error Codes

| Code  | Meaning                                                     |
|:------|:------------------------------------------------------------|
| `200` | Success                                                     |
| `429` | Too Many Requests (`step1_s_429_too_many_requests`)         |
| `500` | Internal Server Error (`step1_s_500_internal_server_error`)  |
| `503` | Service Unavailable (`step1_s_503_service_unavailable`)     |

Step-based error codes are also used in the app (see [README.md](README.md) §3.3 and [ERROR_HANDLING.md](ERROR_HANDLING.md)).
