package dev.sebastiano.camerasync.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.juul.khronicle.Log

/**
 * Utility class for checking and managing battery optimization settings.
 *
 * Battery optimization can interfere with background services and BLE connections, so it's
 * important to request that the app be excluded from battery optimizations.
 *
 * Source for OEM component names: https://stackoverflow.com/a/48166241/2143225 Source:
 * https://github.com/fei0316/snapstreak-alarm Source:
 * https://gist.github.com/moopat/e9735fa8b5cff69d003353a4feadcdbc
 */
object BatteryOptimizationUtil {
    private val logTag = javaClass.simpleName

    /**
     * Checks if the app is currently ignoring battery optimizations.
     *
     * @param context The application context
     * @return true if the app is ignoring battery optimizations (set to "Unrestricted"), false
     *   otherwise
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        Log.debug(logTag) {
            "Battery optimization status: ${if (isIgnoring) "DISABLED (Unrestricted)" else "ENABLED (Optimized or Restricted)"}"
        }
        return isIgnoring
    }

    /**
     * Creates an intent to request that battery optimizations be disabled for this app.
     *
     * First attempts to use ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to directly request the
     * exemption. If that's not available or resolvable, falls back to
     * ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS which opens the general battery optimization
     * settings screen.
     *
     * @param context The application context
     * @return An intent to launch the battery optimization settings
     */
    @SuppressLint("BatteryLife") // Not publishing on Play — and a legit usecase anyway
    fun createBatteryOptimizationSettingsIntent(context: Context): Intent {
        // Try the direct request intent first
        val directIntent =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri(),
            )

        // Check if the intent can be resolved
        val packageManager = context.packageManager
        if (directIntent.resolveActivity(packageManager) != null) {
            Log.info(logTag) { "Using direct battery optimization request intent" }
            return directIntent
        }

        // Fallback to the general battery optimization settings screen
        Log.info(logTag) {
            "Direct request not available, falling back to general battery settings"
        }
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /**
     * Checks if an OEM-specific battery optimization settings intent is available.
     *
     * Many manufacturers (Xiaomi, Huawei, Oppo, Samsung, etc.) have their own aggressive battery
     * management systems that require additional configuration beyond standard Android settings.
     *
     * @param context The application context
     * @return true if an OEM-specific intent is available, false otherwise
     */
    fun hasOemBatteryOptimizationSettings(context: Context): Boolean {
        val intent = getOemBatteryOptimizationIntent(context)
        if (intent != null) {
            Log.info(logTag) { "Found OEM battery optimization intent: ${intent.component}" }
        } else {
            Log.debug(logTag) { "No OEM-specific battery optimization settings available" }
        }
        return intent != null
    }

    /**
     * Creates an intent to open OEM-specific battery optimization settings if available.
     *
     * This is important for devices from manufacturers like Xiaomi, Huawei, Oppo, Samsung, etc.,
     * which have their own battery management systems that are more aggressive than stock Android.
     *
     * @param context The application context
     * @return An intent to launch OEM settings, or null if not available
     */
    fun getOemBatteryOptimizationIntent(context: Context): Intent? {
        Log.debug(logTag) { "Checking for OEM-specific battery optimization settings" }
        val packageManager = context.packageManager
        for (componentName in getOemComponentNames()) {
            // First check if the package is actually installed
            val packageName = componentName.packageName
            try {
                packageManager.getPackageInfo(packageName, 0)
                Log.debug(logTag) { "Package found: $packageName, checking if activity exists" }

                // Package exists, now check if the specific activity can be resolved
                val intent = Intent().apply { component = componentName }
                val activities = packageManager.queryIntentActivities(intent, 0)
                if (activities.isNotEmpty()) {
                    Log.info(logTag) {
                        "Resolved OEM battery settings: $packageName with activity ${componentName.className}"
                    }
                    return intent
                } else {
                    Log.debug(logTag) {
                        "Package $packageName exists but activity ${componentName.className} not found"
                    }
                }
            } catch (_: Exception) {
                // Package not installed, continue to next
                Log.verbose(logTag) { "Package not found: $packageName" }
            }
        }
        Log.debug(logTag) { "No OEM battery optimization settings found on this device" }
        return null
    }

    /**
     * Get a list of all known ComponentNames that provide battery optimization settings on
     * different OEM devices.
     *
     * Based on Shivam Oberoi's answer on StackOverflow:
     * https://stackoverflow.com/a/48166241/2143225
     */
    private fun getOemComponentNames(): List<ComponentName> {
        return listOf(
            // Xiaomi MIUI
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            // Huawei
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity",
            ),
            // Oppo ColorOS (newer)
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            // Oppo ColorOS (older)
            ComponentName(
                "com.color.safecenter",
                "com.color.safecenter.permission.startup.StartupAppListActivity",
            ),
            // Oppo
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity",
            ),
            // Samsung (China)
            ComponentName(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
            // Samsung (Global)
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
            // iQOO
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
            // Vivo
            ComponentName(
                "com.vivo.abe",
                "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
            ),
            // HTC
            ComponentName(
                "com.htc.pitroad",
                "com.htc.pitroad.landingpage.activity.LandingPageActivity",
            ),
            // Asus
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"),
            // Meizu
            ComponentName("com.meizu.safe", "com.meizu.safe.powerui.PowerAppPermissionActivity"),
            // ZTE
            ComponentName(
                "com.zte.heartyservice",
                "com.zte.heartyservice.setting.ClearAppSettingsActivity",
            ),
            // Lenovo
            ComponentName(
                "com.lenovo.security",
                "com.lenovo.security.purebackground.PureBackgroundActivity",
            ),
            // Coolpad
            ComponentName("com.yulong.android.security", "com.yulong.android.seccenter.tabbarmain"),
            // LeTV
            ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.BackgroundAppManageActivity",
            ),
            // Gionee
            ComponentName("com.gionee.softmanager", "com.gionee.softmanager.MainActivity"),
        )
    }
}
