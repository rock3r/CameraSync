# Sony Camera Protocol Documentation

Documentation for Sony Alpha / ZV series BLE and Wi‑Fi protocols used by CameraSync and for
remote shooting design. Sources: decompiled Creators' App, public reverse-engineering (Greg Leeds,
freemote, HYPOXIC), and alpha-shot.

## Document Index

| Document | Scope |
|----------|--------|
| **[BLE_STATE_MONITORING.md](BLE_STATE_MONITORING.md)** | **BLE protocol (single reference for BLE-only behaviour).** Remote Control Service `8000FF00` (shutter, focus, zoom, record, custom buttons; full command/notification tables, sequences, implementation notes). Camera Control Service `8000CC00` (battery, status, media, Wi‑Fi handoff). Location `8000DD00`, Pairing `8000EE00`. Discovery & advertising (manufacturer data, tags 0x22/0x21, pairing detection). Refs: Greg Leeds, freemote, HYPOXIC, alpha-shot. |
| **[REMOTE_CAPTURE.md](REMOTE_CAPTURE.md)** | **Wi‑Fi / PTP/IP path.** BLE discovery → Wi‑Fi activation (CC08, SSID/password read) → SSDP → PTP/IP init → remote capture (S1/S2, RequestOneShooting, MovieRec, control codes, device properties) and media transfer. Includes minimal checklist for BLE-only (Path A) vs full Wi‑Fi (Path B). |
| **[DATETIME_GPS_SYNC.md](DATETIME_GPS_SYNC.md)** | Date/time and GPS sync over BLE (Location service `8000DD00`). |
| **[FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md)** | Firmware version and update detection (e.g. CC0A, version comparison). |
| **[DOWNLOAD_PICS.md](DOWNLOAD_PICS.md)** | Downloading images (Wi‑Fi/PTP/IP and transfer flows). |
| **[CAMERA_PICS.md](CAMERA_PICS.md)** | Camera images / media-related behaviour. |

## Quick reference: BLE remote shooting

- **Service:** `8000FF00`, **Command char:** `FF01` (write), **Notify char:** `FF02` (subscribe).
- **Still (AF):** `0x0107` → wait `[0x02,0x3F,0x20]` → `0x0109` → wait `[0x02,0xA0,0x20]` → `0x0108` → `0x0106`.
- **Record:** `0x010E` (toggle). **Zoom:** `[0x02, 0x6D, speed]` / `[0x02, 0x6C, 0x00]`. **Focus (MF):** `[0x02, 0x47, speed]` / `[0x02, 0x46, 0x00]`.
- **Pairing:** BLE bond required; invalid command → disconnect; GATT error `0x0185`. Use write-with-response for `FF01`.

Full tables, step-size ranges, and discovery/pairing details are in **BLE_STATE_MONITORING.md**.
