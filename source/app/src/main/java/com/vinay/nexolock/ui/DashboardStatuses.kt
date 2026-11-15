package com.vinay.nexolock.ui

import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.vault.CaptureState

/**
 * Truthful dashboard status derivation.
 *
 * Everything here is pure: the statuses depend only on persisted policy,
 * measured permission state and observed hardware capabilities. No status may
 * claim that protection, monitoring, a vault or a factor is healthy unless the
 * corresponding observable state is actually healthy.
 */
enum class StatusTone { OK, WARN, BAD, NEUTRAL }

/**
 * The only labels the UI may use for a protection feature. "Enabled" is
 * reserved for a feature that is configured AND currently operational.
 */
enum class MonitoringState(val label: String) {
    ENABLED("Enabled"),
    NOT_CONFIGURED("Not configured"),
    PERMISSION_REQUIRED("Permission required"),
    UNSUPPORTED("Unsupported"),
    TEMPORARILY_UNAVAILABLE("Temporarily unavailable"),
}

enum class DashboardAction {
    SETTINGS,
    PERMISSIONS,
    GESTURE,
    BIOMETRIC,
    PERSONAL_VAULT,
    INTRUDER_VAULT,
}

/** One truthful dashboard status, derived from observable state only. */
data class DashboardStatus(
    val title: String,
    val value: String,
    val detail: String,
    val tone: StatusTone,
    val action: DashboardAction,
)

object DashboardStatuses {

    /** Monitoring is "Enabled" only when configuration, permission and service all agree. */
    fun monitoringState(
        config: NexoConfig,
        health: PermissionManager.Health,
        protectionRunning: Boolean,
    ): MonitoringState = when {
        !config.hasMainPin || !config.hasMainGesture -> MonitoringState.NOT_CONFIGURED
        !config.protectionEnabled -> MonitoringState.NOT_CONFIGURED
        health.accessibility == PermissionManager.State.UNSUPPORTED &&
            health.usage == PermissionManager.State.UNSUPPORTED -> MonitoringState.UNSUPPORTED
        !health.detectionAvailable || !health.canDrawLock -> MonitoringState.PERMISSION_REQUIRED
        !protectionRunning || !health.serviceRunning -> MonitoringState.TEMPORARILY_UNAVAILABLE
        health.accessibility == PermissionManager.State.GRANTED &&
            !health.accessibilityConnected &&
            health.usage != PermissionManager.State.GRANTED ->
            MonitoringState.TEMPORARILY_UNAVAILABLE
        else -> MonitoringState.ENABLED
    }

    fun volumePatternState(config: NexoConfig): MonitoringState = when {
        !config.hasMainGesture || !config.gestureEnabled -> MonitoringState.NOT_CONFIGURED
        config.mainGestureLength !in
            com.vinay.nexolock.domain.VolumePattern.MIN_LENGTH..
            com.vinay.nexolock.domain.VolumePattern.MAX_LENGTH ->
            MonitoringState.TEMPORARILY_UNAVAILABLE
        else -> MonitoringState.ENABLED
    }

    fun biometricState(config: NexoConfig, biometric: BiometricState): MonitoringState = when {
        biometric == BiometricState.UNSUPPORTED -> MonitoringState.UNSUPPORTED
        biometric == BiometricState.NOT_ENROLLED -> MonitoringState.NOT_CONFIGURED
        !config.biometricEnabled -> MonitoringState.NOT_CONFIGURED
        else -> MonitoringState.ENABLED
    }

    fun overlayState(health: PermissionManager.Health): MonitoringState =
        when (health.overlay) {
            PermissionManager.State.GRANTED -> MonitoringState.ENABLED
            PermissionManager.State.UNSUPPORTED -> MonitoringState.UNSUPPORTED
            PermissionManager.State.RESTRICTED,
            PermissionManager.State.MISSING,
            -> MonitoringState.PERMISSION_REQUIRED
        }

