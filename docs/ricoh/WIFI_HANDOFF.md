# Ricoh Wi-Fi Handoff (Phase 2: "Transfer" Mode)

When the user taps "Import Photos" or "Remote Shutter", the app needs high bandwidth. This document
describes enabling the camera's WLAN, reading credentials over BLE, and binding the Android network
to the camera AP.

**Prerequisites:** BLE connected and Step 4 handshake complete. See [README.md](README.md) §3.

---

## 4.1. Enabling Camera WLAN (BLE Command)

Before connecting to Wi-Fi, you may need to turn on the camera's WLAN function via BLE:

**Command Characteristic:** `A3C51525-DE3E-4777-A1C2-699E28736FCF`

**WLAN Control Command Model:**

```dart
WLANControlCommandModel(
  networkType: String,  // "wifi"
  wlanFreq: int         // 0=2.4GHz, 1=5GHz
)
```

**Write Operation:**

1. Serialize command to bytes
2. Write to characteristic
3. Wait for notification confirming WLAN enabled

**Error Cases:**

- `step2_ble_wlan_on_failure` - Failed to enable WLAN
- WLAN cannot be enabled if camera is in Movie mode
- WLAN cannot be enabled if camera is connected via USB

**Related Logs:**

- "write wlanFrequency"
- "write wifiInfo: ..."

---

## 4.2. Getting Credentials (BLE)

You need the SSID and Password.

**Wi-Fi Config Characteristic:** `5f0a7ba9-ae46-4645-abac-58ab2a1f4fe4`

**Read Operation:**

1. Read characteristic value
2. Parse response for SSID and password

**Response Format (presumed):**
The characteristic likely returns WiFi credentials in a structured format (binary or JSON). Reverse engineering (e.g., BLE sniffing) may be required to parse it. It likely contains the `ssid` and `password` fields similar to the HTTP `/v1/props` response.

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

## 4.3. WLAN Frequency Settings

The camera supports different frequency bands:

* **2.4 GHz** - Better compatibility, longer range
* **5 GHz** - Faster speeds, less interference

The `wlan_frequency` parameter is stored in the device database and can be changed via camera
settings.

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
