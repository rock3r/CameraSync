# CameraSync

A simple Android application that synchronizes GPS data and date/time from your Android phone to
your camera via Bluetooth Low Energy (BLE).

The app supports a multi-vendor architecture, allowing it to work with cameras from various
manufacturers (Ricoh, Sony, etc.).

The app uses Android's system pairing flow to select your camera on first start. Once paired, it
automatically reconnects and maintains synchronization in the background whenever the camera's
Bluetooth is active.

## Features

- **Multi-Vendor Support**: Built with an extensible architecture to support various camera brands (
  Ricoh and Sony currently supported).
- **Automatic Synchronization**: Real-time GPS location and date/time sync.
- **Auto-reconnection**: Automatically reconnects to paired cameras when they are within range.
- **Background Operation**: Continues to sync even when the app is backgrounded (using a foreground
  service and passive BLE scanning).
- **Quick Settings Tile**: Toggle global synchronization on/off directly from your Android
  notification tray.
- **Homescreen Widget**: View connection status and toggle sync directly from your home screen.
- **Firmware Update Notifications**: Automatically notifies you when a firmware update is available
  for your paired cameras.
- **Battery Optimization Detection**: Proactive warnings and guidance to ensure stable background
  operation by excluding the app from system battery optimizations.
- **Feedback & Issue Reporting**: Integrated system to collect BLE metadata and system info for
  troubleshooting.
- **Material 3 UI**: Modern and clean interface built with Jetpack Compose.

## Supported cameras

- **Sony Alpha** cameras (tested with Alpha 7 Mk 4, but all other modern Alpha cameras compatible
  with the Creators' App should work)
- **Ricoh GR III/IIIx** (tested with GR IIIx, but other Ricoh models compatible with the GR World
  app could also work)

Let me know if you manage to use the app with other camera models so I can add them to the list.

## Requirements

### Hardware

- An Android device running Android 13 (API level 33) or higher.
- A supported BLE-enabled camera (e.g., Ricoh GR III, GR IIIx, Sony A7 IV).

### Permissions

The app requires the following permissions to function correctly:

- **Bluetooth**: For discovering and communicating with the camera.
- **Location (Fine/Precise)**: Required by Android for BLE scanning and for providing GPS data to
  the camera.
- **Notifications**: For the foreground service that maintains the connection in the background and
  for firmware update alerts.
- **Background Location**: To continue providing GPS data when the app is in the background (
  optional but recommended for full functionality).

> [!IMPORTANT]
> The app MUST be in the foreground during the initial connection to the camera. After the
> connection is established, it can safely run in the background. You should exclude the app from
> battery optimizations to ensure stable background operation; the app will guide you through this
> process if needed.

## Architecture & Multi-Vendor Support

The project has been refactored to support cameras from multiple manufacturers. It uses a strategy
pattern where each vendor provides its own GATT specification and protocol implementation.

For more details on how to add support for new vendors,
see [docs/MULTI_VENDOR_SUPPORT.md](docs/MULTI_VENDOR_SUPPORT.md).

## Setup & Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/CameraSync.git
   cd CameraSync
   ```

2. **Open in Android Studio**:
   Open the project in Android Studio (Ladybug or newer recommended).

3. **Build the project**:
   ```bash
   ./gradlew build
   ```

## Running the App

To install and run the debug version of the app on your connected device:

```bash
./gradlew installDebug
```

## Development & Scripts

This project uses Gradle with Kotlin DSL.

### Key Gradle Tasks

- `./gradlew assembleDebug`: Build the debug APK.
- `./gradlew bundleRelease`: Build the release App Bundle.
- `./gradlew test`: Run unit tests.
- `./gradlew connectedAndroidTest`: Run instrumented tests on a device.
- `./gradlew ktfmtFormat`: Format the code using ktfmt.

### Environment Variables

No specific environment variables are required for a standard build. Ensure `JAVA_HOME` is set to a
compatible JDK (JDK 11+).

## Project Structure

- `app/src/main/kotlin`: Main source code.
    - `.../vendors`: Vendor-specific implementations (GATT specs, protocols).
    - `.../data`: Repositories and data sources (Location, Camera, Protobuf).
    - `.../devicesync`: Foreground service, coordination, and synchronization logic.
    - `.../domain`: Business logic, models, and repository interfaces.
    - `.../pairing`: Camera pairing UI (Companion Device Manager flow).
    - `.../ui`: Theme and shared UI components.
    - `.../widget`: Homescreen widget implementation (Glance).
- `app/src/main/proto`: Protocol Buffer definitions for data storage.
- `app/src/test`: Unit tests.

## Technical Stack

- **Language**: [Kotlin](https://kotlinlang.org/) (2.3.0)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose)
- **BLE Library**: [Kable](https://github.com/JuulLabs/kable)
- **Data Persistence
  **: [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
  with [Protocol Buffers](https://developers.google.com/protocol-buffers)
- **Dependency Injection**: [Metro](https://github.com/ZacSweers/metro) (compile-time DI framework)
- **Dependency Management**: Gradle Version Catalogs (`libs.versions.toml`)

## Testing

Run unit tests:

```bash
./gradlew test
```

> [!NOTE]
> Primary test configuration used during development: Pixel 9 + Android 15 + Ricoh GR IIIx.

## License

Copyright 2026 Sebastiano Poggi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
