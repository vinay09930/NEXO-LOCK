package com.vinay.nexolock.protection

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Single source of truth for permission state and degraded-protection warnings.
 *
 * Android offers no public API that says "restricted setting" was applied. The
 * manager therefore combines the observable state with the documented Android
 * 13 install-source heuristic and reports a *risk*, never a certainty. The UI
 * tells the owner how to resolve it; the app never pretends the permission is
 * granted.
 */
object PermissionManager {

    enum class State {
        GRANTED,
        MISSING,
        /** Observable permission is off and the install source indicates restricted settings. */
        RESTRICTED,
        UNSUPPORTED,
    }

    enum class BiometricState { READY, NOT_ENROLLED, UNSUPPORTED }

    data class Health(
        val overlay: State = State.MISSING,
        val accessibility: State = State.MISSING,
        val usage: State = State.MISSING,
        val notifications: State = State.MISSING,
        val camera: State = State.MISSING,
        val batteryOptimized: Boolean = true,
        val biometric: BiometricState = BiometricState.UNSUPPORTED,
        val restrictedSettingsRisk: Boolean = false,
        val serviceRunning: Boolean = false,
        /** The accessibility service is enabled in settings AND currently bound. */
        val accessibilityConnected: Boolean = false,
    ) {
        /** At least one foreground-detection channel is usable. */
        val detectionAvailable: Boolean
            get() = accessibility == State.GRANTED || usage == State.GRANTED

        /** A lock surface can actually be drawn over the protected app. */
        val canDrawLock: Boolean get() = overlay == State.GRANTED

        /**
         * Full protection means the foreground monitor is running, a lock surface
         * exists, and at least one app-switch detector works.
         */
        val fullyProtecting: Boolean
            get() = serviceRunning && canDrawLock && detectionAvailable

        val hardFailure: Boolean
            get() = !canDrawLock || !detectionAvailable

        /** Problems that mean protection is currently degraded. */
        val warnings: List<String>
            get() = buildList {
                if (serviceRunning && !canDrawLock) {
                    add("Display over other apps is off. Protected apps cannot be covered.")
                }
                if (serviceRunning && !detectionAvailable) {
                    add("No app-switch detector is active. Enable Accessibility or Usage access.")
                }
                if (serviceRunning && accessibility == State.GRANTED && !accessibilityConnected) {
                    add(
                        "Accessibility is enabled but the service is not responding. " +
                            "Usage-access polling is covering detection until it reconnects.",
                    )
                }
                if (serviceRunning && notifications == State.MISSING) {
                    add("Notifications are off. The ongoing protection status is hidden.")
                }
            }

        /** Non-blocking risks the owner should review. */
        val advisories: List<String>
            get() = buildList {
                if (batteryOptimized) {
                    add(
                        "Battery optimisation is on. Android may stop protection in the " +
                            "background; exempt NEXO LOCK from battery optimisation in system settings.",
                    )
                }
                if (restrictedSettingsRisk && accessibility == State.MISSING) {
                    add("Android may be hiding Accessibility behind \"Allow restricted settings\".")
                }
                if (biometric == BiometricState.NOT_ENROLLED) {
                    add(
                        "No biometric is enrolled in Android settings. Sessions fall back to the " +
                            "volume pattern + master PIN; full three-factor protection is unavailable.",
                    )
                }
                if (biometric == BiometricState.UNSUPPORTED) {
                    add(
                        "This device reports no usable biometric sensor. Only the volume pattern " +
                            "+ master PIN can be enforced; full three-factor protection is unavailable.",
                    )
                }
            }

        val allMessages: List<String> get() = warnings + advisories
    }

    fun snapshot(context: Context, serviceRunning: Boolean): Health {
        val installer = installerPackage(context)
        val restrictedRisk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            installer != PLAY_STORE &&
            !isAccessibilityEnabled(context)

        return Health(
            overlay = if (Settings.canDrawOverlays(context)) State.GRANTED else State.MISSING,
            accessibility = when {
                isAccessibilityEnabled(context) -> State.GRANTED
                restrictedRisk -> State.RESTRICTED
                else -> State.MISSING
            },
            usage = if (hasUsageAccess(context)) State.GRANTED else State.MISSING,
            notifications = if (notificationsEnabled(context)) State.GRANTED else State.MISSING,
            camera = if (hasCameraPermission(context)) State.GRANTED else State.MISSING,
            batteryOptimized = isBatteryOptimized(context),
            biometric = biometricState(context),
            restrictedSettingsRisk = restrictedRisk,
            serviceRunning = serviceRunning,
            accessibilityConnected = isAccessibilityEnabled(context) && isAccessibilityConnected(),
        )
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, NexoAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            entry.equals(expected.flattenToString(), ignoreCase = true) ||
                entry.equals(expected.flattenToShortString(), ignoreCase = true)
        }
    }

    fun isAccessibilityConnected(): Boolean = NexoAccessibilityService.isConnected

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isBatteryOptimized(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun biometricState(context: Context): BiometricState =
        when (
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricState.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricState.NOT_ENROLLED
            else -> BiometricState.UNSUPPORTED
        }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the install source is not the Play Store: Android 13+ may restrict settings. */
    fun isRestrictedSettingsRisk(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            installerPackage(context) != PLAY_STORE

    private fun installerPackage(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    // ------------------------------------------------------------------ settings intents

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun biometricEnrollIntent(): Intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)

    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private const val PLAY_STORE = "com.android.vending"
}
