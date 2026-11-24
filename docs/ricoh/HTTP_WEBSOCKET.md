# Ricoh HTTP API & WebSocket (Phase 3: High-Bandwidth Features)

After [Wi-Fi handoff](WIFI_HANDOFF.md), the app uses HTTP and WebSocket to the camera at
`http://192.168.0.1` for photo listing/transfer, remote shooting, Image Control read, camera logs,
and firmware operations. This document describes the endpoints, WebSocket status, and exposure/drive/self-timer enums.

**Base URL:** `http://192.168.0.1`  
**WebSocket:** `ws://192.168.0.1/v1/changes`

---

## 5.1. HTTP API Endpoints

### 5.1.1. Camera Information & Control

| Method | Endpoint                 | Purpose                                           | Timeout                   |
|:-------|:-------------------------|:--------------------------------------------------|:--------------------------|
| `GET`  | `/v1/props`              | Camera properties (model, serial, SSID, firmware) | 3s (verify), 10s (normal) |
| `GET`  | `/v1/ping`               | Ping device (returns camera time)                 | 3s                        |
| `GET`  | `/v1/liveview`           | Live view MJPEG stream (if supported)             | 10s                       |
| `GET`  | `/v1/constants/device`   | Legacy device info (GR II era)                    | 10s                       |
| `GET`  | `/_gr/objs`              | Legacy object list (GR Remote/GR II)              | 10s                       |
| `POST` | `/v1/device/finish`      | End session / shutdown camera                     | 10s                       |
| `POST` | `/v1/device/wlan/finish` | Shutdown WLAN only                                | 10s                       |
| `PUT`  | `/v1/params/device`      | Set device parameters (e.g., stillFormat)         | 10s                       |
| `PUT`  | `/v1/params/camera`      | Set capture parameters                            | 10s                       |
| `PUT`  | `/v1/params/camera/compAdjust` | Composition adjustment                      | 10s                       |
| `PUT`  | `/v1/params/lens`        | Set lens parameters (e.g., focusSetting)          | 10s                       |
| `POST` | `/v1/lens/focus`          | Focus at point                                   | 10s                       |
| `POST` | `/v1/lens/focus/lock`     | Lock focus                                       | 10s                       |
| `POST` | `/v1/lens/focus/unlock`   | Unlock focus                                     | 10s                       |
| `POST` | `/v1/params/lens/zoom`    | Set zoom level                                   | 10s                       |

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

**`/v1/params/device` Body (Device Parameters):**

OpenAPI defines this as a form body with fields like `stillFormat`. It is **not** the Wi-Fi
configuration endpoint; Wi-Fi credentials are managed via BLE or `/v1/props`.

### 5.1.2. Photo Operations

| Method | Endpoint                                          | Purpose                     | Timeout |
|:-------|:--------------------------------------------------|:----------------------------|:--------|
| `GET`  | `/v1/photos?storage=in&limit=&after=`            | List photos with pagination | 10s     |
| `GET`  | `/v1/photos/{dir}/{file}?size=thumb`             | Get thumbnail               | 15s     |
| `GET`  | `/v1/photos/{dir}/{file}?size=view`              | Get preview/view size       | 15s     |
| `GET`  | `/v1/photos/{dir}/{file}?size=xs`                | Get extra-small preview     | 15s     |
| `GET`  | `/v1/photos/{dir}/{file}`                        | Download full image         | 120s    |
| `GET`  | `/v1/photos/{dir}/{file}/info`                   | Get single photo info       | 10s     |
| `PUT`  | `/v1/photos/{dir}/{file}/transfer`               | Set transfer status         | 10s     |
| `GET`  | `/v1/transfers?status=&storage=&limit=&after=`   | List transfer queue status  | 10s     |

**Storage Parameter Values:**

- `storage=in` - Internal storage
- `storage=sd1` - SD card slot 1

**`/v1/photos` Response (list only):**

```json
{
  "errCode": 200,
  "dirs": [
    {
      "name": "100RICOH",
      "files": ["R0001234.DNG", "R0001235.JPG"]
    }
  ]
}
```

**`/v1/photos/{dir}/{file}/info` Response (metadata):**

Returns fields like `cameraModel`, `orientation`, `aspectRatio`, `av`, `tv`, `sv`, `xv`, `size`,
`gpsInfo`, and `datetime` (see OpenAPI schema `PhotoMetadata`).

**Transfer List Response (`/v1/transfers`):**

