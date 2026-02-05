package dev.sebastiano.camerasync.firmware.ricoh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Scraped firmware data from GitHub Pages (see `scripts/scrape_ricoh_firmware.py`). */
@Serializable
data class RicohFirmwareData(
    @SerialName("last_updated") val lastUpdated: String? = null,
    val cameras: Map<String, String> = emptyMap(),
)

/** Request body for Ricoh AWS firmware API. */
@Serializable
data class RicohFirmwareApiRequest(
    @SerialName("phone_model") val phoneModel: String,
    @SerialName("phone_os") val phoneOs: String,
    @SerialName("phone_os_version") val phoneOsVersion: String,
    @SerialName("phone_language") val phoneLanguage: String,
    @SerialName("phone_app_ver") val phoneAppVer: String,
    val model: String,
)

/** Response from Ricoh AWS firmware API. */
@Serializable data class RicohFirmwareApiResponse(val version: String = "")
