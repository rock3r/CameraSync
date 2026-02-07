# Sony Camera BLE Protocol

**Target Audience:** Native Android developers implementing Sony Camera Remote SDK features via BLE.
**Scope:** Specification of BLE services for remote shooting, monitoring battery/camera status, and
media info.

## 1. Architecture Overview

Sony cameras (Alpha series, ZV series) expose **two separate BLE services**:

1. **Camera Remote Control Service** (`8000FF00`) — BLE-based remote shutter with focus control
2. **Camera Control Service** (`8000CC00`) — Monitoring (battery, storage, status) and Wi-Fi handoff

The Remote Control Service enables BLE-only remote shooting without Wi-Fi. The Camera Control
Service provides state monitoring and the handoff to Wi-Fi/PTP/IP for advanced features (live view,
device property control, media transfer).

Additionally, cameras expose two more services:
3. **Camera Location Service** (`8000DD00`) — GPS/time push from phone to camera
4. **Camera Pairing Service** (`8000EE00`) — Pairing and power-off commands

### What BLE can do (no Wi-Fi):
1. **Remote Shutter with half-press AF** (focus → capture → release)
2. **Video Recording** (toggle start/stop)
3. **AF-ON** (separate AF trigger, customizable button)
4. **Zoom** (tele/wide, variable speed via step size)
5. **Manual Focus** (near/far, variable speed via step size)
6. **Custom Button (C1)** (camera-assignable function)
7. **Monitor Battery Level** (notifications)
8. **Monitor Camera Status** (Power, Wi-Fi, Recording state)
9. **Monitor Storage Status** (SD card, remaining shots/time)
10. **Negotiate Wi-Fi Credentials** (for high-bandwidth handover)

---

## 2. Camera Remote Control Service (`8000FF00`)

**Service UUID:** `8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF`

This is the primary BLE-based shooting service. It emulates the Sony RMT-P1BT Bluetooth remote
and provides shutter, focus, zoom, recording, and custom button control over BLE alone — no Wi-Fi
required. The camera must have "Bluetooth Rmt Ctrl" (or similar) enabled in settings, and the
device must be paired/bonded.

**Note:** When "Bluetooth Rmt Ctrl" is active, the Camera Control Service (`8000CC00`) may have
reduced functionality. The two modes are somewhat mutually exclusive on some camera models.

### 2.1. Characteristics

| Characteristic   | UUID                                   | Access | Description                 |
|:-----------------|:---------------------------------------|:-------|:----------------------------|
| `RemoteCommand`  | `0000FF01-0000-1000-8000-00805F9B34FB` | Write  | Send commands to camera     |
| `RemoteNotify`   | `0000FF02-0000-1000-8000-00805F9B34FB` | Notify | Receive responses/status    |

### 2.2. Command Format

Commands written to `FF01` use a length-prefixed format:

```
+--------+---------+---------------------+
| Length | Command | Step size (optional) |
+--------+---------+---------------------+
```

- **Length** (1 byte): number of bytes following — `0x01` for button commands, `0x02` for
  variable-speed commands (zoom/focus).
- **Command** (1 byte): the action.
- **Step size** (1 byte, optional): speed/magnitude for zoom and focus commands.

### 2.3. Command Table

**Button commands** (2 bytes: `[0x01, command]`):

| Bytes        | Name              | Description                                       |
|:-------------|:------------------|:--------------------------------------------------|
| `0x01 0x06`  | Shutter Half Up   | Release half-press (focus release)                |
| `0x01 0x07`  | Shutter Half Down | Half-press shutter (acquire focus / AF)           |
| `0x01 0x08`  | Shutter Full Up   | Release full-press (shutter button released)      |
| `0x01 0x09`  | Shutter Full Down | Full-press shutter (take picture)                 |
| `0x01 0x0E`  | Record Toggle     | Start/stop video recording (toggle, no Up needed) |
| `0x01 0x0F`  | Record Down       | Record button down (alternative to toggle)        |
| `0x01 0x14`  | AF-ON Up          | Release AF-ON button                              |
| `0x01 0x15`  | AF-ON Down        | Press AF-ON button (back-button focus)            |
| `0x01 0x20`  | C1 Up             | Release custom button C1                          |
| `0x01 0x21`  | C1 Down           | Press custom button C1                            |

