package com.vinay.nexolock

import com.vinay.nexolock.vault.OriginalDeletionResult
import com.vinay.nexolock.vault.OriginalFileDeletionPolicy
import com.vinay.nexolock.vault.VaultDeletionReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opt-in delete-originals flow must never run without a committed,
 * independently verified vault copy and a content:// grant.
 */
class OriginalFileDeletionPolicyTest {

    @Test
    fun deletionRequiresEveryCondition() {
        assertTrue(
            OriginalFileDeletionPolicy.shouldDelete(
                enabled = true,
                importSucceeded = true,
                verified = true,
                hasContentUri = true,
            ),
        )
    }

    @Test
    fun anyMissingConditionBlocksDeletion() {
        assertFalse(
            OriginalFileDeletionPolicy.shouldDelete(
                enabled = false,
                importSucceeded = true,
                verified = true,
                hasContentUri = true,
            ),
        )
        assertFalse(
            OriginalFileDeletionPolicy.shouldDelete(
                enabled = true,
                importSucceeded = false,
                verified = true,
                hasContentUri = true,
            ),
        )
        assertFalse(
            OriginalFileDeletionPolicy.shouldDelete(
                enabled = true,
                importSucceeded = true,
                verified = false,
                hasContentUri = true,
            ),
        )
        assertFalse(
            OriginalFileDeletionPolicy.shouldDelete(
                enabled = true,
                importSucceeded = true,
                verified = true,
                hasContentUri = false,
            ),
        )
    }

    @Test
    fun reportCountsHonestOutcomes() {
        var report = VaultDeletionReport(requested = 1)
            .record(OriginalDeletionResult.Deleted("document provider"), "a.jpg")
        report = report.copy(requested = report.requested + 1)
            .record(OriginalDeletionResult.Failed("provider refused"), "b.pdf")
        report = report.copy(requested = report.requested + 1)
            .record(OriginalDeletionResult.Unsupported("no grant"), "c.bin")
        report = report.copy(requested = report.requested + 1)
            .record(OriginalDeletionResult.Failed("waiting"), "d.txt")
        assertEquals(1, report.deleted)
        assertEquals(2, report.failed)
        assertEquals(1, report.unsupported)
        assertTrue(report.summary().contains("1 deleted"))
        assertTrue(report.summary().contains("2 failed"))
        assertTrue(report.summary().contains("1 not supported"))
    }

    @Test
    fun resolvingConsentReplacesThePendingCount() {
        val pending = VaultDeletionReport(requested = 1, needsConsent = 1)
            .record(OriginalDeletionResult.Failed("x"), "a.jpg")
            .copy(needsConsent = 1)
        val resolved = pending.resolveConsent(
            OriginalDeletionResult.Deleted("system confirmation"),
            "a.jpg",
        )
        assertEquals(0, resolved.needsConsent)
        assertEquals(1, resolved.deleted)
    }

    @Test
    fun reportNotesAreBounded() {
        var report = VaultDeletionReport()
        repeat(60) { index ->
            report = report.copy(requested = report.requested + 1)
                .record(OriginalDeletionResult.Failed("failure $index"), "file-$index")
        }
        assertTrue(report.notes.size <= 40)
        assertTrue(report.summary().contains("60 failed"))
    }
}
