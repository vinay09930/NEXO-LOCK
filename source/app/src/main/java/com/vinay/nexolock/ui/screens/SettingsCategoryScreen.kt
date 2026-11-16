package com.vinay.nexolock.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.ui.BiometricState
import com.vinay.nexolock.ui.DashboardStatuses
import com.vinay.nexolock.ui.MonitoringState
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.PermissionRequests
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigFilledIconContainer
import com.vinay.nexolock.ui.components.SigFooterNote
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigKeyValueRow
import com.vinay.nexolock.ui.components.SigPermissionCard
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigSettingRow
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace

@Composable
fun SettingsCategoryScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    category: SettingsCategory,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onChangeRecovery: () -> Unit,
    onChangePattern: () -> Unit,
    onGesture: () -> Unit,
    onBiometric: () -> Unit,
    onLogs: () -> Unit,
    onPersonalVault: () -> Unit,
    onIntruderVault: () -> Unit,
    onProtectedApps: () -> Unit,
    onSelectApps: () -> Unit,
) {
    val health = state.permissionHealth
    val context = LocalContext.current
    val cameraGranted = health.camera == PermissionManager.State.GRANTED
    var pendingCapture by rememberSaveable { mutableStateOf(false) }

    // This is the existing capture-enable flow, moved with its control. The
    // permission dialog belongs to the system; once camera is granted, finish
    // the user's original intent without requiring a second tap.
    LaunchedEffect(health.camera, pendingCapture) {
        if (pendingCapture && cameraGranted) {
            pendingCapture = false
            vm.setIntruderCaptureEnabled(true) { failure ->
                if (failure != null) vm.notify(failure, error = true)
            }
        }
    }

    SignatureScreen(
        title = category.title,
        subtitle = category.summary,
        eyebrow = "SETTINGS",
        onBack = onBack,
    ) {
        when (category) {
            SettingsCategory.PROTECTION -> ProtectionCategory(
                state = state,
                vm = vm,
                onProtectedApps = onProtectedApps,
                onSelectApps = onSelectApps,
            )

            SettingsCategory.AUTHENTICATION -> AuthenticationCategory(
                state = state,
                vm = vm,
                onChangePin = onChangePin,
                onChangeRecovery = onChangeRecovery,
                onChangePattern = onChangePattern,
                onGesture = onGesture,
                onBiometric = onBiometric,
            )

            SettingsCategory.VAULTS -> VaultsCategory(
                state = state,
                vm = vm,
                cameraGranted = cameraGranted,
                pendingCapture = pendingCapture,
                onPendingCaptureChange = { pendingCapture = it },
                onPersonalVault = onPersonalVault,
                onIntruderVault = onIntruderVault,
            )

            SettingsCategory.NOTIFICATIONS -> NotificationsCategory(state = state, vm = vm)

            SettingsCategory.APPEARANCE -> AppearanceCategory()

            SettingsCategory.PERMISSIONS -> PermissionsCategory(state = state, vm = vm)

            SettingsCategory.SECURITY -> SecurityCategory(state = state, onLogs = onLogs)

            SettingsCategory.BATTERY -> BatteryCategory(state = state, vm = vm)

            // The hub routes About directly to AboutScreen; this branch is not
            // reachable from the Settings category list.
            SettingsCategory.ABOUT -> Unit
        }
    }
}

@Composable
private fun ProtectionCategory(
    state: NexoUiState,
    vm: NexoViewModel,
    onProtectedApps: () -> Unit,
    onSelectApps: () -> Unit,
) {
    val config = state.config
    SigSectionHeader("Monitoring")
    SigCard(padding = NexoSpace.sm + 2.dp) {
        SigSettingRow(
            icon = Icons.Filled.Lock,
            title = "Protection monitor",
            subtitle = when {
                !config.protectionEnabled -> "Off — protected apps open normally"
                state.protectionRunning -> "Running in the foreground"
                else -> "Configured — service not running"
            },
            trailing = {
                SignatureSwitch(
                    checked = config.protectionEnabled,
                    enabled = config.hasMainPin,
                    onCheckedChange = vm::setProtectionEnabled,
                )
            },
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Eye,
            title = "Privacy cover",
            subtitle = "Block screenshots and blank the recents thumbnail",
            trailing = {
                SignatureSwitch(
                    checked = config.privacyCover,
                    onCheckedChange = vm::setPrivacyCover,
                )
            },
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Protected apps")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.ShieldCheck,
            title = "Protected apps",
            subtitle = when (state.protectedPackages.size) {
                0 -> "No apps selected yet"
                1 -> "1 app opens behind NEXO LOCK"
                else -> "${state.protectedPackages.size} apps open behind NEXO LOCK"
            },
            onClick = onProtectedApps,
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Grid,
            title = "Choose apps",
            subtitle = "Add or remove apps from protection",
            onClick = onSelectApps,
        )
    }
    SigFooterNote(
        "Self-Protection is automatic while this is on: opening NEXO LOCK requires your " +
            "configured factors, and turning protection off asks you to verify again. " +
            "Android cannot prevent uninstalling, force-stopping, clearing app data, or a " +
            "rooted/privileged system from changing this app's storage.",
    )
    SigFooterNote(
        "Display over other apps and at least one detection method are required. " +
            "Their current state is available in Permissions.",
    )
}

