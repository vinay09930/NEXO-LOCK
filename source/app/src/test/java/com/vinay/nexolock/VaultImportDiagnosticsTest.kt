package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultImportDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultImportDiagnosticsTest {

    @Before
    fun setUp() = VaultImportDiagnostics.clear()

    @After
    fun tearDown() = VaultImportDiagnostics.clear()

    @Test
    fun oneAttemptRecordsStagesAndFinalStatus() {
        val diagnostic = VaultImportDiagnostics.begin(
            scheme = "content",
            authority = "com.android.providers.media.documents",
            mimeType = "image/jpeg",
            displayName = "photo.jpg",
            reportedSize = 1_024L,
            permissionGranted = true,
            now = 10L,
        )
        VaultImportDiagnostics.stage(diagnostic.id, "METADATA", "image/jpeg · 1024 bytes", now = 11L)
        VaultImportDiagnostics.finish(diagnostic.id, VaultImportDiagnostics.SUCCESS)
        val saved = VaultImportDiagnostics.snapshot().single()
        assertEquals(VaultImportDiagnostics.SUCCESS, saved.status)
        assertEquals(2, saved.stages.size)
        assertEquals("METADATA", saved.stages.last().stage)
        assertEquals("content", saved.scheme)
        assertEquals("photo.jpg", saved.displayName)
    }

    @Test
    fun historyIsBoundedToTheNewestTwentyFiveAttempts() {
        repeat(30) { index ->
            VaultImportDiagnostics.begin(
                scheme = "content",
                authority = "provider",
                mimeType = "application/octet-stream",
                displayName = "file-$index.bin",
                reportedSize = null,
                permissionGranted = null,
                now = index.toLong(),
            )
        }
        val history = VaultImportDiagnostics.snapshot()
        assertEquals(25, history.size)
        assertEquals("file-29.bin", history.first().displayName)
        assertFalse(history.any { it.displayName == "file-0.bin" })
    }

    @Test
    fun messagesRedactSecretsUrisAndLocalPaths() {
        val message = VaultImportDiagnostics.sanitizeMessage(
            "open failed pin=246810 token=abc123 content://provider/doc/1?key=secret " +
                "file:///data/user/0/app/private.txt",
        )
        assertFalse(message.contains("246810"))
        assertFalse(message.contains("abc123"))
        assertFalse(message.contains("secret"))
        assertTrue(message.contains("pin=[redacted]"))
        assertTrue(message.contains("token=[redacted]"))
        assertTrue(message.contains("content://provider/doc/1?[redacted]"))
        assertTrue(message.contains("[local path]"))
    }

    @Test
    fun messagesDropControlCharactersAndAreBounded() {
        val message = VaultImportDiagnostics.sanitizeMessage("a\u0000b\u0007c" + "x".repeat(500))
        assertFalse(message.contains('\u0000'))
        assertFalse(message.contains('\u0007'))
        assertTrue(message.length <= 200)
        assertTrue(message.startsWith("abc"))
    }

    @Test
    fun namesArePathFreeBoundedAndNeverBlank() {
        assertEquals("photo.jpg", VaultImportDiagnostics.sanitizeName("/tmp/photo.jpg"))
        assertEquals("(unnamed)", VaultImportDiagnostics.sanitizeName("   "))
        assertTrue(VaultImportDiagnostics.sanitizeName("x".repeat(500)).length <= 80)
    }

    @Test
    fun clearRemovesEveryAttempt() {
        VaultImportDiagnostics.begin(
            scheme = "content",
            authority = "provider",
            mimeType = "text/plain",
            displayName = "note.txt",
            reportedSize = 0L,
            permissionGranted = true,
        )
        assertTrue(VaultImportDiagnostics.snapshot().isNotEmpty())
        VaultImportDiagnostics.clear()
        assertTrue(VaultImportDiagnostics.snapshot().isEmpty())
    }
}
