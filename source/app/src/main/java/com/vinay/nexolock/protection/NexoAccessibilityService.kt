package com.vinay.nexolock.protection

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.vinay.nexolock.NexoApplication
import com.vinay.nexolock.data.LogType
import kotlinx.coroutines.launch

/**
 * Reports app switches only. Window content is explicitly disabled in
 * res/xml/nexo_accessibility_service.xml and key filtering is not requested.
 *
 * The connect/disconnect lifecycle is journaled so the security log shows when
 * monitoring started or stopped responding instead of leaving a silent gap.
 */
class NexoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        lastReportedPackage = null
        (application as? NexoApplication)?.let { app ->
            app.container.appScope.launch {
                app.container.logs.log(
                    LogType.MONITORING_CONNECTED,
                    "Accessibility app-switch monitoring connected",
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> Unit
            else -> return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName in IGNORED_PACKAGES) return
        // TYPE_WINDOWS_CHANGED fires several times for one transition (IME,
        // notification shade, dialogs). Suppress consecutive duplicates of that
        // noisy type only; an explicit TYPE_WINDOW_STATE_CHANGED transition —
        // e.g. returning to a protected app after the screen was locked — is
        // always reported so protection cannot miss it.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            packageName == lastReportedPackage
        ) {
            return
        }
        lastReportedPackage = packageName
        (application as NexoApplication).container.runtime.onForegroundPackage(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        reportDisconnected("Accessibility service disconnected; detection falls back to usage polling")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        reportDisconnected("Accessibility service destroyed; detection falls back to usage polling")
        super.onDestroy()
    }

    private fun reportDisconnected(message: String) {
        val wasConnected = isConnected
        isConnected = false
        lastReportedPackage = null
        if (!wasConnected) return
        (application as? NexoApplication)?.let { app ->
            app.container.appScope.launch {
                app.container.logs.log(LogType.MONITORING_INTERRUPTED, message)
            }
        }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        /** Main-thread only; reset on every connect/disconnect. */
        private var lastReportedPackage: String? = null

        private const val SYSTEM_UI = "com.android.systemui"

        /**
         * System surfaces that must never be treated as a protected-app launch:
         * the status bar/notification shade, the navigation bar, the lock screen,
         * the permission controller, and common IME packages.
         */
        private val IGNORED_PACKAGES = setOf(
            SYSTEM_UI,
            "android",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings.intelligence",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.baidu.input",
            "com.sohu.inputmethod.sogou",
            "com.iflytek.inputmethod",
        )
    }
}