```json
{
  "errCode": 200,
  "transfers": [
    {
      "index": 1,
      "filepath": "/100RICOH/R0001234.DNG",
      "size": 25165824
    }
  ]
}
```

Filter by status using the `status` query parameter (`transferred` or `untransferred`).

**Transfer PUT Body (`/v1/photos/{dir}/{file}/transfer`):**

OpenAPI defines a form body with `status` and `storage` (e.g., `status=transferred&storage=sd1`).

**Aspect Ratios:** `1:1`, `3:2`, `4:3`, `16:9`

### 5.1.3. Remote Shooting

| Method | Endpoint                     | Purpose                 |
|:------|:------------------------------|:------------------------|
| `POST` | `/v1/camera/shoot`           | Trigger shutter         |
| `POST` | `/v1/camera/shoot/start`     | Start shoot (sequence)  |
| `POST` | `/v1/camera/shoot/compose`   | Compose shoot           |
| `POST` | `/v1/camera/shoot/cancel`    | Cancel shoot            |
| `POST` | `/v1/camera/shoot/finish`    | Finish shoot            |

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

**Capture Status Model:**

The `CaptureStatusModel` contains two sub-states that independently track countdown and capture:

| Field         | Enum               | Values                                                                    |
|:--------------|:-------------------|:--------------------------------------------------------------------------|
| `countdown`   | `CaptureCountdown` | `notInCountdown` (0) — no timer; `selfTimerCountdown` (1) — timer active |
| `capturing`   | `CaptureCapturing` | `notInShootingProcess` (0) — idle; `inShootingProcess` (1) — capturing    |

> **Note on Remote Shutter:** The Ricoh protocol provides a single-step "shoot" command only (HTTP
> `POST /v1/camera/shoot` or BLE write to **Operation Request** `559644B8`). There is **no half-press/S1 autofocus step**
> — the camera handles AF internally upon trigger. There is also **no touch AF** or **focus status
> reading** capability. AF control is Sony-only.

**Capture Status (WebSocket simplified):**
| Value | Meaning | UI Action |
| :--- | :--- | :--- |
| `idle` | Ready to shoot | Enable Shutter Button |
| `capture` | Currently capturing | Disable Shutter Button, show spinner |

**Shooting Mode Values (`ShootingMode`):**
| Value | Meaning |
| :--- | :--- |
| `still` | Still image mode |
| `movie` | Movie/video mode |

**Capture Mode (BLE `78009238`):**
| Value | Meaning |
| :--- | :--- |
| `0` | Still image mode |
| `2` | Movie/video mode |

**Drive Mode Values (WebSocket, 6 base modes):**
| Value | Asset | Description |
| :--- | :--- | :--- |
| `single` | `drive_single.png` | Single shot |
| `continuous` | `drive_continuous.png` | Continuous shooting |
| `auto_bkt` | `drive_auto_bkt.png` | Auto bracket |
| `multi_exp` | `drive_multi_exp.png` | Multiple exposure |
| `interval` | `drive_interval.png` | Interval timer |
| `multi_exp_interval` | `drive_multi_exp_interval.png` | Multi-exp + interval |

**Drive Mode Enum (BLE `B29E6DE3`):**

The BLE Drive Mode characteristic exposes a **0–65** enum that includes self-timer and remote
variants (see dm-zharov Drive Mode table). The WebSocket/UI still uses the 6 base drive modes
above.

**Time/Bulb Shooting State (`TimeShootingState`):**

For Bulb (`B`), Time (`T`), and Bulb/Time (`BT`) exposure modes, the shutter button acts as a
toggle: first press starts the exposure, second press stops it. The `TimeShootingState` enum tracks
this state for the UI (spinner animation during exposure).

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

**6 base drive modes** (shown in WebSocket and UI):

| Mode               | Internal ID          | Asset                          |
|:-------------------|:---------------------|:-------------------------------|
| Single             | `single`             | `drive_single.png`             |
| Continuous         | `continuous`         | `drive_continuous.png`         |
| Auto Bracket       | `auto_bkt`           | `drive_auto_bkt.png`           |
| Multi-exposure     | `multi_exp`          | `drive_multi_exp.png`          |
| Interval           | `interval`           | `drive_interval.png`           |
| Multi-exp Interval | `multi_exp_interval` | `drive_multi_exp_interval.png` |

> **See also:** The BLE **Drive Mode** characteristic is `B29E6DE3` and exposes a 0–65 enum with
> self-timer and remote variants. See dm-zharov for the full mapping.

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