@Composable
private fun AuthenticationCategory(
    state: NexoUiState,
    vm: NexoViewModel,
    onChangePin: () -> Unit,
    onChangeRecovery: () -> Unit,
    onChangePattern: () -> Unit,
    onGesture: () -> Unit,
    onBiometric: () -> Unit,
) {
    val config = state.config

    SigSectionHeader("Unlock factors")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.Sliders,
            title = "Volume pattern",
            subtitle = if (config.hasMainGesture) {
                "${config.mainGestureLength} presses · " +
                    if (config.gestureEnabled) "enabled" else "disabled"
            } else {
                "Not configured"
            },
            onClick = onGesture,
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Fingerprint,
            title = "Biometric factor",
            subtitle = when (state.biometric) {
                BiometricState.READY ->
                    if (config.biometricEnabled) "Enabled" else "Available"

                BiometricState.NOT_ENROLLED -> "Not enrolled in Android settings"
                BiometricState.UNSUPPORTED -> "Unavailable on this device"
            },
            onClick = onBiometric,
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Key,
            title = "Main PIN",
            subtitle = "${config.pinLength} digits · PBKDF2 verifier",
            onClick = onChangePin,
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Protection decoy")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.Eye,
            title = "Protection decoy",
            subtitle = if (config.decoyEnabled) {
                "App-owned pause screen; tap or a volume key continues its fixed " +
                    "${config.decoyTimeoutSeconds}s window"
            } else {
                "The NEXO authentication screen is shown immediately"
            },
            trailing = {
                SignatureSwitch(
                    checked = config.decoyEnabled,
                    onCheckedChange = vm::setDecoyEnabled,
                )
            },
        )
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            "Independent stage windows",
            style = MaterialTheme.typography.bodyLarge,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Every stage owns a fixed monotonic window. Key presses, digits and retries " +
                "never restart it; expiry denies access and is recorded. Biometric is " +
                "Android-managed.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
        )
        Spacer(Modifier.height(NexoSpace.md))
        TimeoutSetting(
            label = "Decoy visibility",
            seconds = config.decoyTimeoutSeconds,
            options = listOf(3, 4, 5),
            onSelect = vm::setDecoyTimeoutSeconds,
        )
        Spacer(Modifier.height(NexoSpace.md))
        TimeoutSetting(
            label = "Volume pattern window",
            seconds = config.gestureTimeoutSeconds,
            options = listOf(10, 15, 20, 30),
            onSelect = vm::setGestureTimeoutSeconds,
        )
        Spacer(Modifier.height(NexoSpace.md))
        TimeoutSetting(
            label = "Master PIN window",
            seconds = config.pinTimeoutSeconds,
            options = listOf(10, 15, 20, 30),
            onSelect = vm::setPinTimeoutSeconds,
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Recovery")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.ShieldCheck,
            title = "Master Recovery PIN",
            subtitle = if (config.hasRecoveryPin) "Configured" else "Not configured",
            onClick = onChangeRecovery,
            trailing = {
                SigPill(
                    if (config.hasRecoveryPin) "SET" else "MISSING",
                    if (config.hasRecoveryPin) SigTone.SUCCESS else SigTone.DANGER,
                )
            },
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Grid,
            title = "Recovery pattern",
            subtitle = if (config.hasRecoveryPattern) "Configured" else "Optional",
            onClick = onChangePattern,
        )
    }
    SigFooterNote(
        "Changes apply immediately. Every stage window is fixed and is never restarted " +
            "by key presses, digits or retries.",
    )
}

