package com.vinay.nexolock.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.data.VaultLayout
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.VaultFilePicker
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
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigSearchField
import com.vinay.nexolock.ui.components.SigSecondaryButton
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigSettingRow
import com.vinay.nexolock.ui.components.SigTextField
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.formatBytes
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.vault.VaultActivity
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultEntryType
import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileCategory
import com.vinay.nexolock.vault.VaultKind
import com.vinay.nexolock.vault.category

/** Tabs of the Personal Vault workspace. */
internal enum class PersonalTab(val label: String) {
    FILES("Files"),
    CATEGORIES("Albums"),
    ACTIVITY("Activity"),
}

/**
 * User-facing category filter. [VaultFileCategory] stays a pure storage
 * classification; Notes is a UI-only destination for encrypted text entries.
 */
internal enum class PersonalCategory(
    val label: String,
    val fileCategory: VaultFileCategory?,
) {
    ALL("All", null),
    PHOTOS("Photos", VaultFileCategory.PHOTOS),
    VIDEOS("Videos", VaultFileCategory.VIDEOS),
    DOCUMENTS("Documents", VaultFileCategory.DOCUMENTS),
    AUDIO("Audio", VaultFileCategory.AUDIO),
    OTHER("Other", VaultFileCategory.OTHER),
    NOTES("Notes", null),
    ;

    val notesOnly: Boolean get() = this == NOTES

    fun matches(file: VaultFile): Boolean = when (this) {
        ALL -> true
        NOTES -> false
        else -> file.category() == fileCategory
    }
}

internal enum class PersonalSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name"),
    TYPE("Type"),
    LARGEST("Largest"),
    SMALLEST("Smallest"),
}

private sealed interface PersonalVaultDialog {
    data class RenameFile(val file: VaultFile) : PersonalVaultDialog
    data class DeleteFile(val file: VaultFile) : PersonalVaultDialog
    data class DeleteSelected(val ids: List<String>) : PersonalVaultDialog
    data class RenameEntry(val entry: VaultEntry) : PersonalVaultDialog
    data class DeleteEntry(val entry: VaultEntry) : PersonalVaultDialog
    data object DeleteVault : PersonalVaultDialog
    data object Diagnostics : PersonalVaultDialog
}

/**
 * Personal Vault dashboard, file manager, albums and activity.
 *
 * Every count, byte figure and status shown here comes from the unlocked vault
 * state; there are no placeholder rows and no button that pretends to work.
 */
