package com.vinay.nexolock.protection

import android.content.Context
import android.content.Intent

/**
 * Backwards-compatible facade. All real state lives in [PermissionManager]; this
 * object remains so existing call sites keep working while new code uses the
 * structured health snapshot.
 */
object PermissionChecks {

    fun hasUsageAccess(context: Context): Boolean = PermissionManager.hasUsageAccess(context)

    fun isAccessibilityServiceEnabled(context: Context): Boolean =
        PermissionManager.isAccessibilityEnabled(context)

    fun isAccessibilityConnected(): Boolean = PermissionManager.isAccessibilityConnected()

    fun canDrawOverlays(context: Context): Boolean =
        PermissionManager.snapshot(context, serviceRunning = false).canDrawLock

    fun notificationsEnabled(context: Context): Boolean =
        PermissionManager.notificationsEnabled(context)

    fun overlaySettingsIntent(context: Context): Intent =
        PermissionManager.overlaySettingsIntent(context)

    fun accessibilitySettingsIntent(): Intent = PermissionManager.accessibilitySettingsIntent()

    fun usageSettingsIntent(): Intent = PermissionManager.usageSettingsIntent()

    fun appDetailsIntent(context: Context): Intent = PermissionManager.appDetailsIntent(context)

    fun biometricEnrollIntent(): Intent = PermissionManager.biometricEnrollIntent()

    fun batterySettingsIntent(): Intent = PermissionManager.batterySettingsIntent()

    fun notificationSettingsIntent(context: Context): Intent =
        PermissionManager.notificationSettingsIntent(context)
}
