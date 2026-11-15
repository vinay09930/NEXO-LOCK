package com.vinay.nexolock

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.vinay.nexolock.core.crypto.KeystoreCipher
import com.vinay.nexolock.core.crypto.PinHasher
import com.vinay.nexolock.data.CredentialRepository
import com.vinay.nexolock.data.NexoPreferences
import com.vinay.nexolock.data.ProtectedAppsRepository
import com.vinay.nexolock.data.SecurityLogRepository
import com.vinay.nexolock.domain.AuthenticationService
import com.vinay.nexolock.domain.PackageCatalog
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.protection.ProtectionRuntime
import com.vinay.nexolock.protection.VolumeCaptureController
import com.vinay.nexolock.vault.IntruderCaptureManager
import com.vinay.nexolock.vault.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** Process-wide scope for work that must outlive one screen (capture writes). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val keystore = KeystoreCipher()
    val pinHasher = PinHasher()
    val prefs = NexoPreferences(appContext)
    val credentials = CredentialRepository(prefs, pinHasher)
    val protectedApps = ProtectedAppsRepository(prefs, keystore)
    val logs = SecurityLogRepository(appContext, keystore)
    val auth = AuthenticationService(prefs, credentials)
    val catalog = PackageCatalog(appContext)
    val volumeCapture = VolumeCaptureController()
    val runtime = ProtectionRuntime()
    val vaults = VaultManager(appContext, prefs, credentials, auth, logs)
    val intruderCapture = IntruderCaptureManager(appContext, prefs, vaults, logs, appScope)

    fun permissionHealth(): PermissionManager.Health =
        PermissionManager.snapshot(appContext, runtime.status.value.running)

    fun canDrawOverlays(): Boolean = PermissionManager.snapshot(appContext, false).canDrawLock

    fun hasUsageAccess(): Boolean = PermissionManager.hasUsageAccess(appContext)

    fun isAccessibilityEnabled(): Boolean =
        PermissionManager.isAccessibilityConnected() ||
            PermissionManager.isAccessibilityEnabled(appContext)

    fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
}
