# Wi‑Fi–Based Remote Shutter: Remaining Implementation Plan

This document describes the **remaining work** to support remote shutter (and related “Full” mode features) over Wi‑Fi, so it can be implemented at a later date without the original design plan. The capability model, delegate interface, BLE-only remote shutter, and UI scaffolding are already in place.

---

## 1. What’s Already Done

- **Remote control capability model**  
  `RemoteControlCapabilities` (and sub-types like `RemoteCaptureCapabilities`, `ConnectionModeSupport`, `SyncCapabilities`) are defined. Vendors declare `connectionModeSupport.wifiAddsFeatures = true` and which features require Wi‑Fi.

- **RemoteControlDelegate interface**  
  Includes `connectionMode: StateFlow<ShootingConnectionMode>`, `triggerCapture()`, `connectWifi()`, `disconnectWifi()`, and Wi‑Fi–only extensions (e.g. `touchAF`, `observeLiveView()`). See `app/.../domain/vendor/RemoteControlDelegate.kt`.

- **BLE-only remote shutter (Sony)**  
  `SonyRemoteControlDelegate` implements the event-driven sequence (half-press → focus/timeout → full press → shutter/timeout → release) over BLE FF01/FF02. Timeout tests use an injected `captureScope` for virtual time.

- **BLE-only remote shutter (Ricoh)**  
  `RicohRemoteControlDelegate` triggers capture via BLE write to the command characteristic.

- **Remote shooting UI**  
  `RemoteShootingScreen` shows connection banner, status bar (battery, storage), capture button, shooting settings (mode/drive/focus icons), and conditional sections for live view / advanced controls / image control when capabilities and connection mode allow. **Full mode (Wi‑Fi) is intentionally not switchable yet:** the banner shows “Full mode (Wi‑Fi) coming soon” and the “Connect Wi‑Fi” button is not shown.

- **Migration**  
  All sync-related logic uses `getRemoteControlCapabilities().sync`; legacy `CameraCapabilities` and `getCapabilities()` have been removed.

---

## 2. High-Level Remaining Goal

Enable **ShootingConnectionMode.FULL** (Wi‑Fi) so that:

1. The user can tap “Connect Wi‑Fi” (or equivalent) in the remote shooting screen.
2. The app performs vendor-specific Wi‑Fi activation and credential exchange over BLE, connects the device to the camera’s AP (or network), then establishes the data channel (PTP/IP for Sony, HTTP for Ricoh).
3. When in FULL mode, `triggerCapture()` and other delegate methods use the Wi‑Fi path (PTP/IP or HTTP) instead of BLE where applicable.
4. The UI can show live view, touch AF, image browsing, etc., when the vendor supports them and the mode is FULL.
5. The user can disconnect Wi‑Fi to return to BLE-only and save battery.

---

## 3. Prerequisites and Shared Infrastructure

### 3.1 Android Wi‑Fi / Network Binding

- Use `ConnectivityManager.requestNetwork()` with `WifiNetworkSpecifier` (SSID, passphrase, optional BSSID) to connect to the camera’s AP without taking over global routing.
- Prefer **app-only routing** so only the app’s sockets use the camera network: `ConnectivityManager.bindProcessToNetwork(network)` (Android 12+). See Sony `REMOTE_CAPTURE.md` §5.4.
- Handle timeouts (e.g. 30 s Wi‑Fi on, 15 s credential read), and errors: `ConnectionTimeOut`, `WifiOff`, `ErrorAuthenticating`, etc.
- **Permissions:** `ACCESS_FINE_LOCATION` (often required for Wi‑Fi scanning), `CHANGE_WIFI_STATE`, `ACCESS_WIFI_STATE`. Ensure manifest and runtime permissions are documented and requested where needed.

### 3.2 Credential and State Storage (Optional but Recommended)

- Cache SSID/passphrase per camera (e.g. by MAC or device id) so the user doesn’t re-enter after the first successful Wi‑Fi connection. Persist only in app-private storage; do not log credentials.
- Consider storing “last used network” or “prefer 5 GHz” per vendor if the protocol supports it (e.g. Ricoh WLAN frequency, Sony read of `CCAB`).

### 3.3 Re-enable Full-Mode Switch in UI

- In `RemoteShootingScreen.kt`, `ConnectionBanner`: replace the “Full mode (Wi‑Fi) coming soon” text with the actual “Connect Wi‑Fi for Full Features” button that calls `onConnectWifi` (already passed from the parent; only the button is currently hidden).
- Ensure `delegate.connectWifi()` is invoked from a coroutine (already wired as `scope.launch { delegate.connectWifi() }` when the button exists).
- After a successful `connectWifi()`, `delegate.connectionMode` will emit `ShootingConnectionMode.FULL`; the UI already branches on `connectionMode` for live view, advanced controls, and image control.
- Keep the “Disconnect Wi‑Fi (save battery)” path calling `delegate.disconnectWifi()` when mode is FULL.

