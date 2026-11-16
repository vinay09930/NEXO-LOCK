package com.vinay.nexolock

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.vinay.nexolock.ui.NexoRoot
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.selflock.SelfLockViewModel
import com.vinay.nexolock.ui.VaultExportPicker
import com.vinay.nexolock.ui.VaultFilePicker

/**
 * Management UI. Extends FragmentActivity so the biometric test screen can use
 * androidx BiometricPrompt, and handles volume keys only while a capture screen
 * has explicitly registered a listener.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as NexoApplication).container

    private val viewModel: NexoViewModel by viewModels {
        NexoViewModel.factory(container)
    }

    /**
     * Self-Protection authentication. Activity-scoped so a rotation keeps the
     * verified session, while a process death necessarily starts a new one.
     */
    private val selfLockViewModel: SelfLockViewModel by viewModels {
        SelfLockViewModel.factory(container)
    }

    /** Monotonic time the management UI left the foreground, or 0. */
    private var leftForegroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NexoRoot(viewModel, selfLockViewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshBiometric()
        viewModel.refreshLogs()
    }

    override fun onStart() {
        super.onStart()
        val since = leftForegroundAt
        leftForegroundAt = 0L
        if (since != 0L &&
            SystemClock.elapsedRealtime() - since >= VAULT_BACKGROUND_LOCK_MS
        ) {
            viewModel.lockVaultsOnBackground()
        }
        if (since != 0L &&
            SystemClock.elapsedRealtime() - since >= SELF_PROTECTION_BACKGROUND_LOCK_MS
        ) {
            // Coming back after a real absence requires the owner to verify
            // again; a session is never carried across a background trip.
            viewModel.lockSelfProtection()
        }
    }

    override fun onStop() {
        super.onStop()
        // A system file picker or delete confirmation is an intentional trip
        // outside the app; the vault must stay unlocked until its result returns.
        if (!VaultFilePicker.hasPendingResult() && !VaultExportPicker.hasPendingResult()) {
            leftForegroundAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permission state is read from the framework, so a refresh is enough to
        // update the setup/settings screens and the camera availability row.
        viewModel.refreshPermissions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // SAF launchers use fixed 16-bit request codes; see VaultFilePicker and
        // VaultExportPicker for why Compose's launcher cannot be used here.
        when (requestCode) {
            VaultFilePicker.REQUEST_CODE -> {
                val uris = VaultFilePicker.parseResult(requestCode, resultCode, data)
                if (uris.isNotEmpty()) viewModel.importVaultFiles(uris)
            }

            VaultExportPicker.REQUEST_CREATE_DOCUMENT -> {
                val uri = VaultExportPicker.parseCreateDocument(requestCode, resultCode, data)
                if (uri != null) {
                    viewModel.onExportDestination(uri, tree = false)
                } else {
                    viewModel.onExportPickerCancelled()
                }
            }

            VaultExportPicker.REQUEST_OPEN_TREE -> {
                val uri = VaultExportPicker.parseOpenTree(requestCode, resultCode, data)
                if (uri != null) {
                    viewModel.onExportDestination(uri, tree = true)
                } else {
                    viewModel.onExportPickerCancelled()
                }
            }

            VaultExportPicker.REQUEST_DELETE_CONSENT -> {
                val approved = VaultExportPicker.parseDeleteConsent(requestCode, resultCode, data)
                viewModel.onDeleteConsentResult(approved)
            }
        }
    }

    // Lint flags ComponentActivity.dispatchKeyEvent as restricted to the
    // androidx group, but this override is the public framework Activity hook.
    // It is required to swallow volume keys before any view can consume them.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (container.volumeCapture.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        /** Vaults lock after the app has been in the background this long. */
        const val VAULT_BACKGROUND_LOCK_MS = 10_000L

        /** Self-Protection requires a new verification after this absence. */
        const val SELF_PROTECTION_BACKGROUND_LOCK_MS = 30_000L
    }
}
