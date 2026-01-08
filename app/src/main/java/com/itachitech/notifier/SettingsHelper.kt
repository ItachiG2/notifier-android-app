package com.itachitech.notifier

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object SettingsHelper {

    private const val TAG = "SettingsHelper"

    // region Manufacturer-specific Intents
    private val VIVO_AUTOSART_INTENTS = listOf(
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgstartupswitchActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
    )

    private val XIAOMI_AUTOSART_INTENTS = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.BgAutostartManagementActivity")),
        Intent("miui.intent.action.APP_PERM_EDITOR").setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")),
    )

    private val OPPO_REALME_ONEPLUS_AUTOSART_INTENTS = listOf(
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.realme.security", "com.realme.security.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.ChainLaunchAppListActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
    )

    private val SAMSUNG_AUTOSART_INTENTS = listOf(
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
    )
    // endregion

    fun openAutostartSettings(context: Context) {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        val intentsToTry = when {
            brand.contains("samsung") || manufacturer.contains("samsung") -> SAMSUNG_AUTOSART_INTENTS
            brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo") -> VIVO_AUTOSART_INTENTS
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> XIAOMI_AUTOSART_INTENTS
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> OPPO_REALME_ONEPLUS_AUTOSART_INTENTS
            else -> emptyList()
        }

        if (!tryIntents(context, intentsToTry)) {
            openPowerManagementFallback(context)
        }
    }

    @SuppressLint("BatteryLife")
    fun openBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "Device does not support ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", e)
                    openPowerManagementFallback(context)
                }
            } else {
                Toast.makeText(context, "Battery optimization is already disabled for this app.", Toast.LENGTH_SHORT).show()
            }
        } else {
            openStandardAppSettings(context)
        }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Device does not support ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT", e)
                openStandardAppSettings(context)
            }
        } else {
            openStandardAppSettings(context)
        }
    }

    private fun tryIntents(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                try {
                    context.startActivity(intent)
                    Log.d(TAG, "Opened settings with: $intent")
                    return true // Success
                } catch (e: SecurityException) {
                    Log.w(TAG, "Failed to open settings with security exception: $intent", e)
                }
            }
        }
        return false
    }

    private fun openPowerManagementFallback(context: Context) {
        Toast.makeText(context, "Could not find specific setting. Opening general battery settings.", Toast.LENGTH_SHORT).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open battery optimization fallback", e)
                openStandardAppSettings(context)
            }
        } else {
            openStandardAppSettings(context)
        }
    }

    fun openStandardAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open even the standard app details settings.", e)
            Toast.makeText(context, "Could not open settings. Please manually check app permissions.", Toast.LENGTH_LONG).show()
        }
    }
}