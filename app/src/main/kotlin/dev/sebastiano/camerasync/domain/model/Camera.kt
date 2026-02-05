package dev.sebastiano.camerasync.domain.model

import dev.sebastiano.camerasync.domain.vendor.CameraVendor

/**
 * Represents a camera that can be discovered and connected to.
 *
 * This is a domain model decoupled from the Kable library's Advertisement class. Supports cameras
 * from multiple vendors (Ricoh, Canon, Nikon, etc.).
 */
data class Camera(
    val identifier: String,
    val name: String?,
    val macAddress: String,
    val vendor: CameraVendor,
    /**
     * BLE protocol version from the advertisement data. For Sony cameras, this determines which
     * features are available:
     * - Protocol >= 65: Requires DD30/DD31 unlock sequence for location sync
     * - Protocol < 65: Uses legacy protocol without DD30/DD31 Null for non-Sony cameras or if not
     *   available in the advertisement.
     */
    val bleProtocolVersion: Int? = null,
)
