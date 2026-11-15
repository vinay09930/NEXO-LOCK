package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileStatus
import com.vinay.nexolock.vault.VaultImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the V1.1.2 defect: a fully written import was committed
 * with status IMPORTING, so the next index read normalised it to FAILED and the
 * file could never be opened. These tests pin the commit status and the honest
 * legacy-repair rules.
 */
class VaultImportPolicyTest {

    private val digest = "a".repeat(64)

    private fun row(
        status: VaultFileStatus = VaultFileStatus.IMPORTING,
        sha: String = "",
        error: String? = null,
        size: Long = 0L,
        name: String = "photo.jpg",
    ) = VaultFile(
        id = "file-1",
        displayName = name,
        mimeType = "image/jpeg",
        sizeBytes = size,
        chunkCount = 0,
        createdAt = 1_000L,
        modifiedAt = 1_000L,
        encryptionVersion = 2,
        integritySha256 = sha,
        status = status,
        storageName = "file-1.nxf.enc",
        errorMessage = error,
    )

    @Test
    fun completedCommitFlipsImportingToComplete() {
        val completed = VaultImportPolicy.completed(
            seed = row(),
            totalBytes = 123_456L,
            chunks = 2,
            sha256 = digest,
            now = 2_000L,
        )
        assertEquals(VaultFileStatus.COMPLETE, completed.status)
        assertEquals(123_456L, completed.sizeBytes)
        assertEquals(2, completed.chunkCount)
        assertEquals(digest, completed.integritySha256)
        assertEquals(2_000L, completed.modifiedAt)
        assertNull(completed.errorMessage)
    }

    @Test
    fun completedCommitAcceptsAZeroByteFile() {
        val completed = VaultImportPolicy.completed(
            seed = row(),
            totalBytes = 0L,
            chunks = 0,
            sha256 = digest,
            now = 2_000L,
        )
        assertEquals(VaultFileStatus.COMPLETE, completed.status)
        assertEquals(0L, completed.sizeBytes)
        assertEquals(0, completed.chunkCount)
    }

    @Test
    fun completeRowsAreNeverDowngraded() {
        val existing = row(status = VaultFileStatus.COMPLETE, sha = digest)
        val classified = VaultImportPolicy.classifyLegacy(existing, ciphertextExists = false)
        assertEquals(VaultFileStatus.COMPLETE, classified.status)
        assertEquals(digest, classified.integritySha256)
    }

    @Test
    fun v112SuccessfulImportIsRepairedWhenDigestAndCiphertextExist() {
        // V1.1.2 persisted the row as IMPORTING with the final digest and a
        // finalized container. This is exactly the defect being repaired.
        val legacy = row(status = VaultFileStatus.IMPORTING, sha = digest)
        val repaired = VaultImportPolicy.classifyLegacy(legacy, ciphertextExists = true)
        assertEquals(VaultFileStatus.COMPLETE, repaired.status)
        assertNull(repaired.errorMessage)
        assertEquals(digest, repaired.integritySha256)
    }

    @Test
    fun interruptedImportWithoutCiphertextStaysFailed() {
        val legacy = row(status = VaultFileStatus.IMPORTING, sha = digest)
        val failed = VaultImportPolicy.classifyLegacy(legacy, ciphertextExists = false)
        assertEquals(VaultFileStatus.FAILED, failed.status)
        assertTrue(failed.errorMessage!!.contains("interrupted", ignoreCase = true))
    }

    @Test
    fun rowWithoutDigestStaysFailedEvenWhenACiphertextExists() {
        val legacy = row(status = VaultFileStatus.IMPORTING, sha = "")
        val failed = VaultImportPolicy.classifyLegacy(legacy, ciphertextExists = true)
        assertEquals(VaultFileStatus.FAILED, failed.status)
        assertTrue(failed.errorMessage!!.contains("interrupted", ignoreCase = true))
    }

    @Test
    fun existingFailureMessageIsPreserved() {
        val legacy = row(
            status = VaultFileStatus.FAILED,
            sha = "",
            error = "Access to the selected file was denied.",
        )
        val failed = VaultImportPolicy.classifyLegacy(legacy, ciphertextExists = false)
        assertEquals(VaultFileStatus.FAILED, failed.status)
        assertEquals("Access to the selected file was denied.", failed.errorMessage)
    }

    @Test
    fun duplicateOfMatchesCompleteRowWithSameSizeAndDigest() {
        val existing = row(status = VaultFileStatus.COMPLETE, sha = digest, size = 4_096L)
        val match = VaultImportPolicy.duplicateOf(
            files = listOf(existing),
            sizeBytes = 4_096L,
            sha256 = digest.uppercase(),
        )
        assertEquals(existing, match)
    }

    @Test
    fun duplicateOfIgnoresUnfinishedRows() {
        val importing = row(status = VaultFileStatus.IMPORTING, sha = digest, size = 4_096L)
        val failed = row(status = VaultFileStatus.FAILED, sha = digest, size = 4_096L)
        assertNull(
            VaultImportPolicy.duplicateOf(
                files = listOf(importing, failed),
                sizeBytes = 4_096L,
                sha256 = digest,
            ),
        )
    }

    @Test
    fun duplicateOfIgnoresBlankDigestsAndDifferentContent() {
        val legacy = row(status = VaultFileStatus.COMPLETE, sha = "", size = 4_096L)
        val otherSize = row(status = VaultFileStatus.COMPLETE, sha = digest, size = 10L)
        assertNull(
            VaultImportPolicy.duplicateOf(
                files = listOf(legacy),
                sizeBytes = 4_096L,
                sha256 = digest,
            ),
        )
        assertNull(
            VaultImportPolicy.duplicateOf(
                files = listOf(otherSize),
                sizeBytes = 4_096L,
                sha256 = digest,
            ),
        )
        assertNull(
            VaultImportPolicy.duplicateOf(
                files = listOf(row(status = VaultFileStatus.COMPLETE, sha = digest, size = 4_096L)),
                sizeBytes = 4_096L,
                sha256 = "b".repeat(64),
            ),
        )
        assertNull(
            VaultImportPolicy.duplicateOf(
                files = listOf(row(status = VaultFileStatus.COMPLETE, sha = digest, size = 4_096L)),
                sizeBytes = 4_096L,
                sha256 = "   ",
            ),
        )
    }
}
