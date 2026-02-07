# Ricoh HTTP API & WebSocket (Phase 3: High-Bandwidth Features)

After [Wi-Fi handoff](WIFI_HANDOFF.md), the app uses HTTP and WebSocket to the camera at
`http://192.168.0.1` for photo listing/transfer, remote shooting, Image Control read, camera logs,
and firmware operations. This document describes the endpoints, WebSocket status, and exposure/drive/self-timer enums.

**Base URL:** `http://192.168.0.1`  
**WebSocket:** `ws://192.168.0.1/v1/changes`

---

## 5.1. HTTP API Endpoints

### 5.1.1. Camera Information & Control

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

### 5.1.2. Photo Operations

| Method | Endpoint                             | Purpose                     | Timeout |
|:-------|:-------------------------------------|:----------------------------|:--------|
| `GET`  | `/v1/photos/infos?storage=in&after=` | List photos with pagination | 10s     |
| `GET`  | `/v1/photos/<path>?size=thumb`       | Get thumbnail               | 15s     |
| `GET`  | `/v1/photos/<path>?size=view`        | Get preview/view size       | 15s     |
| `GET`  | `/v1/photos/<path>?size=xs`          | Get extra-small preview     | 15s     |
| `GET`  | `/v1/photos/<path>?storage=in`       | Download full image         | 120s    |
| `GET`  | `/v1/photos/<path>/info?storage=in`   | Get single photo info       | 10s     |
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
| `dir`           | string | Directory name (e.g., "100RICOH")           |
| `file`          | string | Filename (e.g., "R0001234.DNG")             |
| `size`          | int    | File size in bytes                          |
| `recorded_size` | string | Human-readable file size                    |
| `datetime`      | int    | Unix timestamp                              |
| `recorded_time` | string | Formatted datetime string                   |
| `orientation`   | int    | EXIF orientation (1-8)                      |
| `aspect_ratio`  | string | "1:1", "3:2", "4:3", "16:9"                 |
| `av`            | string | Aperture value (e.g., "F2.8")               |
| `tv`            | string | Shutter speed (e.g., "1/250")                |
| `sv`            | string | ISO sensitivity (e.g., "ISO200")            |
| `xv`            | string | Exposure compensation (e.g., "+0.3")          |
| `lat_lng`       | string | GPS coordinates "lat,lng"                   |
| `gps_info`      | string | Full GPS info JSON                          |

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
| `transferred`   | File transfer complete    |
| `untransferred` | File not yet transferred |

**Transfer PUT Body (`/v1/photos/<path>/transfer`):**

```
Content-Type: application/json

storage=in
```

**Aspect Ratios:** `1:1`, `3:2`, `4:3`, `16:9`

### 5.1.3. Remote Shooting

| Method     | Endpoint                | Purpose                     |
|:-----------|:------------------------|:----------------------------|
| `POST`     | `/v1/camera/shoot`      | Trigger shutter             |
| `GET/POST` | `/v1/photos?storage=in` | Photo capture/list endpoint |

### 5.1.4. Image Control (Read)

| Method | Endpoint              | Purpose                            |
|:-------|:----------------------|:-----------------------------------|
| `GET`  | `/imgctrl?storage=in` | Get Image Control data from camera |

The raw binary data (`.BIN` format) is fetched and can be stored locally. Writing Image Control to
camera custom slots is described in [IMAGE_CONTROL.md](IMAGE_CONTROL.md).

### 5.1.5. Camera Logs (GR Log Feature)

| Method | Endpoint                                            | Purpose                    |
|:-------|:----------------------------------------------------|:---------------------------|
| `GET`  | `/v1/logs/camera?type=monthly_capture&date=YYYY-MM` | Get monthly shooting stats |

**Log Data Includes:**

- `still_count`, `video_count` - Shot counts
- `exposure_p`, `exposure_tv`, `exposure_av`, `exposure_m`, `exposure_b`, `exposure_t`,
  `exposure_bt`, `exposure_sfp` - Shots per exposure mode
- `effect_*` - Shots per Image Control preset
- `aspect_ratio_*` - Shots per aspect ratio

### 5.1.6. Firmware Operations (Camera-Side)

See [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md) for the full flow. Summary:

| Method | Endpoint                                      | Purpose                              |
|:-------|:----------------------------------------------|:-------------------------------------|
| `GET`  | `/v1/configs/firmware/prepare`                | Prepare for firmware update          |
| `PUT`  | `/v1/configs/firmware`                        | Upload firmware binary (stream body) |
| `GET`  | `/v1/configs/firmware?storage=in&reboot=true` | Apply firmware and reboot            |
| `GET`  | `/v1/configs/firmware/cancel`                 | Cancel firmware update               |

---

## 5.2. WebSocket: Real-Time Status

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

---

## 5.3. Exposure Modes

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

**ExposureMode EXIF Field:** EXIF tag `ExposureMode` (34850): 1 = Manual, 2 = Normal program, 3 = Aperture priority, 4 = Shutter priority.

---

## 5.4. Drive Modes

| Mode               | Internal ID          | Asset                          |
|:-------------------|:---------------------|:-------------------------------|
| Single             | `single`             | `drive_single.png`             |
| Continuous         | `continuous`         | `drive_continuous.png`         |
| Auto Bracket       | `auto_bkt`           | `drive_auto_bkt.png`           |
| Multi-exposure     | `multi_exp`          | `drive_multi_exp.png`          |
| Interval           | `interval`           | `drive_interval.png`           |
| Multi-exp Interval | `multi_exp_interval` | `drive_multi_exp_interval.png` |

---

## 5.5. Self-Timer

**SelfTimer Enum Values:**
| Value | Description |
| :--- | :--- |
| `off` | No self-timer (`timer_off.svg`) |
| `2sec` | 2-second delay |
| `10sec` | 10-second delay |

**Timer Frames (for composition modes):**

- `intervalFrame`, `intervalTenSecondFrame`, `intervalTwoSecondFrame`
- `intervalCompositionFrame`, `intervalCompositionTenSecondFrame`, `intervalCompositionTwoSecondFrame`

**Timer State:** `timerDuration`, `selfTimerCountdown` (from WebSocket).
