# Camera Images Documentation in Creators Cloud

This document outlines how the application manages and retrieves images for supported camera models.

## 1. Overview
The application uses a hybrid approach for camera images:
- **Bundled Images:** A small subset of camera images is included directly within the APK's resources.
- **Dynamic Downloads:** For most cameras, images are fetched from a remote server upon registration and stored locally for future use.

## 2. Bundled Images
Several camera models have dedicated PNG icons (128x128 pixels) located in the application's resources.

### Local Resource Paths
- `app/src/main/base.apk/res/drawable/ilce_1m2_128x128.png` (α1 II)
- `app/src/main/base.apk/res/drawable/ilme_fx3a_128x128.png` (FX3)
- `app/src/main/base.apk/res/drawable/ilx_lr1_128x128.png` (ILX-LR1)
- `app/src/main/base.apk/res/drawable/dsc_rx1rm3_128x128.png` (RX1R II)
- `app/src/main/base.apk/res/drawable/zv_e1_128x128.png` (ZV-E1)

### Generic Fallbacks
If a specific image is not available, the app uses generic vector drawables:
- `app/src/main/base.apk/res/drawable/device_alpha.xml` (General Alpha series icon)

### Usage Notes
- Bundled PNGs are used in the Bluetooth pairing flow camera list (`SupportCameraListAdapter`).
- The dynamic image lookup does not fall back to bundled PNGs; it returns `null` and the UI shows generic icons (e.g., `ic_img_camera`, `ic_camera_2`).

## 3. Dynamic Download Mechanism
The application dynamically downloads images for registered cameras using a Sony web API.

### API Base URL
The base URL is defined in `strings.xml` and accessed via `Consts.WEB_API_URL`:
- **URL:** `https://ws.imagingedge.sony.net`

### Request Flow
1. **Fetch Image Metadata:**
   The app first requests a JSON file containing metadata for a specific camera model.
   - **Endpoint:** `GET /clients/device/v2/camera/models/{model_name}/images/camera_image.json`
   - **Example:** `https://ws.imagingedge.sony.net/clients/device/v2/camera/models/ILCE-7M4/images/camera_image.json`

2. **Download Image File:**
   The metadata response provides a `path` for the actual image.
   - **Endpoint:** `GET https://ws.imagingedge.sony.net{path}`

### HTTP Headers
The following headers are included in the requests:
- **User-Agent:** `Creators App/[version] ( Android [release] )` (Managed by `UserAgentInterceptor`).
  - Example: `Creators App/2.3.1 ( Android 14 )`

**Note:** `CameraImageService` is constructed without an `AuthService`, so **no Authorization header** is added for camera image requests.

### JSON Response Structure
The metadata JSON is parsed into the following nested structure (names match the Java models):

```
CameraImageInfo {
  formatVer: int
  model: String
  cameraImages: {
    medium: {
      square: {
        images: [
          {
            fileName: String
            path: String
            isTypical: boolean
            isDevelop: boolean
            attrs: JsonElement
            version: int
          }
        ]
      }
    }
  }
}
```

### Image Selection Logic
The app selects the first image entry where `isTypical == true` from
`cameraImages.medium.square.images[]`. If none are marked typical, no download occurs.

### Version & File Checks
An image is re-downloaded when either:
- The `fileName` differs from the stored entry, or
- The metadata `version` is greater than the stored entry.

## 4. Local Storage
Downloaded image data and metadata are stored in a Realm database to avoid redundant downloads.

### Database Details
- **Database Name:** `CameraDb`
- **Table/Object:** `CameraImageV2Object`
  - **Legacy Fallback:** `CameraImageObject` is used if no V2 record exists.

### Data Fields
| Field Name | Type | Description |
| :--- | :--- | :--- |
| `modelName` | String | The model name of the camera (e.g., "ILCE-7M4"). |
| `cameraImageData` | byte[] | The raw bytes of the downloaded image file. |
| `fileName` | String | The name of the downloaded file. |
| `path` | String | The remote path of the image. |
| `lastUpdateDate` | Date | Timestamp of the last successful download. |
| `version` | int | Version of the image metadata. |
| `isTypical` | boolean | Whether the image was marked as typical. |
| `attr` | String | JSON attributes blob from metadata. |

### Transaction Pattern
When updating, the app deletes all existing `CameraImageV2Object` rows for the model,
then inserts the new object with `copyToRealmOrUpdate()` in a single Realm transaction.

## 5. Update Logic
The `CameraImageClient` checks if a download is needed by comparing the `lastUpdateDate` in the database. A refresh is triggered every 24 hours (86,400,000 milliseconds).

Additional details:
- Updates are mutex-protected per model name to avoid concurrent downloads.
- Bulk refresh iterates `CameraDb.getRegisteredCamerasDistinctModelName()` and triggers updates per model.
- A worker (`CameraImageDownloadWorker`) runs with an execution flag to prevent duplicate runs.
- After a successful download, a `downloadCameraImageEvent` flow emits `(modelName, Bitmap)`.