    fun accessibilityState(health: PermissionManager.Health): MonitoringState = when {
        health.accessibility == PermissionManager.State.UNSUPPORTED -> MonitoringState.UNSUPPORTED
        health.accessibility != PermissionManager.State.GRANTED ->
            MonitoringState.PERMISSION_REQUIRED
        !health.accessibilityConnected -> MonitoringState.TEMPORARILY_UNAVAILABLE
        else -> MonitoringState.ENABLED
    }

    fun protectedAppsState(count: Int): MonitoringState =
        if (count > 0) MonitoringState.ENABLED else MonitoringState.NOT_CONFIGURED

    /** Permissions that must work before protected apps can be covered. */
    fun missingRequiredPermissions(health: PermissionManager.Health): List<String> = buildList {
        if (health.overlay != PermissionManager.State.GRANTED) {
            add("Display over other apps")
        }
        if (!health.detectionAvailable) {
            add("Accessibility or Usage access")
        }
    }

    fun build(
        config: NexoConfig,
        health: PermissionManager.Health,
        protectionRunning: Boolean,
        biometric: BiometricState,
        vaults: VaultOverview,
        capture: CaptureState,
    ): List<DashboardStatus> {
        val missingPermissions = missingRequiredPermissions(health)
        return listOf(
            protectionStatus(config, health, protectionRunning, missingPermissions),
            permissionStatus(health, missingPermissions),
            monitoringStatus(config, health, protectionRunning),
            vaultStatus(config, vaults),
            authenticationStatus(config, biometric),
            deviceStatus(config, biometric, capture),
        )
    }

    private fun protectionStatus(
        config: NexoConfig,
        health: PermissionManager.Health,
        protectionRunning: Boolean,
        missingPermissions: List<String>,
    ): DashboardStatus = when {
        !config.hasMainPin -> DashboardStatus(
            title = "Protection Active",
            value = "SETUP REQUIRED",
            detail = "Create a master PIN before protection can start.",
            tone = StatusTone.WARN,
            action = DashboardAction.SETTINGS,
        )
        !config.protectionEnabled -> DashboardStatus(
            title = "Protection Active",
            value = "OFF",
            detail = "Protection is disabled. Selected apps open without a NEXO lock.",
            tone = StatusTone.NEUTRAL,
            action = DashboardAction.SETTINGS,
        )
        !protectionRunning -> DashboardStatus(
            title = "Protection Active",
            value = "NOT RUNNING",
            detail = "Protection is enabled, but the foreground monitor is not running.",
            tone = StatusTone.BAD,
            action = DashboardAction.SETTINGS,
        )
        missingPermissions.isNotEmpty() -> DashboardStatus(
            title = "Protection Active",
            value = "PERMISSION REQUIRED",
            detail = "Missing: ${missingPermissions.joinToString(", ")}. " +
                "Protected apps cannot be covered until this is fixed.",
            tone = StatusTone.WARN,
            action = DashboardAction.PERMISSIONS,
        )
        health.fullyProtecting -> DashboardStatus(
            title = "Protection Active",
            value = "ACTIVE",
            detail = "Monitor, foreground detector and overlay lock surface are running.",
            tone = StatusTone.OK,
            action = DashboardAction.SETTINGS,
        )
        else -> DashboardStatus(
            title = "Protection Active",
            value = "DEGRADED",
            detail = health.warnings.firstOrNull()
                ?: "Protection is running with reduced coverage.",
            tone = StatusTone.WARN,
            action = DashboardAction.SETTINGS,
        )
    }

    private fun permissionStatus(
        health: PermissionManager.Health,
        missingPermissions: List<String>,
    ): DashboardStatus = when {
        missingPermissions.isNotEmpty() -> DashboardStatus(
            title = "Permission Required",
            value = "${missingPermissions.size} MISSING",
            detail = buildString {
                append("Required: ${missingPermissions.joinToString(", ")}.")
                if (health.notifications != PermissionManager.State.GRANTED) {
                    append(" Notifications are also off, so the ongoing status is hidden.")
                }
            },
            tone = StatusTone.BAD,
            action = DashboardAction.PERMISSIONS,
        )
        health.notifications != PermissionManager.State.GRANTED -> DashboardStatus(
            title = "Permission Required",
            value = "NOTIFICATIONS",
            detail = "Protection permissions are granted. Notifications are off, so the " +
                "ongoing protection status is hidden.",
            tone = StatusTone.WARN,
            action = DashboardAction.PERMISSIONS,
        )
        else -> DashboardStatus(
            title = "Permission Required",
            value = "GRANTED",
            detail = "Overlay, foreground detection and notification permissions are granted.",
            tone = StatusTone.OK,
            action = DashboardAction.PERMISSIONS,
        )
    }