**Variable-speed commands** (3 bytes: `[0x02, command, step_size]`):

| Command byte | Name              | Step range   | Description                     |
|:-------------|:------------------|:-------------|:--------------------------------|
| `0x44`       | Focus Out Release | `0x00–0x0F`  | Stop focus far movement         |
| `0x45`       | Focus Out Press   | `0x10–0x8F`  | Focus far (MF) at given speed   |
| `0x46`       | Focus In Release  | `0x00–0x0F`  | Stop focus near movement        |
| `0x47`       | Focus In Press    | `0x10–0x8F`  | Focus near (MF) at given speed  |
| `0x6A`       | Zoom Wide Release | `0x00–0x0F`  | Stop zoom-out                   |
| `0x6B`       | Zoom Wide Press   | `0x10–0x7F`  | Zoom out (wide) at given speed  |
| `0x6C`       | Zoom Tele Release | `0x00–0x0F`  | Stop zoom-in                    |
| `0x6D`       | Zoom Tele Press   | `0x10–0x7F`  | Zoom in (tele) at given speed   |

### 2.4. Notification Table (from `FF02`)

Subscribe to `RemoteNotify` (`FF02`) to receive camera status responses:

| Bytes             | Category    | Meaning             |
|:------------------|:------------|:--------------------|
| `0x02 0x3F 0x00`  | Focus       | Focus lost          |
| `0x02 0x3F 0x20`  | Focus       | Focus acquired      |
| `0x02 0xA0 0x00`  | Shutter     | Shutter ready       |
| `0x02 0xA0 0x20`  | Shutter     | Shutter active      |
| `0x02 0xD5 0x00`  | Recording   | Recording stopped   |
| `0x02 0xD5 0x20`  | Recording   | Recording started   |

### 2.5. Shooting Sequences

**Still capture (with AF):**

1. `0x01 0x07` — Shutter Half Down (acquire focus)
2. Wait for `FF02` notification `[0x02, 0x3F, 0x20]` (focus acquired)
3. `0x01 0x09` — Shutter Full Down (take picture)
4. Wait for `FF02` notification `[0x02, 0xA0, 0x20]` (shutter active / picture taken)
5. `0x01 0x08` — Shutter Full Up
6. `0x01 0x06` — Shutter Half Up

**Important:** Commands must be issued in matched pairs (Down → Up) and in the correct sequence
(Half Down → Full Down → Full Up → Half Up). Skipping the Up commands can leave the camera in
an inoperable state.

**Still capture (manual focus mode — skip AF wait):**

1. `0x01 0x07` — Shutter Half Down
2. `0x01 0x09` — Shutter Full Down (take picture immediately, no focus wait)
3. `0x01 0x08` — Shutter Full Up
4. `0x01 0x06` — Shutter Half Up

In MF mode the focus-acquired notification is irrelevant, so the sequence can be sent without
waiting for `FF02` feedback (though a short delay between commands is advisable).

**Focus-and-hold (separate from shutter):**

For a dedicated "half-press" UX (e.g., hold focus, then shoot separately):

1. `0x01 0x07` — Shutter Half Down (start AF)
2. Wait for `[0x02, 0x3F, 0x20]` (focus acquired)
3. *(user holds... camera maintains focus lock)*
4. When releasing: `0x01 0x08` + `0x01 0x06` — Full Up then Half Up

This allows decoupling focus acquisition from shutter release, useful for focus-recompose
workflows. The freemote implementation uses this pattern with a dedicated focus button.

**Timeout:** The freemote reference implementation uses a 3-second timeout waiting for focus
acquisition. If focus is not acquired within the timeout, it proceeds with the shutter anyway.

**Video recording:**

1. `0x01 0x0E` — Record Toggle (start recording)
2. Wait for `FF02` notification `[0x02, 0xD5, 0x20]` (recording started)
3. `0x01 0x0E` — Record Toggle (stop recording)
4. Wait for `FF02` notification `[0x02, 0xD5, 0x00]` (recording stopped)

