package com.vinay.nexolock.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.data.LogEntry
import com.vinay.nexolock.data.LogSeverity
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.PillTone
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigKeyValueRow
import com.vinay.nexolock.ui.components.SigMetric
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigSecondaryButton
import com.vinay.nexolock.ui.components.SigSelectionToolbar
import com.vinay.nexolock.ui.components.SigTimelineRow
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.formatBytes
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.vault.VaultDeletionReport
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultExportResult
import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileCategory
import com.vinay.nexolock.vault.VaultFileStatus
import com.vinay.nexolock.vault.VaultKind
import com.vinay.nexolock.vault.category
import com.vinay.nexolock.vault.isImageMime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PersonalMetric(label: String, value: String, modifier: Modifier = Modifier) {
    SigMetric(value = value, label = label, modifier = modifier)
}

@Composable
internal fun PersonalProgressCard(
    title: String,
    name: String,
    stage: String?,
    bytes: Long,
    total: Long?,
    onCancel: () -> Unit,
) {
    SigCard(padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stage ?: "Working",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextSecondary,
                )
            }
            IconAction(
                icon = Icons.Filled.Clear,
                contentDescription = "Cancel operation",
                onClick = onCancel,
                size = 36.dp,
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))
        if (total != null && total > 0L) {
            LinearProgressIndicator(
                progress = { (bytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexoRadius.pill)),
                color = NexoPalette.Silver,
                trackColor = NexoPalette.SurfaceAlt,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexoRadius.pill)),
                color = NexoPalette.Silver,
                trackColor = NexoPalette.SurfaceAlt,
            )
        }
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            if (total != null && total > 0L) {
                "${formatBytes(bytes)} of ${formatBytes(total)}"
            } else {
                "${formatBytes(bytes)} processed · size unknown"
            },
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
    }
}

@Composable
internal fun PersonalExportReportCard(report: VaultExportResult, onDismiss: () -> Unit) {
    SigCard(padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigPill(
                if (report.cancelled) "EXPORT CANCELLED" else "EXPORT COMPLETE",
                if (report.cancelled) SigTone.WARNING else SigTone.SUCCESS,
            )
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = Icons.Filled.Clear,
                contentDescription = "Dismiss export report",
                onClick = onDismiss,
                size = 34.dp,
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            "${report.exported} of ${report.files.size} verified " +
                "${personalPlural(report.files.size, "copy", "copies")} restored · " +
                formatBytes(report.bytes),
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextPrimary,
        )
        report.files.take(8).forEach { result ->
            SigDivider(Modifier.padding(vertical = NexoSpace.xs))
            Text(
                result.name,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.failure ?: "Restored to ${result.destination ?: "the selected destination"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (result.failure == null) {
                    NexoPalette.TextSecondary
                } else {
                    NexoPalette.Error
                },
            )
        }
        if (report.files.size > 8) {
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                "+${report.files.size - 8} more",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
    }
}

@Composable
internal fun PersonalDeletionReportCard(report: VaultDeletionReport) {
    SigCard(padding = NexoSpace.md) {
        SigPill(
            if (report.needsConsent > 0) "ORIGINALS PENDING" else "ORIGINALS",
            if (report.failed > 0 || report.unsupported > 0) SigTone.WARNING else SigTone.NEUTRAL,
        )
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            report.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextPrimary,
        )
        report.notes.take(8).forEach { note ->
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextSecondary,
            )
        }
        if (report.notes.size > 8) {
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                "+${report.notes.size - 8} more",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
    }
}

@Composable
internal fun PersonalFileGridCard(
    file: VaultFile,
    vm: NexoViewModel,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SigCard(
        modifier = modifier,
        onClick = onOpen,
        padding = NexoSpace.sm,
    ) {
        Box {
            PersonalThumbnail(
                file = file,
                vm = vm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            )
            if (selecting) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexoPalette.Surface.copy(alpha = 0.82f)),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onOpen() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NexoPalette.Silver,
                            checkmarkColor = NexoPalette.OnSilver,
                            uncheckedColor = NexoPalette.TextSecondary,
                        ),
                    )
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexoPalette.Surface.copy(alpha = 0.72f)),
                ) {
                    PersonalFileActionsMenu(
                        file = file,
                        onOpen = onOpen,
                        onDetails = onDetails,
                        onExport = onExport,
                        onRename = onRename,
                        onVerify = onVerify,
                        onDelete = onDelete,
                    )
                }
            }
            if (selected && selecting) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(NexoRadius.small))
                        .background(NexoPalette.Silver.copy(alpha = 0.10f)),
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            file.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = NexoPalette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${formatBytes(file.sizeBytes)} · ${personalFormatDate(file.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        if (file.status != VaultFileStatus.COMPLETE) {
            Spacer(Modifier.height(NexoSpace.xs))
            SigPill(
                if (file.status == VaultFileStatus.IMPORTING) "INTERRUPTED" else "FAILED",
                SigTone.DANGER,
            )
        }
    }
}

