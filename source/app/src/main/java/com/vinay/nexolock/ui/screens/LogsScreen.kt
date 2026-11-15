package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.data.LogEntry
import com.vinay.nexolock.data.LogGroup
import com.vinay.nexolock.data.LogSeverity
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigDialog
import com.vinay.nexolock.ui.components.SigDivider
import com.vinay.nexolock.ui.components.SigEmptyState
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigTimelineRow
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) {
    ALL("All"),
    UNLOCKS("Unlocks"),
    FAILURES("Failures"),
    SYSTEM("System"),
    ;

    fun matches(entry: LogEntry): Boolean = when (this) {
        ALL -> true
        UNLOCKS -> entry.type.group == LogGroup.UNLOCKS
        FAILURES -> entry.type.group == LogGroup.FAILURES
        SYSTEM -> entry.type.group == LogGroup.SYSTEM
    }
}

@Composable
fun LogsScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val formatter = remember {
        SimpleDateFormat("d MMM yyyy · HH:mm:ss", Locale.getDefault())
    }
    val entries = remember(state.logs, filter) {
        state.logs.entries.filter(filter::matches)
    }

    LaunchedEffect(Unit) { vm.refreshLogs() }

    // A clear confirmation must not survive the log becoming empty (for
    // example after another screen clears it). The dialog describes a real
    // destructive action, so it is never shown without something to delete.
    LaunchedEffect(state.logs.entries.isEmpty()) {
        if (state.logs.entries.isEmpty()) confirmClear = false
    }

    if (confirmClear) {
        SigDialog(
            title = "Clear the security log?",
            message = "The whole local audit file will be deleted. A single \"log cleared\" " +
                "event is written afterwards.",
            confirmLabel = "Clear log",
            dismissLabel = "Cancel",
            destructive = true,
            icon = Icons.Filled.Delete,
            onConfirm = {
                confirmClear = false
                vm.clearLogs()
            },
            onDismiss = { confirmClear = false },
        )
    }

    SignatureScreen(
        title = "Security activity",
        subtitle = "Encrypted, hash-chained, stored on this device.",
        eyebrow = "JOURNAL",
        onBack = onBack,
        scrollable = false,
        actions = {
            IconAction(
                icon = Icons.Filled.Refresh,
                contentDescription = "Refresh log",
                onClick = vm::refreshLogs,
            )
            IconAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Clear log",
                enabled = state.logs.entries.isNotEmpty(),
                onClick = { confirmClear = true },
            )
        },
    ) {
        if (state.logWriteError != null) {
            SigBanner(
                text = "The last journal write failed: ${state.logWriteError}. Events after " +
                    "this point may be missing.",
                tone = SigTone.DANGER,
                title = "Journal write error",
            )
            Spacer(Modifier.height(NexoSpace.sm))
        }
        if (state.logs.tamperedCount > 0 || state.logs.corruptedRecords > 0) {
            SigBanner(
                text = "Integrity warning: ${state.logs.tamperedCount} record(s) failed the " +
                    "hash chain and ${state.logs.corruptedRecords} could not be decrypted. " +
                    "The file may have been altered or damaged.",
                tone = SigTone.DANGER,
                title = "Integrity check",
            )
            Spacer(Modifier.height(NexoSpace.sm))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
        ) {
            LogFilter.entries.forEach { candidate ->
                SigChip(
                    text = candidate.label,
                    selected = filter == candidate,
                    onClick = { filter = candidate },
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${entries.size} of ${state.logs.entries.size} events",
                style = MaterialTheme.typography.labelMedium,
                color = NexoPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(NexoSpace.xs))

        if (entries.isEmpty()) {
            SigEmptyState(
                icon = NexoIcons.Clock,
                title = "No events in this filter",
                message = "Unlocks, rejected attempts, recovery use, lockouts and protection " +
                    "changes appear here.",
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(entries) { entry ->
                    LogRow(entry, formatter.format(Date(entry.timestamp)))
                    SigDivider()
                }
                item { Spacer(Modifier.height(NexoSpace.lg)) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, time: String) {
    val tone = when (entry.type.severity) {
        LogSeverity.SUCCESS -> SigTone.SUCCESS
        LogSeverity.INFO -> SigTone.INFO
        LogSeverity.WARNING -> SigTone.WARNING
        LogSeverity.DANGER -> SigTone.DANGER
    }
    val icon = when (entry.type.severity) {
        LogSeverity.SUCCESS -> NexoIcons.ShieldCheck
        LogSeverity.INFO -> Icons.Filled.Info
        LogSeverity.WARNING -> Icons.Filled.Warning
        LogSeverity.DANGER -> NexoIcons.Shield
    }
    SigTimelineRow(
        icon = icon,
        title = entry.type.title,
        message = buildString {
            if (entry.method.isNotBlank()) {
                append(entry.method)
                if (entry.message.isNotBlank()) append(" · ")
            }
            if (entry.message.isNotBlank()) append(entry.message)
        },
        meta = time,
        tone = tone,
        trailing = if (entry.tampered) {
            { SigPill("CHAIN MISMATCH", SigTone.DANGER) }
        } else {
            null
        },
    )
}