**Zoom (example: zoom in):**

1. `0x02 0x6D 0x20` — Zoom Tele Press at speed `0x20`
2. (hold for desired duration)
3. `0x02 0x6C 0x00` — Zoom Tele Release

### 2.6. Camera Compatibility

The `8000FF00` service is supported by cameras that support the Sony RMT-P1BT remote, including
(but not limited to): ILCE-7M3, ILCE-7M4, ILCE-7RM5, ILCE-7C, ILCE-6100, ILCE-6600, ILCE-9M2,
ILCE-1, ZV-1, ZV-E10, RX100 VII. The camera must have the "Bluetooth Rmt Ctrl" setting enabled.

### 2.7. Implementation Notes

**Byte ordering:** The command bytes are written MSB-first as raw byte arrays. When using APIs
that take `uint16` values (e.g., NRF52840 Bluefruit `write16`), note that BLE transmits uint16
in little-endian — so `0x0601` on the wire becomes bytes `[0x01, 0x06]` matching the
`[length, command]` format. Prefer writing raw byte arrays to avoid endianness confusion.

**GATT write type:** The freemote implementation uses `write_with_response` (ATT Write Request)
for all commands to `FF01`. This ensures the camera acknowledges each command before the next
is sent.

**Error response:** The camera returns GATT status `0x0185` if it receives an invalid command.
Invalid commands also cause the camera to disconnect the BLE link immediately. An exhaustive scan
of all command codes `0x00`–`0xFF` for both 2-byte and 3-byte formats confirmed no hidden
commands exist beyond those documented in Section 2.3.

**Pairing requirement:** Unpaired devices are immediately disconnected. The camera must be BLE
bonded (not just connected). Bond information is persisted, so subsequent connections do not
require re-pairing.

### 2.8. References