    private fun monitoringStatus(
        config: NexoConfig,
        health: PermissionManager.Health,
        protectionRunning: Boolean,
    ): DashboardStatus {
        val detectionDetail = when {
            health.accessibility == PermissionManager.State.GRANTED -> "event-driven accessibility"
            health.usage == PermissionManager.State.GRANTED -> "usage-access polling (slower)"
            else -> "no detector"
        }
        return when {
            !config.protectionEnabled -> DashboardStatus(
                title = "Monitoring Unavailable",
                value = "NOT EXPECTED",
                detail = "Protection is off, so no monitor is expected to run.",
                tone = StatusTone.NEUTRAL,
                action = DashboardAction.SETTINGS,
            )
            !protectionRunning -> DashboardStatus(
                title = "Monitoring Unavailable",
                value = "YES",
                detail = "The protection service is not running. Nothing currently detects " +
                    "protected-app launches.",
                tone = StatusTone.BAD,
                action = DashboardAction.SETTINGS,
            )
            !health.detectionAvailable -> DashboardStatus(
                title = "Monitoring Unavailable",
                value = "YES",
                detail = "No foreground detector is active. Enable Accessibility or Usage access.",
                tone = StatusTone.BAD,
                action = DashboardAction.PERMISSIONS,
            )
            health.accessibility == PermissionManager.State.GRANTED &&
                !health.accessibilityConnected -> DashboardStatus(
                title = "Monitoring Unavailable",
                value = "PARTIAL",
                detail = "Accessibility is enabled but not connected. Usage polling is the only " +
                    "detector right now.",
                tone = StatusTone.WARN,
                action = DashboardAction.PERMISSIONS,
            )
            else -> DashboardStatus(
                title = "Monitoring Unavailable",
                value = "NO",
                detail = "Foreground detection is running via $detectionDetail.",
                tone = StatusTone.OK,
                action = DashboardAction.PERMISSIONS,
            )
        }
    }

    private fun vaultStatus(
        config: NexoConfig,
        vaults: VaultOverview,
    ): DashboardStatus {
        val damaged = vaults.personalCorrupted || vaults.intruderCorrupted
        return when {
            damaged -> DashboardStatus(
                title = "Vault Secured",
                value = "DAMAGED",
                detail = "A vault index failed authentication. Encrypted content is not " +
                    "trusted until it is reviewed.",
                tone = StatusTone.BAD,
                action = if (vaults.personalCorrupted) {
                    DashboardAction.PERSONAL_VAULT
                } else {
                    DashboardAction.INTRUDER_VAULT
                },
            )
            !config.hasPersonalVaultPin && !config.hasIntruderVaultPin -> DashboardStatus(
                title = "Vault Secured",
                value = "NOT CONFIGURED",
                detail = "No vault PIN exists. Create one to store encrypted files and records.",
                tone = StatusTone.NEUTRAL,
                action = DashboardAction.PERSONAL_VAULT,
            )
            !config.hasPersonalVaultPin -> DashboardStatus(
                title = "Vault Secured",
                value = "INTRUDER ONLY",
                detail = "Only the Intruder Vault is configured, holding " +
                    "${vaults.intruderCount} encrypted records.",
                tone = StatusTone.WARN,
                action = DashboardAction.PERSONAL_VAULT,
            )
            !config.hasIntruderVaultPin -> DashboardStatus(
                title = "Vault Secured",
                value = "PERSONAL ONLY",
                detail = "The Personal Vault holds ${vaults.personalCount} encrypted items. " +
                    "The Intruder Vault is not configured.",
                tone = StatusTone.WARN,
                action = DashboardAction.INTRUDER_VAULT,
            )
            else -> DashboardStatus(
                title = "Vault Secured",
                value = "SECURED",
                detail = "${vaults.personalCount} personal items and " +
                    "${vaults.intruderCount} intruder records, encrypted with separate " +
                    "AES-256-GCM keys.",
                tone = StatusTone.OK,
                action = DashboardAction.PERSONAL_VAULT,
            )
        }
    }

