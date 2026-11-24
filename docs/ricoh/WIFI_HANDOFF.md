# Ricoh Wi-Fi Handoff (Phase 2: "Transfer" Mode)

When the user taps "Import Photos" or "Remote Shutter", the app needs high bandwidth. This document
describes enabling the camera's WLAN, reading credentials over BLE, and binding the Android network
to the camera AP.

**Prerequisites:** BLE connected and Step 4 handshake complete. See [README.md](README.md) §3.

---

## 4.1. Enabling Camera WLAN (BLE Command)

Before connecting to Wi-Fi, turn on the camera's WLAN via the **WLAN Control Command** service
`F37F568F-9071-445D-A938-5441F2E82399`.

**Network Type Characteristic:** `9111CDD0-9F01-45C4-A2D4-E09E8FB0424D`

**Write Operation:**

1. Write `1` to enable AP mode (write `0` to disable).
2. Wait for the next camera/shooting notification to confirm state (or poll readback).

**Error Cases:**

- `step2_ble_wlan_on_failure` - Failed to enable WLAN
- WLAN cannot be enabled if camera is in Movie mode
- WLAN cannot be enabled if camera is connected via USB

**Related Logs:**

- "write wlanFrequency"
- "write wifiInfo: ..."

---

## 4.2. Getting Credentials (BLE)

You can read credentials directly from the WLAN Control Command service:

- **SSID:** `90638E5A-E77D-409D-B550-78F7E1CA5AB4` (UTF-8 string)
- **Passphrase:** `0F38279C-FE9E-461B-8596-81287E8C9A81` (UTF-8 string)
- **Channel:** `51DE6EBC-0F22-4357-87E4-B1FA1D385AB8` (0=Auto, 1-11)

**Read Operation:**

1. Read SSID and Passphrase characteristics.
2. Optionally read channel (if you need a fixed channel hint).

**Fallback Strategy (Manual Connection):**
If the BLE credential read fails or is unimplemented:

1. **Prompt User:** "Please manually connect to the camera Wi-Fi." (User finds SSID/Password on camera screen: Settings → Wi-Fi Info).
2. **User Action:** User connects phone to the Camera AP in Android settings.
3. **App Detection:** App detects the connection via `ConnectivityManager`.
4. **Fetch & Cache:** App makes an HTTP request to `GET http://192.168.0.1/v1/props` to verify the connection and retrieve/cache the `ssid` and `key` (password).
5. **Future Use:** The app can now use these cached credentials for automatic connection in the future.

**Related Logs:**

- "write wifiInfo: ..."

---

## 4.3. WLAN Channel Settings

The WLAN Control Command service exposes a **Channel** characteristic (`51DE6EBC`):

- `0` = Auto
- `1`–`11` = specific channel

The camera still negotiates 2.4 GHz Wi-Fi; no public BLE characteristic exposes a 5 GHz toggle.

---

## 4.4. Android Network Binding

**Library:** `ConnectivityManager`, `WifiNetworkSpecifier`

**The "Pre-Check":**
Before firing up the heavy network requester, try a `GET http://192.168.0.1/v1/props` (Timeout: 3s).
If it returns 200 OK, *you are already connected*. Save 10 seconds of user time.

**The "Request":**

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid(ssid)
    .setWpa2Passphrase(password) // Note: Retry with Wpa3Passphrase if this fails!
    .build()
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)     // = 1
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // = 12, forces traffic to local AP
    .setNetworkSpecifier(specifier)
    .build()

// Request network with 60-second timeout
connectivityManager.requestNetwork(request, networkCallback)
handler.postDelayed(timeoutRunnable, 60000L)
```

**The "WPA3 Dance":**
The official app explicitly handles a "WPA3 Transition Mode" issue.

1. Try connection with `securityType` (WPA2 or WPA3).
2. If `onUnavailable` fires, **retry immediately** with the *other* security type.

**GR II note:** GR II only supports 20 MHz 802.11n; real-world throughput tops out around 65 Mbps.

---

## 4.5. Verification

Once `onAvailable` fires, **bind the process** (
`connectivityManager.bindProcessToNetwork(network)`). Then, verify the target:

`GET http://192.168.0.1/v1/props`

```json
{
  "errCode": 200,
  "model": "RICOH GR III",
  "serialNo": "12345678",
  "ssid": "RICOH_1234",
  "firmwareVersion": "1.50"
}
```

*Check:* Does `model` contain "RICOH"? If not, you connected to a rogue AP. Abort.