- [Greg Leeds: Reverse Engineering Sony Camera Bluetooth](https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/)
- [coral/freemote: Sony Alpha BLE Remote Protocol on NRF52840](https://github.com/coral/freemote) — Complete reference implementation with source code
- [HYPOXIC: Sony Camera BLE Control Interface](https://gethypoxic.com/blogs/technical/sony-camera-ble-control-protocol-di-remote-control)
- [alpha-shot: Kotlin Multiplatform Sony BLE remote](https://github.com/nickalvarezpm/alpha-shot) (reference implementation using Kable)

---

## 3. Camera Control Service (`8000CC00`)

**Service UUID:** `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF`

This service handles monitoring (battery, storage, status) and Wi-Fi handoff. It is used by Sony's
Creators' App for PTP/IP-based advanced features. Note that on some camera models, this service
may have reduced functionality when "Bluetooth Rmt Ctrl" is enabled (the two modes can be
mutually exclusive).

### 3.1. Battery Information

* **Characteristic UUID:** `0000CC10-CC00-FFFF-FFFF-FFFFFFFFFFFF`
* **Access:** Read, Notify
* **Purpose:** Real-time battery level and power source monitoring.

#### Data Structure (Byte Array)

The payload is variable length, containing one or more "Battery Packs".

| Offset | Field        | Description                                |
|:-------|:-------------|:-------------------------------------------|
| 0      | Total Length | Total length of the packet                 |
| 1-2    | Data Type    | `0x0000` (Battery Info)                    |
| 3      | Count        | Number of battery packs (usually 1 or 2)   |
| 4+     | Pack 1 Data  | See "Pack Structure" below                 |
| ...    | Pack 2 Data  | (Optional) Vertical grip battery           |
| End    | Power Status | (Optional byte at end) Power supply status |

**Pack Structure (Variable Length):**

| Byte | Bitmask  | Field       | Description                                                               |
|:-----|:---------|:------------|:--------------------------------------------------------------------------|
| 0    | `0x01`   | Enable      | 1 = Battery slot enabled                                                  |
| 0    | `0x02`   | InfoLithium | 1 = InfoLithium supported                                                 |
| 1    | `0xFF`   | Position    | `0x00`=Unknown, `0x01`=Body, `0x02`=Grip1, `0x03`=Grip2                   |
| 2    | `0xFF`   | Status      | `0x02`=Level1, `0x03`=Level2, `0x04`=Level3, `0x05`=Level4, `0x01`=PreEnd |
| 3-6  | `0xFF..` | Remainder   | Battery remaining percentage (Integer, 4 bytes)                           |

**Power Supply Status (Last Byte):**

* `0x00`: Indefinite
* `0x01`: No Power
* `0x02`: Unknown
* `0x03`: Powering (USB Power)

### 3.2. Camera Status (General)

* **Characteristic UUID:** `0000CC09-CC00-FFFF-FFFF-FFFFFFFFFFFF`
* **Access:** Read, Notify
* **Purpose:** Monitors global camera state (Wi-Fi, Recording, Remote Control availability).

#### Data Structure

Parses into a list of status attributes. The structure uses a Tag-Length-Value (TLV) like format.

| Tag (2 bytes) | Description     | Values                                                       |
|:--------------|:----------------|:-------------------------------------------------------------|
| `0x0001`      | Wi-Fi Status    | `0`=Terminated, `1`=Launching, `2`=Launched, `3`=Terminating |
| `0x0002`      | Image Transfer  | `1`=Available, `0`=Unavailable                               |
| `0x0003`      | Remote Control  | `1`=Available, `0`=Unavailable                               |
| `0x0005`      | Time Setting    | `1`=Done, `0`=Not Done                                       |
| `0x0007`      | Live Streaming  | `1`=Started, `0`=Stopped                                     |
| `0x0008`      | Movie Recording | `1`=Started, `0`=Stopped                                     |
| `0x0009`      | Streaming Mode  | `1`=Active, `0`=Inactive                                     |
| `0x000A`      | Bg Transfer     | `1`=Available, `0`=Unavailable                               |

### 3.3. Media Information

* **Characteristic UUID:** `0000CC0F-CC00-FFFF-FFFF-FFFFFFFFFFFF`
* **Access:** Read, Notify
* **Purpose:** Monitors SD card status (Slot 1, Slot 2).

#### Data Structure

Similar to Battery Info, this contains info for multiple slots.

| Field           | Description                                                   |
|:----------------|:--------------------------------------------------------------|
| Status          | `0x00`=No Media, `0x01`=Media Present, `0x02`=Format Required |
| Remaining Shots | Integer (4 bytes)                                             |
| Remaining Time  | Integer (4 bytes, seconds)                                    |

### 3.4. Other Known Characteristics

| UUID (Short) | Name              | Access      | Description                 |
|:-------------|:------------------|:------------|:----------------------------|
| `CC03`       | Push Notification | Notify      | Push transfer status        |
| `CC0E`       | Initial Setting   | Read/Notify | Camera initial setup result |
| `CCA6`       | Lens Info         | Read/Notify | Lens mounting status        |
| `CC0A`       | Firmware Version  | Read        | Camera firmware version (US-ASCII string, from byte index 3) |
| `CCAB`       | Wi-Fi Freq        | Read        | 2.4GHz / 5GHz status        |
| `CC0D`       | Device Info       | Read        | Device model/version info   |

## 4. Camera Location Service (`8000DD00`)

**Service UUID:** `8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF`

This service allows the phone to push GPS location and time data to the camera. Enabled when the
camera's "Location Info. Link Set." is turned on.

| Characteristic              | UUID (Short) | Access      | Description                  |
|:----------------------------|:-------------|:------------|:-----------------------------|
| LocationNotify              | `DD01`       | Notify      | Status notifications         |
| LocationInfo                | `DD11`       | Write       | Push GPS/time data           |
| LocationFeature             | `DD21`       | Read        | Supported location features  |

*This is what CameraSync currently uses for GPS/time synchronization via the Sony vendor.*

## 5. Camera Pairing Service (`8000EE00`)

**Service UUID:** `8000EE00-EE00-FFFF-FFFF-FFFFFFFFFFFF`

| Characteristic              | UUID (Short) | Access      | Description                  |
|:----------------------------|:-------------|:------------|:-----------------------------|
| PairingCharacteristic       | `EE01`       | Write       | Pairing and power commands   |

### Commands (Untested)

| Command                        | Bytes              |
|:-------------------------------|:-------------------|
| Pairing                        | `06 08 01 00 00 00`|
| Pairing with Disconnect        | `06 08 02 00 00 00`|
| Power Off                      | `03 08 13`         |

## 6. Discovery & Advertising

Sony cameras advertise with manufacturer-specific data using Sony's Bluetooth Company ID `0x012D`
(little-endian: `0x2D 0x01`).

### Advertisement Header

| Bytes    | Description                                                     |
|:---------|:----------------------------------------------------------------|
| `2D 01`  | Sony Corporation Company Identifier (`0x012D` LE)               |
| `03 00`  | Device type: Camera                                             |
| `64`     | Protocol version                                                |
| `00`     | Reserved                                                        |
| `XX XX`  | Model Code (ASCII, e.g., `45 31` = "E1" for e-mount)           |

### Status Tags

Tags follow the header in `<tag> <00> <data>` format. Multiple tags can appear.

**Tag `0x22` — Features/Pairing status:**

| Bitmask  | Meaning                   |
|:---------|:--------------------------|
| `0x80`   | Pairing Supported         |
| `0x40`   | Pairing Enabled           |
| `0x20`   | Location Function Supported|
| `0x10`   | Location Function Enabled |
| `0x08`   | Unknown Function Supported|
| `0x04`   | Unknown Function Enabled  |
| `0x02`   | Remote Function Enabled   |
| `0x01`   | Unknown                   |

Common values:
- `0xEF` — Pairing mode active, location not enabled (`0x80 | 0x40 | 0x20 | 0x08 | 0x04 | 0x02 | 0x01`)
- `0xAF` — Not in pairing mode, location not enabled (`0x80 | 0x20 | 0x08 | 0x04 | 0x02 | 0x01`)

**Pairing detection (from freemote source):** To determine if the camera is ready to pair for
BLE remote control, check that **both** `PairingEnabled` (`0x40`) and `RemoteFunctionEnabled`
(`0x02`) bits are set: `(tag_value & 0x40) != 0 && (tag_value & 0x02) != 0`. The first 4 bytes
of manufacturer data (`0x2D 0x01 0x03 0x00`) should be matched to confirm the device is a Sony
camera before checking the tag.

**Tag `0x21` — Power/Wi-Fi state:**

| Bitmask  | Meaning                   |
|:---------|:--------------------------|
| `0x80`   | Wireless Power-On Enabled |
| `0x40`   | Camera On                 |
| `0x20`   | Wi-Fi Handover Supported  |
| `0x10`   | Wi-Fi Handover Enabled    |

## 7. Implementation Guidelines

### For Remote Shooting (Service `8000FF00`):
1. **Pair & Bond:** Camera must be BLE bonded; unparied devices are immediately disconnected.
2. **Enable "Bluetooth Rmt Ctrl"** in camera settings.
3. **Service Discovery:** Look for service `8000FF00...`.
4. **Subscribe to FF02:** Enable notifications on `RemoteNotify` to receive focus/shutter/record
   status feedback.
5. **Send commands to FF01:** Write command bytes as described in Section 2.3.
6. **Always pair Down/Up commands:** Failing to release buttons leaves the camera stuck.
7. **Shutter sequence:** Half Down → Full Down → Full Up → Half Up. Do not skip steps.

### For Monitoring (Service `8000CC00`):
1. **Service Discovery:** Look for service `8000CC00...`.
2. **Notification Enable:** Write `0x0100` to the CCCD (`00002902-0000-1000-8000-00805f9b34fb`)
   for `CC10` (Battery) and `CC09` (Status).
3. **Parsing:** Use the byte-level structures in Section 3. Data is often Big-Endian.
4. **State Machine:**
    * Connect BLE.
    * Enable Notifications for `CC10` & `CC09`.
    * Update UI based on incoming byte arrays.

### For Location Sync (Service `8000DD00`):
1. **Enable "Location Info. Link Set."** in camera settings.
2. **Subscribe to DD01** for status notifications.
3. **Write GPS/time data to DD11** using the protocol defined in the Sony vendor implementation.