@Composable
internal fun PersonalVaultHome(
    state: NexoUiState,
    vm: NexoViewModel,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = VaultKind.PERSONAL
    val vault = state.vault
    val context = LocalContext.current
    val layout = state.config.vaultLayout

    var tabName by rememberSaveable { mutableStateOf(PersonalTab.FILES.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(PersonalCategory.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(PersonalSort.NEWEST.name) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dialog by remember { mutableStateOf<PersonalVaultDialog?>(null) }
    var detailsFile by remember { mutableStateOf<VaultFile?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPinChange by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showAutoDeleteDialog by remember { mutableStateOf(false) }

    val tab = PersonalTab.entries.firstOrNull { it.name == tabName } ?: PersonalTab.FILES
    val category = PersonalCategory.entries.firstOrNull { it.name == categoryName }
        ?: PersonalCategory.ALL
    val sort = PersonalSort.entries.firstOrNull { it.name == sortName } ?: PersonalSort.NEWEST
    val notes = remember(vault.entries) {
        vault.entries.filter { it.type == VaultEntryType.NOTE }
    }
    val files = remember(vault.files, query, category, sort) {
        vault.files
            .filter { file -> category.matches(file) && file.personalMatches(query) }
            .sortedWith(personalFileComparator(sort))
    }
    val filteredNotes = remember(notes, query, category, sort) {
        if (category != PersonalCategory.ALL && category != PersonalCategory.NOTES) {
            emptyList()
        } else {
            notes
                .filter { it.personalMatches(query) }
                .sortedWith(personalEntryComparator(sort))
        }
    }
    val busy = vault.busy || vault.exporting

    val toggleSelection: (String) -> Unit = { id ->
        selection = if (id in selection) selection - id else selection + id
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NexoSpace.md),
        ) {
            // ------------------------------------------------------------ hero
            SigCard(
                container = NexoPalette.SurfaceElevated,
                padding = NexoSpace.xl,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SigPill("UNLOCKED", SigTone.SUCCESS)
                        Spacer(Modifier.height(NexoSpace.sm))
                        Text(
                            "Personal Vault",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NexoPalette.TextPrimary,
                        )
                        Text(
                            "${vault.files.size} ${personalPlural(vault.files.size, "file")} · " +
                                "${notes.size} ${personalPlural(notes.size, "note")} · AES-256-GCM",
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
                                text = { Text("Import diagnostics") },
                                onClick = {
                                    showOverflow = false
                                    dialog = PersonalVaultDialog.Diagnostics
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete vault") },
                                onClick = {
                                    showOverflow = false
                                    dialog = PersonalVaultDialog.DeleteVault
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(NexoSpace.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
                    PersonalMetric(
                        label = "Encrypted",
                        value = formatBytes(vault.storage?.encryptedBytes ?: 0L),
                        modifier = Modifier.weight(1f),
                    )
                    PersonalMetric(
                        label = "File data",
                        value = formatBytes(vault.storage?.logicalBytes ?: 0L),
                        modifier = Modifier.weight(1f),
                    )
                    PersonalMetric(
                        label = "Free space",
                        value = vault.availableBytes?.let { formatBytes(it) } ?: "Unknown",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(NexoSpace.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
                    SigPrimaryButton(
                        text = if (vault.busy) "Importing…" else "Import files",
                        onClick = {
                            val launched = VaultFilePicker.launch(
                                context = context,
                                allowWrite = state.config.deleteOriginalsAfterImport,
                            )
                            if (!launched) {
                                vm.notify(
                                    "The system file picker could not be opened.",
                                    error = true,
                                )
                            }
                        },
                        enabled = !busy,
                        loading = vault.busy,
                        modifier = Modifier.weight(1f),
                    )
                    SigSecondaryButton(
                        text = "New note",
                        onClick = { showNoteDialog = true },
                        enabled = !busy,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(NexoSpace.xs))
                Text(
                    "Imports are streamed into 64 KiB AES-256-GCM chunks with a unique nonce " +
                        "per chunk. There is no arbitrary file-size limit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
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

            // ------------------------------------------------------------ live operations
            if (vault.importStage != null || vault.importingName != null) {
                Spacer(Modifier.height(NexoSpace.md))
                PersonalProgressCard(
                    title = "ENCRYPTING IMPORT",
                    name = vault.importingName ?: "Preparing import",
                    stage = buildString {
                        if (vault.importCount > 0) {
                            append(
                                "File ${vault.importIndex.coerceAtLeast(1)} of " +
                                    "${vault.importCount} · ",
                            )
                        }
                        append(vault.importStage ?: "Working")
                    },
                    bytes = vault.importedBytes,
                    total = vault.importTotalBytes,
                    onCancel = vm::cancelVaultImport,
                )
            }
            if (vault.exporting) {
                Spacer(Modifier.height(NexoSpace.md))
                PersonalProgressCard(
                    title = "VERIFIED EXPORT",
                    name = vault.exportingName ?: "Preparing export",
                    stage = if (vault.exportCount > 0) {
                        "File ${vault.exportIndex.coerceAtLeast(1)} of ${vault.exportCount} · " +
                            "decrypting and verifying"
                    } else {
                        "Decrypting and verifying"
                    },
                    bytes = vault.exportedBytes,
                    total = vault.exportTotalBytes.takeIf { it > 0L },
                    onCancel = vm::cancelVaultExport,
                )
            }
            vault.exportError?.let {
                Spacer(Modifier.height(NexoSpace.sm))
                SigBanner(text = it, tone = SigTone.DANGER)
            }
            vault.exportReport?.let { report ->
                Spacer(Modifier.height(NexoSpace.sm))
                PersonalExportReportCard(report = report, onDismiss = vm::clearExportReport)
            }
            vault.deletionReport?.let { report ->
                Spacer(Modifier.height(NexoSpace.sm))
                PersonalDeletionReportCard(report)
            }
            if (vault.importFailures.isNotEmpty()) {
                Spacer(Modifier.height(NexoSpace.sm))
                SigCard(padding = NexoSpace.md) {
                    Text(
                        "IMPORT ISSUES",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                    )
                    Spacer(Modifier.height(NexoSpace.xs))
                    vault.importFailures.take(6).forEach { failure ->
                        Text(
                            failure,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexoPalette.Error,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }

            // ------------------------------------------------------------ tabs
            Spacer(Modifier.height(NexoSpace.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
            ) {
                PersonalTab.entries.forEach { candidate ->
                    SigChip(
                        text = candidate.label,
                        selected = tab == candidate,
                        onClick = { tabName = candidate.name },
                    )
                }
            }

            when (tab) {
                PersonalTab.FILES -> {
                    // ---------------------------------------------------- search / filters
                    Spacer(Modifier.height(NexoSpace.md))
                    SigSearchField(
                        value = query,
                        onValueChange = { query = it.take(120) },
                        placeholder = "Search names, types and notes",
                    )
                    Spacer(Modifier.height(NexoSpace.sm))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
                    ) {
                        PersonalCategory.entries.forEach { candidate ->
                            SigChip(
                                text = candidate.label,
                                selected = category == candidate,
                                onClick = {
                                    categoryName = candidate.name
                                    if (candidate.notesOnly) {
                                        selecting = false
                                        selection = emptySet()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(NexoSpace.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonalSortMenu(sort = sort, onSelect = { sortName = it.name })
                        Spacer(Modifier.weight(1f))
                        SigChip(
                            text = "Grid",
                            selected = layout == VaultLayout.GRID,
                            onClick = { vm.setVaultLayout(VaultLayout.GRID) },
                        )
                        Spacer(Modifier.width(NexoSpace.xs))
                        SigChip(
                            text = "List",
                            selected = layout == VaultLayout.LIST,
                            onClick = { vm.setVaultLayout(VaultLayout.LIST) },
                        )
                        if (!category.notesOnly) {
                            Spacer(Modifier.width(NexoSpace.xs))
                            SigChip(
                                text = if (selecting) "Cancel" else "Select",
                                selected = selecting,
                                onClick = {
                                    selecting = !selecting
                                    if (!selecting) selection = emptySet()
                                },
                            )
                        }
                    }

                    if (selecting) {
                        Spacer(Modifier.height(NexoSpace.sm))
                        PersonalSelectionBar(
                            count = selection.size,
                            allSelected = files.isNotEmpty() &&
                                selection.size == files.size,
                            onSelectAll = {
                                selection = if (selection.size == files.size) {
                                    emptySet()
                                } else {
                                    files.mapTo(HashSet()) { it.id }
                                }
                            },
                            onClear = {
                                selection = emptySet()
                                selecting = false
                            },
                            onExport = { vm.requestExportFiles(selection) },
                            onDelete = {
                                if (selection.isNotEmpty()) {
                                    dialog = PersonalVaultDialog.DeleteSelected(selection.toList())
                                }
                            },
                        )
                    }

                    // ---------------------------------------------------- recent
                    if (!category.notesOnly &&
                        query.isBlank() &&
                        category == PersonalCategory.ALL &&
                        sort == PersonalSort.NEWEST &&
                        !selecting &&
                        files.size > 3
                    ) {
                        Spacer(Modifier.height(NexoSpace.lg))
                        SigSectionHeader("Recent")
                        files.take(3).forEach { file ->
                            PersonalFileListCard(
                                file = file,
                                vm = vm,
                                selecting = false,
                                selected = false,
                                onOpen = { onOpenFile(kind, file.id) },
                                onDetails = { detailsFile = file },
                                onExport = { vm.requestExportFile(file.id) },
                                onRename = { dialog = PersonalVaultDialog.RenameFile(file) },
                                onVerify = { vm.verifyVaultFile(file.id) },
                                onDelete = { dialog = PersonalVaultDialog.DeleteFile(file) },
                            )
                            Spacer(Modifier.height(NexoSpace.sm))
                        }
                    }

                    // ---------------------------------------------------- files
                    if (!category.notesOnly) {
                        Spacer(Modifier.height(NexoSpace.lg))
                        SigSectionHeader("Files · ${files.size}")
                        if (files.isEmpty()) {
                            SigEmptyState(
                                icon = Icons.Filled.Search,
                                title = if (vault.files.isEmpty()) {
                                    "No files yet"
                                } else {
                                    "No matching files"
                                },
                                message = if (vault.files.isEmpty()) {
                                    "Import photos, videos, audio, documents or any other " +
                                        "file. Imports are streamed, encrypted in authenticated " +
                                        "chunks and have no arbitrary size limit."
                                } else {
                                    "Try another search, album or sort order."
                                },
                            )
                        } else if (layout == VaultLayout.GRID) {
                            files.chunked(2).forEach { rowFiles ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
                                ) {
                                    rowFiles.forEach { file ->
                                        PersonalFileGridCard(
                                            file = file,
                                            vm = vm,
                                            selecting = selecting,
                                            selected = file.id in selection,
                                            modifier = Modifier.weight(1f),
                                            onOpen = {
                                                if (selecting) {
                                                    toggleSelection(file.id)
                                                } else {
                                                    onOpenFile(kind, file.id)
                                                }
                                            },
                                            onDetails = { detailsFile = file },
                                            onExport = { vm.requestExportFile(file.id) },
                                            onRename = {
                                                dialog = PersonalVaultDialog.RenameFile(file)
                                            },
                                            onVerify = { vm.verifyVaultFile(file.id) },
                                            onDelete = {
                                                dialog = PersonalVaultDialog.DeleteFile(file)
                                            },
                                        )
                                    }
                                    if (rowFiles.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(NexoSpace.sm))
                            }
                        } else {
                            files.forEach { file ->
                                PersonalFileListCard(
                                    file = file,
                                    vm = vm,
                                    selecting = selecting,
                                    selected = file.id in selection,
                                    onOpen = {
                                        if (selecting) {
                                            toggleSelection(file.id)
                                        } else {
                                            onOpenFile(kind, file.id)
                                        }
                                    },
                                    onDetails = { detailsFile = file },
                                    onExport = { vm.requestExportFile(file.id) },
                                    onRename = { dialog = PersonalVaultDialog.RenameFile(file) },
                                    onVerify = { vm.verifyVaultFile(file.id) },
                                    onDelete = { dialog = PersonalVaultDialog.DeleteFile(file) },
                                )
                                Spacer(Modifier.height(NexoSpace.sm))
                            }
                        }
                    }

                    // ---------------------------------------------------- notes
                    if (category == PersonalCategory.ALL || category.notesOnly) {
                        Spacer(Modifier.height(NexoSpace.lg))
                        SigSectionHeader("Notes · ${filteredNotes.size}")
                        if (filteredNotes.isEmpty()) {
                            SigEmptyState(
                                icon = Icons.Filled.Edit,
                                title = if (notes.isEmpty()) {
                                    "No notes yet"
                                } else {
                                    "No matching notes"
                                },
                                message = if (notes.isEmpty()) {
                                    "Create a note to store private text inside the " +
                                        "encrypted index."
                                } else {
                                    "Try another search."
                                },
                            )
                        } else {
                            filteredNotes.forEach { entry ->
                                PersonalNoteCard(
                                    entry = entry,
                                    onOpen = { onOpenEntry(kind, entry.id) },
                                    onRename = {
                                        dialog = PersonalVaultDialog.RenameEntry(entry)
                                    },
                                    onDelete = {
                                        dialog = PersonalVaultDialog.DeleteEntry(entry)
                                    },
                                )
                                Spacer(Modifier.height(NexoSpace.sm))
                            }
                        }
                    }
                }

                PersonalTab.CATEGORIES -> {
                    Spacer(Modifier.height(NexoSpace.lg))
                    SigSectionHeader("Albums")
                    Text(
                        "Albums are derived from each file's verified type. NEXO LOCK does " +
                            "not create user-defined folders, so no folder can be shown that " +
                            "does not exist.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                    )
                    Spacer(Modifier.height(NexoSpace.sm))
                    PersonalCategory.entries
                        .filter { it != PersonalCategory.ALL }
                        .forEach { candidate ->
                            val candidateFiles = vault.files.filter(candidate::matches)
                            val count = if (candidate.notesOnly) {
                                notes.size
                            } else {
                                candidateFiles.size
                            }
                            val bytes = if (candidate.notesOnly) {
                                notes.sumOf { entry ->
                                    (entry.title + entry.body)
                                        .toByteArray(Charsets.UTF_8)
                                        .size
                                        .toLong()
                                }
                            } else {
                                candidateFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
                            }
                            PersonalCategoryCard(
                                category = candidate,
                                count = count,
                                bytes = bytes,
                                onClick = {
                                    categoryName = candidate.name
                                    tabName = PersonalTab.FILES.name
                                    if (candidate.notesOnly) {
                                        selecting = false
                                        selection = emptySet()
                                    }
                                },
                            )
                            Spacer(Modifier.height(NexoSpace.sm))
                        }

                    Spacer(Modifier.height(NexoSpace.lg))
                    SigSectionHeader("Storage")
                    SigCard(padding = NexoSpace.md) {
                        SigKeyValueRow(
                            "Encrypted on disk",
                            formatBytes(vault.storage?.encryptedBytes ?: 0L),
                        )
                        SigKeyValueRow(
                            "Plaintext total",
                            formatBytes(vault.storage?.logicalBytes ?: 0L),
                        )
                        SigKeyValueRow(
                            "Temporary previews",
                            formatBytes(vault.storage?.previewBytes ?: 0L),
                        )
                        SigKeyValueRow(
                            "Free space",
                            vault.availableBytes?.let { formatBytes(it) } ?: "Unknown",
                        )
                        SigKeyValueRow(
                            "Interrupted imports",
                            (vault.storage?.failedImportCount ?: 0).toString(),
                        )
                        Spacer(Modifier.height(NexoSpace.sm))
                        Text(
                            "Encrypted size is measured from the vault directory, not " +
                                "estimated. Temporary previews are deleted when the vault locks.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoPalette.TextMuted,
                        )
                    }
                }

                PersonalTab.ACTIVITY -> {
                    val events = remember(state.logs.entries, query) {
                        VaultActivity.events(state.logs.entries, kind, query)
                    }
                    Spacer(Modifier.height(NexoSpace.md))
                    SigSearchField(
                        value = query,
                        onValueChange = { query = it.take(120) },
                        placeholder = "Search vault activity",
                    )
                    Spacer(Modifier.height(NexoSpace.md))
                    SigSectionHeader("Vault activity · ${events.size}")
                    if (events.isEmpty()) {
                        SigEmptyState(
                            icon = Icons.Filled.Info,
                            title = "No vault activity",
                            message = "Imports, exports, unlocks and deletions appear here " +
                                "after they are written to the encrypted security journal.",
                        )
                    } else {
                        events.forEach { entry ->
                            PersonalActivityRow(entry)
                            SigDivider()
                        }
                    }
                    Spacer(Modifier.height(NexoSpace.sm))
                    Text(
                        "This list is filtered from the encrypted security journal. Events " +
                            "that happen outside the app, such as access by another app or a " +
                            "rooted system, cannot be observed and are not claimed here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                    )
                }
            }

            // ------------------------------------------------------------ management
            Spacer(Modifier.height(NexoSpace.xl))
            SigSectionHeader("Vault management")
            SigCard(padding = NexoSpace.md) {
                SigSettingRow(
                    icon = Icons.Filled.Delete,
                    title = "Delete originals after import",
                    subtitle = if (state.config.deleteOriginalsAfterImport) {
                        "On · every import batch still asks for confirmation"
                    } else {
                        "Off · originals are always kept"
                    },
                    trailing = {
                        Switch(
                            checked = state.config.deleteOriginalsAfterImport,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showAutoDeleteDialog = true
                                } else {
                                    vm.setDeleteOriginalsAfterImport(false)
                                }
                            },
                            enabled = !busy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NexoPalette.OnSilver,
                                checkedTrackColor = NexoPalette.Silver,
                                uncheckedThumbColor = NexoPalette.TextMuted,
                                uncheckedTrackColor = NexoPalette.SurfaceAlt,
                                uncheckedBorderColor = NexoPalette.Border,
                            ),
                        )
                    },
                )
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Lock,
                    title = "Change Personal Vault PIN",
                    subtitle = "Requires the current vault PIN",
                    onClick = { showPinChange = !showPinChange },
                )
                if (showPinChange) {
                    VaultPinChangeForm(kind, vm)
                }
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Info,
                    title = "Import diagnostics",
                    subtitle = "${vault.diagnostics.size} safe attempts recorded in memory",
                    onClick = { dialog = PersonalVaultDialog.Diagnostics },
                )
                SigDivider()
                SigSettingRow(
                    icon = Icons.Filled.Delete,
                    title = "Delete Personal Vault",
                    subtitle = "Removes the PIN, index and every encrypted attachment",
                    onClick = { dialog = PersonalVaultDialog.DeleteVault },
                    destructive = true,
                )
            }
            Spacer(Modifier.height(NexoSpace.sm))
            Text(
                "Vault files are encrypted with AES-256-GCM using a key held by the Android " +
                    "Keystore. Screenshots and task-switcher previews are blocked with " +
                    "FLAG_SECURE, but a rooted device or a camera pointed at the screen " +
                    "cannot be prevented by any Android app.",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.lg))
        }
    }

    // ------------------------------------------------------------------ dialogs
    if (showNoteDialog) {
        AddNoteDialog(kind = kind, vm = vm, onDismiss = { showNoteDialog = false })
    }

    if (showAutoDeleteDialog) {
        SigDialog(
            title = "Delete originals after import?",
            message = "When this is on, each import batch asks for confirmation before the " +
                "picker's original documents are deleted. A file is only deleted after the " +
                "encrypted vault copy has been committed and fully verified with its SHA-256 " +
                "digest. The document provider can still refuse or require a system " +
                "confirmation, and any refusal is reported honestly.",
            confirmLabel = "Enable",
            onConfirm = {
                showAutoDeleteDialog = false
                vm.setDeleteOriginalsAfterImport(true)
            },
            onDismiss = { showAutoDeleteDialog = false },
        )
    }

    vault.pendingImport?.let { pending ->
        SigDialog(
            title = "Import ${pending.uris.size} " +
                "${personalPlural(pending.uris.size, "file")}?",
            message = "Delete originals is enabled. After each encrypted copy is committed " +
                "and verified, Nexo Lock will ask the document provider to delete the " +
                "original. This cannot be undone. If you choose Keep originals, the import " +
                "still proceeds and nothing is deleted.",
            confirmLabel = "Import & delete originals",
            dismissLabel = "Keep originals",
            onConfirm = { vm.confirmPendingImport(true) },
            onDismiss = vm::cancelPendingImport,
        )
    }

    when (val current = dialog) {
        is PersonalVaultDialog.RenameFile -> PersonalRenameDialog(
            title = "Rename encrypted file",
            initial = current.file.displayName,
            confirmLabel = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.renameVaultFile(current.file.id, name) { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
        )

        is PersonalVaultDialog.DeleteFile -> SigDialog(
            title = "Delete encrypted file?",
            message = "\"${current.file.displayName}\" and its encrypted chunks will be " +
                "permanently removed from the Personal Vault.",
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                dialog = null
                vm.deleteVaultFile(current.file.id) { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
            onDismiss = { dialog = null },
        )

        is PersonalVaultDialog.DeleteSelected -> SigDialog(
            title = "Delete ${current.ids.size} selected " +
                "${personalPlural(current.ids.size, "file")}?",
            message = "Their encrypted chunks and index rows will be permanently removed. " +
                "This cannot be undone.",
            confirmLabel = "Delete selected",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                val ids = current.ids
                dialog = null
                selection = emptySet()
                selecting = false
                vm.deleteVaultFiles(ids) { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
            onDismiss = { dialog = null },
        )

        is PersonalVaultDialog.RenameEntry -> PersonalRenameDialog(
            title = "Rename note",
            initial = current.entry.title,
            confirmLabel = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.renameVaultEntry(current.entry.id, name) { failure ->
                    if (failure != null) vm.notify(failure, error = true)
                }
            },
        )

        is PersonalVaultDialog.DeleteEntry -> SigDialog(
            title = "Delete encrypted note?",
            message = "\"${current.entry.title}\" and its attachment will be permanently " +
                "removed from the Personal Vault.",
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                dialog = null
                vm.deleteVaultEntry(current.entry.id)
            },
            onDismiss = { dialog = null },
        )

        PersonalVaultDialog.DeleteVault -> SigDialog(
            title = "Delete Personal Vault?",
            message = "This deletes the vault PIN, the encrypted index and all attachments. " +
                "It cannot be undone.",
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

        PersonalVaultDialog.Diagnostics -> ImportDiagnosticsDialog(
            diagnostics = vault.diagnostics,
            onClear = vm::clearImportDiagnostics,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }

    detailsFile?.let { file ->
        SigBottomSheet(onDismiss = { detailsFile = null }) {
            PersonalDetailsSheetContent(
                file = file,
                vm = vm,
                onOpen = {
                    detailsFile = null
                    onOpenFile(kind, file.id)
                },
                onExport = {
                    detailsFile = null
                    vm.requestExportFile(file.id)
                },
                onRename = {
                    detailsFile = null
                    dialog = PersonalVaultDialog.RenameFile(file)
                },
                onVerify = { vm.verifyVaultFile(file.id) },
                onDelete = {
                    detailsFile = null
                    dialog = PersonalVaultDialog.DeleteFile(file)
                },
                onDismiss = { detailsFile = null },
            )
        }
    }
}

// ---------------------------------------------------------------------------- helpers

@Composable
private fun PersonalSortMenu(sort: PersonalSort, onSelect: (PersonalSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SigChip(
            text = "Sort: ${sort.label}",
            selected = false,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PersonalSort.entries.forEach { candidate ->
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
private fun PersonalRenameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
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
        confirmLabel = confirmLabel,
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

private fun VaultFile.personalMatches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return displayName.contains(needle, ignoreCase = true) ||
        mimeType.contains(needle, ignoreCase = true) ||
        category().label.contains(needle, ignoreCase = true)
}

private fun VaultEntry.personalMatches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        body.contains(needle, ignoreCase = true)
}

private fun personalFileComparator(sort: PersonalSort): Comparator<VaultFile> = when (sort) {
    PersonalSort.NEWEST -> compareByDescending { it.createdAt }
    PersonalSort.OLDEST -> compareBy { it.createdAt }
    PersonalSort.NAME -> compareBy { it.displayName.lowercase() }
    PersonalSort.TYPE -> compareBy<VaultFile> { it.mimeType.lowercase() }
        .thenBy { it.displayName.lowercase() }
    PersonalSort.LARGEST -> compareByDescending { it.sizeBytes }
    PersonalSort.SMALLEST -> compareBy { it.sizeBytes }
}

private fun personalEntryComparator(sort: PersonalSort): Comparator<VaultEntry> = when (sort) {
    PersonalSort.OLDEST -> compareBy { it.createdAt }
    PersonalSort.NAME -> compareBy { it.title.lowercase() }
    else -> compareByDescending { it.createdAt }
}
