package dev.sebastiano.camerasync

import kotlinx.serialization.Serializable

/** Navigation routes for the application. */
sealed interface NavRoute {

    /** Permissions need to be requested. */
    @Serializable data object NeedsPermissions : NavRoute

    /** Main screen showing paired devices list. */
    @Serializable data object DevicesList : NavRoute

    /** Pairing screen for adding new devices. */
    @Serializable data object Pairing : NavRoute

    /** Log viewer screen. */
    @Serializable data object LogViewer : NavRoute
}
