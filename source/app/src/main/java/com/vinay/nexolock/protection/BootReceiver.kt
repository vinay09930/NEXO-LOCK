package com.vinay.nexolock.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vinay.nexolock.NexoApplication
import com.vinay.nexolock.data.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts protection after a reboot or an app update when the owner has left
 * protection enabled. Boot start is best-effort: OEM battery managers may still
 * block it, which is documented in the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as? NexoApplication ?: run {
            pending.finish()
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val config = app.container.prefs.snapshot()
                if (!config.setupComplete || !config.protectionEnabled) return@launch
                val health = PermissionManager.snapshot(
                    context,
                    serviceRunning = config.protectionEnabled,
                )
                if (health.warnings.isNotEmpty()) {
                    app.container.logs.log(
                        LogType.DEGRADED_PROTECTION,
                        "After restart: ${health.warnings.joinToString(" ")}",
                    )
                }
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, ProtectionService::class.java),
                    )
                }.onFailure { error ->
                    // Android 15+ can refuse a background FGS start; record the
                    // failure instead of silently believing protection resumed.
                    app.container.logs.log(
                        LogType.SERVICE_ERROR,
                        "Protection could not restart after ${action}: ${error.javaClass.simpleName}",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
