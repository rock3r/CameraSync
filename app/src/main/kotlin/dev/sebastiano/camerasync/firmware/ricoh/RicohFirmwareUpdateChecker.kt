package dev.sebastiano.camerasync.firmware.ricoh

import android.content.Context
import com.juul.khronicle.Log
import dev.sebastiano.camerasync.domain.model.PairedDevice
import dev.sebastiano.camerasync.firmware.FirmwareUpdateCheckResult
import dev.sebastiano.camerasync.firmware.FirmwareUpdateChecker
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val TAG = "RicohFirmwareUpdateChecker"

private val json = Json { ignoreUnknownKeys = true }

// Hosted on GitHub Pages via the workflow
// Using raw.githubusercontent.com for reliability (avoids custom domain redirect issues)
private const val SCRAPED_JSON_URL =
    "https://raw.githubusercontent.com/rock3r/CameraSync/gh-pages/firmware/ricoh_firmware.json"
private const val JSON_CACHE_FILE = "ricoh_firmware_cache.json"
private const val JSON_CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours

// AWS API for newer models
private const val AWS_API_URL =
    "https://iazjp2ji87.execute-api.ap-northeast-1.amazonaws.com/v1/fwDownload"
private const val AWS_API_KEY = "B1zN8uvCXN8QnN8t7rKExHKRLDKVm769qMebhera"
private const val AWS_SECRET_KEY = "CySfvt88t8"