---

## 4. Sony: Wi‑Fi Activation and PTP/IP Remote Shutter

### 4.1 Reference Documentation

- **`docs/sony/REMOTE_CAPTURE.md`** — End-to-end flow: BLE discovery → Wi‑Fi activation (CC08, CC06/CC07) → Wi‑Fi association → SSDP → PTP/IP init → remote capture (S1/S2, RequestOneShooting, MovieRec).
- **`docs/sony/BLE_STATE_MONITORING.md`** — BLE Remote Control Service (FF00) used for BLE-only shutter; Camera Control Service (CC00) for Wi‑Fi handoff.
- **`docs/sony/README.md`** — Document index and quick refs.

### 4.2 BLE Wi‑Fi Activation (Sony)

- **Service:** `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF` (Camera Control Service).
- **Turn Wi‑Fi on:** Write `{0x01}` to characteristic `0000CC08`. Wait for camera to report Wi‑Fi on (observe Wi‑Fi status if available); timeout ~30 s.
- **Read credentials:** Read `0000CC06` (SSID), `0000CC07` (password), optionally `0000CC0C` (BSSID). SSID/password start at byte index 3 (US-ASCII). Timeout ~15 s.
- **Error handling:** Timeout, GATT errors, camera “NoMedia” or similar. Map to user-visible messages or retry policy.

### 4.3 Wi‑Fi Association and SSDP (Sony)

- Create `WifiNetworkSpecifier` with SSID and WPA2 passphrase (and BSSID if read). Use `ConnectivityManager.requestNetwork()` and, on success, `bindProcessToNetwork(network)` so only the app uses the camera AP.
- **SSDP discovery:** Send M-SEARCH for `urn:schemas-sony-com:service:ScalarWebAPI:1` (multicast `239.255.255.250:1900`). Parse `LOCATION` header to get `http://<camera-ip>:8080/description.xml`. Extract camera IP for PTP/IP (TCP port 15740).

### 4.4 PTP/IP Session (Sony)

- **Port:** TCP 15740.
- **Command channel:** Send `InitCommandRequest` (Type 0x01) with client GUID (16 bytes, stable per install), friendly name (UTF-16LE), protocol version `0x00010000`. Receive `InitCommandAck` (Type 0x02) and connection number.
- **Event channel:** Second TCP connection to same port. Send `InitEventRequest` (Type 0x03) with connection number; receive `InitEventAck` (Type 0x04). **Important:** Respond to `ProbeRequest` (Type 0x0D) with `ProbeResponse` (Type 0x0E) on the event channel; otherwise the session can fail.
- **Function mode:** Use `REMOTE_CONTROL_MODE` (or `REMOTE_CONTROL_WITH_TRANSFER_MODE` if the camera supports it and transfer is needed later). Session and mode setup are described in REMOTE_CAPTURE.md §7.

### 4.5 Remote Shutter Over PTP/IP (Sony)

- **Control codes:** Sent via PTP/IP (e.g. `SDIO_ControlDevice`). Relevant codes:
  - **RequestOneShooting** (53959) — Single-shot capture (no separate S1/S2).
  - **S1Button** (53953) — Half-press AF (press=1, release=2).
  - **S2Button** (53954) — Full-press shutter (press=1, release=2).
- **Typical still capture:** Either `RequestOneShooting`, or `pressButton(S1)` → (optional wait for focus) → `pressButton(S2)` → `releaseButton(S2)` → `releaseButton(S1)`.
- **ShootingController** (from Creators’ App) maintains shooting mode (Still, Movie, Continuous, Bulb, etc.); different modes may require different sequences. Start with Still mode: `RequestOneShooting` or S1/S2 pair.
- **Movie:** `MovieRecButton` (53960) toggle to start/stop recording.

### 4.6 Implementation Tasks (Sony)

1. **Implement `SonyRemoteControlDelegate.connectWifi()`**  
   Sequence: (1) Write CC08 `{0x01}` and wait for Wi‑Fi on, (2) Read CC06/CC07/CC0C and parse SSID/password/BSSID, (3) Call into a shared or Sony-specific Wi‑Fi connector that uses `ConnectivityManager.requestNetwork` + `bindProcessToNetwork`, (4) Run SSDP discovery to get camera IP, (5) Open PTP/IP command and event channels and perform init handshake (including ProbeResponse), (6) Set function mode to REMOTE_CONTROL, (7) set `_connectionMode.value = ShootingConnectionMode.FULL`.

