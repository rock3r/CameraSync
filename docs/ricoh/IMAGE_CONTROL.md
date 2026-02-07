# Ricoh Image Control Feature

Image Controls are camera picture profiles/presets that can be synced between camera and app. This
document covers built-in presets, parameters, and applying Image Control to the camera (including
custom slots).

**See also:** [HTTP_WEBSOCKET.md](HTTP_WEBSOCKET.md) for the `/imgctrl` HTTP endpoint and camera-side
constraints.

---

## 6.1. Built-in Presets

| Preset        | Asset               |
|:--------------|:--------------------|
| Standard      | `standard.png`      |
| Vivid         | `vivid.png`         |
| Monotone      | `monotone.png`      |
| Soft Monotone | `soft_monotone.png` |
| Hard Monotone | `hard_monotone.png` |
| Hi-Contrast   | `hi_contrast.png`   |
| Positive Film | `posi_film.png`     |
| Negative Film | `nega_film.png`     |
| Bleach Bypass | `bleach_bypass.png` |
| Retro         | `retro.png`         |
| HDR Tone      | `hdr.png`           |
| Cross Process | `cross_process.png` |
| Cinema Yellow | `cinema_yellow.png` |
| Cinema Green  | `cinema_green.png`  |
| Grainy        | `grainy.png`        |
| Hi            | `hi.png`            |
| Soft          | `soft.png`          |
| Solid         | `solid.png`         |

**GR IV Monochrome-specific:**
| Preset | Description |
| :--- | :--- |
| Mono Standard | Standard monochrome |
| Mono Soft | Soft monochrome |
| Mono High-Contrast | High contrast monochrome |
| Mono Grainy | Grainy film look |
| Mono Solid | Solid blacks |
| Mono HDR Tone | HDR-style monochrome |

---

## 6.2. Image Control Parameters

These parameters can be adjusted for each preset:

| Parameter                | Asset                        | Description          |
|:-------------------------|:-----------------------------|:---------------------|
| Saturation               | `saturation.png`             | Color intensity      |
| Hue                      | `hue.png`                    | Color shift          |
| Key                      | `key.png`                    | Overall brightness   |
| Contrast                 | `contrast.png`               | Overall contrast     |
| Contrast (Highlight)     | `contrast_highlight.png`     | Highlight contrast   |
| Contrast (Shadow)        | `contrast_shadow.png`        | Shadow contrast      |
| Sharpness                | `sharpness.png`              | Edge sharpness       |
| Shading                  | `shading.png`                | Vignette effect      |
| Clarity                  | `clarity.png`                | Local contrast       |
| Toning                   | `toning.png`                 | Color toning         |
| Toning (Monotone)        | `toning_monotone.png`        | Sepia/toning for B&W |
| Filter Effect (Monotone) | `filter_effect_monotone.png`| Color filter for B&W |
| Granular                 | `granular.png`               | Film grain           |
| Granular Size            | `granular_size.png`          | Grain size           |
| Granular Strength        | `granular_strength.png`      | Grain intensity      |
| HDR Tone                 | `hdrtone.png`                | HDR intensity        |

---

## 6.3. Applying Image Control to Camera

1. Camera must be in still image mode (not movie mode)
2. Select target mode dial position (P/Av/Tv/Sn/M, USER1, USER2, USER3)
3. Select Custom slot (Custom 1, Custom 2, or Custom 3)
4. Binary Image Control data (`.BIN` format) is sent to the camera

**Warning:** "Please do not operate the camera's mode dial while the Image Control is being set."

**GR IV Firmware Requirement:**
Image Control setting requires GR IV firmware version 1.04 or later.

**BLE Service:** `9F00F387-8345-4BBC-8B92-B87B52E3091A` (Image Control Service). Error code: `ER_BL_004` on read failure.

**Error Messages:**

- "Image Control could not be set."
- "The Image Control cannot be set when the camera is in the movie mode."
- "The camera does not support the Image Control setting feature."

> **Note:** The exact HTTP or BLE mechanism for the write operation could not be fully determined
> from static binary analysis. The binary data is passed internally as `customImageControlData`
> with a `customNum` (slot 1-3) parameter. The write likely uses the Wi-Fi HTTP connection since
> it requires an active data session, but the specific endpoint/characteristic is not exposed as
> a string literal in the binary.
