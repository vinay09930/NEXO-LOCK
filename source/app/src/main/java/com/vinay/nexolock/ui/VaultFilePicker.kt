package com.vinay.nexolock.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * Multi-file picker for vault imports using a fixed request code.
 *
 * activity-compose 1.13 generates random request codes >= 0x10000 in
 * ComponentActivity's ActivityResultRegistry. FragmentActivity (kept for
 * BiometricPrompt 1.1) rejects any request code above 0xFFFF, so
 * rememberLauncherForActivityResult crashes when used from this host. The
 * picker therefore goes through ActivityCompat with a small fixed code.
 *
 * The result is delivered to MainActivity, which forwards the URIs to the
 * ViewModel. No composable-owned callback is retained, so a configuration
 * change or navigation cannot strand the picker result.
 */
object VaultFilePicker {

    const val REQUEST_CODE = 0x4E03

    private val contract = ActivityResultContracts.OpenMultipleDocuments()

    @Volatile
    private var pending = false

    /**
     * Opens the system document picker. Returns false when no Activity is
     * available or the picker intent cannot be resolved.
     *
     * [allowWrite] requests FLAG_GRANT_WRITE_URI_PERMISSION so the opt-in
     * "delete original after import" flow can ask the owning provider to remove
     * the document. The flag does not grant any other filesystem access.
     */
    fun launch(context: Context, allowWrite: Boolean = false): Boolean {
        val activity = context.findActivity() ?: return false
        val intent = contract.createIntent(context, arrayOf("*/*"))
        if (allowWrite) {
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return runCatching {
            ActivityCompat.startActivityForResult(activity, intent, REQUEST_CODE, null)
            pending = true
            true
        }.getOrElse {
            pending = false
            false
        }
    }

    /** True while the system picker is still expected to deliver a result. */
    fun hasPendingResult(): Boolean = pending

    /**
     * Converts an activity result into the picked URIs. Cancelled results and
     * empty selections produce an empty list; the pending flag is cleared in
     * every case.
     */
    fun parseResult(requestCode: Int, resultCode: Int, data: Intent?): List<Uri> {
        if (requestCode != REQUEST_CODE) return emptyList()
        pending = false
        if (resultCode != Activity.RESULT_OK) return emptyList()
        return contract.parseResult(resultCode, data)
    }

    /** Clears a pending picker after the hosting activity is recreated. */
    fun clearPending() {
        pending = false
    }
}
