# Ricoh GPS/Location Transmission

The app can transmit smartphone GPS coordinates to the camera for geotagging photos. Transmission is
over **BLE** (GeoTag Write Service) to keep low-latency updates even when Wi-Fi is not connected.

**See also:** [README.md](README.md) for BLE UUIDs; GeoTag Write Service `84A0DD62-E8AA-4D0F-91DB-819B6724C69E`, characteristic `28F59D60-8B8E-4FCD-A81F-61BDB46595A9`.

---

## 7.1. GeoTag Model

```dart
GeoTagModel(
  isEnabled: bool,
  isLocationExisted: bool,
  isLocationRecordEnabled: bool
)
```

---

## 7.2. Location Settings

| Setting                 | Internal Key      | Description                               |
|:------------------------|:------------------|:------------------------------------------|
| Location Transmission   | `isEnabled`       | Enable/disable GPS transmission           |
| Frequency               | `locationFreqNum` | "High" (accurate) or "Low" (power-saving) |
| Background Transmission | `bgLocationLimit` | Continue when app backgrounded            |
| Background Time Limit   | -                 | Auto-stop after set duration              |

**LocationFreqButtonState:** `high` (accurate, higher battery) / `low` (power-saving).

**BgLocationLimitButtonState:** Configurable timeout for background transmission.

---

## 7.3. GPS Data Fields (EXIF)

The following GPS fields are written to image EXIF (IFD GPS):

| Tag    | Name            | Description              |
|:-------|:----------------|:-------------------------|
| 0x0000 | GPSVersionID    | GPS tag version          |
| 0x0001 | GPSLatitudeRef  | "N" or "S"               |
| 0x0002 | GPSLatitude     | Latitude in degrees      |
| 0x0003 | GPSLongitudeRef | "E" or "W"               |
| 0x0004 | GPSLongitude    | Longitude in degrees     |
| 0x0005 | GPSAltitudeRef  | 0 = above sea level      |
| 0x0006 | GPSAltitude     | Altitude in meters       |
| 0x0007 | GPSTimeStamp    | UTC time                 |
| 0x0008 | GPSSatellites   | Satellites used          |
| 0x0009 | GPSStatus       | "A" = measurement active |
| 0x000A | GPSMeasureMode  | 2D or 3D                 |
| 0x000B | GPSDOP          | Precision                |
| 0x001D | GPSDate         | UTC date                 |

---

## 7.4. Location Transmission Protocol

**BLE Service:** `84A0DD62-E8AA-4D0F-91DB-819B6724C69E` (GeoTag Write Service)  
**BLE Characteristic:** `28F59D60-8B8E-4FCD-A81F-61BDB46595A9` (GeoTag Write)

**Write Data Format (from dm-zharov):**

The payload is a fixed binary structure:

```dart
GeoTagWriteData(
  latitude: float64,   // BIG_ENDIAN
  longitude: float64,  // BIG_ENDIAN
  altitude: float64,   // BIG_ENDIAN
  year: int16,         // LITTLE_ENDIAN
  month: int8,
  day: int8,
  hours: int8,
  minutes: int8,
  seconds: int8,
  datum: int8          // Always 0 = WGS84
)
```

**Endianness:** All fields are BIG_ENDIAN **except** `year`, which is LITTLE_ENDIAN.
The timestamp is the time the location was acquired (UTC), not necessarily the current wall clock time.

**Write configuration:** Uses `writeCharacteristic` with `write_type` and `allow_long_write`
parameters (the payload exceeds the default BLE MTU).

**Transmission Flow:**

1. App acquires GPS fix from smartphone
2. Coordinates + UTC timestamp + datum serialized to byte array
3. Written to GeoTag Write characteristic (`28F59D60`) with response
4. Camera stores location for next shot
5. Location embedded in EXIF when photo taken

**Messages:**

- "The app is acquiring location information."
- "Location information transmission is complete."
- Log: "parse: latitude: ..., longitude: ..., altitude: ..., year: ..., month: ..., day: ..., hours: ..., minutes: ..., seconds: ..., datum: ..."

---

## 7.5. Permissions Required

Android permissions needed:

- `location` - Basic location
- `locationAlways` - Background location
- `locationWhenInUse` - Foreground location
- `android_uses_fine_location` - High accuracy GPS