    private fun authenticationStatus(
        config: NexoConfig,
        biometric: BiometricState,
    ): DashboardStatus = when {
        !config.hasMainPin -> DashboardStatus(
            title = "Mandatory Factors",
            value = "INCOMPLETE",
            detail = "The master PIN is not configured.",
            tone = StatusTone.WARN,
            action = DashboardAction.SETTINGS,
        )
        !config.hasMainGesture -> DashboardStatus(
            title = "Mandatory Factors",
            value = "INCOMPLETE",
            detail = "The volume pattern factor is not configured.",
            tone = StatusTone.WARN,
            action = DashboardAction.GESTURE,
        )
        biometric == BiometricState.UNSUPPORTED -> DashboardStatus(
            title = "Mandatory Factors",
            value = "FALLBACK",
            detail = "Device Feature Unsupported: no biometric sensor. The documented " +
                "fallback is volume pattern + master PIN.",
            tone = StatusTone.WARN,
            action = DashboardAction.BIOMETRIC,
        )
        biometric == BiometricState.NOT_ENROLLED -> DashboardStatus(
            title = "Mandatory Factors",
            value = "INCOMPLETE",
            detail = "No biometric is enrolled in Android. Enroll one, or the documented " +
                "fallback is volume pattern + master PIN.",
            tone = StatusTone.WARN,
            action = DashboardAction.BIOMETRIC,
        )
        !config.biometricEnabled -> DashboardStatus(
            title = "Mandatory Factors",
            value = "INCOMPLETE",
            detail = "Biometric is available but disabled. Unlock currently uses volume " +
                "pattern + master PIN.",
            tone = StatusTone.WARN,
            action = DashboardAction.BIOMETRIC,
        )
        else -> DashboardStatus(
            title = "Mandatory Factors",
            value = "COMPLETE",
            detail = "Biometric and volume pattern are required in that order before the master " +
                "PIN. Each stage has its own fixed window: decoy ${config.decoyTimeoutSeconds}s, " +
                "pattern ${config.gestureTimeoutSeconds}s, PIN ${config.pinTimeoutSeconds}s.",
            tone = StatusTone.OK,
            action = DashboardAction.GESTURE,
        )
    }

    private fun deviceStatus(
        config: NexoConfig,
        biometric: BiometricState,
        capture: CaptureState,
    ): DashboardStatus {
        val unsupported = buildList {
            if (biometric == BiometricState.UNSUPPORTED) {
                add("No biometric sensor — the documented fallback is volume pattern + master PIN")
            }
            if (capture.checked && config.intruderCaptureEnabled && config.hasIntruderVaultPin &&
                !capture.cameraAvailable
            ) {
                add("No usable front camera — intruder capture cannot photograph")
            }
        }
        return if (unsupported.isEmpty()) {
            DashboardStatus(
                title = "Device Feature Unsupported",
                value = "NONE",
                detail = "No configured feature is blocked by missing hardware.",
                tone = StatusTone.OK,
                action = DashboardAction.SETTINGS,
            )
        } else {
            DashboardStatus(
                title = "Device Feature Unsupported",
                value = if (biometric == BiometricState.UNSUPPORTED) "BIOMETRIC" else "CAMERA",
                detail = unsupported.joinToString(". "),
                tone = StatusTone.WARN,
                action = if (biometric == BiometricState.UNSUPPORTED) {
                    DashboardAction.BIOMETRIC
                } else {
                    DashboardAction.INTRUDER_VAULT
                },
            )
        }
    }
}
