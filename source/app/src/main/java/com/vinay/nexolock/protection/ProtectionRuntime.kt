package com.vinay.nexolock.protection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProtectionStatus(
    val running: Boolean = false,
    val targetPackage: String? = null,
    val lastError: String? = null,
    val degradedReasons: List<String> = emptyList(),
) {
    val degraded: Boolean get() = degradedReasons.isNotEmpty()
}

/**
 * Process-local bridge between the accessibility service, the foreground
 * service, the lock activity and the UI. No broadcasts, no exported surface.
 */
class ProtectionRuntime {

    private val _status = MutableStateFlow(ProtectionStatus())
    val status: StateFlow<ProtectionStatus> = _status.asStateFlow()

    @Volatile
    private var service: ProtectionService? = null

    fun attach(service: ProtectionService) {
        this.service = service
        _status.update { it.copy(running = true, lastError = null) }
    }

    fun detach(service: ProtectionService) {
        if (this.service === service) {
            this.service = null
            _status.update {
                it.copy(running = false, targetPackage = null, degradedReasons = emptyList())
            }
        }
    }

    fun onForegroundPackage(packageName: String) {
        service?.onForegroundPackage(packageName)
    }

    fun onLockUiVisible(packageName: String, sessionToken: String) {
        service?.onLockUiVisible(packageName, sessionToken)
    }

    fun onLockUiReady(packageName: String, sessionToken: String) {
        service?.onLockUiReady(packageName, sessionToken)
    }

    fun onLockUiHidden(packageName: String, sessionToken: String) {
        service?.onLockUiHidden(packageName, sessionToken)
    }

    /** The lock activity was destroyed without reporting a result. Fail closed. */
    fun onLockSurfaceGone(packageName: String, sessionToken: String) {
        service?.onLockSurfaceGone(packageName, sessionToken)
    }

    /**
     * Reports that a system biometric prompt owned by the lock activity is on
     * screen. While true the service must not treat the lock activity's
     * onPause as an abandoned lock or relaunch a duplicate prompt.
     */
    fun onLockPromptShown(packageName: String, sessionToken: String) {
        service?.onLockPromptShown(packageName, sessionToken)
    }

    fun onLockPromptDismissed(packageName: String, sessionToken: String) {
        service?.onLockPromptDismissed(packageName, sessionToken)
    }

    fun onLockResult(packageName: String, sessionToken: String, unlocked: Boolean) {
        service?.onLockResult(packageName, sessionToken, unlocked)
    }

    fun setTarget(packageName: String?) {
        _status.update { it.copy(targetPackage = packageName) }
    }

    fun reportError(message: String) {
        _status.update { it.copy(lastError = message) }
    }

    /** Periodic health result from the running service. */
    fun reportHealth(reasons: List<String>) {
        _status.update { it.copy(degradedReasons = reasons.distinct()) }
    }

    fun clearError() {
        _status.update { it.copy(lastError = null) }
    }
}
