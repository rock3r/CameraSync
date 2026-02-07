# Sony Camera BLE State Monitoring Protocol

**Target Audience:** Native Android developers implementing Sony Camera Remote SDK features via BLE.
**Scope:** Specification of BLE characteristics used for monitoring battery, camera status, and
media info.

## 1. Architecture Overview

Sony cameras (Alpha series, ZV series) use Bluetooth Low Energy (BLE) for persistent low-power
connection and state monitoring. Unlike the high-bandwidth PTP/IP over Wi-Fi, BLE is used to:

1. **Monitor Battery Level** (notifications)
2. **Monitor Camera Status** (Power, Wi-Fi, Recording state)
3. **Negotiate Wi-Fi Credentials** (for high-bandwidth handover)

## 2. BLE Services & Characteristics

**Base Service UUID:** `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF` (The "Camera Control Service")
*Note: Some initial setup operations use `8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF`.*

### 2.1. Battery Information

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

### 2.2. Camera Status (General)

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

### 2.3. Media Information

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

### 2.4. Other Known Characteristics

| UUID (Short) | Name              | Access      | Description                 |
|:-------------|:------------------|:------------|:----------------------------|
| `CC03`       | Push Notification | Notify      | Push transfer status        |
| `CC0E`       | Initial Setting   | Read/Notify | Camera initial setup result |
| `CCA6`       | Lens Info         | Read/Notify | Lens mounting status        |
| `CC0A`       | Focus Status      | Read        | AF status                   |
| `CCAB`       | Wi-Fi Freq        | Read        | 2.4GHz / 5GHz status        |
| `CC0D`       | Device Info       | Read        | Device model/version info   |

## 3. Implementation Guidelines

1. **Service Discovery:** Look for service `8000CC00...`.
2. **Notification Enable:** Write `0x0100` to the Client Characteristic Configuration Descriptor (
   CCCD) `00002902-0000-1000-8000-00805f9b34fb` for `CC10` (Battery) and `CC09` (Status).
3. **Parsing:** Use the byte-level structures defined above. Note that data is often Big-Endian (
   Network Byte Order).
4. **State Machine:**
    * Connect BLE.
    * Enable Notifications for `CC10` & `CC09`.
    * Update UI based on incoming byte arrays.
