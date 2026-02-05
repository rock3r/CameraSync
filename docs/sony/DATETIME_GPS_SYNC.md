# Camera Date, Time, and Location Synchronization Protocol

This document details the reverse-engineered protocol used to synchronize date, time, and location information with the camera. The synchronization is performed entirely over **Bluetooth Low Energy (BLE)**; Wi-Fi is **not** used for this process.

## 1. Camera Discovery & Identification

The app identifies the camera through BLE advertising packets containing specific Manufacturer Specific Data.

*   **Manufacturer ID**: `0x012D` (Sony) - *Note: Little-endian representation of 301*
*   **Scan Mode**: Low Power (continuous background scanning)
*   **Advertising Packet Structure**:
    *   **Company ID**: `0x2D 0x01` (Sony)
    *   **Device Type**: `0x03 0x00` (Sony Camera)
    *   **Reserved**: 2 bytes
    *   **Model Code**: 2 bytes (UTF-8 string, e.g., "U0", "U1")
    *   **Flags (TLV)**:
        *   **Tag 0x21 (33)**: Power/Wi-Fi status (Bit 7: WirelessPowerOnEnabled, Bit 6: CameraOn, Bit 5: WifiHandoverSupported, Bit 4: WifiHandoverEnabled)
        *   **Tag 0x22 (34)**: Pairing/Location status (Bit 7: PairingSupported, Bit 6: PairingEnabled, Bit 5: LocationSupported, Bit 4: LocationEnabled)

## 2. Services & Characteristics

Two primary GATT services are used for synchronization:

### A. Camera Control Service (Date/Time)
**UUID**: `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF`

| Characteristic UUID | Access | Description |
| :--- | :--- | :--- |
| `0000CC13-...` | Write | **Time Area Setting**: Sets the current time and timezone. |
| `0000CC12-...` | Write | **Date Format Setting**: Sets the date display format (YMD/DMY/etc). |
| `0000CC0E-...` | Notify | **Notification**: Confirmation of setting success/failure. |
| `0000CC09-...` | Read | **Completion Status**: Checks if time setting is done. |
| `0000CC03-...` | Notify | **Push Transfer**: Notifies when camera media is ready for transfer. |
| `0000CC0F-...` | Notify | **Media Status**: Updates on SD card / recording capacity. |
| `0000CC10-...` | Notify | **Battery Status**: Updates on camera battery levels. |
| `0000CCA6-...` | Notify | **Lens Info**: Updates on attached lens status. |

### B. Location Service (GPS)
**UUID**: `8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF`

| Characteristic UUID | Access | Description |
| :--- | :--- | :--- |
| `0000DD11-...` | Write | **Location Data**: The actual GPS and timestamp payload. |
| `0000DD30-...` | Write | **Lock Control**: Locks/Unlocks the location transfer session (`1`=Lock, `0`=Unlock). |
| `0000DD31-...` | Write | **Transfer Enable**: Enables/Disables transfer (`1`=Enable, `0`=Disable). |
| `0000DD01-...` | Notify | **Notification**: Updates on transfer status. |
| `0000DD21-...` | Read | **Capability Info**: Reads camera capabilities. |

---

## 3. Date & Time Synchronization Flow

This process runs during initial setup or when the app detects a time difference.

### Step 1: Subscribe to Notifications
*   Enable notifications on `0000CC0E` (Write CCCD).

### Step 2: Set Date Format (Optional/Initial Setup)
*   **Characteristic**: `0000CC12`
*   **Payload** (4 bytes): `{ 0x03, 0x00, 0x00, FORMAT_VALUE }`
    *   `FORMAT_VALUE`:
        *   `1`: YMD (Year/Month/Day)
        *   `2`: DMY
        *   `3`: MDY
        *   `4`: MDY_E
*   **Wait**: Expect notification on `0000CC0E` with success status.

### Step 3: Set Time & Timezone
*   **Characteristic**: `0000CC13`
*   **Payload** (13 bytes): `BluetoothGattUtil.serializeTimeAreaData()`

| Byte Offset | Value | Description |
| :--- | :--- | :--- |
| 0-2 | `0x0C 0x00 0x00` | Header (Fixed) |
| 3-4 | `uint16` | Year (Big-Endian) |
| 5 | `uint8` | Month (1-12) |
| 6 | `uint8` | Day (1-31) |
| 7 | `uint8` | Hour (0-23) |
| 8 | `uint8` | Minute (0-59) |
| 9 | `uint8` | Second (0-59) |
| 10 | `0x00` / `0x01` | DST Flag (0=Standard, 1=DST) |
| 11 | `int8` | Timezone Offset Hours (Signed) |
| 12 | `uint8` | Timezone Offset Minutes |

*   **Wait**: Expect notification on `0000CC0E` confirming success.

---

## 4. Location Synchronization Flow

Location sync requires a "locking" handshake before data can be streamed.

