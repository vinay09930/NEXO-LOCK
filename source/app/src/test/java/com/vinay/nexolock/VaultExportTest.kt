package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure naming rules for restoring decrypted copies through SAF. These tests
 * pin the behaviour that protects existing files in the chosen folder.
 */
class VaultExportTest {

    @Test
    fun extensionsAreLowercaseAndBounded() {
        assertEquals("jpg", VaultExport.extensionOf("photo.JPG"))
        assertEquals("gz", VaultExport.extensionOf("archive.tar.gz"))
        assertEquals("", VaultExport.extensionOf("noext"))
        assertEquals("", VaultExport.extensionOf(".hidden"))
        assertEquals("", VaultExport.extensionOf("trailing."))
        assertEquals("", VaultExport.extensionOf("name.abcdefghijklmnopq"))
    }

    @Test
    fun safeNameRemovesPathComponentsAndControlCharacters() {
        assertEquals("evil.jpg", VaultExport.safeName("../../evil.jpg"))
        assertEquals("photo.png", VaultExport.safeName("C:\\Users\\owner\\photo.png"))
        assertEquals("a_b_c_.txt", VaultExport.safeName("a:b*c?.txt"))
        assertEquals("ab", VaultExport.safeName("a\u0000b\u0007"))
        assertEquals("name", VaultExport.safeName("name."))
        assertEquals("Restored file", VaultExport.safeName(null))
        assertEquals("Restored file", VaultExport.safeName("   "))
    }

    @Test
    fun safeNameIsBoundedWithoutDroppingTheExtension() {
        val long = "x".repeat(500) + ".pdf"
        val safe = VaultExport.safeName(long)
        assertEquals(VaultExport.MAX_NAME_LENGTH, safe.length)
        assertTrue(safe.endsWith(".pdf"))
        assertFalse(safe.contains('/'))
    }

    @Test
    fun uniqueNameInsertsANumberBeforeTheExtension() {
        assertEquals(
            "photo (1).jpg",
            VaultExport.uniqueName("photo.jpg", listOf("photo.jpg")),
        )
        assertEquals(
            "photo (2).jpg",
            VaultExport.uniqueName("photo.jpg", listOf("photo.jpg", "photo (1).jpg")),
        )
        assertEquals(
            "notes (1)",
            VaultExport.uniqueName("notes", listOf("notes")),
        )
    }

    @Test
    fun uniqueNameComparisonIsCaseInsensitiveAndNeverOverwrites() {
        assertEquals(
            "Photo (1).jpg",
            VaultExport.uniqueName("Photo.JPG", listOf("photo.jpg")),
        )
        assertEquals(
            "Report (1).pdf",
            VaultExport.uniqueName("Report.PDF", listOf("report.pdf")),
        )
    }

    @Test
    fun uniqueNameStaysWithinTheLengthLimitForVeryLongStems() {
        val stem = "y".repeat(200)
        val first = VaultExport.uniqueName("$stem.pdf", emptyList())
        val second = VaultExport.uniqueName("$stem.pdf", listOf(first))
        assertTrue(first.length <= VaultExport.MAX_NAME_LENGTH)
        assertTrue(second.length <= VaultExport.MAX_NAME_LENGTH)
        assertTrue(second.endsWith(".pdf"))
        assertFalse(first.equals(second, ignoreCase = true))
    }
}
