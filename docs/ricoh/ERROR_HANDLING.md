# Ricoh Error Handling Strategy

This document covers HTTP response handling, user-facing error messages, and connection recovery for
the Ricoh GR protocol. Step-based error codes are listed in [README.md](README.md) §3.3.

---

## 11.1. HTTP Response Handling

| Code  | Meaning      | Action                                                          |
|:------|:-------------|:----------------------------------------------------------------|
| `200` | Success      | Process response body                                           |
| `404` | Not Found    | For pagination: end of list; For single resource: doesn't exist |
| `401` | Unauthorized | Re-run Wi-Fi Handoff                                            |
| Other | Error        | Display "GET/PUT error" with response code                      |

---

## 11.2. User-Facing Errors

| Scenario                        | User Message / Action                                                             |
|:--------------------------------|:----------------------------------------------------------------------------------|
| **BLE Scan Empty**              | "Camera not found. Is Bluetooth enabled on the camera?"                           |
| **Step 4 Timeout**              | "Connection handshake failed. Please restart camera."                             |
| **Wi-Fi Timeout**               | "Could not join camera network. Please verify Android Settings."                  |
| **Wi-Fi Connection Failed**     | Try: Turn Wi-Fi off/on, restart phone, forget saved network                       |
| **WLAN Enable Failed**          | "The WLAN function cannot be enabled. Set camera to Photo/Playback mode."         |
| **HTTP 401**                    | "Authentication failed." → Re-run Wi-Fi Handoff.                                 |
| **Firmware Mismatch**           | "Firmware update required. Please update camera manually."                         |
| **Camera Model Incorrect**      | "The camera model is incorrect."                                                  |
| **Battery Too Low**             | "The battery level is low." / "The battery will run out soon."                    |
| **Storage Full**                | Check available storage                                                          |
| **Storage Not Ready**           | "Please make sure the storage media is ready for use, then try again."            |
| **Shooting Mode Error**          | "The camera could not be switched to the shooting mode."                          |
| **Image Control Not Supported** | "The camera does not support the Image Control setting feature. Update firmware."  |

---

## 11.3. Connection Recovery

If WLAN connection fails:

1. Turn smartphone Wi-Fi off, wait a few seconds, turn on again
2. Restart smartphone
3. If camera network is saved as "My Network", delete it and reconnect
4. Verify camera WLAN is enabled (check camera menu)

High-level BLE → WLAN state machine: [README.md](README.md) § State Machine Summary.