### Phase 1: Locking & Setup
0.  **Subscribe to DD01 Notifications**: Write CCCD descriptor to enable notifications on `0000DD01`. This **MUST** be done FIRST before any other operations. The camera sends notifications on DD01 when location transfer is disabled.
1.  **Lock Session**: Write `{ 0x01 }` to `0000DD30`.
2.  **Enable Transfer**: Write `{ 0x01 }` to `0000DD31`.
3.  **Read Capabilities** (in this order per Sony app):
    *   Read `0000DD32` (Time Correction).
    *   Read `0000DD33` (Area Adjustment).
    *   **CRITICAL**: Read `0000DD21` (Camera Info). This determines if the camera supports timezone data.
        *   **Check**: Examine the 5th byte (Index 4) of the response.
        *   **Mask**: `0x02` (Bit 1).
        *   **Logic**: If `(Byte4 & 0x02) == 0x02`, the camera **SUPPORTS** timezone data (use 95-byte payload). Otherwise, it does **NOT** (use 91-byte payload).
4.  **Wait for Ready**: The app waits for the `onTransferReady` internal state.

### Phase 2: Sending Location Updates
When a valid GPS location (< 10 seconds old) is received, construct the payload based on the capability check in Phase 1.

#### Option A: Camera Supports Timezone (95 Bytes)
Used if `(0000DD21_Value[4] & 0x02) == 0x02`.

| Byte Offset | Value | Description |
| :--- | :--- | :--- |
| 0-1 | `0x00 0x5D` | Total Length (93 + 2 header bytes) |
| 2-4 | `0x08 0x02 0xFC` | Protocol Header |
| 5 | `0x03` | **Flag**: Timezone/DST present |
| 6-7 | `0x00` | Padding |
| 8-10 | `0x10` | Fixed Padding (16) |
| 11-14 | `int32` | **Latitude** * 10,000,000 (Big-Endian) |
| 15-18 | `int32` | **Longitude** * 10,000,000 (Big-Endian) |
| 19-20 | `uint16` | Year (Big-Endian) |
| 21 | `uint8` | Month |
| 22 | `uint8` | Day |
| 23 | `uint8` | Hour |
| 24 | `uint8` | Minute |
| 25 | `uint8` | Second |
| ... | ... | Padding/Reserved |
| 91-92 | `int16` | **Timezone Offset** (Minutes, Big-Endian) |
| 93-94 | `int16` | **DST Savings** (Minutes, Big-Endian) |

#### Option B: No Timezone Support (91 Bytes)
Used if `(0000DD21_Value[4] & 0x02) != 0x02`.

| Byte Offset | Value | Description |
| :--- | :--- | :--- |
| 0-1 | `0x00 0x59` | Total Length (89 + 2 header bytes) |
| 2-4 | `0x08 0x02 0xFC` | Protocol Header |
| 5 | `0x00` | **Flag**: No Timezone |
| 6-7 | `0x00` | Padding |
| 8-10 | `0x10` | Fixed Padding (16) |
| 11-14 | `int32` | Latitude * 10,000,000 (Big-Endian) |
| 15-18 | `int32` | Longitude * 10,000,000 (Big-Endian) |
| 19-25 | ... | Date/Time (Same as above) |
| 26-90 | ... | Padding/Reserved |

### Phase 3: Unlocking/Cleanup
To stop syncing:
1.  **Disable Transfer**: Write `{ 0x00 }` to `0000DD31`.
2.  **Unlock Session**: Write `{ 0x00 }` to `0000DD30`.

---

## 5. Error Handling & Edge Cases

### Timeouts
*   **Command Timeout**: All BLE write operations have a **30-second timeout**. If no success notification is received within this window, the operation is aborted.
*   **Failure Action**: The state machine transitions to `IdleState` and invokes the failure callback.

### Retries
*   **Time Sync**: No automatic retries. Fails immediately on error or timeout.
*   **Location Sync**:
    *   Write failures to `0000DD11` are retried up to **3 times**.
    *   If the camera actively disables location (notification on `0000DD01`), the app transitions to `TransferringLocationInfoUnlockingState` with an `OffByCamera` error.

### Connection Loss
*   **Disconnect**: If the BLE connection drops (`onGattDisconnected`), all active commands are aborted. The app must re-scan and re-connect to resume.

### Implementation Notes for Clean-Room Reimplementation
1.  **Big-Endian (Network Byte Order)**: All multi-byte integers (Year, Latitude, Longitude) are encoded as **Big-Endian**.
    *   *Correction from previous version*: The Android `ByteBuffer` default is Big-Endian, and the decompiled code does not change this order.
2.  **Time Reference Differs Between Characteristics**:
    *   **Time Sync (CC13)**: Uses **LOCAL time** components (from `ZonedDateTime`) alongside timezone offset.
    *   **Location Sync (DD11)**: Uses **UTC time** (from `Calendar.getInstance(TimeZone.getTimeZone("UTC"))`).
