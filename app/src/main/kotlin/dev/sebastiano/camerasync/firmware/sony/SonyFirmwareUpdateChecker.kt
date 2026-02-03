package dev.sebastiano.camerasync.firmware.sony

import android.os.Build
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.firmware.FirmwareUpdateCheckResult
import dev.sebastiano.camerasync.firmware.FirmwareUpdateChecker
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SonyFirmwareUpdateChecker"
private const val FIRMWARE_LIST_API_URL =
    "https://support.d-imaging.sony.co.jp/FSErFHP8Je0xINMFVv9P/api/firmwarelist_api.php"

/**
 * Sony-specific firmware update checker implementation.
 *
 * Checks for firmware updates by calling Sony's firmware list API with the camera model and current
 * firmware version.
 */
class SonyFirmwareUpdateChecker(private val appContext: android.content.Context) :
    FirmwareUpdateChecker {

    override fun supportsVendor(vendorId: String): Boolean = vendorId == "sony"

    override suspend fun checkForUpdate(
        device: PairedDevice,
        currentFirmwareVersion: String?,
    ): FirmwareUpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (currentFirmwareVersion == null) {
                return@withContext FirmwareUpdateCheckResult.CheckFailed(
                    "Firmware version not available"
                )
            }

            // Extract model name from device name or use a default
            val modelName = extractModelName(device)
            if (modelName == null) {
                return@withContext FirmwareUpdateCheckResult.CheckFailed(
                    "Could not determine camera model"
                )
            }

            try {
                val latestVersion = fetchLatestFirmwareVersion(modelName, currentFirmwareVersion)
                if (latestVersion == null) {
                    FirmwareUpdateCheckResult.CheckFailed("No firmware info found for model")
                } else if (isNewerVersion(latestVersion, currentFirmwareVersion)) {
                    FirmwareUpdateCheckResult.UpdateAvailable(
                        currentVersion = currentFirmwareVersion,
                        latestVersion = latestVersion,
                        modelName = modelName,
                    )
                } else {
                    FirmwareUpdateCheckResult.NoUpdateAvailable
                }
            } catch (e: Exception) {
                Log.warn(tag = TAG, throwable = e) {
                    "Failed to check for firmware update for ${device.macAddress}"
                }
                FirmwareUpdateCheckResult.CheckFailed("Network error: ${e.message}")
            }
        }

    /**
     * Extracts the Sony camera model name from the device.
     *
     * Sony cameras typically use names like "ILCE-7M4" or "DSC-RX1RM3".
     */
    private fun extractModelName(device: PairedDevice): String? {
        // Try device name first
        device.name?.let { name ->
            // Sony cameras often have model names like "ILCE-7M4" or "DSC-RX1RM3"
            if (
                name.startsWith("ILCE-", ignoreCase = true) ||
                    name.startsWith("DSC-", ignoreCase = true) ||
                    name.startsWith("ZV-", ignoreCase = true) ||
                    name.startsWith("FX", ignoreCase = true)
            ) {
                return name.trim()
            }
        }

        // Fallback: could try parsing from MAC address or other identifiers
        // For now, return null if we can't determine the model
        return null
    }

    /**
     * Fetches the latest firmware version for the given model from Sony's API.
     *
     * @return The latest firmware version string, or null if not found or error occurred.
     */
    private suspend fun fetchLatestFirmwareVersion(
        modelName: String,
        currentVersion: String,
    ): String? {
        val connection = URL(FIRMWARE_LIST_API_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", buildUserAgent())
            connection.doOutput = true

            // Build request body
            val requestBody =
                JSONObject().apply {
                    put("locale", "pmm")
                    put("language", getCurrentLocale())
                    put(
                        "modelList",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("modelName", modelName)
                                    put("currentVersion", currentVersion)
                                }
                            )
                        },
                    )
                }

            // Write request body
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            // Read response
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.warn(tag = TAG) { "Firmware API returned error code: $responseCode" }
                return null
            }

            val responseBody =
                connection.inputStream.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }

            return parseFirmwareVersionFromResponse(responseBody, modelName, currentVersion)
        } catch (e: Exception) {
            Log.warn(tag = TAG, throwable = e) {
                "Error fetching firmware version for model $modelName"
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses the firmware version from the API response.
     *
     * The response format is expected to be JSON with a structure like: { "firmwareList":
     * [ { "modelName": "ILCE-7M4", "firmwareVersion": "2.01", ... } ], "firmwareInfoApi": "..." }
     */
    private fun parseFirmwareVersionFromResponse(
        responseBody: String,
        modelName: String,
        currentVersion: String,
    ): String? {
        return try {
            val json = JSONObject(responseBody)
            val firmwareList = json.optJSONArray("firmwareList") ?: return null

            // Find the entry for this model
            for (i in 0 until firmwareList.length()) {
                val entry = firmwareList.getJSONObject(i)
                val entryModelName = entry.optString("modelName", "")
                if (entryModelName.equals(modelName, ignoreCase = true)) {
                    val latestVersion = entry.optString("firmwareVersion", "")
                    if (latestVersion.isNotEmpty()) {
                        return latestVersion
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.warn(tag = TAG, throwable = e) { "Error parsing firmware API response" }
            null
        }
    }

    /**
     * Compares two firmware version strings to determine if the second is newer.
     *
     * Sony firmware versions are typically in the format "X.XX" (e.g., "2.01", "1.20"). This
     * compares versions by converting to float and comparing numerically.
     */
    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        return try {
            val latest = latestVersion.toFloatOrNull() ?: return false
            val current = currentVersion.toFloatOrNull() ?: return false
            latest > current
        } catch (e: Exception) {
            Log.warn(tag = TAG, throwable = e) {
                "Error comparing firmware versions: $currentVersion vs $latestVersion"
            }
            false
        }
    }

    /** Builds the User-Agent string for API requests. */
    private fun buildUserAgent(): String {
        val appVersion =
            try {
                val packageInfo =
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                packageInfo.versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
        val osVersion = Build.VERSION.RELEASE
        return "Creators App/$appVersion ( Android $osVersion )"
    }

    /**
     * Gets the current locale string for the API request.
     *
     * Returns locale in format like "en_US" or "ja_JP".
     */
    private fun getCurrentLocale(): String {
        val locale = Locale.getDefault()
        val language = locale.language.lowercase()
        val country = locale.country.uppercase()
        return if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }
    }
}
