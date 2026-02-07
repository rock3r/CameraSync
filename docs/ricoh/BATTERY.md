# Ricoh Battery Monitoring

Battery level is read and notified over BLE. Sufficient battery is required for firmware updates and
can affect file transfer and connection steps.

**BLE Characteristic:** `FE3A32F8-A189-42DE-A391-BC81AE4DAA76` (Battery/Info) — Read and Notify.  
**Model:** `BatteryLevelModel(level: int)` — 0–100 percentage.

---

## 8.1. Battery Level States

| State   | Asset                 | Description         |
|:--------|:----------------------|:--------------------|
| Full    | `battery_full.svg`    | Full charge         |
| Half    | `battery_half.svg`    | ~50% charge         |
| Low     | `battery_low.svg`    | Low battery warning |
| Empty   | `battery_empty.svg`   | Critical battery    |
| Unknown | `battery_unknown.svg` | Cannot read battery |

---

## 8.2. Battery-Related Constraints

- Firmware update requires sufficient battery level (camera and phone).
- File transfer may be blocked on low battery.
- Error: `step0_o_camera_battery_low` — camera battery too low to proceed.
- Error: `step0_o_phone_battery_low` — phone battery too low.

See [FIRMWARE_UPDATES.md](FIRMWARE_UPDATES.md) for pre-update checks and [ERROR_HANDLING.md](ERROR_HANDLING.md) for user-facing battery messages.
