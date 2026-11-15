package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.data.LogEntry
import com.vinay.nexolock.data.LogSeverity
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.BrandHeader
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigFilledIconContainer
import com.vinay.nexolock.ui.components.SigMetric
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigTimelineRow
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureBackdrop
import com.vinay.nexolock.ui.components.color
import com.vinay.nexolock.ui.theme.LocalNexoBottomPadding
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onSettings: () -> Unit,
    onProtection: () -> Unit,
    onAuthentication: () -> Unit,
    onPermissions: () -> Unit,
    onGesture: () -> Unit,
    onLogs: () -> Unit,
    onProtectedApps: () -> Unit,
    onPersonalVault: () -> Unit,
    onIntruderVault: () -> Unit,
) {
    val config = state.config
    val health = state.permissionHealth
    val protecting = config.protectionEnabled && state.protectionRunning
    val healthy = protecting && health.fullyProtecting
    val monitorTone = when {
        healthy -> SigTone.SUCCESS
        config.protectionEnabled -> SigTone.WARNING
        else -> SigTone.NEUTRAL
    }
    val monitorPill = when {
        healthy -> "ACTIVE" to SigTone.SUCCESS
        config.protectionEnabled -> "DEGRADED" to SigTone.WARNING
        else -> "OFF" to SigTone.NEUTRAL
    }
    val stats = dashboardStats(state).take(3)
    val warning = dashboardWarning(
        state = state,
        onAuthentication = onAuthentication,
        onProtection = onProtection,
        onPermissions = onPermissions,
        onGesture = onGesture,
        onLogs = onLogs,
        onPersonalVault = onPersonalVault,
        onIntruderVault = onIntruderVault,
    )
    val recentEntries = state.logs.entries.take(2)
    val formatter = remember {
        SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
    }

    SignatureBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexoSpace.screenHorizontal)
                .padding(bottom = LocalNexoBottomPadding.current + NexoSpace.md),
        ) {
            Spacer(Modifier.height(NexoSpace.xs))
            BrandHeader(
                title = "NEXO LOCK",
                subtitle = "PROTECTION",
                actions = {
                    IconAction(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = onSettings,
                    )
                },
            )

            Spacer(Modifier.height(NexoSpace.sm))
            SigCard(
                onClick = onProtection,
                container = NexoPalette.SurfaceElevated,
                padding = NexoSpace.lg,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SigFilledIconContainer(
                        icon = if (healthy) NexoIcons.ShieldCheck else NexoIcons.Shield,
                        tone = monitorTone,
                        size = 44.dp,
                        iconSize = 22.dp,
                    )
                    Spacer(Modifier.width(NexoSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Protection",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexoPalette.TextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        SigPill(monitorPill.first, monitorPill.second)
                    }
                    Spacer(Modifier.width(NexoSpace.sm))
                    Switch(
                        checked = config.protectionEnabled,
                        enabled = config.hasMainPin,
                        onCheckedChange = vm::setProtectionEnabled,
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
                Spacer(Modifier.height(NexoSpace.sm))
                Text(
                    when {
                        !config.hasMainPin -> "Create a main PIN before protection can run."
                        protecting && health.fullyProtecting ->
                            "Selected apps open behind the NEXO authentication surface."

                        config.protectionEnabled ->
                            "Protection is on, but one or more requirements need attention."

                        else -> "Protected apps currently open without a lock."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(NexoSpace.lg))
                    SigDivider()
                    Spacer(Modifier.height(NexoSpace.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
                        stats.forEach { stat ->
                            SigMetric(
                                value = stat.value,
                                label = stat.label,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (warning != null) {
                Spacer(Modifier.height(NexoSpace.md))
                DashboardWarningCard(warning)
            }

            Spacer(Modifier.height(NexoSpace.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
                HomeShortcut(
                    icon = NexoIcons.ShieldCheck,
                    title = "App Lock",
                    onClick = onProtectedApps,
                    modifier = Modifier.weight(1f),
                )
                HomeShortcut(
                    icon = NexoIcons.Vault,
                    title = "Personal Vault",
                    onClick = onPersonalVault,
                    modifier = Modifier.weight(1f),
                )
                HomeShortcut(
                    icon = NexoIcons.Camera,
                    title = "Intruder Vault",
                    onClick = onIntruderVault,
                    modifier = Modifier.weight(1f),
                )
            }

            if (recentEntries.isNotEmpty()) {
                Spacer(Modifier.height(NexoSpace.xl))
                SigSectionHeader(
                    title = "Recent activity",
                    actionLabel = "View journal",
                    onAction = onLogs,
                )
                SigCard(padding = NexoSpace.md) {
                    recentEntries.forEachIndexed { index, entry ->
                        RecentActivityRow(
                            entry = entry,
                            time = formatter.format(Date(entry.timestamp)),
                            onClick = onLogs,
                        )
                        if (index != recentEntries.lastIndex) {
                            SigDivider()
                        }
                    }
                }
            }
        }
    }
}

private data class DashboardStat(
    val value: String,
    val label: String,
)

private data class DashboardWarning(
    val title: String,
    val message: String,
    val actionLabel: String,
    val tone: SigTone,
    val onClick: () -> Unit,
)

private fun dashboardStats(state: NexoUiState): List<DashboardStat> = buildList {
    add(DashboardStat(state.protectedPackages.size.toString(), "Protected apps"))
    if (state.config.hasPersonalVaultPin) {
        add(DashboardStat(state.vaults.personalCount.toString(), "Personal items"))
    }
    if (state.config.hasIntruderVaultPin) {
        add(DashboardStat(state.vaults.intruderCount.toString(), "Intruder records"))
    }
    if (state.logs.entries.isNotEmpty()) {
        add(DashboardStat(state.logs.entries.size.toString(), "Journal events"))
    }
}

private fun dashboardWarning(
    state: NexoUiState,
    onAuthentication: () -> Unit,
    onProtection: () -> Unit,
    onPermissions: () -> Unit,
    onGesture: () -> Unit,
    onLogs: () -> Unit,
    onPersonalVault: () -> Unit,
    onIntruderVault: () -> Unit,
): DashboardWarning? {
    val config = state.config
    val health = state.permissionHealth
    return when {
        state.logWriteError != null -> DashboardWarning(
            title = "Journal write failed",
            message = "Security events after this point may be missing.",
            actionLabel = "Open journal",
            tone = SigTone.DANGER,
            onClick = onLogs,
        )

        state.logs.tamperedCount > 0 || state.logs.corruptedRecords > 0 -> DashboardWarning(
            title = "Journal integrity check",
            message = "${state.logs.tamperedCount} record(s) failed the hash chain and " +
                "${state.logs.corruptedRecords} could not be decrypted.",
            actionLabel = "Review journal",
            tone = SigTone.DANGER,
            onClick = onLogs,
        )

        state.vaults.personalCorrupted -> DashboardWarning(
            title = "Personal Vault needs review",
            message = "The encrypted vault index failed authentication.",
            actionLabel = "Open Personal Vault",
            tone = SigTone.DANGER,
            onClick = onPersonalVault,
        )

        state.vaults.intruderCorrupted -> DashboardWarning(
            title = "Intruder Vault needs review",
            message = "The encrypted vault index failed authentication.",
            actionLabel = "Open Intruder Vault",
            tone = SigTone.DANGER,
            onClick = onIntruderVault,
        )

        !config.hasMainPin -> DashboardWarning(
            title = "Main PIN required",
            message = "Create a main PIN before protection can run.",
            actionLabel = "Open authentication",
            tone = SigTone.WARNING,
            onClick = onAuthentication,
        )

        config.protectionEnabled && !state.protectionRunning -> DashboardWarning(
            title = "Protection monitor is stopped",
            message = "Protection is enabled, but no monitor is currently running.",
            actionLabel = "Review protection",
            tone = SigTone.DANGER,
            onClick = onProtection,
        )

        config.protectionEnabled && !health.canDrawLock -> DashboardWarning(
            title = "Display over other apps is off",
            message = "Protected apps cannot be covered until overlay access is granted.",
            actionLabel = "Open permissions",
            tone = SigTone.DANGER,
            onClick = onPermissions,
        )

        config.protectionEnabled && !health.detectionAvailable -> DashboardWarning(
            title = "No app-switch detector",
            message = "Enable Accessibility or Usage access so protected launches can be detected.",
            actionLabel = "Open permissions",
            tone = SigTone.DANGER,
            onClick = onPermissions,
        )

        config.protectionEnabled && health.warnings.isNotEmpty() -> DashboardWarning(
            title = "Protection is degraded",
            message = health.warnings.first(),
            actionLabel = "Open permissions",
            tone = SigTone.WARNING,
            onClick = onPermissions,
        )

        !config.hasMainGesture -> DashboardWarning(
            title = "Volume pattern not configured",
            message = "The volume-pattern factor is required to complete authentication.",
            actionLabel = "Configure pattern",
            tone = SigTone.WARNING,
            onClick = onGesture,
        )

        else -> null
    }
}

@Composable
private fun DashboardWarningCard(warning: DashboardWarning) {
    val accent = warning.tone.color()
    SigCard(
        onClick = warning.onClick,
        container = accent.copy(alpha = 0.06f),
        borderColor = accent.copy(alpha = 0.32f),
        padding = NexoSpace.md,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(NexoRadius.small))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(NexoSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    warning.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    warning.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
                Spacer(Modifier.height(NexoSpace.xs))
                Text(
                    warning.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            Spacer(Modifier.width(NexoSpace.sm))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HomeShortcut(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(NexoRadius.card))
            .background(NexoPalette.Surface)
            .border(
                1.dp,
                NexoPalette.Border.copy(alpha = 0.7f),
                RoundedCornerShape(NexoRadius.card),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexoSpace.sm, vertical = NexoSpace.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SigFilledIconContainer(icon = icon, size = 34.dp, iconSize = 17.dp)
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = NexoPalette.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentActivityRow(
    entry: LogEntry,
    time: String,
    onClick: () -> Unit,
) {
    val tone = when (entry.type.severity) {
        LogSeverity.SUCCESS -> SigTone.SUCCESS
        LogSeverity.INFO -> SigTone.INFO
        LogSeverity.WARNING -> SigTone.WARNING
        LogSeverity.DANGER -> SigTone.DANGER
    }
    val icon = when (entry.type.severity) {
        LogSeverity.SUCCESS -> NexoIcons.ShieldCheck
        LogSeverity.INFO -> NexoIcons.Clock
        LogSeverity.WARNING -> Icons.Filled.Warning
        LogSeverity.DANGER -> NexoIcons.Shield
    }
    SigTimelineRow(
        icon = icon,
        title = entry.type.title,
        message = entry.message,
        meta = time,
        tone = tone,
        onClick = onClick,
    )
}