2. **Implement `SonyRemoteControlDelegate.disconnectWifi()`**  
   Tear down PTP/IP (close sockets), release network (e.g. `ConnectivityManager.bindProcessToNetwork(null)` or equivalent), optionally command camera to turn Wi‑Fi off via BLE if desired. Set `_connectionMode.value = ShootingConnectionMode.BLE_ONLY`.

3. **Implement `triggerCapture()` when `connectionMode == FULL`**  
   Use PTP/IP to send `RequestOneShooting` or S1/S2 sequence (depending on shooting mode and preference). Do not use BLE FF01 in this branch.

4. **Optional but recommended:**  
   Implement start/stop bulb, video record toggle, and other PTP/IP control codes in the delegate so the same UI can drive them when in FULL mode. Document which PTP/IP operations are implemented.

5. **Tests:**  
   Unit tests for credential parsing, SSDP parsing, and PTP/IP init/probe can be done with mocked sockets or canned byte streams. Integration tests with a real camera or a mock TCP server that speaks PTP/IP will help validate the full path.

---

## 5. Ricoh: Wi‑Fi Activation and HTTP Remote Shutter

### 5.1 Reference Documentation

- **`docs/ricoh/README.md`** — BLE UUIDs, dual-transport architecture, remote shutter (BLE write to `A3C51525`), Wi‑Fi handoff concept.
- **`docs/ricoh/WIFI_HANDOFF.md`** — Enabling camera WLAN via BLE (command characteristic `A3C51525`, WLAN control command model: networkType, wlanFreq), reading Wi‑Fi config from characteristic `5f0a7ba9` (SSID/password). Fallback to manual connection and `GET http://192.168.0.1/v1/props` to verify and cache credentials.
- **`docs/ricoh/HTTP_WEBSOCKET.md`** — HTTP API base `http://192.168.0.1`: `POST /v1/camera/shoot` to trigger shutter; capture status, shooting mode, drive mode, etc. Ricoh remote shutter is single-step (no half-press AF); BLE or HTTP can trigger it.

### 5.2 BLE Wi‑Fi Activation (Ricoh)

- **WLAN on:** Write to `A3C51525-DE3E-4777-A1C2-699E28736FCF` a WLAN control command (networkType `"wifi"`, wlanFreq 0 or 1 for 2.4/5 GHz). Serialize per Ricoh spec; wait for notification confirming WLAN enabled. Error: e.g. `step2_ble_wlan_on_failure`; WLAN cannot be enabled in Movie mode or when USB connected.
- **Credentials:** Read characteristic `5f0a7ba9-ae46-4645-abac-58ab2a1f4fe4` and parse SSID/password (format may require reverse engineering or docs). If read fails, support manual connection and then `GET http://192.168.0.1/v1/props` to verify and cache credentials.

### 5.3 Wi‑Fi Association and HTTP (Ricoh)

- Use the same Android pattern: `WifiNetworkSpecifier` + `requestNetwork()` + `bindProcessToNetwork(network)`. No SSDP; Ricoh cameras typically use a fixed IP (e.g. `192.168.0.1`) when in AP mode. Confirm default in docs or from device.
- **Verify connection:** `GET http://192.168.0.1/v1/props` (or similar) to confirm the app can reach the camera. Cache props (e.g. ssid, key) for future auto-connect if desired.

### 5.4 Remote Shutter Over HTTP (Ricoh)

- **Endpoint:** `POST /v1/camera/shoot` (see HTTP_WEBSOCKET.md). Single-shot trigger; no S1/S2. Optional: observe capture status via WebSocket or polling if the app needs to disable the shutter button during “capture”.
- **Bulb/Time:** For Bulb (B), Time (T), Bulb/Time (BT), the shutter button is typically hold-to-expose; document whether Ricoh HTTP supports a “start/stop” shoot or only a single trigger, and implement accordingly.

### 5.5 Implementation Tasks (Ricoh)

1. **Implement `RicohRemoteControlDelegate.connectWifi()`**  
   (1) Send BLE WLAN-on command to `A3C51525` and wait for confirmation, (2) Read `5f0a7ba9` for SSID/password (or use manual + GET /v1/props), (3) Connect Android to camera AP via `requestNetwork` + `bindProcessToNetwork`, (4) Verify with GET to camera base URL, (5) set `_connectionMode.value = ShootingConnectionMode.FULL`.

2. **Implement `RicohRemoteControlDelegate.disconnectWifi()`**  
   Release network binding; optionally send BLE command to turn WLAN off. Set `_connectionMode.value = ShootingConnectionMode.BLE_ONLY`.