class RicohFirmwareUpdateChecker(
    private val context: Context,
    /** Override for tests; null uses [AWS_API_URL]. */
    private val awsApiUrl: String? = null,
) : FirmwareUpdateChecker {

    override fun supportsVendor(vendorId: String): Boolean = vendorId == "ricoh"

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

            val modelName =
                device.name
                    ?: run {
                        return@withContext FirmwareUpdateCheckResult.CheckFailed(
                            "Device name not available"
                        )
                    }

            // Determine which strategy to use
            if (isLegacyModel(modelName)) {
                checkForUpdateViaScrapedJson(modelName, currentFirmwareVersion)
            } else {
                checkForUpdateViaAwsApi(modelName, currentFirmwareVersion)
            }
        }

    internal fun isLegacyModel(modelName: String): Boolean {
        // Models that don't support the app API and need scraping
        // Includes GR II, GR III series, GR IIIx series
        val normalized = modelName.trim().uppercase()
        return normalized.contains("GR II") ||
            normalized.contains("GR III") ||
            normalized.contains("GR IIIX")
    }

    // region Scraped JSON Strategy (Legacy)

    private fun checkForUpdateViaScrapedJson(
        modelName: String,
        currentVersion: String,
    ): FirmwareUpdateCheckResult {
        val firmwareData =
            getScrapedFirmwareData()
                ?: return FirmwareUpdateCheckResult.CheckFailed("Could not fetch firmware data")

        // Map app model name to scraped JSON key
        // App uses "RICOH GR III", JSON might use "GR III"
        // We search for the JSON key that is contained in the app model name.
        // Iterate by key length descending so "GR IIIx" is matched before "GR III"
        // (otherwise "RICOH GR IIIx".contains("GR III") would wrongly match GR III).
        val cameras = firmwareData.cameras
        if (cameras.isEmpty()) {
            return FirmwareUpdateCheckResult.CheckFailed("Invalid firmware data format")
        }

        val match = findBestMatchingCameraKey(cameras, modelName)
        val latestVersion = match?.second
        val matchedModelKey = match?.first

        if (latestVersion == null) {
            return FirmwareUpdateCheckResult.CheckFailed(
                "Model not found in firmware list: $modelName"
            )
        }

        return if (isNewerVersion(latestVersion, currentVersion)) {
            FirmwareUpdateCheckResult.UpdateAvailable(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                modelName = matchedModelKey ?: modelName,
            )
        } else {
            FirmwareUpdateCheckResult.NoUpdateAvailable
        }
    }

    /**
     * Finds the best-matching camera key and its firmware version for the given model name. Keys
     * are tried in descending length order so that "GR IIIx" matches before "GR III".
     */
    internal fun findBestMatchingCameraKey(
        cameras: Map<String, String>,
        modelName: String,
    ): Pair<String, String>? {
        val keysByLengthDesc = cameras.keys.sortedByDescending { it.length }
        for (key in keysByLengthDesc) {
            if (modelName.contains(key, ignoreCase = true)) {
                cameras[key]?.let { version ->
                    return key to version
                }
            }
        }
        return null
    }

    private fun getScrapedFirmwareData(): RicohFirmwareData? {
        val cacheFile = File(context.cacheDir, JSON_CACHE_FILE)

        // Check cache
        if (
            cacheFile.exists() &&
                System.currentTimeMillis() - cacheFile.lastModified() < JSON_CACHE_VALIDITY_MS
        ) {
            try {
                return json.decodeFromString<RicohFirmwareData>(cacheFile.readText())
            } catch (e: SerializationException) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read cached firmware data" }
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) { "Failed to read cached firmware data" }
            }
        }

        // Fetch from network
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(SCRAPED_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.warn(tag = TAG) { "Failed to fetch firmware JSON: ${connection.responseCode}" }
                return null
            }

            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val data = json.decodeFromString<RicohFirmwareData>(jsonStr)

            // Save to cache
            try {
                cacheFile.writeText(jsonStr)
            } catch (e: IOException) {
                Log.warn(tag = TAG, throwable = e) { "Failed to cache firmware data" }
            }

            data
        } catch (e: IOException) {
            Log.warn(tag = TAG, throwable = e) { "Error fetching firmware JSON" }
            null
        } catch (e: SerializationException) {
            Log.warn(tag = TAG, throwable = e) { "Error parsing firmware JSON" }
            null
        } finally {
            connection?.disconnect()
        }
    }

    // endregion

    // region AWS API Strategy (Modern)

    private fun checkForUpdateViaAwsApi(
        modelName: String,
        currentVersion: String,
    ): FirmwareUpdateCheckResult {
        // Map model name to API model code
        val modelCode =
            mapModelToApiCode(modelName)
                ?: return FirmwareUpdateCheckResult.CheckFailed(
                    "Unsupported model for API: $modelName"
                )

        var connection: HttpURLConnection? = null
        return try {
            connection = URL(awsApiUrl ?: AWS_API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Headers
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", AWS_API_KEY)
            connection.setRequestProperty("x-secret-key", AWS_SECRET_KEY)

            // Body (RICOH_PROTOCOL.md: minimal required fields + model)
            val request =
                RicohFirmwareApiRequest(
                    phoneModel = android.os.Build.MODEL ?: "unknown",
                    phoneOs = "Android",
                    phoneOsVersion = android.os.Build.VERSION.RELEASE ?: "unknown",
                    phoneLanguage = "en",
                    phoneAppVer = "1.0.0",
                    model = modelCode,
                )
            connection.outputStream.write(
                json.encodeToString(RicohFirmwareApiRequest.serializer(), request).toByteArray()
            )

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.warn(tag = TAG) { "AWS API error: ${connection.responseCode}" }
                return FirmwareUpdateCheckResult.CheckFailed(
                    "API error: ${connection.responseCode}"
                )
            }

            val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<RicohFirmwareApiResponse>(responseStr)

            val latestVersion = response.version
            if (latestVersion.isEmpty()) {
                return FirmwareUpdateCheckResult.CheckFailed("Empty version in API response")
            }

            if (isNewerVersion(latestVersion, currentVersion)) {
                FirmwareUpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    modelName = modelName,
                )
            } else {
                FirmwareUpdateCheckResult.NoUpdateAvailable
            }
        } catch (e: IOException) {
            Log.warn(tag = TAG, throwable = e) { "Error checking AWS API" }
            FirmwareUpdateCheckResult.CheckFailed("Network error: ${e.message}")
        } catch (e: SerializationException) {
            Log.warn(tag = TAG, throwable = e) { "Error parsing AWS API response" }
            FirmwareUpdateCheckResult.CheckFailed(
                "Could not parse firmware response: ${e.message ?: "invalid format"}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    internal fun mapModelToApiCode(modelName: String): String? {
        val name = modelName.trim()
        return when {
            name.contains("GR IV", ignoreCase = true) -> "gr4"
            // Add other supported models for API here if any
            else -> null
        }
    }

    // endregion

    internal fun isNewerVersion(latest: String, current: String): Boolean =
        try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }

            val length = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: NumberFormatException) {
            Log.warn(TAG, e) { "Error parsing version numbers: $latest vs $current" }
            false
        }
}