3.  **Timezone Formats Differ**:
    *   **Time Sync (CC13)**: Uses a custom split format (Byte 11 = Signed Hour, Byte 12 = Minute).
    *   **Location Sync (DD11)**: Uses a standard `int16` (Big-Endian) for total minutes.
4.  **GPS Precision**: Multiply Latitude/Longitude by `1E7` and cast to integer.
5.  **State Machine**: Implement a robust state machine to handle the Lock -> Enable -> Write -> Unlock sequence. Do not attempt to write location data without first locking and enabling.
6.  **Freshness**: Only send location data if it is fresh (< 10s old). Stale data should be discarded.
7.  **Keep-Alive Mechanism**: The camera has an internal timeout for location data. If it doesn't receive location updates for an extended period (observed: ~2 minutes), it will show a 🚫 icon on the location link indicator, even if the BLE session is still active. To prevent this:
    *   Implement a keep-alive timer that runs every **30 seconds**.
    *   If no fresh GPS location has been received and sent within the interval, re-send the **last known location** with a **fresh timestamp**.
    *   This keeps the camera's location indicator active during periods when the phone's GPS is not providing frequent updates (e.g., indoors, stationary).
    *   The keep-alive uses the same DD11 characteristic and packet format as normal location updates.

---

## 6. Reference Implementation (Pseudo-Code)

Below are Python-like snippets demonstrating the exact packet creation logic, ensuring zero ambiguity regarding byte order and structure.

### Time Sync Packet (`0000CC13`)

```python
import struct

def create_time_sync_packet(year, month, day, hour, minute, second, 
                            tz_offset_hours, tz_offset_minutes, is_dst):
    """
    Creates the 13-byte payload for characteristic 0000CC13.
    """
    packet = bytearray(13)
    
    # Header
    packet[0] = 0x0C
    packet[1] = 0x00
    packet[2] = 0x00
    
    # Year: Big-Endian uint16
    struct.pack_into('>H', packet, 3, year)
    
    # Date/Time components
    packet[5] = month
    packet[6] = day
    packet[7] = hour
    packet[8] = minute
    packet[9] = second
    
    # DST Flag (1 = DST, 0 = Standard)
    packet[10] = 1 if is_dst else 0
    
    # Timezone Offset (Custom Split Format)
    # Byte 11: Signed hours (int8)
    # Byte 12: Minutes (uint8)
    struct.pack_into('b', packet, 11, tz_offset_hours)
    packet[12] = tz_offset_minutes
    
    return packet
```

### Location Sync Packet (`0000DD11`)

```python
import struct

def create_location_sync_packet(lat_deg, lon_deg, 
                                utc_year, utc_month, utc_day, utc_hour, utc_minute, utc_second,
                                tz_total_minutes=0, dst_savings_minutes=0,
                                camera_supports_timezone=False):
    """
    Creates the 91 or 95-byte payload for characteristic 0000DD11.
    
    IMPORTANT: Time components must be in UTC (not local time).
    Use Calendar.getInstance(TimeZone.getTimeZone("UTC")) to extract components.
    The timezone offset tells the camera how to convert to local time.
    """
    
    # Determine length based on camera capability
    if camera_supports_timezone:
        length = 95
        payload_len_field = 93 # 0x5D
        flag_byte = 0x03
    else:
        length = 91
        payload_len_field = 89 # 0x59
        flag_byte = 0x00
        
    packet = bytearray(length)
    
    # Header: Total Length (uint16 Big-Endian)
    struct.pack_into('>H', packet, 0, payload_len_field)
    
    # Protocol Header
    packet[2] = 0x08
    packet[3] = 0x02
    packet[4] = 0xFC
    
    # Flags / Padding
    packet[5] = flag_byte
    # Bytes 6-7 are 0x00, bytes 8-10 are 0x10 (Fixed Padding)
    packet[8] = 0x10
    packet[9] = 0x10
    packet[10] = 0x10
    
    # Latitude / Longitude: int32 Big-Endian (degrees * 1E7)
    lat_int = int(lat_deg * 10000000)
    lon_int = int(lon_deg * 10000000)
    
    struct.pack_into('>i', packet, 11, lat_int)
    struct.pack_into('>i', packet, 15, lon_int)
    
    # Date / Time (UTC!)
    struct.pack_into('>H', packet, 19, utc_year) # Year Big-Endian
    packet[21] = utc_month
    packet[22] = utc_day
    packet[23] = utc_hour
    packet[24] = utc_minute
    packet[25] = utc_second
    
    # Timezone Data (Only if supported)
    if camera_supports_timezone:
        # Timezone Offset: int16 Big-Endian (Total minutes)
        struct.pack_into('>h', packet, 91, tz_total_minutes)
        # DST Savings: int16 Big-Endian (Total minutes)
        struct.pack_into('>h', packet, 93, dst_savings_minutes)
        
    return packet
```
