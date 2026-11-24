# External Ricoh Wireless References

Community reverse‑engineered specs and tools. Use to cross-check BLE/Wi‑Fi behavior and fill gaps in the in-repo docs.

---

## Primary BLE / Protocol Specs

### [dm-zharov/ricoh-gr-bluetooth-api](https://github.com/dm-zharov/ricoh-gr-bluetooth-api)

**RICOH GR Bluetooth API** — Unofficial list of GATT characteristics and values (GR II/III/IIIx, G900 SE, WG-M2, PENTAX K1/K-3/K-70/KF/KP). Sourced from reverse engineering and [RICOH THETA API](https://github.com/ricohapi/theta-api-specs). **Use at your own risk.**

- [Characteristics list](https://github.com/dm-zharov/ricoh-gr-bluetooth-api/blob/main/characteristics_list.md) — full table of services/characteristics and R/W/Notify.
- **Shooting → Operation Request**  
  - Service: `9F00F387-8345-4BBC-8B92-B87B52E3091A`  
  - Characteristic: `559644B8-E0BC-4011-929B-5CF9199851E7`  
  - Format: 2 bytes `[OperationCode, Parameter]`  
  - **OperationCode:** `0` = NOP, `1` = Start Shooting/Recording, `2` = Stop Shooting/Recording  
  - **Parameter:** `0` = No AF, `1` = AF, `2` = Green Button Function  
  - Example: Start with AF → write `[0x01, 0x01]`; Stop → `[0x02, 0x00]`.
- **Camera → Camera Power**  
  - Service: `4B445988-CAA0-4DD3-941D-37B4F52ACA86`  
  - Characteristic: `B58CE84C-0666-4DE9-BEC8-2D27B27B3211`  
  - Value: `0` = Off, `1` = On, `2` = Sleep.
- **WLAN Control → Network Type**  
  - Service: `F37F568F-9071-445D-A938-5441F2E82399`  
  - Characteristic: `9111CDD0-9F01-45C4-A2D4-E09E8FB0424D`  
  - Value: `0` = OFF, `1` = AP mode.
- **WLAN Control → SSID**  
  - Same service; Characteristic: `90638E5A-E77D-409D-B550-78F7E1CA5AB4` (utf8s).
- **Bluetooth Control → BLE Enable Condition**  
  - Service: `0F291746-0C80-4726-87A7-3C501FD3B4B6`  
  - Characteristic: `D8676C92-DC4E-4D9E-ACCE-B9E251DDCC0C`  
  - Value: `0` = Disable, `1` = On anytime, `2` = On when power is on.
- **Shooting → Capture Mode**  
  - Service: `9F00F387-8345-4BBC-8B92-B87B52E3091A`  
  - Characteristic: `78009238-AC3D-4370-9B6F-C9CE2F4E3CA8`  
  - Value: `0` = Still, `2` = Movie.

**Relation to this app:** We follow the dm-zharov spec for remote shutter: we use **Operation Request**
`559644B8` (Shooting service `9F00F387`) with 2-byte `[OperationCode, Parameter]` (Start=1, Stop=2;
AF=1, No AF=0). **Drive Mode** is `B29E6DE3`; **Shooting Mode** is `A3C51525`. For WLAN on/off, use
**Network Type** `9111CDD0` (service `F37F568F`), value 1=AP. Camera power off: **Camera Power**
`B58CE84C` (Camera service), value 0. HCI snoop is only needed if device testing shows issues;
iterate then.

---

## Wireless Protocol & Wi‑Fi

### [CursedHardware/ricoh-wireless-protocol](https://github.com/CursedHardware/ricoh-wireless-protocol)

**RICOH Camera Wireless Protocol** — Reverse engineered from Image Sync 2.1.17. Wi‑Fi control plane documented (OpenAPI 3.0.3 in `openapi.yaml`); Bluetooth control plane in progress. Contains `definitions` (from app `res/raw`) and scripts.
Notable OpenAPI endpoints: `/v1/photos`, `/v1/photos/{dir}/{file}`, `/v1/photos/{dir}/{file}/info`,
`/v1/transfers`, `/v1/props`, `/v1/ping`, `/v1/liveview`, `/v1/device/finish`, `/v1/device/wlan/finish`.

### [jkbrzt/grfs](https://github.com/jkbrzt/grfs)

FUSE filesystem for **Ricoh GR II over Wi‑Fi** (read-only). Uses the camera’s HTTP API; no BLE. Useful for understanding the Wi‑Fi/HTTP side.

### [clyang/GRsync](https://github.com/clyang/GRsync)

**Sync photos from GR II / GR III via Wi‑Fi** (Python). Connects to camera AP and downloads images; supports GR IIIx. Complements [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) and [WIFI_HANDOFF.md](WIFI_HANDOFF.md).

---

## Tools & Firmware

### [yeahnope/gr_unpack](https://github.com/yeahnope/gr_unpack)

**Ricoh GR III firmware unpacker** — Documents the packing format of GR III firmware images (header, frames, compression). No direct protocol impact; useful for low-level analysis.

### [adriantache/GReat-Image-Downloader](https://github.com/adriantache/GReat-Image-Downloader)

Android app that connects to **GR III / IIIx** over Wi‑Fi to download images (Kotlin, Clean Architecture). Good reference for Wi‑Fi flow and UX; no BLE remote control.

---

## How we use this

- **BLE command bytes / UUIDs:** Implementation is aligned with dm-zharov (Operation Request for shutter, Camera Power, Network Type). Use snoop only to verify or debug if behaviour differs on device.
- **Wi‑Fi handoff:** Align [WIFI_HANDOFF.md](WIFI_HANDOFF.md) with CursedHardware’s OpenAPI and GRsync/grfs HTTP usage where relevant.
- **Firmware / definitions:** Use CursedHardware definitions and gr_unpack only when digging into firmware or app resource formats.