3. **Implement `triggerCapture()` when `connectionMode == FULL`**  
   Use HTTP client (e.g. OkHttp or Ktor) to `POST http://192.168.0.1/v1/camera/shoot` (or the correct base URL from props). Handle timeouts and HTTP errors.

4. **Optional:**  
   Use WebSocket or polling for capture status so the UI can show “capturing” vs “idle”. Document where this is used (e.g. disable shutter button during capture).

5. **Tests:**  
   Unit tests for BLE command encoding and credential parsing; use a mock HTTP server for POST /v1/camera/shoot in tests.

---

## 6. Delegate and UI Consistency

- **Connection mode source of truth:** Each delegate holds `_connectionMode: MutableStateFlow<ShootingConnectionMode>`. BLE-only starts as default; after successful `connectWifi()` set to FULL; after `disconnectWifi()` set to BLE_ONLY. The UI already collects `delegate.connectionMode` and shows the correct banner and sections.
- **triggerCapture():** In both Sony and Ricoh delegates, the existing `if (connectionMode == FULL)` branch must be implemented (Sony: PTP/IP; Ricoh: HTTP). BLE branch is already implemented.
- **Wi‑Fi–only methods:** `touchAF`, `observeLiveView()`, `toggleAELock()`, etc., already have stubs or early returns when `connectionMode != FULL`. Once PTP/IP (Sony) or HTTP/WebSocket (Ricoh) is available, implement those that the vendor supports and that the UI exposes based on capabilities.
- **Error handling:** If `connectWifi()` fails (timeout, auth failure, no network), surface a clear message (and optionally retry). Do not set mode to FULL. Consider storing “last failure” for debugging or user guidance.

---

## 7. Testing and Validation

- **Unit tests:** Credential parsing (Sony CC06/CC07, Ricoh 5f0a7ba9), SSDP parsing (Sony), PTP/IP init/probe (Sony with mocked sockets), HTTP shoot (Ricoh with mock server). Use injected dispatchers/scopes where async or timeouts are involved.
- **Integration / device tests:** With a real Sony and Ricoh camera: BLE connection → Connect Wi‑Fi → verify FULL mode → trigger capture over Wi‑Fi → disconnect Wi‑Fi → verify BLE-only again. Document any model-specific quirks (e.g. ILCE-7M4 vs ZV-E10).
- **UI:** Re-enable the Connect Wi‑Fi button and run through the flow; confirm banner, capture button, and optional live view/advanced panels behave when switching between BLE-only and FULL.

---

## 8. Out of Scope for “Wi‑Fi Remote Shutter” (Can Be Separate Follow-Ups)

- **Live view stream:** Sony PTP/IP `SetLiveViewEnable` and frame handling; Ricoh WebSocket or HTTP for preview if available. Requires decoding and rendering pipeline.
- **Image browsing and transfer:** Sony PTP/IP GetContentsInfoList and object get; Ricoh HTTP photo listing and download. Covered by capability model but not required for “remote shutter” per se.
- **Touch AF, AEL, FEL, etc.:** Implement when FULL mode and PTP/IP (or Ricoh equivalent) are in place; delegate stubs already exist.
- **Firmware OTA over Wi‑Fi:** Separate feature; see docs (e.g. Ricoh CLOUD_SERVICES, firmware upload flows).

---

## 9. Summary Checklist (Remaining Work)

- [ ] **Shared:** Document/implement app-only network binding (requestNetwork + bindProcessToNetwork), permissions, and optional credential caching.
- [ ] **UI:** Re-enable “Connect Wi‑Fi for Full Features” in `ConnectionBanner`; keep “Disconnect Wi‑Fi” when in FULL.
- [ ] **Sony:** Implement `connectWifi()` (CC08 → CC06/CC07/CC0C → Wi‑Fi → SSDP → PTP/IP init + probe).
- [ ] **Sony:** Implement `disconnectWifi()` (tear down PTP/IP and network).
- [ ] **Sony:** Implement `triggerCapture()` in FULL mode via PTP/IP (RequestOneShooting or S1/S2).
- [ ] **Ricoh:** Implement `connectWifi()` (WLAN-on BLE → read credentials → Wi‑Fi → verify HTTP).
- [ ] **Ricoh:** Implement `disconnectWifi()` (release network, optional WLAN off).
- [ ] **Ricoh:** Implement `triggerCapture()` in FULL mode via `POST /v1/camera/shoot`.
- [ ] **Tests:** Unit tests for parsing and mocked PTP/IP/HTTP; device/integration tests for full path.
- [ ] **Docs:** Update AGENTS.md or README if Wi‑Fi becomes a supported user-facing feature; document any new permissions or OEM quirks.

This plan is intended to be self-contained so that Wi‑Fi–based remote shutter can be completed later without the original design plan file.
