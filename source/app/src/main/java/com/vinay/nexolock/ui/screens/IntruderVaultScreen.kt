package com.vinay.nexolock.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigBottomSheet
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigDialog
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigEmptyState
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigKeyValueRow
import com.vinay.nexolock.ui.components.SigMetric
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigSearchField
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigSettingRow
import com.vinay.nexolock.ui.components.SigTextField
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.formatBytes
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.vault.CaptureStatus
import com.vinay.nexolock.vault.IntruderIncidents
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultEntryType
import com.vinay.nexolock.vault.VaultKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class IntruderFilter(val label: String) {
    ALL("All"),
    CAPTURED("Captured"),
    NO_CAPTURE("No capture"),
    NOTES("Notes"),
}

private enum class IntruderSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name A–Z"),
    LARGEST("Largest"),
}

private sealed interface IntruderVaultDialog {
    data class Details(val entry: VaultEntry) : IntruderVaultDialog
    data class Rename(val entry: VaultEntry) : IntruderVaultDialog
    data class Delete(val entry: VaultEntry) : IntruderVaultDialog
    data object ClearHistory : IntruderVaultDialog
    data object DeleteVault : IntruderVaultDialog
    data object Diagnostics : IntruderVaultDialog
}

private val INTRUDER_STAGE_OPTIONS = listOf(
    "ALL" to "All stages",
    "MAIN_PIN" to "Main PIN",
    "VOLUME_PATTERN" to "Volume pattern",
    "BIOMETRIC" to "Biometric",
    "STAGE_TIMEOUT" to "Stage timeout",
    "RECOVERY" to "Recovery",
    "AUTHENTICATION" to "Authentication",
)

/**
 * Intruder Vault dashboard: incident statistics, capture readiness, encrypted
 * incident history, secure notes and management.
 *
 * Every number and every row is derived from the unlocked encrypted index. A
 * failed capture is shown as a failed capture; no incident, image or count is
 * fabricated.
 */
