package com.vinay.nexolock.vault

import com.vinay.nexolock.data.LogEntry
import com.vinay.nexolock.data.LogType

/**
 * Read-only projection of the shared, encrypted security journal onto one
 * vault. The Activity tab filters by explicit event types instead of guessing
 * from free text, so a Personal Vault event can never appear in the Intruder
 * Vault and vice versa.
 */
object VaultActivity {

    fun typesFor(kind: VaultKind): Set<LogType> = when (kind) {
        VaultKind.PERSONAL -> PERSONAL_TYPES
        VaultKind.INTRUDER -> INTRUDER_TYPES
    }

    fun events(
        log: List<LogEntry>,
        kind: VaultKind,
        query: String = "",
    ): List<LogEntry> {
        val types = typesFor(kind)
        val needle = query.trim()
        return log.asSequence()
            .filter { it.type in types }
            .filter { entry ->
                needle.isEmpty() ||
                    entry.message.contains(needle, ignoreCase = true) ||
                    entry.method.contains(needle, ignoreCase = true) ||
                    entry.type.title.contains(needle, ignoreCase = true)
            }
            .sortedByDescending { it.timestamp }
            .toList()
    }

    private val PERSONAL_TYPES: Set<LogType> = setOf(
        LogType.VAULT_CREATED,
        LogType.VAULT_UNLOCKED,
        LogType.VAULT_LOCKED,
        LogType.VAULT_ENTRY_ADDED,
        LogType.VAULT_ENTRY_DELETED,
        LogType.VAULT_PIN_CHANGED,
        LogType.VAULT_DELETED,
        LogType.VAULT_CORRUPTED,
        LogType.VAULT_FILE_IMPORTED,
        LogType.VAULT_FILE_IMPORT_FAILED,
        LogType.VAULT_FILE_VERIFIED,
        LogType.VAULT_FILE_DELETED,
        LogType.VAULT_FILE_OPENED,
        LogType.VAULT_FILE_RENAMED,
        LogType.VAULT_FILE_EXPORTED,
        LogType.VAULT_EXPORT_FAILED,
        LogType.VAULT_EXPORT_CANCELLED,
        LogType.VAULT_ORIGINAL_DELETED,
        LogType.VAULT_ORIGINAL_DELETE_FAILED,
        LogType.VAULT_AUTO_DELETE_CHANGED,
    )

    private val INTRUDER_TYPES: Set<LogType> = setOf(
        LogType.INTRUDER_VAULT_CREATED,
        LogType.INTRUDER_VAULT_UNLOCKED,
        LogType.INTRUDER_VAULT_LOCKED,
        LogType.INTRUDER_VAULT_PIN_CHANGED,
        LogType.INTRUDER_VAULT_DELETED,
        LogType.INTRUDER_VAULT_CORRUPTED,
        LogType.INTRUDER_ENTRY_ADDED,
        LogType.INTRUDER_ENTRY_DELETED,
        LogType.INTRUDER_ENTRY_OPENED,
        LogType.INTRUDER_CAPTURE,
        LogType.INTRUDER_CAPTURE_FAILED,
        LogType.INTRUDER_HISTORY_CLEARED,
        LogType.CAPTURE_SETTING_CHANGED,
    )
}
