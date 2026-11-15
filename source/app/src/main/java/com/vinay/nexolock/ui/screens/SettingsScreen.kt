package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.BuildConfig
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigFilledIconContainer
import com.vinay.nexolock.ui.components.SigFooterNote
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCategory: (SettingsCategory) -> Unit,
    onAbout: () -> Unit,
) {
    SignatureScreen(
        title = "Settings",
        subtitle = "Security, vaults, appearance and permissions.",
        eyebrow = "NEXO LOCK",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        SigCard(padding = NexoSpace.xs) {
            SettingsCategory.entries.forEachIndexed { index, category ->
                SettingsCategoryRow(
                    category = category,
                    onClick = {
                        if (category == SettingsCategory.ABOUT) {
                            onAbout()
                        } else {
                            onCategory(category)
                        }
                    },
                )
                if (index != SettingsCategory.entries.lastIndex) {
                    SigDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
        SigFooterNote(
            "Every category opens a dedicated page. Changes apply immediately and " +
                "persist across restarts.",
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexoSpace.sm, vertical = NexoSpace.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SigFilledIconContainer(
            icon = categoryIcon(category),
            size = 36.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                category.title,
                style = MaterialTheme.typography.bodyLarge,
                color = NexoPalette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (category == SettingsCategory.ABOUT) {
                    "Version ${BuildConfig.VERSION_NAME} · Security model and limits"
                } else {
                    category.summary
                },
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(NexoSpace.sm))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NexoPalette.TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun categoryIcon(category: SettingsCategory): ImageVector = when (category) {
    SettingsCategory.PROTECTION -> NexoIcons.ShieldCheck
    SettingsCategory.AUTHENTICATION -> NexoIcons.Key
    SettingsCategory.VAULTS -> NexoIcons.Vault
    SettingsCategory.NOTIFICATIONS -> NexoIcons.Clock
    SettingsCategory.APPEARANCE -> NexoIcons.Eye
    SettingsCategory.PERMISSIONS -> NexoIcons.Shield
    SettingsCategory.SECURITY -> Icons.AutoMirrored.Filled.List
    SettingsCategory.BATTERY -> NexoIcons.Sliders
    SettingsCategory.ABOUT -> Icons.Filled.Info
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    state: NexoUiState,
) {
    SignatureScreen(
        title = "About NEXO LOCK",
        subtitle = "Version ${BuildConfig.VERSION_NAME}",
        eyebrow = "SIGNATURE",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.md))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NexoPalette.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NexoIcons.ShieldCheck,
                    contentDescription = null,
                    tint = NexoPalette.Silver,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(NexoSpace.md))
            Text(
                "NEXO LOCK",
                style = MaterialTheme.typography.headlineSmall,
                color = NexoPalette.TextPrimary,
            )
            Text(
                "PRIVATE APP LOCK",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.sm))
            Text(
                "Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextSecondary,
            )
            Text(
                BuildConfig.APPLICATION_ID,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }

        Spacer(Modifier.height(NexoSpace.lg))
        SigBanner(
            text = "NEXO LOCK is an offline, local-only app lock. It has no network " +
                "permission, no account, no analytics and no cloud backup.",
            tone = SigTone.SUCCESS,
            title = "Offline by design",
        )

        Spacer(Modifier.height(NexoSpace.xl))
        SigSectionHeader("Core features")
        SigCard(padding = NexoSpace.md) {
            FeatureLine("Configurable 4–8 digit main PIN")
            FeatureLine(
                "Biometric → volume pattern → master PIN; every configured factor is mandatory",
            )
            FeatureLine(
                "Independent fixed stage windows: decoy 4s, pattern 15s, PIN 15s; expiry denies",
            )
            FeatureLine(
                "App-owned decoy with a truthful label and native entrance/exit animation",
            )
            FeatureLine("Personal Vault for encrypted notes and files")
            FeatureLine("Independent Intruder Vault with failed-unlock capture records")
            FeatureLine("Master Recovery PIN and optional recovery pattern")
            FeatureLine("Encrypted, hash-chained security journal")
        }

        Spacer(Modifier.height(NexoSpace.xl))
        SigSectionHeader("Security model")
        SigCard(padding = NexoSpace.md) {
            Text(
                "PINs and patterns are never stored. Only salted PBKDF2-HMAC-SHA256 verifiers " +
                    "are kept. The protected-app list, journal and both vaults use AES-256-GCM " +
                    "with non-exportable Android Keystore keys. Each vault has its own " +
                    "credential, Keystore alias, associated data and directory. The lock " +
                    "screen sets FLAG_SECURE. Repeated failures trigger per-credential " +
                    "progressive lockouts.",
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextSecondary,
            )
        }

        Spacer(Modifier.height(NexoSpace.xl))
        SigSectionHeader("Known limitations")
        SigCard(padding = NexoSpace.md) {
            FeatureLine(
                "Android provides no API to intercept another app's launch; detection is " +
                    "best effort and has a short exposure window.",
            )
            FeatureLine(
                "The protection decoy is drawn by NEXO LOCK itself and is visibly labelled " +
                    "as app-owned. It is not a system dialog and never fabricates an ANR or crash.",
            )
            FeatureLine(
                "Volume buttons are only captured while a NEXO LOCK screen is focused. " +
                    "Global key interception is intentionally not used.",
            )
            FeatureLine(
                "OEM battery managers may stop the foreground service; boot restart is best effort.",
            )
            FeatureLine(
                "Accessibility, display-over-other-apps and camera permissions must be " +
                    "granted by the owner.",
            )
            FeatureLine(
                "Data from NEXO LOCK V2 cannot be migrated because V2 credentials were " +
                    "Keystore-bound.",
            )
        }

        Spacer(Modifier.height(NexoSpace.lg))
        SigFooterNote(
            "Built with Kotlin, Jetpack Compose, Material 3, Navigation Compose and DataStore.",
        )
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NexoPalette.Silver),
        )
        Spacer(Modifier.width(NexoSpace.sm))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
        )
    }
}
