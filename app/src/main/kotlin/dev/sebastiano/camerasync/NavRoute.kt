package dev.sebastiano.camerasync

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** Navigation routes for the application. */
sealed interface NavRoute : Parcelable {

    /** Permissions need to be requested. */
    @Parcelize @Serializable data object NeedsPermissions : NavRoute

    /** Main screen showing paired devices list. */
    @Parcelize @Serializable data object DevicesList : NavRoute

    /** Pairing screen for adding new devices. */
    @Parcelize @Serializable data object Pairing : NavRoute

    /** Log viewer screen. */
    @Parcelize @Serializable data object LogViewer : NavRoute
}