@Composable
internal fun IntruderVaultHome(
    state: NexoUiState,
    vm: NexoViewModel,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = VaultKind.INTRUDER
    val vault = state.vault
    val capture = vault.capture

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(IntruderFilter.ALL.name) }
    var stageFilter by rememberSaveable { mutableStateOf("ALL") }
    var sortName by rememberSaveable { mutableStateOf(IntruderSort.NEWEST.name) }
    var dialog by remember { mutableStateOf<IntruderVaultDialog?>(null) }
    var showPinChange by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    val filter = IntruderFilter.entries.firstOrNull { it.name == filterName }
        ?: IntruderFilter.ALL
    val sort = IntruderSort.entries.firstOrNull { it.name == sortName } ?: IntruderSort.NEWEST
    val stats = remember(vault.entries) { IntruderIncidents.stats(vault.entries) }
    val incidents = remember(vault.entries) {
        vault.entries.filter { it.type == VaultEntryType.CAPTURE }
    }
    val notes = remember(vault.entries) {
        vault.entries.filter { it.type == VaultEntryType.NOTE }
    }

    val filteredIncidents = remember(incidents, query, filter, stageFilter, sort) {
        if (filter == IntruderFilter.NOTES) {
            emptyList()
        } else {
            incidents
                .filter { entry ->
                    val captureMatches = when (filter) {
                        IntruderFilter.CAPTURED ->
                            entry.captureStatus == CaptureStatus.CAPTURED &&
                                entry.attachmentId != null

                        IntruderFilter.NO_CAPTURE ->
                            entry.captureStatus != CaptureStatus.CAPTURED ||
                                entry.attachmentId == null

                        else -> true
                    }
                    val stageMatches = stageFilter == "ALL" ||
                        IntruderIncidents.displayStage(entry) == stageFilter
                    captureMatches && stageMatches && entry.intruderMatches(query)
                }
                .sortedWith(intruderEntryComparator(sort))
        }
    }
    val filteredNotes = remember(notes, query, filter, sort) {
        if (filter != IntruderFilter.ALL && filter != IntruderFilter.NOTES) {
            emptyList()
        } else {
            notes.filter { it.intruderMatches(query) }.sortedWith(intruderEntryComparator(sort))
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NexoSpace.md),
        ) {
            // -------------------------------------------------------------- hero
            SigCard(
                container = NexoPalette.SurfaceElevated,
                padding = NexoSpace.xl,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SigPill("UNLOCKED", SigTone.SUCCESS)
                        Spacer(Modifier.height(NexoSpace.sm))
                        Text(
                            "Incident evidence",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NexoPalette.TextPrimary,
                        )
                        Text(
                            "A separate encrypted repository for failed unlocks and captures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexoPalette.TextSecondary,
                        )
                    }
                    Box {
                        IconAction(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = "Vault actions",
                            onClick = { showOverflow = true },
                        )
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Lock vault") },
                                onClick = {
                                    showOverflow = false
                                    vm.lockVault()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Change vault PIN") },
                                onClick = {
                                    showOverflow = false
                                    showPinChange = !showPinChange
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear incident history") },
                                onClick = {
                                    showOverflow = false
                                    dialog = IntruderVaultDialog.ClearHistory
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import diagnostics") },
                                onClick = {
                                    showOverflow = false
                                    dialog = IntruderVaultDialog.Diagnostics
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete vault") },
                                onClick = {
                                    showOverflow = false
                                    dialog = IntruderVaultDialog.DeleteVault
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(NexoSpace.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
                    SigMetric(
                        value = stats.incidentCount.toString(),
                        label = "Incidents",
                        modifier = Modifier.weight(1f),
                    )
                    SigMetric(
                        value = stats.capturedCount.toString(),
                        label = "Captured",
                        modifier = Modifier.weight(1f),
                    )
                    SigMetric(
                        value = stats.failedAuthCount.toString(),
                        label = "Failed auths",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(NexoSpace.md))
                Text(
                    "Latest: ${intruderTimestamp(stats.latestIncidentAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
                Spacer(Modifier.height(NexoSpace.md))
                SigGhostButton(
                    text = "Lock ${kind.title}",
                    onClick = vm::lockVault,
                    enabled = !vault.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            vault.message?.let {
                Spacer(Modifier.height(NexoSpace.sm))
                SigBanner(text = it, tone = SigTone.SUCCESS)
            }
            vault.error?.let {
                Spacer(Modifier.height(NexoSpace.sm))
                SigBanner(text = it, tone = SigTone.DANGER)
            }

            // -------------------------------------------------------------- capture readiness
            Spacer(Modifier.height(NexoSpace.md))
            val captureMessage: String
            val captureTone: SigTone
            when {
                capture.inProgress -> {
                    captureMessage = "Capture in progress: waiting for the front camera…"
                    captureTone = SigTone.INFO
                }

                !capture.hasVaultPin -> {
                    captureMessage = "Intruder capture needs the Intruder Vault PIN."
                    captureTone = SigTone.WARNING
                }

                !capture.enabled -> {
                    captureMessage = "Intruder capture is OFF. Failed unlocks are still " +
                        "recorded as incidents without a photo."
                    captureTone = SigTone.WARNING
                }

                !capture.cameraPermission -> {
                    captureMessage = "Capture is ON, but camera permission is missing, so no " +
                        "photo can be taken."
                    captureTone = SigTone.DANGER
                }

                !capture.cameraAvailable -> {
                    captureMessage = "Capture is ON, but no usable front camera was detected " +
                        "on this device."
                    captureTone = SigTone.DANGER
                }

                capture.lastStatus == CaptureStatus.CAPTURED -> {
                    captureMessage = "Last capture succeeded and is stored encrypted."
                    captureTone = SigTone.SUCCESS
                }

                capture.lastStatus != null -> {
                    captureMessage = "Last capture failed " +
                        "(${capture.lastStatus.name.lowercase()}): " +
                        (capture.lastMessage ?: "no detail")
                    captureTone = SigTone.WARNING
                }

                else -> {
                    captureMessage = "Capture is ready. A front-camera photo is requested " +
                        "after a failed unlock; every outcome is recorded, including failures."
                    captureTone = SigTone.INFO
                }
            }
            SigBanner(text = captureMessage, tone = captureTone, title = "Capture readiness")
            if (capture.attemptCount > 0) {
                Spacer(Modifier.height(NexoSpace.xs))
                Text(
                    "${capture.attemptCount} capture " +
                        (if (capture.attemptCount == 1) "attempt" else "attempts") +
                        " this session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
            }

            // -------------------------------------------------------------- storage
            Spacer(Modifier.height(NexoSpace.lg))
            SigSectionHeader("Storage")
            SigCard(padding = NexoSpace.md) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
                    SigMetric(
                        value = formatBytes(vault.storage?.encryptedBytes ?: 0L),
                        label = "Encrypted",
                        modifier = Modifier.weight(1f),
                    )
                    SigMetric(
                        value = formatBytes(vault.storage?.attachmentBytes ?: 0L),
                        label = "Captures",
                        modifier = Modifier.weight(1f),
                    )
                    SigMetric(
                        value = vault.availableBytes?.let(::formatBytes) ?: "Unknown",
                        label = "Available",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(NexoSpace.sm))
                Text(
                    "${stats.incidentCount} incidents · ${stats.capturedCount} with encrypted " +
                        "capture · ${stats.captureUnavailableCount} without a capture · " +
                        "${stats.noteCount} secure notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
            }

            // -------------------------------------------------------------- search / filters
            Spacer(Modifier.height(NexoSpace.lg))
            SigSearchField(
                value = query,
                onValueChange = { query = it.take(120) },
                placeholder = "Search target, reason, stage or note",
            )
            Spacer(Modifier.height(NexoSpace.sm))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
            ) {
                IntruderFilter.entries.forEach { candidate ->
                    SigChip(
                        text = candidate.label,
                        selected = filter == candidate,
                        onClick = { filterName = candidate.name },
                    )
                }
            }
            Spacer(Modifier.height(NexoSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IntruderStageMenu(
                    current = stageFilter,
                    onSelect = { stageFilter = it },
                )
                Spacer(Modifier.weight(1f))
                IntruderSortMenu(sort = sort, onSelect = { sortName = it.name })
            }

            // -------------------------------------------------------------- incident history
            if (filter != IntruderFilter.NOTES) {
                Spacer(Modifier.height(NexoSpace.lg))
                SigSectionHeader("Incident history · ${filteredIncidents.size}")
                if (filteredIncidents.isEmpty()) {
                    SigEmptyState(
                        icon = Icons.Filled.Search,
                        title = if (incidents.isEmpty()) {
                            "No incidents recorded"
                        } else {
                            "No matching incidents"
                        },
                        message = if (incidents.isEmpty()) {
                            "Failed unlocks appear here after the protection service records " +
                                "them. No event or image is fabricated."
                        } else {
                            "Try another search, capture filter or stage."
                        },
                    )
                } else {
                    filteredIncidents.forEach { entry ->
                        IntruderIncidentCard(
                            entry = entry,
                            kind = kind,
                            vm = vm,
                            onOpen = {
                                if (entry.attachmentId != null) {
                                    onOpenEntry(kind, entry.id)
                                }
                            },
                            onDetails = { dialog = IntruderVaultDialog.Details(entry) },
                            onRename = { dialog = IntruderVaultDialog.Rename(entry) },
                            onDelete = { dialog = IntruderVaultDialog.Delete(entry) },
                        )
                        Spacer(Modifier.height(NexoSpace.sm))
                    }
                }
            }

            // -------------------------------------------------------------- secure notes
            if (filter == IntruderFilter.ALL || filter == IntruderFilter.NOTES) {
                Spacer(Modifier.height(NexoSpace.lg))
                SigSectionHeader("Secure notes · ${filteredNotes.size}")
                if (filteredNotes.isEmpty()) {
                    SigEmptyState(
                        icon = Icons.Filled.Edit,
                        title = if (notes.isEmpty()) "No secure notes" else "No matching notes",
                        message = if (notes.isEmpty()) {
                            "Notes stored here share the Intruder Vault key and are preserved " +
                                "when incident history is cleared."
                        } else {
                            "Try another search."
                        },
                    )
                } else {
                    filteredNotes.forEach { entry ->
                        IntruderNoteCard(
                            entry = entry,
                            onOpen = { onOpenEntry(kind, entry.id) },
                            onRename = { dialog = IntruderVaultDialog.Rename(entry) },
                            onDelete = { dialog = IntruderVaultDialog.Delete(entry) },
                        )
                        Spacer(Modifier.height(NexoSpace.sm))
                    }
                }
            }

            // -------------------------------------------------------------- management
            Spacer(Modifier.height(NexoSpace.xl))
            SigSectionHeader("Vault management")
            SigCard(padding = NexoSpace.md) {
                SigSettingRow(
                    icon = Icons.Filled.Lock,
                    title = "Change ${kind.title} PIN",
                    subtitle = "Requires the current vault PIN",
                    onClick = { showPinChange = !showPinChange },
                )
                if (showPinChange) {
                    VaultPinChangeForm(kind, vm)
                }
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear incident history",
                    subtitle = "Deletes every incident and encrypted capture; keeps secure notes",
                    onClick = { dialog = IntruderVaultDialog.ClearHistory },
                )
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Info,
                    title = "Import diagnostics",
                    subtitle = "${vault.diagnostics.size} safe attempts recorded in memory",
                    onClick = { dialog = IntruderVaultDialog.Diagnostics },
                )
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Delete,
                    title = "Delete ${kind.title}",
                    subtitle = "Removes the PIN, index, incidents and every encrypted attachment",
                    onClick = { dialog = IntruderVaultDialog.DeleteVault },
                    destructive = true,
                )
            }
            Spacer(Modifier.height(NexoSpace.sm))
            Text(
                "Incident records and captures are encrypted with AES-256-GCM under the " +
                    "Intruder Vault key. Clearing history keeps secure notes. Screenshots " +
                    "and recents previews are blocked with FLAG_SECURE.",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.lg))
        }
    }

    // ------------------------------------------------------------------ dialogs
    when (val current = dialog) {
        is IntruderVaultDialog.Details -> SigBottomSheet(onDismiss = { dialog = null }) {
            IntruderDetailsContent(
                entry = current.entry,
                onOpen = {
                    if (current.entry.attachmentId != null) {
                        onOpenEntry(kind, current.entry.id)
                    }
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        is IntruderVaultDialog.Rename -> IntruderRenameDialog(
            title = if (current.entry.type == VaultEntryType.CAPTURE) {
                "Rename capture record"
            } else {
                "Rename note"
            },
            initial = current.entry.title,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.renameVaultEntry(current.entry.id, name) { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
        )

        is IntruderVaultDialog.Delete -> SigDialog(
            title = if (current.entry.type == VaultEntryType.CAPTURE) {
                "Delete incident?"
            } else {
                "Delete secure note?"
            },
            message = "\"${current.entry.title}\" and its encrypted attachment will be " +
                "permanently removed from ${kind.title}.",
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                dialog = null
                vm.deleteVaultEntry(current.entry.id)
            },
            onDismiss = { dialog = null },
        )

        IntruderVaultDialog.ClearHistory -> SigDialog(
            title = "Clear incident history?",
            message = "Every incident record and its encrypted capture will be deleted. " +
                "Secure notes are preserved. This cannot be undone.",
            confirmLabel = "Clear history",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                dialog = null
                vm.clearIntruderHistory { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
            onDismiss = { dialog = null },
        )

        IntruderVaultDialog.DeleteVault -> SigDialog(
            title = "Delete ${kind.title}?",
            message = "This deletes the vault PIN, the encrypted index, every incident and " +
                "all attachments. It cannot be undone.",
            confirmLabel = "Delete permanently",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                dialog = null
                vm.deleteVault { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
            onDismiss = { dialog = null },
        )

        IntruderVaultDialog.Diagnostics -> ImportDiagnosticsDialog(
            diagnostics = vault.diagnostics,
            onClear = vm::clearImportDiagnostics,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }

}

// ---------------------------------------------------------------------------- pieces

@Composable
private fun IntruderIncidentCard(
    entry: VaultEntry,
    kind: VaultKind,
    vm: NexoViewModel,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val stage = IntruderIncidents.displayStage(entry)
    val target = IntruderIncidents.displayPackage(entry)
    val status = entry.captureStatus
    SigCard(padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigPill(
                IntruderIncidents.failureStageLabel(stage).uppercase(),
                SigTone.WARNING,
            )
            Spacer(Modifier.width(NexoSpace.xs))
            SigPill(
                IntruderIncidents.captureStatusLabel(status),
                intruderCaptureTone(status),
            )
            Spacer(Modifier.weight(1f))
            Text(
                intruderTimestamp(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.attachmentId != null) {
                IntruderThumbnail(kind = kind, entry = entry, vm = vm)
                Spacer(Modifier.width(NexoSpace.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "Protected app" },
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (target != null) {
                    Text(
                        target,
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    IntruderIncidents.reasonText(entry.body).ifBlank { "No reason recorded." },
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "Capture: ${IntruderIncidents.captureStatusLabel(status)}" +
                (entry.attachmentId?.let { " · ref ${it.take(8)}…" } ?: " · no encrypted image"),
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigGhostButton(text = "Details", onClick = onDetails)
            if (entry.attachmentId != null) {
                SigGhostButton(text = "Open capture", onClick = onOpen)
            }
            Spacer(Modifier.weight(1f))
            SigGhostButton(text = "Rename", onClick = onRename)
            SigGhostButton(text = "Delete", onClick = onDelete, destructive = true)
        }
    }
}

@Composable
private fun IntruderNoteCard(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    SigCard(padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigPill("NOTE", SigTone.SILVER)
            Spacer(Modifier.weight(1f))
            Text(
                intruderTimestamp(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            entry.title,
            style = MaterialTheme.typography.titleSmall,
            color = NexoPalette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.body.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                entry.body,
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(NexoSpace.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigGhostButton(text = "Open", onClick = onOpen)
            SigGhostButton(text = "Rename", onClick = onRename)
            Spacer(Modifier.weight(1f))
            SigGhostButton(text = "Delete", onClick = onDelete, destructive = true)
        }
    }
}

@Composable
private fun IntruderThumbnail(kind: VaultKind, entry: VaultEntry, vm: NexoViewModel) {
    val attachment = entry.attachmentId
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.id, attachment) {
        value = if (attachment != null) {
            vm.loadVaultAttachmentThumbnail(kind, attachment)
        } else {
            null
        }
    }
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(NexoRadius.small))
            .background(NexoPalette.SurfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Encrypted capture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = NexoPalette.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun IntruderDetailsContent(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stage = IntruderIncidents.displayStage(entry)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "INCIDENT DETAILS",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            entry.title.ifBlank { "Protected app" },
            style = MaterialTheme.typography.titleLarge,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.md))
        SigKeyValueRow("Timestamp", intruderTimestamp(entry.createdAt))
        SigKeyValueRow("Target app", entry.title.ifBlank { "Protected app" })
        SigKeyValueRow(
            "Package",
            IntruderIncidents.displayPackage(entry) ?: "Not recorded",
        )
        SigKeyValueRow("Failure stage", IntruderIncidents.failureStageLabel(stage))
        SigKeyValueRow(
            "Reason",
            IntruderIncidents.reasonText(entry.body).ifBlank { "Not recorded" },
        )
        SigKeyValueRow(
            "Capture status",
            IntruderIncidents.captureStatusLabel(entry.captureStatus),
        )
        SigKeyValueRow(
            "Capture reference",
            entry.attachmentId ?: "No encrypted capture",
        )
        SigKeyValueRow("Record ID", entry.id)
        Spacer(Modifier.height(NexoSpace.md))
        if (entry.attachmentId != null) {
            SigGhostButton(
                text = "Open capture",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NexoSpace.xs))
        }
        SigGhostButton(
            text = "Close",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.md))
    }
}

@Composable
private fun IntruderStageMenu(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (current == "ALL") {
        "Stage: All"
    } else {
        "Stage: ${IntruderIncidents.failureStageLabel(current)}"
    }
    Box {
        SigChip(
            text = label,
            selected = current != "ALL",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            INTRUDER_STAGE_OPTIONS.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun IntruderSortMenu(sort: IntruderSort, onSelect: (IntruderSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SigChip(
            text = "Sort: ${sort.label}",
            selected = false,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IntruderSort.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.label) },
                    onClick = {
                        expanded = false
                        onSelect(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun IntruderRenameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val clean = value.trim()
    val valid = clean.isNotEmpty() && clean.length <= 120 &&
        clean.none { it == '/' || it == '\\' || it.isISOControl() }
    SigDialog(
        title = title,
        message = "Renaming changes only the authenticated metadata in the encrypted " +
            "index. The encrypted content is not re-written.",
        confirmLabel = "Rename",
        confirmEnabled = valid,
        onConfirm = { if (valid) onConfirm(clean) },
        onDismiss = onDismiss,
    ) {
        SigTextField(
            value = value,
            onValueChange = { value = it.take(120) },
            label = "Name",
            singleLine = true,
            isError = value.isNotEmpty() && !valid,
        )
        if (value.isNotEmpty() && !valid) {
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                "Use a name without slashes or control characters (maximum 120).",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.Error,
            )
        }
    }
}

// ---------------------------------------------------------------------------- helpers

private fun VaultEntry.intruderMatches(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    val stage = IntruderIncidents.displayStage(this)
    val target = IntruderIncidents.displayPackage(this)
    return title.contains(needle, ignoreCase = true) ||
        body.contains(needle, ignoreCase = true) ||
        IntruderIncidents.failureStageLabel(stage).contains(needle, ignoreCase = true) ||
        target?.contains(needle, ignoreCase = true) == true
}

private fun intruderEntryComparator(sort: IntruderSort): Comparator<VaultEntry> = when (sort) {
    IntruderSort.NEWEST -> compareByDescending { it.createdAt }
    IntruderSort.OLDEST -> compareBy { it.createdAt }
    IntruderSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    IntruderSort.LARGEST -> compareByDescending { it.body.length }
}

private fun intruderCaptureTone(status: CaptureStatus?): SigTone = when (status) {
    CaptureStatus.CAPTURED -> SigTone.SUCCESS
    CaptureStatus.PERMISSION_MISSING,
    CaptureStatus.CAMERA_UNAVAILABLE,
    CaptureStatus.CAMERA_BUSY,
    CaptureStatus.FAILED,
    -> SigTone.DANGER

    CaptureStatus.DISABLED, null -> SigTone.NEUTRAL
}

private fun intruderTimestamp(millis: Long): String =
    if (millis <= 0L) {
        "No incident recorded"
    } else {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))
    }
