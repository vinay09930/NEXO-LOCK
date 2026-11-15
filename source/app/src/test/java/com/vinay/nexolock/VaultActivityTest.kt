package com.vinay.nexolock

import com.vinay.nexolock.data.LogEntry
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.vault.VaultActivity
import com.vinay.nexolock.vault.VaultKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Activity-tab filtering. A Personal Vault event must never appear in the
 * Intruder Vault and vice versa, and free-text search must not leak unrelated
 * journal entries into either list.
 */
class VaultActivityTest {

    private fun entry(
        timestamp: Long,
        type: LogType,
        message: String,
        method: String = "",
    ) = LogEntry(
        timestamp = timestamp,
        type = type,
        method = method,
        message = message,
    )

    private val journal = listOf(
        entry(1L, LogType.SETUP_COMPLETED, "Security setup completed"),
        entry(2L, LogType.VAULT_UNLOCKED, "Personal Vault unlocked", "PIN"),
        entry(3L, LogType.VAULT_FILE_IMPORTED, "Personal Vault: photo.jpg encrypted"),
        entry(4L, LogType.VAULT_FILE_EXPORTED, "Personal Vault: photo.jpg restored"),
        entry(5L, LogType.VAULT_ORIGINAL_DELETED, "photo.jpg: original deleted"),
        entry(6L, LogType.INTRUDER_VAULT_UNLOCKED, "Intruder Vault unlocked", "PIN"),
        entry(7L, LogType.INTRUDER_CAPTURE, "Intruder capture saved", "front camera"),
        entry(8L, LogType.INTRUDER_HISTORY_CLEARED, "Intruder history cleared"),
        entry(9L, LogType.INTRUDER_ENTRY_OPENED, "Intruder capture opened"),
        entry(10L, LogType.VAULT_FILE_OPENED, "Personal Vault: file opened"),
    )

    @Test
    fun personalActivityContainsOnlyPersonalEventsNewestFirst() {
        val events = VaultActivity.events(journal, VaultKind.PERSONAL)
        assertEquals(
            listOf(10L, 5L, 4L, 3L, 2L),
            events.map { it.timestamp },
        )
        assertTrue(events.none { it.type == LogType.INTRUDER_CAPTURE })
        assertTrue(events.none { it.type == LogType.INTRUDER_HISTORY_CLEARED })
        assertTrue(events.none { it.type == LogType.INTRUDER_ENTRY_OPENED })
    }

    @Test
    fun intruderActivityContainsOnlyIntruderEvents() {
        val events = VaultActivity.events(journal, VaultKind.INTRUDER)
        assertEquals(
            listOf(9L, 8L, 7L, 6L),
            events.map { it.timestamp },
        )
        assertTrue(events.none { it.type == LogType.VAULT_FILE_EXPORTED })
        assertTrue(events.none { it.type == LogType.VAULT_FILE_OPENED })
        assertTrue(events.none { it.type == LogType.VAULT_ORIGINAL_DELETED })
    }

    @Test
    fun querySearchesMessageMethodAndTypeTitle() {
        val byMessage = VaultActivity.events(journal, VaultKind.PERSONAL, "photo.jpg")
        assertEquals(listOf(5L, 4L, 3L), byMessage.map { it.timestamp })

        val byMethod = VaultActivity.events(journal, VaultKind.PERSONAL, "PIN")
        assertEquals(listOf(2L), byMethod.map { it.timestamp })

        val byTitle = VaultActivity.events(journal, VaultKind.INTRUDER, "capture")
        assertEquals(listOf(9L, 7L), byTitle.map { it.timestamp })
    }

    @Test
    fun unrelatedJournalEntriesAreNeverShownInEitherVault() {
        assertTrue(
            VaultActivity.events(journal, VaultKind.PERSONAL)
                .none { it.type == LogType.SETUP_COMPLETED },
        )
        assertTrue(
            VaultActivity.events(journal, VaultKind.INTRUDER)
                .none { it.type == LogType.SETUP_COMPLETED },
        )
        assertTrue(VaultActivity.events(emptyList(), VaultKind.PERSONAL).isEmpty())
    }
}
