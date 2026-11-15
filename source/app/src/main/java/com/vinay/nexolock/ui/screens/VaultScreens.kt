package com.vinay.nexolock.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.VaultFilePicker
import com.vinay.nexolock.ui.VaultUiState
import com.vinay.nexolock.ui.components.BannerTone
import com.vinay.nexolock.ui.components.EmptyState
import com.vinay.nexolock.ui.components.InfoBanner
import com.vinay.nexolock.ui.components.NexoButton
import com.vinay.nexolock.ui.components.NexoCard
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.NexoScreen
import com.vinay.nexolock.ui.components.PillTone
import com.vinay.nexolock.ui.components.PinEntryPanel
import com.vinay.nexolock.ui.components.ScreenHeader
import com.vinay.nexolock.ui.components.SectionLabel
import com.vinay.nexolock.ui.components.SettingsRow
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigDialog
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigLoadingState
import com.vinay.nexolock.ui.components.SigPinPanel
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SigTextField
import com.vinay.nexolock.ui.components.SignatureBackdrop
import com.vinay.nexolock.ui.components.StatusPill
import com.vinay.nexolock.ui.formatBytes
import com.vinay.nexolock.ui.theme.LocalNexoBottomPadding
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.vault.CaptureStatus
import com.vinay.nexolock.vault.IntruderIncidents
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultEntryType
import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileCategory
import com.vinay.nexolock.vault.VaultFileStatus
import com.vinay.nexolock.vault.VaultKind
import com.vinay.nexolock.vault.category
import com.vinay.nexolock.vault.isImageMime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------- entry points

@Composable
fun PersonalVaultScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
) {
    VaultScreen(VaultKind.PERSONAL, state, vm, onBack, onOpenFile, onOpenEntry)
}

@Composable
fun IntruderVaultScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
) {
    VaultScreen(VaultKind.INTRUDER, state, vm, onBack, onOpenFile, onOpenEntry)
}