@Composable
internal fun PersonalFileListCard(
    file: VaultFile,
    vm: NexoViewModel,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
) {
    SigCard(
        onClick = onOpen,
        padding = NexoSpace.md,
        borderColor = if (selected && selecting) {
            NexoPalette.Silver.copy(alpha = 0.55f)
        } else {
            NexoPalette.Border.copy(alpha = 0.72f)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonalThumbnail(
                file = file,
                vm = vm,
                modifier = Modifier.size(54.dp),
                compact = true,
            )
            Spacer(Modifier.width(NexoSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatBytes(file.sizeBytes)} · ${personalFormatDate(file.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
                if (file.status != VaultFileStatus.COMPLETE) {
                    Spacer(Modifier.height(NexoSpace.xs))
                    SigPill(
                        if (file.status == VaultFileStatus.IMPORTING) "INTERRUPTED" else "FAILED",
                        SigTone.DANGER,
                    )
                }
            }
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onOpen() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NexoPalette.Silver,
                        checkmarkColor = NexoPalette.OnSilver,
                        uncheckedColor = NexoPalette.Border,
                    ),
                )
            } else {
                PersonalFileActionsMenu(
                    file = file,
                    onOpen = onOpen,
                    onDetails = onDetails,
                    onExport = onExport,
                    onRename = onRename,
                    onVerify = onVerify,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PersonalFileActionsMenu(
    file: VaultFile,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconAction(
            icon = Icons.Filled.MoreVert,
            contentDescription = "Actions for ${file.displayName}",
            onClick = { expanded = true },
            size = 36.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = { expanded = false; onOpen() },
            )
            DropdownMenuItem(
                text = { Text("Details") },
                onClick = { expanded = false; onDetails() },
            )
            DropdownMenuItem(
                text = { Text("Export verified copy") },
                onClick = { expanded = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Verify integrity") },
                onClick = { expanded = false; onVerify() },
            )
            DropdownMenuItem(
                text = { Text("Delete from vault") },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
internal fun PersonalThumbnail(
    file: VaultFile,
    vm: NexoViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file.id, file.modifiedAt) {
        value = if (isImageMime(file.mimeType) && file.status == VaultFileStatus.COMPLETE) {
            vm.loadVaultThumbnail(VaultKind.PERSONAL, file.id)
        } else {
            null
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) NexoRadius.small else NexoRadius.button))
            .background(NexoPalette.SurfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = file.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                personalFileIcon(file),
                contentDescription = null,
                tint = NexoPalette.TextSecondary,
                modifier = Modifier.size(if (compact) 20.dp else 26.dp),
            )
        }
    }
}

@Composable
internal fun PersonalNoteCard(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    SigCard(onClick = onOpen, padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(NexoRadius.small))
                    .background(NexoPalette.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NexoIcons.Note,
                    contentDescription = null,
                    tint = NexoPalette.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(NexoSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    personalFormatDate(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs)) {
            SigGhostButton(text = "Rename", onClick = onRename)
            SigGhostButton(text = "Delete", onClick = onDelete, destructive = true)
        }
    }
}

@Composable
internal fun PersonalSelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    SigSelectionToolbar(
        count = count,
        onClear = onClear,
        actions = {
            IconAction(
                icon = Icons.Filled.Check,
                contentDescription = if (allSelected) "Clear all" else "Select all",
                onClick = onSelectAll,
                size = 34.dp,
            )
            IconAction(
                icon = NexoIcons.Export,
                contentDescription = "Export selected",
                onClick = onExport,
                enabled = count > 0,
                size = 34.dp,
            )
            IconAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete selected",
                onClick = onDelete,
                enabled = count > 0,
                tint = NexoPalette.Error,
                size = 34.dp,
            )
        },
    )
}

@Composable
internal fun PersonalCategoryCard(
    category: PersonalCategory,
    count: Int,
    bytes: Long,
    onClick: () -> Unit,
) {
    SigCard(onClick = onClick, padding = NexoSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(NexoRadius.small))
                    .background(NexoPalette.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    personalCategoryIcon(category),
                    contentDescription = null,
                    tint = NexoPalette.Silver,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(NexoSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NexoPalette.TextPrimary,
                )
                Text(
                    "$count ${personalPlural(count, "item")} · ${formatBytes(bytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NexoPalette.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun PersonalActivityRow(entry: LogEntry) {
    SigTimelineRow(
        icon = when (entry.type.severity) {
            LogSeverity.SUCCESS -> NexoIcons.ShieldCheck
            LogSeverity.INFO -> NexoIcons.Clock
            LogSeverity.WARNING -> NexoIcons.Shield
            LogSeverity.DANGER -> NexoIcons.Shield
        },
        title = entry.type.title,
        message = entry.message,
        meta = buildString {
            append(personalFormatDate(entry.timestamp))
            if (entry.method.isNotBlank()) append(" · ${entry.method}")
            if (entry.tampered) append(" · CHAIN MISMATCH")
        },
        tone = entry.type.severity.sigTone(),
        trailing = if (entry.tampered) {
            { SigPill("CHAIN MISMATCH", SigTone.DANGER) }
        } else {
            null
        },
    )
}

@Composable
internal fun PersonalDetailsSheetContent(
    file: VaultFile,
    vm: NexoViewModel,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "FILE DETAILS",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            file.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.md))
        PersonalThumbnail(
            file = file,
            vm = vm,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
        )
        Spacer(Modifier.height(NexoSpace.md))
        SigKeyValueRow("Type", file.mimeType)
        SigKeyValueRow("Category", file.category().label)
        SigKeyValueRow("Plaintext size", formatBytes(file.sizeBytes))
        SigKeyValueRow("Encrypted chunks", file.chunkCount.toString())
        SigKeyValueRow("Created", personalFormatDate(file.createdAt))
        SigKeyValueRow("Modified", personalFormatDate(file.modifiedAt))
        SigKeyValueRow(
            "SHA-256",
            file.integritySha256.take(20).ifBlank { "Not recorded" } +
                if (file.integritySha256.length > 20) "…" else "",
        )
        SigKeyValueRow("Status", file.status.name)
        Spacer(Modifier.height(NexoSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
            SigPrimaryButton(
                text = "Open",
                onClick = onOpen,
                enabled = file.status == VaultFileStatus.COMPLETE,
                modifier = Modifier.weight(1f),
            )
            SigSecondaryButton(
                text = "Export",
                onClick = onExport,
                enabled = file.status == VaultFileStatus.COMPLETE,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
            SigSecondaryButton(
                text = "Rename",
                onClick = onRename,
                modifier = Modifier.weight(1f),
            )
            SigSecondaryButton(
                text = "Verify",
                onClick = onVerify,
                modifier = Modifier.weight(1f),
            )
            SigSecondaryButton(
                text = "Delete",
                onClick = onDelete,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(NexoSpace.xs))
        SigGhostButton(
            text = "Close",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.md))
    }
}

internal fun personalFileBadge(file: VaultFile): String = when (file.category()) {
    VaultFileCategory.PHOTOS -> "IMG"
    VaultFileCategory.VIDEOS -> "VID"
    VaultFileCategory.AUDIO -> "AUD"
    VaultFileCategory.DOCUMENTS -> "DOC"
    else -> "FILE"
}

internal fun personalFileIcon(file: VaultFile) = when (file.category()) {
    VaultFileCategory.PHOTOS -> NexoIcons.Image
    VaultFileCategory.VIDEOS -> NexoIcons.Video
    VaultFileCategory.AUDIO -> NexoIcons.Audio
    VaultFileCategory.DOCUMENTS -> NexoIcons.Document
    else -> NexoIcons.Folder
}

internal fun personalCategoryIcon(category: PersonalCategory) = when (category) {
    PersonalCategory.ALL -> NexoIcons.Album
    PersonalCategory.PHOTOS -> NexoIcons.Image
    PersonalCategory.VIDEOS -> NexoIcons.Video
    PersonalCategory.DOCUMENTS -> NexoIcons.Document
    PersonalCategory.AUDIO -> NexoIcons.Audio
    PersonalCategory.OTHER -> NexoIcons.Folder
    PersonalCategory.NOTES -> NexoIcons.Note
}

internal fun personalFormatDate(millis: Long): String =
    if (millis <= 0L) {
        "Unknown date"
    } else {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }

internal fun personalPlural(count: Int, singular: String, plural: String = "${singular}s"): String =
    if (count == 1) singular else plural

internal fun LogSeverity.pillTone(): PillTone = when (this) {
    LogSeverity.INFO -> PillTone.NEUTRAL
    LogSeverity.SUCCESS -> PillTone.SUCCESS
    LogSeverity.WARNING -> PillTone.WARNING
    LogSeverity.DANGER -> PillTone.DANGER
}

internal fun LogSeverity.sigTone(): SigTone = when (this) {
    LogSeverity.INFO -> SigTone.INFO
    LogSeverity.SUCCESS -> SigTone.SUCCESS
    LogSeverity.WARNING -> SigTone.WARNING
    LogSeverity.DANGER -> SigTone.DANGER
}