@Composable
private fun VaultsCategory(
    state: NexoUiState,
    vm: NexoViewModel,
    cameraGranted: Boolean,
    pendingCapture: Boolean,
    onPendingCaptureChange: (Boolean) -> Unit,
    onPersonalVault: () -> Unit,
    onIntruderVault: () -> Unit,
) {
    val config = state.config
    val context = LocalContext.current

    SigSectionHeader("Vaults")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.Vault,
            title = "Personal Vault",
            subtitle = if (config.hasPersonalVaultPin) {
                "${state.vaults.personalCount} encrypted entries · separate PIN"
            } else {
                "Not created · private notes and files"
            },
            onClick = onPersonalVault,
            trailing = {
                SigPill(
                    if (config.hasPersonalVaultPin) "SET" else "OPEN",
                    if (config.hasPersonalVaultPin) SigTone.SUCCESS else SigTone.WARNING,
                )
            },
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigSettingRow(
            icon = NexoIcons.Camera,
            title = "Intruder Vault",
            subtitle = if (config.hasIntruderVaultPin) {
                "${state.vaults.intruderCount} records · independent PIN"
            } else {
                "Not created · failed-unlock capture records"
            },
            onClick = onIntruderVault,
            trailing = {
                SigPill(
                    if (config.hasIntruderVaultPin) "SET" else "OPEN",
                    if (config.hasIntruderVaultPin) SigTone.SUCCESS else SigTone.WARNING,
                )
            },
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Intruder capture")
    SigCard(padding = NexoSpace.sm + 2.dp) {
        SigSettingRow(
            icon = NexoIcons.Camera,
            title = "Intruder capture",
            subtitle = when {
                !config.hasIntruderVaultPin -> "Create the Intruder Vault PIN first"
                !cameraGranted -> "Camera permission is not granted"
                state.vault.capture.checked && !state.vault.capture.cameraAvailable ->
                    "No usable front camera detected on this device"

                config.intruderCaptureEnabled -> "Front camera after failed unlock · 10 s throttle"
                else -> "Off"
            },
            trailing = {
                SignatureSwitch(
                    checked = config.intruderCaptureEnabled,
                    enabled = config.hasIntruderVaultPin,
                    onCheckedChange = { enabled ->
                        if (enabled && !cameraGranted) {
                            onPendingCaptureChange(true)
                            val requested = PermissionRequests.request(
                                context,
                                Manifest.permission.CAMERA,
                                PermissionRequests.CAMERA,
                            )
                            if (!requested) {
                                onPendingCaptureChange(false)
                                vm.notify(
                                    "Camera permission cannot be requested right now.",
                                    error = true,
                                )
                            }
                        } else {
                            if (!enabled) onPendingCaptureChange(false)
                            vm.setIntruderCaptureEnabled(enabled) { failure ->
                                if (failure != null) vm.notify(failure, error = true)
                            }
                        }
                    },
                )
            },
        )
    }
    SigFooterNote(
        if (pendingCapture) {
            "Waiting for Android to return the camera permission result."
        } else {
            "Capture requires the Intruder Vault PIN and camera permission. Records are " +
                "stored only in the Intruder Vault."
        },
    )
}

@Composable
private fun NotificationsCategory(
    state: NexoUiState,
    vm: NexoViewModel,
) {
    val health = state.permissionHealth
    val context = LocalContext.current

    SigSectionHeader("Permission")
    SigPermissionCard(
        icon = Icons.Filled.Info,
        title = "Notifications",
        description = "Ongoing protection status and degraded warnings.",
        granted = health.notifications == PermissionManager.State.GRANTED,
        onAction = {
            vm.openSystemSettings(PermissionManager.notificationSettingsIntent(context))
        },
        actionLabel = "Open settings",
        missingLabel = "NOT GRANTED",
    )
    Spacer(Modifier.height(NexoSpace.md))
    SigBanner(
        text = "NEXO LOCK uses one ongoing notification for protection status and warnings. " +
            "Android controls the channel's importance; this version has no separate " +
            "in-app notification toggles.",
        tone = SigTone.INFO,
        title = "How notifications are used",
    )
    SigFooterNote("The permission state above is read from Android each time this page is shown.")
}

@Composable
private fun AppearanceCategory() {
    SigSectionHeader("Interface")
    SigCard(padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigFilledIconContainer(
                icon = NexoIcons.Eye,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(NexoSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "Management interface",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NexoPalette.TextPrimary,
                )
                Text(
                    "Always dark · NEXO LOCK SIGNATURE",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
            }
            SigPill("DARK", SigTone.SILVER)
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            "The legacy theme preference is retained in storage for upgrade compatibility. " +
                "Management screens no longer switch themes, so this page does not offer a " +
                "light-mode control.",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
    }
    SigFooterNote("Appearance is fixed by design; the lock and decoy surfaces are unchanged.")
}

@Composable
private fun PermissionsCategory(
    state: NexoUiState,
    vm: NexoViewModel,
) {
    val health = state.permissionHealth
    val context = LocalContext.current
    val cameraGranted = health.camera == PermissionManager.State.GRANTED

    SigSectionHeader("Core protection")
    Column(verticalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
        SigPermissionCard(
            icon = NexoIcons.Eye,
            title = "Display over other apps",
            description = "Required to draw the NEXO lock surface above a protected app.",
            granted = health.canDrawLock,
            onAction = {
                vm.openSystemSettings(PermissionManager.overlaySettingsIntent(context))
            },
            actionLabel = if (health.canDrawLock) "Review permission" else "Fix permission",
            missingLabel = "REQUIRED",
        )
        SigPermissionCard(
            icon = NexoIcons.ShieldCheck,
            title = "Accessibility detection",
            description = "Event-driven app-switch detection. It never reads screen content.",
            granted = health.accessibility == PermissionManager.State.GRANTED,
            onAction = {
                vm.openSystemSettings(PermissionManager.accessibilitySettingsIntent())
            },
            actionLabel = "Open settings",
            missingLabel = "REQUIRED",
        )
        SigPermissionCard(
            icon = NexoIcons.Clock,
            title = "Usage access fallback",
            description = "Polled only when accessibility detection is unavailable.",
            granted = health.usage == PermissionManager.State.GRANTED,
            onAction = { vm.openSystemSettings(PermissionManager.usageSettingsIntent()) },
            actionLabel = "Open settings",
            missingLabel = "OPTIONAL",
        )
    }

    if (health.restrictedSettingsRisk &&
        health.accessibility != PermissionManager.State.GRANTED
    ) {
        Spacer(Modifier.height(NexoSpace.md))
        SigBanner(
            text = "Android may hide Accessibility behind \"Allow restricted settings\". " +
                "Open App info, tap ⋮, choose Allow restricted settings, then retry.",
            tone = SigTone.WARNING,
            title = "Restricted settings",
        )
        Spacer(Modifier.height(NexoSpace.xs))
        SigGhostButton(
            text = "Open App info",
            onClick = { vm.openSystemSettings(PermissionManager.appDetailsIntent(context)) },
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Notifications")
    SigPermissionCard(
        icon = Icons.Filled.Info,
        title = "Notifications",
        description = "Ongoing protection status and degraded warnings.",
        granted = health.notifications == PermissionManager.State.GRANTED,
        onAction = {
            vm.openSystemSettings(PermissionManager.notificationSettingsIntent(context))
        },
        actionLabel = "Open settings",
        missingLabel = "RECOMMENDED",
    )

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Background operation")
    SigPermissionCard(
        icon = Icons.Filled.Refresh,
        title = "Battery optimisation",
        description = "Unrestricted battery use keeps the monitor alive in the background.",
        granted = !health.batteryOptimized,
        onAction = { vm.openSystemSettings(PermissionManager.batterySettingsIntent()) },
        grantedLabel = "UNRESTRICTED",
        missingLabel = "OPTIMISED",
        actionLabel = "Open settings",
    )

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Optional features")
    SigPermissionCard(
        icon = NexoIcons.Camera,
        title = "Camera · Intruder Vault",
        description = "Used only for capture records after failed unlock attempts.",
        granted = cameraGranted,
        onAction = {
            if (!cameraGranted) {
                PermissionRequests.request(
                    context,
                    Manifest.permission.CAMERA,
                    PermissionRequests.CAMERA,
                )
            } else {
                vm.openSystemSettings(PermissionManager.appDetailsIntent(context))
            }
        },
        actionLabel = if (cameraGranted) "Review permission" else "Grant camera",
        missingLabel = "NOT GRANTED",
    )
    SigFooterNote(
        "States are read from Android. Return to NEXO LOCK after changing a system setting; " +
            "the snapshot refreshes when the app resumes.",
    )
}

@Composable
private fun SecurityCategory(
    state: NexoUiState,
    onLogs: () -> Unit,
) {
    val health = state.permissionHealth
    val monitoring = DashboardStatuses.monitoringState(
        config = state.config,
        health = health,
        protectionRunning = state.protectionRunning,
    )
    val monitoringTone = when (monitoring) {
        MonitoringState.ENABLED -> SigTone.SUCCESS
        MonitoringState.PERMISSION_REQUIRED -> SigTone.DANGER
        MonitoringState.TEMPORARILY_UNAVAILABLE -> SigTone.WARNING
        MonitoringState.NOT_CONFIGURED,
        MonitoringState.UNSUPPORTED,
        -> SigTone.NEUTRAL
    }
    val detection = when {
        health.accessibility == PermissionManager.State.GRANTED ->
            "Accessibility (event-driven)"

        health.usage == PermissionManager.State.GRANTED ->
            "Usage access (polling fallback)"

        else -> "No detector active"
    }

    SigSectionHeader("Monitoring")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.ShieldCheck,
            title = "Protection monitor",
            subtitle = when (monitoring) {
                MonitoringState.ENABLED ->
                    "Configuration, permissions and service agree."

                MonitoringState.NOT_CONFIGURED ->
                    "Protection or a required factor is not configured."

                MonitoringState.PERMISSION_REQUIRED ->
                    "A required permission or the lock surface is missing."

                MonitoringState.UNSUPPORTED ->
                    "No supported foreground detector is available."

                MonitoringState.TEMPORARILY_UNAVAILABLE ->
                    "The monitor or detector is not currently responding."
            },
            trailing = {
                SigPill(monitoring.label.uppercase(), monitoringTone)
            },
        )
        SigDivider(Modifier.padding(start = 48.dp))
        SigKeyValueRow("Detection", detection)
        SigKeyValueRow(
            "Service",
            if (state.protectionRunning) "Running" else "Stopped",
        )
        SigKeyValueRow("Journal records", state.logs.entries.size.toString())
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigSectionHeader("Security journal")
    SigCard(padding = NexoSpace.md) {
        SigSettingRow(
            icon = NexoIcons.Clock,
            title = "Security journal",
            subtitle = "${state.logs.entries.size} entries · encrypted and chained",
            onClick = onLogs,
        )
    }

    if (state.logWriteError != null) {
        Spacer(Modifier.height(NexoSpace.md))
        SigBanner(
            text = "The last journal write failed: ${state.logWriteError}. Events after " +
                "this point may be missing.",
            tone = SigTone.DANGER,
            title = "Journal write error",
        )
    }
    if (state.logs.tamperedCount > 0 || state.logs.corruptedRecords > 0) {
        Spacer(Modifier.height(NexoSpace.md))
        SigBanner(
            text = "Integrity warning: ${state.logs.tamperedCount} record(s) failed the " +
                "hash chain and ${state.logs.corruptedRecords} could not be decrypted. " +
                "The file may have been altered or damaged.",
            tone = SigTone.DANGER,
            title = "Integrity check",
        )
    }

    Spacer(Modifier.height(NexoSpace.xl))
    SigBanner(
        text = "Android has no API to intercept another app's launch. Protection is " +
            "best effort with a short exposure window; the decoy appears as soon as " +
            "the switch is detected. If the lock surface cannot be drawn, a protected " +
            "app is sent home instead of opening.",
        tone = SigTone.INFO,
        title = "How protection works",
    )
    SigFooterNote("Permission details are managed from the Permissions category.")
}

@Composable
private fun BatteryCategory(
    state: NexoUiState,
    vm: NexoViewModel,
) {
    val health = state.permissionHealth
    SigSectionHeader("Background operation")
    SigPermissionCard(
        icon = Icons.Filled.Refresh,
        title = "Battery optimisation",
        description = "Unrestricted battery use keeps the monitor alive in the background.",
        granted = !health.batteryOptimized,
        onAction = { vm.openSystemSettings(PermissionManager.batterySettingsIntent()) },
        grantedLabel = "UNRESTRICTED",
        missingLabel = "OPTIMISED",
        actionLabel = "Open settings",
    )
    Spacer(Modifier.height(NexoSpace.md))
    SigBanner(
        text = "Android may still stop the foreground service under memory pressure or " +
            "an OEM battery manager. NEXO LOCK restarts after boot on a best-effort basis.",
        tone = SigTone.INFO,
        title = "Background limits",
    )
    SigFooterNote(
        "This page opens the existing Android battery-optimisation screen. It does not " +
            "change service scheduling.",
    )
}

@Composable
private fun SignatureSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = NexoPalette.OnSilver,
            checkedTrackColor = NexoPalette.Silver,
            uncheckedThumbColor = NexoPalette.TextMuted,
            uncheckedTrackColor = NexoPalette.SurfaceAlt,
            uncheckedBorderColor = NexoPalette.Border,
            disabledCheckedTrackColor = NexoPalette.Silver.copy(alpha = 0.3f),
            disabledUncheckedTrackColor = NexoPalette.SurfaceAlt.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun TimeoutSetting(
    label: String,
    seconds: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
            options.forEach { option ->
                SigChip(
                    text = "${option}s",
                    selected = seconds == option,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}