@Composable
private fun VaultScreen(
    kind: VaultKind,
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
) {
    val vault = state.vault

    LaunchedEffect(kind) { vm.openVault(kind) }

    SignatureBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ScreenHeader(
                title = kind.title,
                subtitle = kind.subtitle,
                eyebrow = "ENCRYPTED VAULT",
                onBack = onBack,
                actions = {
                    if (vault.kind == kind && vault.hasPin) {
                        SigPill(
                            if (vault.unlocked) "UNLOCKED" else "LOCKED",
                            if (vault.unlocked) SigTone.SUCCESS else SigTone.WARNING,
                        )
                    }
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexoSpace.screenHorizontal)
                    .padding(bottom = LocalNexoBottomPadding.current + NexoSpace.md),
            ) {
                when {
                    vault.kind != kind -> {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            SigLoadingState("Opening ${kind.title}…")
                        }
                    }

                    !vault.hasPin -> ScrollingContent {
                        CreateVaultCredential(kind, vault.pinLength, vm)
                    }

                    !vault.unlocked -> ScrollingContent {
                        UnlockVault(kind, vault, vm)
                    }

                    kind == VaultKind.PERSONAL -> PersonalVaultHome(
                        state = state,
                        vm = vm,
                        onOpenFile = onOpenFile,
                        onOpenEntry = onOpenEntry,
                        modifier = Modifier.weight(1f),
                    )

                    else -> IntruderVaultHome(
                        state = state,
                        vm = vm,
                        onOpenFile = onOpenFile,
                        onOpenEntry = onOpenEntry,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ScrollingContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------- create / unlock

/**
 * Explicit stages for creating an independent vault PIN. A confirmation stage
 * can only exist when a first PIN was captured, so "confirming an empty PIN"
 * is unrepresentable. Secret buffers are held with `remember` and are never
 * written to saved instance state.
 */
private sealed interface VaultCreateStage {
    data class Entering(val length: Int, val buffer: String = "") : VaultCreateStage
    data class Confirming(
        val length: Int,
        val first: String,
        val buffer: String = "",
    ) : VaultCreateStage
    data class Saving(val length: Int) : VaultCreateStage
}

private val VaultCreateStage.length: Int
    get() = when (this) {
        is VaultCreateStage.Entering -> length
        is VaultCreateStage.Confirming -> length
        is VaultCreateStage.Saving -> length
    }

private val VaultCreateStage.buffer: String
    get() = when (this) {
        is VaultCreateStage.Entering -> buffer
        is VaultCreateStage.Confirming -> buffer
        is VaultCreateStage.Saving -> ""
    }

private val VaultCreateStage.busy: Boolean
    get() = this is VaultCreateStage.Saving

@Composable
private fun CreateVaultCredential(
    kind: VaultKind,
    storedLength: Int,
    vm: NexoViewModel,
) {
    var stage by remember {
        mutableStateOf<VaultCreateStage>(
            VaultCreateStage.Entering(storedLength.coerceIn(4, 8)),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val length = stage.length
    val entered = stage.buffer
    val confirming = stage is VaultCreateStage.Confirming
    val busy = stage.busy

    Text(
        "Create ${kind.title}",
        style = MaterialTheme.typography.headlineSmall,
        color = NexoPalette.TextPrimary,
    )
    Spacer(Modifier.height(NexoSpace.sm))
    SigBanner(
        text = "This vault has its own PIN and its own Keystore key. The main PIN, " +
            "recovery PIN and the other vault PIN cannot open it.",
        tone = SigTone.INFO,
    )
    Spacer(Modifier.height(NexoSpace.md))

    if (stage is VaultCreateStage.Entering) {
        Text(
            "Choose the PIN length",
            style = MaterialTheme.typography.bodyMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
            (4..8).forEach { candidate ->
                SigChip(
                    text = "$candidate",
                    selected = length == candidate,
                    onClick = { stage = VaultCreateStage.Entering(candidate) },
                )
            }
        }
    }
    Spacer(Modifier.height(NexoSpace.md))
    error?.let {
        SigBanner(text = it, tone = SigTone.DANGER)
        Spacer(Modifier.height(NexoSpace.sm))
    }
    Text(
        if (confirming) "Confirm the ${kind.title} PIN" else "Enter a new ${kind.title} PIN",
        style = MaterialTheme.typography.titleMedium,
        color = NexoPalette.TextPrimary,
    )
    Spacer(Modifier.height(NexoSpace.sm))
    SigPinPanel(
        entered = entered,
        slots = length,
        enabled = !busy,
        error = error != null,
        onDigit = { digit ->
            if (!busy) {
                when (val current = stage) {
                    is VaultCreateStage.Entering -> {
                        if (current.buffer.length < current.length) {
                            val next = current.buffer + digit
                            error = null
                            stage = if (next.length == current.length) {
                                VaultCreateStage.Confirming(
                                    length = current.length,
                                    first = next,
                                )
                            } else {
                                current.copy(buffer = next)
                            }
                        }
                    }

                    is VaultCreateStage.Confirming -> {
                        if (current.buffer.length < current.length) {
                            val next = current.buffer + digit
                            error = null
                            if (next.length < current.length) {
                                stage = current.copy(buffer = next)
                            } else if (next != current.first) {
                                error = "The PINs did not match. Start again."
                                stage = VaultCreateStage.Entering(current.length)
                            } else {
                                stage = VaultCreateStage.Saving(current.length)
                                vm.createVaultPin(next) { failure ->
                                    stage = VaultCreateStage.Entering(current.length)
                                    if (failure != null) error = failure
                                }
                            }
                        }
                    }

                    is VaultCreateStage.Saving -> Unit
                }
            }
        },
        onBackspace = {
            when (val current = stage) {
                is VaultCreateStage.Entering ->
                    stage = current.copy(buffer = current.buffer.dropLast(1))

                is VaultCreateStage.Confirming ->
                    stage = current.copy(buffer = current.buffer.dropLast(1))

                is VaultCreateStage.Saving -> Unit
            }
        },
    )
    if (confirming) {
        Spacer(Modifier.height(NexoSpace.xs))
        SigGhostButton(
            text = "Start over",
            enabled = !busy,
            onClick = {
                stage = VaultCreateStage.Entering(length)
                error = null
            },
        )
    }
    Spacer(Modifier.height(NexoSpace.lg))
}

@Composable
private fun UnlockVault(
    kind: VaultKind,
    vault: VaultUiState,
    vm: NexoViewModel,
) {
    var entered by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        "Vault locked",
        style = MaterialTheme.typography.headlineSmall,
        color = NexoPalette.TextPrimary,
    )
    Spacer(Modifier.height(NexoSpace.xs))
    Text(
        "${kind.title} is encrypted at rest. Enter its independent PIN to decrypt the " +
            "index in memory.",
        style = MaterialTheme.typography.bodySmall,
        color = NexoPalette.TextSecondary,
    )
    Spacer(Modifier.height(NexoSpace.md))
    vault.message?.let {
        SigBanner(text = it, tone = SigTone.SUCCESS)
        Spacer(Modifier.height(NexoSpace.sm))
    }
    error?.let {
        SigBanner(text = it, tone = SigTone.DANGER)
        Spacer(Modifier.height(NexoSpace.sm))
    }
    SigPinPanel(
        entered = entered,
        slots = vault.pinLength.coerceIn(4, 8),
        enabled = !vault.busy,
        error = error != null,
        onDigit = { digit ->
            if (!vault.busy && entered.length < vault.pinLength) {
                entered += digit.toString()
                // Keep the previous error visible while the next PIN is typed.
                // Removing it here shifts the keypad and button mid-entry.
            }
        },
        onBackspace = { entered = entered.dropLast(1) },
    )
    Spacer(Modifier.height(NexoSpace.lg))
    SigPrimaryButton(
        text = if (vault.busy) "Decrypting…" else "Unlock ${kind.title}",
        enabled = !vault.busy && entered.length == vault.pinLength,
        loading = vault.busy,
        onClick = {
            vm.unlockVault(entered) { failure ->
                if (failure != null) error = failure
                entered = ""
            }
        },
    )
    Spacer(Modifier.height(NexoSpace.lg))
}

// ---------------------------------------------------------------------------- personal vault

private enum class PersonalFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    OTHER("Other"),
    NOTES("Notes"),
    ATTENTION("Needs attention"),
}

private enum class VaultSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name A–Z"),
    SIZE("Largest"),
}

private sealed interface PersonalDialog {
    data class InfoFile(val file: VaultFile) : PersonalDialog
    data class RenameFile(val file: VaultFile) : PersonalDialog
    data class DeleteFile(val file: VaultFile) : PersonalDialog
    data class DeleteSelected(val ids: Set<String>) : PersonalDialog
    data class RenameEntry(val entry: VaultEntry) : PersonalDialog
    data class DeleteEntry(val entry: VaultEntry) : PersonalDialog
    data object DeleteVault : PersonalDialog
    data object Diagnostics : PersonalDialog
}

@Composable
private fun PersonalVaultContent(
    state: NexoUiState,
    vm: NexoViewModel,
    onOpenFile: (VaultKind, String) -> Unit,
    onOpenEntry: (VaultKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = VaultKind.PERSONAL
    val vault = state.vault
    val context = LocalContext.current

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(PersonalFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(VaultSort.NEWEST.name) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dialog by remember { mutableStateOf<PersonalDialog?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPinChange by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    val filter = PersonalFilter.entries.firstOrNull { it.name == filterName } ?: PersonalFilter.ALL
    val sort = VaultSort.entries.firstOrNull { it.name == sortName } ?: VaultSort.NEWEST
    val notes = vault.entries.filter { it.type == VaultEntryType.NOTE }
    val files = remember(vault.files, query, filter, sort) {
        vault.files
            .filter { file ->
                val categoryMatches = when (filter) {
                    PersonalFilter.ALL -> true
                    PersonalFilter.PHOTOS -> file.category() == VaultFileCategory.PHOTOS
                    PersonalFilter.VIDEOS -> file.category() == VaultFileCategory.VIDEOS
                    PersonalFilter.DOCUMENTS -> file.category() == VaultFileCategory.DOCUMENTS
                    PersonalFilter.OTHER -> file.category() == VaultFileCategory.OTHER
                    PersonalFilter.NOTES -> false
                    PersonalFilter.ATTENTION -> file.status != VaultFileStatus.COMPLETE
                }
                categoryMatches && file.matches(query)
            }
            .sortedWith(fileComparator(sort))
    }
    val filteredNotes = remember(notes, query, filter, sort) {
        if (filter != PersonalFilter.ALL && filter != PersonalFilter.NOTES) {
            emptyList()
        } else {
            notes
                .filter { it.matches(query) }
                .sortedWith(entryComparator(sort))
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        // ---------------------------------------------------------- status / actions
        NexoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatusPill("UNLOCKED", PillTone.SUCCESS)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your private files and notes",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${vault.files.size} ${plural(vault.files.size, "file")} · " +
                            "${notes.size} ${plural(notes.size, "note")} · AES-256-GCM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Vault actions")
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
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
                                dialog = PersonalDialog.Diagnostics
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete vault") },
                            onClick = {
                                showOverflow = false
                                dialog = PersonalDialog.DeleteVault
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NexoButton(
                    text = if (vault.busy) "Working…" else "Import files",
                    onClick = {
                        if (!VaultFilePicker.launch(context)) {
                            vm.notify("The system file picker could not be opened.", error = true)
                        }
                    },
                    enabled = !vault.busy,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { showNoteDialog = true },
                    enabled = !vault.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New note")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::lockVault, enabled = !vault.busy) {
                Text("Lock ${kind.title}")
            }
        }

        vault.message?.let {
            Spacer(Modifier.height(10.dp))
            InfoBanner(it, BannerTone.SUCCESS)
        }
        vault.error?.let {
            Spacer(Modifier.height(10.dp))
            InfoBanner(it, BannerTone.DANGER)
        }

        // ---------------------------------------------------------- import progress
        if (vault.importStage != null || vault.importingName != null) {
            Spacer(Modifier.height(12.dp))
            ImportProgressCard(vault = vault, onCancel = vm::cancelVaultImport)
        }

        // ---------------------------------------------------------- storage summary
        Spacer(Modifier.height(14.dp))
        StorageSummaryCard(
            encryptedBytes = vault.storage?.encryptedBytes ?: 0L,
            logicalBytes = vault.storage?.logicalBytes ?: 0L,
            fileCount = vault.files.size,
            noteCount = notes.size,
            availableBytes = vault.availableBytes,
            failedImports = vault.storage?.failedImportCount ?: 0,
            onShowAttention = { filterName = PersonalFilter.ATTENTION.name },
        )

        // ---------------------------------------------------------- search / filters
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search names, types and notes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonalFilter.entries.forEach { candidate ->
                FilterChip(
                    selected = filter == candidate,
                    onClick = {
                        filterName = candidate.name
                        if (candidate == PersonalFilter.NOTES) selecting = false
                    },
                    label = { Text(candidate.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SortMenu(sort = sort, onSelect = { sortName = it.name })
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = grid,
                onClick = { grid = true },
                label = { Text("Grid") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !grid,
                onClick = { grid = false },
                label = { Text("List") },
            )
            if (filter != PersonalFilter.NOTES) {
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = selecting,
                    onClick = {
                        selecting = !selecting
                        if (!selecting) selection = emptySet()
                    },
                    label = { Text(if (selecting) "Cancel select" else "Select") },
                )
            }
        }

        if (selecting) {
            Spacer(Modifier.height(6.dp))
            SelectionBar(
                count = selection.size,
                onClear = {
                    selection = emptySet()
                    selecting = false
                },
                onDelete = {
                    if (selection.isNotEmpty()) {
                        dialog = PersonalDialog.DeleteSelected(selection)
                    }
                },
            )
        }

        // ---------------------------------------------------------- files
        if (filter != PersonalFilter.NOTES) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("FILES · ${files.size}")
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = if (vault.files.isEmpty()) "No files yet" else "No matching files",
                    message = if (vault.files.isEmpty()) {
                        "Import photos, videos, documents or any other file. Imports are " +
                            "streamed, encrypted in 64 KiB authenticated chunks and have no size limit."
                    } else {
                        "Try another search, category or sort order."
                    },
                )
            } else if (grid) {
                files.chunked(2).forEach { rowFiles ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowFiles.forEach { file ->
                            FileGridCard(
                                kind = kind,
                                file = file,
                                vm = vm,
                                selecting = selecting,
                                selected = file.id in selection,
                                modifier = Modifier.weight(1f),
                                onOpen = {
                                    if (selecting) {
                                        selection = selection.toggle(file.id)
                                    } else {
                                        onOpenFile(kind, file.id)
                                    }
                                },
                                onInfo = { dialog = PersonalDialog.InfoFile(file) },
                                onRename = { dialog = PersonalDialog.RenameFile(file) },
                                onVerify = { vm.verifyVaultFile(file.id) },
                                onDelete = { dialog = PersonalDialog.DeleteFile(file) },
                            )
                        }
                        if (rowFiles.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                files.forEach { file ->
                    FileListCard(
                        kind = kind,
                        file = file,
                        vm = vm,
                        selecting = selecting,
                        selected = file.id in selection,
                        onOpen = {
                            if (selecting) {
                                selection = selection.toggle(file.id)
                            } else {
                                onOpenFile(kind, file.id)
                            }
                        },
                        onInfo = { dialog = PersonalDialog.InfoFile(file) },
                        onRename = { dialog = PersonalDialog.RenameFile(file) },
                        onVerify = { vm.verifyVaultFile(file.id) },
                        onDelete = { dialog = PersonalDialog.DeleteFile(file) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------------------------------------------------------- notes
        if (filter == PersonalFilter.ALL || filter == PersonalFilter.NOTES) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("NOTES · ${filteredNotes.size}")
            Spacer(Modifier.height(8.dp))
            if (filteredNotes.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Edit,
                    title = if (notes.isEmpty()) "No notes yet" else "No matching notes",
                    message = if (notes.isEmpty()) {
                        "Create a note to store private text inside the encrypted index."
                    } else {
                        "Try another search."
                    },
                )
            } else {
                filteredNotes.forEach { entry ->
                    NoteCard(
                        entry = entry,
                        onOpen = { onOpenEntry(kind, entry.id) },
                        onRename = { dialog = PersonalDialog.RenameEntry(entry) },
                        onDelete = { dialog = PersonalDialog.DeleteEntry(entry) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------------------------------------------------------- management
        Spacer(Modifier.height(18.dp))
        SectionLabel("VAULT MANAGEMENT")
        Spacer(Modifier.height(8.dp))
        NexoCard {
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "Change ${kind.title} PIN",
                subtitle = "Requires the current vault PIN",
                onClick = { showPinChange = !showPinChange },
            )
            if (showPinChange) {
                VaultPinChangeForm(kind, vm)
            }
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Import diagnostics",
                subtitle = "${vault.diagnostics.size} safe attempts recorded in memory",
                onClick = { dialog = PersonalDialog.Diagnostics },
            )
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Delete,
                title = "Delete ${kind.title}",
                subtitle = "Removes the PIN, index and every encrypted attachment",
                onClick = { dialog = PersonalDialog.DeleteVault },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showNoteDialog) {
        AddNoteDialog(
            kind = kind,
            vm = vm,
            onDismiss = { showNoteDialog = false },
        )
    }

    when (val current = dialog) {
        is PersonalDialog.InfoFile -> FileInfoDialog(
            file = current.file,
            onDismiss = { dialog = null },
        )

        is PersonalDialog.RenameFile -> RenameDialog(
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

        is PersonalDialog.DeleteFile -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete encrypted file?") },
            text = {
                Text(
                    "\"${current.file.displayName}\" and its encrypted chunks will be permanently removed from ${kind.title}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.deleteVaultFile(current.file.id) { failure ->
                            if (failure != null) vm.notify(failure, error = true)
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        is PersonalDialog.DeleteSelected -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${current.ids.size} selected files?") },
            text = {
                Text("Their encrypted chunks and index rows will be permanently removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = current.ids
                        dialog = null
                        selection = emptySet()
                        selecting = false
                        vm.deleteVaultFiles(ids) { failure ->
                            if (failure != null) vm.notify(failure, error = true)
                        }
                    },
                ) { Text("Delete selected") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        is PersonalDialog.RenameEntry -> RenameDialog(
            title = if (current.entry.type == VaultEntryType.CAPTURE) {
                "Rename capture record"
            } else {
                "Rename note"
            },
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

        is PersonalDialog.DeleteEntry -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete encrypted entry?") },
            text = {
                Text(
                    "\"${current.entry.title}\" and its attachment will be permanently removed from ${kind.title}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.deleteVaultEntry(current.entry.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        PersonalDialog.DeleteVault -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${kind.title}?") },
            text = {
                Text(
                    "This deletes the vault PIN, the encrypted index and all attachments. " +
                        "It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.deleteVault { failure ->
                            if (failure != null) vm.notify(failure, error = true)
                        }
                    },
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        PersonalDialog.Diagnostics -> ImportDiagnosticsDialog(
            diagnostics = vault.diagnostics,
            onClear = vm::clearImportDiagnostics,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

// ---------------------------------------------------------------------------- intruder vault

private enum class IncidentFilter(val label: String) {
    ALL("All"),
    CAPTURED("Captured"),
    NO_CAPTURE("No capture"),
    NOTES("Notes"),
}

private sealed interface IntruderDialog {
    data class Details(val entry: VaultEntry) : IntruderDialog
    data class Rename(val entry: VaultEntry) : IntruderDialog
    data class Delete(val entry: VaultEntry) : IntruderDialog
    data object ClearHistory : IntruderDialog
    data object DeleteVault : IntruderDialog
    data object Diagnostics : IntruderDialog
}

@Composable
private fun IntruderVaultContent(
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
    var filterName by rememberSaveable { mutableStateOf(IncidentFilter.ALL.name) }
    var stageFilter by rememberSaveable { mutableStateOf("ALL") }
    var sortName by rememberSaveable { mutableStateOf(VaultSort.NEWEST.name) }
    var dialog by remember { mutableStateOf<IntruderDialog?>(null) }
    var showPinChange by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    val filter = IncidentFilter.entries.firstOrNull { it.name == filterName } ?: IncidentFilter.ALL
    val sort = VaultSort.entries.firstOrNull { it.name == sortName } ?: VaultSort.NEWEST
    val stats = remember(vault.entries) { IntruderIncidents.stats(vault.entries) }
    val incidents = vault.entries.filter { it.type == VaultEntryType.CAPTURE }
    val notes = vault.entries.filter { it.type == VaultEntryType.NOTE }

    val filteredIncidents = remember(incidents, query, filter, stageFilter, sort) {
        if (filter == IncidentFilter.NOTES) {
            emptyList()
        } else {
            incidents
                .filter { entry ->
                    val captureMatches = when (filter) {
                        IncidentFilter.CAPTURED ->
                            entry.captureStatus == CaptureStatus.CAPTURED &&
                                entry.attachmentId != null

                        IncidentFilter.NO_CAPTURE ->
                            entry.captureStatus != CaptureStatus.CAPTURED ||
                                entry.attachmentId == null

                        else -> true
                    }
                    val stageMatches = stageFilter == "ALL" ||
                        IntruderIncidents.displayStage(entry) == stageFilter
                    captureMatches && stageMatches && entry.matches(query)
                }
                .sortedWith(entryComparator(sort))
        }
    }
    val filteredNotes = remember(notes, query, filter, sort) {
        if (filter != IncidentFilter.ALL && filter != IncidentFilter.NOTES) {
            emptyList()
        } else {
            notes.filter { it.matches(query) }.sortedWith(entryComparator(sort))
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        NexoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatusPill("UNLOCKED", PillTone.SUCCESS)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Incident evidence",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "A separate encrypted repository for failed unlocks and captures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Vault actions")
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
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
                                dialog = IntruderDialog.ClearHistory
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import diagnostics") },
                            onClick = {
                                showOverflow = false
                                dialog = IntruderDialog.Diagnostics
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete vault") },
                            onClick = {
                                showOverflow = false
                                dialog = IntruderDialog.DeleteVault
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Incidents", stats.incidentCount.toString(), Modifier.weight(1f))
                Metric("Captured", stats.capturedCount.toString(), Modifier.weight(1f))
                Metric("Failed auths", stats.failedAuthCount.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Latest: ${formatTimestamp(stats.latestIncidentAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = vm::lockVault, enabled = !vault.busy) {
                Text("Lock ${kind.title}")
            }
        }

        vault.message?.let {
            Spacer(Modifier.height(10.dp))
            InfoBanner(it, BannerTone.SUCCESS)
        }
        vault.error?.let {
            Spacer(Modifier.height(10.dp))
            InfoBanner(it, BannerTone.DANGER)
        }

        // ---------------------------------------------------------- capture readiness
        Spacer(Modifier.height(12.dp))
        val captureMessage: String
        val captureTone: BannerTone
        when {
            capture.inProgress -> {
                captureMessage = "Capture in progress: waiting for the front camera…"
                captureTone = BannerTone.INFO
            }

            !capture.hasVaultPin -> {
                captureMessage = "Intruder capture needs the Intruder Vault PIN."
                captureTone = BannerTone.WARNING
            }

            !capture.enabled -> {
                captureMessage = "Intruder capture is OFF. Failed unlocks are still recorded as incidents without a photo."
                captureTone = BannerTone.WARNING
            }

            !capture.cameraPermission -> {
                captureMessage = "Capture is ON, but camera permission is missing, so no photo can be taken."
                captureTone = BannerTone.DANGER
            }

            !capture.cameraAvailable -> {
                captureMessage = "Capture is ON, but no usable front camera was detected on this device."
                captureTone = BannerTone.DANGER
            }

            capture.lastStatus == CaptureStatus.CAPTURED -> {
                captureMessage = "Last capture succeeded and is stored encrypted."
                captureTone = BannerTone.SUCCESS
            }

            capture.lastStatus != null -> {
                captureMessage = "Last capture failed (${capture.lastStatus.name.lowercase()}): " +
                    (capture.lastMessage ?: "no detail")
                captureTone = BannerTone.WARNING
            }

            else -> {
                captureMessage = "Capture is ready. A front-camera photo is requested after a failed " +
                    "unlock; every outcome is recorded, including failures."
                captureTone = BannerTone.INFO
            }
        }
        InfoBanner(captureMessage, captureTone)
        if (capture.attemptCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${capture.attemptCount} capture " +
                    (if (capture.attemptCount == 1) "attempt" else "attempts") +
                    " this session.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------- storage
        Spacer(Modifier.height(14.dp))
        NexoCard {
            SectionLabel("STORAGE")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(
                    "Encrypted",
                    formatBytes(vault.storage?.encryptedBytes ?: 0L),
                    Modifier.weight(1f),
                )
                Metric(
                    "Captures",
                    formatBytes(vault.storage?.attachmentBytes ?: 0L),
                    Modifier.weight(1f),
                )
                Metric(
                    "Available",
                    vault.availableBytes?.let(::formatBytes) ?: "—",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${stats.incidentCount} incidents · ${stats.capturedCount} with encrypted capture · " +
                    "${stats.captureUnavailableCount} without a capture · ${stats.noteCount} secure notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------- search / filter
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search target, reason, stage or note") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IncidentFilter.entries.forEach { candidate ->
                FilterChip(
                    selected = filter == candidate,
                    onClick = { filterName = candidate.name },
                    label = { Text(candidate.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StageMenu(current = stageFilter, onSelect = { stageFilter = it })
            Spacer(Modifier.weight(1f))
            SortMenu(sort = sort, onSelect = { sortName = it.name })
        }

        // ---------------------------------------------------------- incident history
        if (filter != IncidentFilter.NOTES) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("INCIDENT HISTORY · ${filteredIncidents.size}")
            Spacer(Modifier.height(8.dp))
            if (filteredIncidents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = if (incidents.isEmpty()) "No incidents recorded" else "No matching incidents",
                    message = if (incidents.isEmpty()) {
                        "Failed unlocks appear here after the protection service records them. " +
                            "No event or image is fabricated."
                    } else {
                        "Try another search, capture filter or stage."
                    },
                )
            } else {
                filteredIncidents.forEach { entry ->
                    IncidentCard(
                        entry = entry,
                        kind = kind,
                        vm = vm,
                        onOpen = {
                            if (entry.attachmentId != null) {
                                onOpenEntry(kind, entry.id)
                            }
                        },
                        onDetails = { dialog = IntruderDialog.Details(entry) },
                        onRename = { dialog = IntruderDialog.Rename(entry) },
                        onDelete = { dialog = IntruderDialog.Delete(entry) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------------------------------------------------------- secure notes
        if (filter == IncidentFilter.ALL || filter == IncidentFilter.NOTES) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("SECURE NOTES · ${filteredNotes.size}")
            Spacer(Modifier.height(8.dp))
            if (filteredNotes.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Edit,
                    title = if (notes.isEmpty()) "No secure notes" else "No matching notes",
                    message = if (notes.isEmpty()) {
                        "Notes stored here share the Intruder Vault key and are preserved when " +
                            "incident history is cleared."
                    } else {
                        "Try another search."
                    },
                )
            } else {
                filteredNotes.forEach { entry ->
                    NoteCard(
                        entry = entry,
                        onOpen = { onOpenEntry(kind, entry.id) },
                        onRename = { dialog = IntruderDialog.Rename(entry) },
                        onDelete = { dialog = IntruderDialog.Delete(entry) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------------------------------------------------------- management
        Spacer(Modifier.height(18.dp))
        SectionLabel("VAULT MANAGEMENT")
        Spacer(Modifier.height(8.dp))
        NexoCard {
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "Change ${kind.title} PIN",
                subtitle = "Requires the current vault PIN",
                onClick = { showPinChange = !showPinChange },
            )
            if (showPinChange) {
                VaultPinChangeForm(kind, vm)
            }
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Delete,
                title = "Clear incident history",
                subtitle = "Deletes every incident and encrypted capture; keeps secure notes",
                onClick = { dialog = IntruderDialog.ClearHistory },
            )
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Import diagnostics",
                subtitle = "${vault.diagnostics.size} safe attempts recorded in memory",
                onClick = { dialog = IntruderDialog.Diagnostics },
            )
            HorizontalDivider()
            SettingsRow(
                icon = Icons.Filled.Delete,
                title = "Delete ${kind.title}",
                subtitle = "Removes the PIN, index, incidents and every encrypted attachment",
                onClick = { dialog = IntruderDialog.DeleteVault },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    when (val current = dialog) {
        is IntruderDialog.Details -> IncidentDetailsDialog(
            entry = current.entry,
            onOpen = {
                if (current.entry.attachmentId != null) {
                    onOpenEntry(kind, current.entry.id)
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is IntruderDialog.Rename -> RenameDialog(
            title = if (current.entry.type == VaultEntryType.CAPTURE) {
                "Rename capture record"
            } else {
                "Rename note"
            },
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

        is IntruderDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = {
                Text(
                    if (current.entry.type == VaultEntryType.CAPTURE) {
                        "Delete incident?"
                    } else {
                        "Delete secure note?"
                    },
                )
            },
            text = {
                Text(
                    "\"${current.entry.title}\" and its encrypted attachment will be permanently removed from ${kind.title}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.deleteVaultEntry(current.entry.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        IntruderDialog.ClearHistory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear incident history?") },
            text = {
                Text(
                    "Every incident record and its encrypted capture will be deleted. " +
                        "Secure notes are preserved. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.clearIntruderHistory { failure ->
                            if (failure != null) vm.notify(failure, error = true)
                        }
                    },
                ) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        IntruderDialog.DeleteVault -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${kind.title}?") },
            text = {
                Text(
                    "This deletes the vault PIN, the encrypted index, every incident and all " +
                        "attachments. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        vm.deleteVault { failure ->
                            if (failure != null) vm.notify(failure, error = true)
                        }
                    },
                ) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )

        IntruderDialog.Diagnostics -> ImportDiagnosticsDialog(
            diagnostics = vault.diagnostics,
            onClear = vm::clearImportDiagnostics,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

// ---------------------------------------------------------------------------- shared UI

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageSummaryCard(
    encryptedBytes: Long,
    logicalBytes: Long,
    fileCount: Int,
    noteCount: Int,
    availableBytes: Long?,
    failedImports: Int,
    onShowAttention: () -> Unit,
) {
    NexoCard {
        SectionLabel("STORAGE SUMMARY")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Encrypted", formatBytes(encryptedBytes), Modifier.weight(1f))
            Metric("Files", fileCount.toString(), Modifier.weight(1f))
            Metric("Notes", noteCount.toString(), Modifier.weight(1f))
            Metric("Available", availableBytes?.let(::formatBytes) ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (logicalBytes > 0L) {
            Text(
                "Plaintext size of stored files: ${formatBytes(logicalBytes)}. " +
                    "Encrypted data includes the authenticated index and every chunk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        val total = encryptedBytes + (availableBytes ?: 0L)
        if (total > 0L && availableBytes != null) {
            LinearProgressIndicator(
                progress = { (encryptedBytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatBytes(encryptedBytes)} used by this vault on the shared device volume.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (failedImports > 0) {
            Spacer(Modifier.height(8.dp))
            InfoBanner(
                "$failedImports interrupted or failed ${plural(failedImports, "import")} kept honestly as FAILED. " +
                    "Delete the row and import the file again.",
                BannerTone.WARNING,
            )
            TextButton(onClick = onShowAttention) { Text("Show failed imports") }
        }
    }
}

@Composable
private fun ImportProgressCard(vault: VaultUiState, onCancel: () -> Unit) {
    val total = vault.importTotalBytes
    NexoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    vault.importingName?.let { "Encrypting $it" } ?: "Preparing import",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (vault.importCount > 0) {
                            append("File ${vault.importIndex.coerceAtLeast(1)} of ${vault.importCount} · ")
                        }
                        append(vault.importStage ?: "Working")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(6.dp))
        if (total != null && total > 0L) {
            LinearProgressIndicator(
                progress = { (vault.importedBytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (total != null && total > 0L) {
                "${formatBytes(vault.importedBytes)} of ${formatBytes(total)}"
            } else {
                "${formatBytes(vault.importedBytes)} encrypted so far · size unknown"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SortMenu(sort: VaultSort, onSelect: (VaultSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text("Sort: ${sort.label}") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VaultSort.entries.forEach { candidate ->
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
private fun StageMenu(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (current == "ALL") "Stage: All" else "Stage: ${IntruderIncidents.failureStageLabel(current)}"
    Box {
        FilterChip(
            selected = current != "ALL",
            onClick = { expanded = true },
            label = { Text(label) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            STAGE_OPTIONS.forEach { (value, text) ->
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

private val STAGE_OPTIONS = listOf(
    "ALL" to "All stages",
    "MAIN_PIN" to "Main PIN",
    "VOLUME_PATTERN" to "Volume pattern",
    "BIOMETRIC" to "Biometric",
    "STAGE_TIMEOUT" to "Stage timeout",
    "RECOVERY" to "Recovery",
    "AUTHENTICATION" to "Authentication",
)

@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onDelete: () -> Unit) {
    NexoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Cancel") }
            TextButton(onClick = onDelete, enabled = count > 0) { Text("Delete") }
        }
    }
}

@Composable
private fun FileGridCard(
    kind: VaultKind,
    file: VaultFile,
    vm: NexoViewModel,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    NexoCard(modifier = modifier.clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth()) {
            VaultThumbnail(
                kind = kind,
                file = file,
                vm = vm,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f),
            )
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onOpen() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "File actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Info") },
                        onClick = {
                            menu = false
                            onInfo()
                        },
                    )
                    if (file.status == VaultFileStatus.COMPLETE) {
                        DropdownMenuItem(
                            text = { Text("Verify integrity") },
                            onClick = {
                                menu = false
                                onVerify()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            file.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${formatBytes(file.sizeBytes)} · ${formatDate(file.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (file.status != VaultFileStatus.COMPLETE) {
            Spacer(Modifier.height(6.dp))
            StatusPill(
                if (file.status == VaultFileStatus.IMPORTING) "INTERRUPTED" else "FAILED",
                PillTone.DANGER,
            )
        }
    }
}

@Composable
private fun FileListCard(
    kind: VaultKind,
    file: VaultFile,
    vm: NexoViewModel,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    NexoCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultThumbnail(
                kind = kind,
                file = file,
                vm = vm,
                modifier = Modifier.size(58.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatBytes(file.sizeBytes)} · ${formatDate(file.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (file.status != VaultFileStatus.COMPLETE) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        if (file.status == VaultFileStatus.IMPORTING) "INTERRUPTED" else "FAILED",
                        PillTone.DANGER,
                    )
                }
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onOpen() })
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "File actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            enabled = file.status == VaultFileStatus.COMPLETE,
                            onClick = {
                                menu = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Info") },
                            onClick = {
                                menu = false
                                onInfo()
                            },
                        )
                        if (file.status == VaultFileStatus.COMPLETE) {
                            DropdownMenuItem(
                                text = { Text("Verify integrity") },
                                onClick = {
                                    menu = false
                                    onVerify()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultThumbnail(
    kind: VaultKind,
    file: VaultFile,
    vm: NexoViewModel,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        file.id,
        file.modifiedAt,
        file.status,
    ) {
        value = if (file.status == VaultFileStatus.COMPLETE && isImageMime(file.mimeType)) {
            vm.loadVaultThumbnail(kind, file.id)
        } else {
            null
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = file.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                fileBadge(file),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NoteCard(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    NexoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("NOTE", PillTone.ACCENT)
            Spacer(Modifier.weight(1f))
            Text(
                formatDate(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (entry.body.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                entry.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onRename) { Text("Rename") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun IncidentCard(
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
    NexoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                IntruderIncidents.failureStageLabel(stage).uppercase(),
                PillTone.WARNING,
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(
                IntruderIncidents.captureStatusLabel(status),
                captureTone(status),
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatTimestamp(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.attachmentId != null) {
                IncidentThumbnail(kind = kind, entry = entry, vm = vm)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "Protected app" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (target != null) {
                    Text(
                        target,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    IntruderIncidents.reasonText(entry.body).ifBlank { "No reason recorded." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Capture: ${IntruderIncidents.captureStatusLabel(status)}" +
                (entry.attachmentId?.let { " · ref ${it.take(8)}…" } ?: " · no encrypted image"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDetails) { Text("Details") }
            if (entry.attachmentId != null) {
                TextButton(onClick = onOpen) { Text("Open capture") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun IncidentThumbnail(kind: VaultKind, entry: VaultEntry, vm: NexoViewModel) {
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun IncidentDetailsDialog(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stage = IntruderIncidents.displayStage(entry)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Incident details") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DetailRow("Timestamp", formatTimestamp(entry.createdAt))
                DetailRow("Target app", entry.title.ifBlank { "Protected app" })
                DetailRow(
                    "Package",
                    IntruderIncidents.displayPackage(entry) ?: "Not recorded",
                )
                DetailRow("Failure stage", IntruderIncidents.failureStageLabel(stage))
                DetailRow(
                    "Reason",
                    IntruderIncidents.reasonText(entry.body).ifBlank { "Not recorded" },
                )
                DetailRow(
                    "Capture status",
                    IntruderIncidents.captureStatusLabel(entry.captureStatus),
                )
                DetailRow(
                    "Capture reference",
                    entry.attachmentId ?: "No encrypted capture",
                )
                DetailRow("Record ID", entry.id)
            }
        },
        confirmButton = {
            if (entry.attachmentId != null) {
                TextButton(onClick = onOpen) { Text("Open capture") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (entry.attachmentId != null) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun FileInfoDialog(file: VaultFile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypted file details") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DetailRow("Name", file.displayName)
                DetailRow("Type", file.mimeType)
                DetailRow("Status", file.status.name)
                DetailRow("Plaintext size", formatBytes(file.sizeBytes))
                DetailRow("Created", formatTimestamp(file.createdAt))
                DetailRow("Modified", formatTimestamp(file.modifiedAt))
                DetailRow("Chunks", "${file.chunkCount} × 64 KiB AES-256-GCM")
                DetailRow("Format", "v${file.encryptionVersion}")
                DetailRow(
                    "SHA-256",
                    file.integritySha256.ifBlank { "Not recorded (import did not complete)" },
                )
                file.errorMessage?.let { DetailRow("Error", it) }
                DetailRow("Record ID", file.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AddNoteDialog(kind: VaultKind, vm: NexoViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New encrypted note") },
        text = {
            Column {
                error?.let {
                    InfoBanner(it, BannerTone.DANGER)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Secret text") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (title.isNotBlank() || body.isNotBlank()),
                onClick = {
                    busy = true
                    vm.addVaultNote(title, body) { failure ->
                        busy = false
                        if (failure == null) {
                            onDismiss()
                        } else {
                            error = failure
                        }
                    }
                },
            ) { Text(if (busy) "Encrypting…" else "Encrypt into ${kind.title}") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ImportDiagnosticsDialog(
    diagnostics: List<com.vinay.nexolock.vault.ImportDiagnostic>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    SigDialog(
        title = "Import diagnostics",
        message = "Only safe metadata is kept in memory: no file content, keys, PINs, " +
            "full URIs or query parameters.",
        confirmLabel = "Clear history",
        confirmEnabled = diagnostics.isNotEmpty(),
        onConfirm = {
            onClear()
            onDismiss()
        },
        onDismiss = onDismiss,
        dismissLabel = "Close",
        icon = Icons.Filled.Info,
    ) {
        Column(
            Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (diagnostics.isEmpty()) {
                Text(
                    "No imports have been attempted in this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
            } else {
                diagnostics.forEach { diagnostic ->
                    Column(Modifier.padding(vertical = NexoSpace.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SigPill(
                                diagnostic.status,
                                when (diagnostic.status) {
                                    "SUCCESS" -> SigTone.SUCCESS
                                    "FAILED" -> SigTone.DANGER
                                    "CANCELLED" -> SigTone.WARNING
                                    else -> SigTone.NEUTRAL
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatTimestamp(diagnostic.startedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoPalette.TextMuted,
                            )
                        }
                        Spacer(Modifier.height(NexoSpace.xs))
                        Text(
                            diagnostic.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = NexoPalette.TextPrimary,
                        )
                        Text(
                            buildString {
                                append(diagnostic.mimeType)
                                append(" · ")
                                append(
                                    diagnostic.reportedSize?.let(::formatBytes) ?: "size unknown",
                                )
                                diagnostic.scheme?.let { append(" · $it") }
                                diagnostic.authority?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoPalette.TextMuted,
                        )
                        diagnostic.stages.forEach { event ->
                            Text(
                                "• ${event.stage}" + (event.detail?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoPalette.TextSecondary,
                            )
                        }
                        diagnostic.errorClass?.let {
                            Text(
                                "Error: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoPalette.Error,
                            )
                        }
                        diagnostic.errorMessage?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoPalette.Error,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(NexoPalette.Border),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VaultPinChangeForm(kind: VaultKind, vm: NexoViewModel) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.padding(vertical = NexoSpace.sm)) {
        error?.let {
            SigBanner(text = it, tone = SigTone.DANGER)
            Spacer(Modifier.height(NexoSpace.sm))
        }
        SigTextField(
            value = current,
            onValueChange = { current = it.filter(Char::isDigit).take(8) },
            label = "Current ${kind.title} PIN",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(NexoSpace.sm))
        SigTextField(
            value = next,
            onValueChange = { next = it.filter(Char::isDigit).take(8) },
            label = "New PIN",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(NexoSpace.sm))
        SigTextField(
            value = repeated,
            onValueChange = { repeated = it.filter(Char::isDigit).take(8) },
            label = "Repeat new PIN",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(NexoSpace.md))
        SigPrimaryButton(
            text = "Change vault PIN",
            enabled = AuthPolicy.validPin(next) && next == repeated && current.isNotEmpty(),
            onClick = {
                vm.changeVaultPin(current, next) { failure ->
                    if (failure == null) {
                        current = ""
                        next = ""
                        repeated = ""
                        error = null
                    } else {
                        error = failure
                    }
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(120) },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = value.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isNotEmpty() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Use a name without slashes or control characters.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(clean) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------- helpers

private fun VaultFile.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return displayName.contains(needle, ignoreCase = true) ||
        mimeType.contains(needle, ignoreCase = true)
}

private fun VaultEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    val stage = IntruderIncidents.displayStage(this)
    val target = IntruderIncidents.displayPackage(this)
    return title.contains(needle, ignoreCase = true) ||
        body.contains(needle, ignoreCase = true) ||
        IntruderIncidents.failureStageLabel(stage).contains(needle, ignoreCase = true) ||
        target?.contains(needle, ignoreCase = true) == true
}

private fun fileComparator(sort: VaultSort): Comparator<VaultFile> = when (sort) {
    VaultSort.NEWEST -> compareByDescending { it.createdAt }
    VaultSort.OLDEST -> compareBy { it.createdAt }
    VaultSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
    VaultSort.SIZE -> compareByDescending { it.sizeBytes }
}

private fun entryComparator(sort: VaultSort): Comparator<VaultEntry> = when (sort) {
    VaultSort.NEWEST -> compareByDescending { it.createdAt }
    VaultSort.OLDEST -> compareBy { it.createdAt }
    VaultSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    VaultSort.SIZE -> compareByDescending { it.body.length }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private fun plural(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"

private fun captureTone(status: CaptureStatus?): PillTone = when (status) {
    CaptureStatus.CAPTURED -> PillTone.SUCCESS
    CaptureStatus.PERMISSION_MISSING,
    CaptureStatus.CAMERA_UNAVAILABLE,
    CaptureStatus.CAMERA_BUSY,
    CaptureStatus.FAILED,
    -> PillTone.DANGER

    CaptureStatus.DISABLED, null -> PillTone.NEUTRAL
}

private fun fileBadge(file: VaultFile): String {
    val extension = file.displayName.substringAfterLast('.', "").uppercase(Locale.US)
    if (extension.isNotBlank() && extension.length <= 4) return extension
    return when {
        file.mimeType.startsWith("image/") -> "IMG"
        file.mimeType.startsWith("video/") -> "VID"
        file.mimeType.startsWith("audio/") -> "AUD"
        file.mimeType == "application/pdf" -> "PDF"
        file.mimeType.startsWith("text/") -> "TXT"
        else -> "FILE"
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun formatTimestamp(millis: Long): String =
    if (millis <= 0L) {
        "No incident recorded"
    } else {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))
    }
