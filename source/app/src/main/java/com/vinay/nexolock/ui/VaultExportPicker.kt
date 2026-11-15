package com.vinay.nexolock.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * SAF launchers for vault export and original-file deletion consent.
 *
 * Fixed 16-bit request codes are used for the same FragmentActivity limitation
 * documented in [VaultFilePicker]: activity-compose generates request codes
 * above 0xFFFF and FragmentActivity rejects them. Results are routed by
 * MainActivity to the ViewModel, so a configuration change or navigation
 * cannot strand a pending export.
 */
object VaultExportPicker {

    const val REQUEST_CREATE_DOCUMENT = 0x4E04
    const val REQUEST_OPEN_TREE = 0x4E05
    const val REQUEST_DELETE_CONSENT = 0x4E06

    private val createDocument = ActivityResultContracts.CreateDocument("*/*")
    private val openTree = ActivityResultContracts.OpenDocumentTree()
    private val deleteConsent = ActivityResultContracts.StartIntentSenderForResult()

    @Volatile
    private var pending = false

    fun launchCreateDocument(context: Context, suggestedName: String): Boolean {
        val activity = context.findActivity() ?: return false
        return launch(
            activity,
            createDocument.createIntent(context, suggestedName),
            REQUEST_CREATE_DOCUMENT,
        )
    }

    fun launchOpenTree(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        return launch(activity, openTree.createIntent(context, null), REQUEST_OPEN_TREE)
    }

    fun launchDeleteConsent(context: Context, sender: IntentSender): Boolean {
        val activity = context.findActivity() ?: return false
        return launch(
            activity,
            deleteConsent.createIntent(
                context,
                IntentSenderRequest.Builder(sender).build(),
            ),
            REQUEST_DELETE_CONSENT,
        )
    }

    private fun launch(activity: Activity, intent: Intent, requestCode: Int): Boolean =
        runCatching {
            ActivityCompat.startActivityForResult(activity, intent, requestCode, null)
            pending = true
            true
        }.getOrElse {
            pending = false
            false
        }

    /** Clears the pending flag and returns the chosen document URI, if any. */
    fun parseCreateDocument(requestCode: Int, resultCode: Int, data: Intent?): Uri? {
        if (requestCode != REQUEST_CREATE_DOCUMENT) return null
        pending = false
        if (resultCode != Activity.RESULT_OK) return null
        return createDocument.parseResult(resultCode, data)
    }

    /** Clears the pending flag and returns the chosen tree URI, if any. */
    fun parseOpenTree(requestCode: Int, resultCode: Int, data: Intent?): Uri? {
        if (requestCode != REQUEST_OPEN_TREE) return null
        pending = false
        if (resultCode != Activity.RESULT_OK) return null
        return openTree.parseResult(resultCode, data)
    }

    /** True only when the user approved the system delete request. */
    fun parseDeleteConsent(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_DELETE_CONSENT) return false
        pending = false
        return resultCode == Activity.RESULT_OK
    }

    fun hasPendingResult(): Boolean = pending

    fun clearPending() {
        pending = false
    }
}
