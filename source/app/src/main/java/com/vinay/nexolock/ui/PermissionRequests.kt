package com.vinay.nexolock.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.app.ActivityCompat

/**
 * Runtime-permission requests with fixed request codes.
 *
 * Compose's rememberLauncherForActivityResult generates request codes above
 * 0xFFFF. FragmentActivity (kept for BiometricPrompt 1.1) rejects those in
 * validateRequestPermissionsRequestCode, so permission launchers crash on this
 * host. Sending the request through ActivityCompat with a small fixed code
 * avoids that incompatibility. Results are delivered to MainActivity, which
 * refreshes PermissionManager; onResume also refreshes after the dialog closes.
 */
object PermissionRequests {

    const val NOTIFICATIONS = 0x4E01
    const val CAMERA = 0x4E02

    /** Returns false when no Activity is available to own the request. */
    fun request(context: Context, permission: String, requestCode: Int): Boolean {
        val activity = context.findActivity() ?: return false
        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        return true
    }
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
