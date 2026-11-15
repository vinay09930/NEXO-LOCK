package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.InstalledApp
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.AppIcon
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigEmptyState
import com.vinay.nexolock.ui.components.SigLoadingState
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigSearchField
import com.vinay.nexolock.ui.components.SigSecondaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace

@Composable
fun AppSelectionScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(state.apps, query) {
        if (query.isBlank()) {
            state.apps
        } else {
            state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    SignatureScreen(
        title = "App selection",
        subtitle = "Choose the apps NEXO LOCK should guard.",
        eyebrow = "APP LOCK",
        onBack = onBack,
        scrollable = false,
        actions = {
            SigPill("${state.protectedPackages.size} SELECTED", SigTone.SILVER)
        },
    ) {
        SigSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search installed apps",
        )
        Spacer(Modifier.height(NexoSpace.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
        ) {
            SigChip(
                text = "Select all shown",
                selected = false,
                enabled = filtered.isNotEmpty(),
                onClick = { vm.setAllVisibleAppsProtected(filtered, true) },
            )
            SigChip(
                text = "Clear shown",
                selected = false,
                enabled = filtered.isNotEmpty(),
                onClick = { vm.setAllVisibleAppsProtected(filtered, false) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${filtered.size}",
                style = MaterialTheme.typography.labelMedium,
                color = NexoPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))

        if (state.appsLoading) {
            SigLoadingState("Reading installed apps…")
        } else if (filtered.isEmpty()) {
            SigEmptyState(
                icon = Icons.Filled.Search,
                title = "No apps found",
                message = "Try a different search term.",
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        checked = app.packageName in state.protectedPackages,
                        onToggle = {
                            vm.setAppProtected(
                                app.packageName,
                                app.packageName !in state.protectedPackages,
                            )
                        },
                    )
                    SigDivider()
                }
                item {
                    Text(
                        "Only apps with a launcher icon can be protected. System apps are included.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                        modifier = Modifier.padding(vertical = NexoSpace.md),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.small))
            .clickable(onClick = onToggle)
            .padding(vertical = NexoSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, label = app.label)
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = NexoPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (app.isSystem) {
            SigPill("SYSTEM", SigTone.NEUTRAL, dot = false)
            Spacer(Modifier.width(NexoSpace.xs))
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = NexoPalette.Silver,
                checkmarkColor = NexoPalette.OnSilver,
                uncheckedColor = NexoPalette.Border,
            ),
        )
    }
}

@Composable
fun ProtectedAppsScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onAddApps: () -> Unit,
) {
    val labelMap = remember(state.apps) { state.apps.associateBy { it.packageName } }
    val protected = remember(state.protectedPackages, labelMap) {
        state.protectedPackages
            .map { pkg -> pkg to (labelMap[pkg]?.label ?: pkg) }
            .sortedBy { it.second.lowercase() }
    }

    SignatureScreen(
        title = "App Lock",
        subtitle = "Apps that require NEXO LOCK authentication.",
        eyebrow = "PROTECTION",
        onBack = onBack,
        scrollable = false,
        actions = {
            IconAction(
                icon = Icons.Filled.Add,
                contentDescription = "Add apps",
                onClick = onAddApps,
            )
        },
    ) {
        if (protected.isEmpty()) {
            SigEmptyState(
                icon = NexoIcons.Shield,
                title = "No protected apps yet",
                message = "Select the apps you want to lock. They will open behind the " +
                    "NEXO LOCK authentication surface.",
                actionLabel = "Choose apps",
                onAction = onAddApps,
            )
        } else {
            SigBanner(
                text = "Removing an app takes effect immediately. The monitor reloads the " +
                    "encrypted package list automatically.",
                tone = SigTone.INFO,
            )
            Spacer(Modifier.height(NexoSpace.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SigPill(
                    "${protected.size} LOCKED",
                    SigTone.SILVER,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Tap the bin to remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
            }
            Spacer(Modifier.height(NexoSpace.xs))
            LazyColumn(Modifier.weight(1f)) {
                items(protected, key = { it.first }) { (pkg, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = NexoSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(packageName = pkg, label = label)
                        Spacer(Modifier.width(NexoSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = NexoPalette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                pkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoPalette.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconAction(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Remove $label from protection",
                            onClick = { vm.setAppProtected(pkg, false) },
                            tint = NexoPalette.TextMuted,
                            size = 38.dp,
                        )
                    }
                    SigDivider()
                }
                item {
                    Spacer(Modifier.height(NexoSpace.md))
                    SigSecondaryButton(
                        text = "Add more apps",
                        onClick = onAddApps,
                        icon = Icons.Filled.Add,
                    )
                    Spacer(Modifier.height(NexoSpace.lg))
                }
            }
        }
    }
}
