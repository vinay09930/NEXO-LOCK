package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileCategory
import com.vinay.nexolock.vault.VaultFileStatus
import com.vinay.nexolock.vault.category
import com.vinay.nexolock.vault.isDocumentMime
import com.vinay.nexolock.vault.isImageMime
import com.vinay.nexolock.vault.isPreviewableMime
import com.vinay.nexolock.vault.isVideoMime
import com.vinay.nexolock.vault.sanitizeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFileTypesTest {

    private fun file(name: String, mime: String) = VaultFile(
        id = "id",
        displayName = name,
        mimeType = mime,
        sizeBytes = 1L,
        chunkCount = 1,
        createdAt = 1L,
        modifiedAt = 1L,
        encryptionVersion = 2,
        integritySha256 = "a".repeat(64),
        status = VaultFileStatus.COMPLETE,
        storageName = "id.nxf.enc",
    )

    @Test
    fun categoriesCoverImagesVideosDocumentsAndOtherFiles() {
        assertEquals(VaultFileCategory.PHOTOS, file("a.jpg", "image/jpeg").category())
        assertEquals(VaultFileCategory.VIDEOS, file("a.mp4", "video/mp4").category())
        assertEquals(VaultFileCategory.DOCUMENTS, file("a.pdf", "application/pdf").category())
        assertEquals(
            VaultFileCategory.DOCUMENTS,
            file("a.docx", "application/octet-stream").category(),
        )
        assertEquals(VaultFileCategory.OTHER, file("a.bin", "application/octet-stream").category())
    }

    @Test
    fun audioIsItsOwnCategoryWithADefensiveExtensionFallback() {
        assertEquals(VaultFileCategory.AUDIO, file("song.mp3", "audio/mpeg").category())
        assertEquals(VaultFileCategory.AUDIO, file("voice.m4a", "application/octet-stream").category())
        assertEquals(VaultFileCategory.AUDIO, file("clip.opus", "application/octet-stream").category())
        assertEquals(VaultFileCategory.AUDIO, file("sound.flac", "application/octet-stream").category())
        assertEquals(VaultFileCategory.OTHER, file("sound.unknownext", "application/octet-stream").category())
        assertTrue(com.vinay.nexolock.vault.isAudioMime("audio/ogg", "anything"))
        assertFalse(com.vinay.nexolock.vault.isAudioMime("video/mp4", "movie.mp4"))
    }

    @Test
    fun documentMimeMatchingIsCaseInsensitiveAndCoversOfficeFormats() {
        assertTrue(isDocumentMime("Application/PDF", "file.pdf"))
        assertTrue(isDocumentMime("text/plain", "file.txt"))
        assertTrue(isDocumentMime("application/rtf", "file.rtf"))
        assertTrue(
            isDocumentMime(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "file.docx",
            ),
        )
        assertTrue(
            isDocumentMime(
                "application/vnd.oasis.opendocument.spreadsheet",
                "file.ods",
            ),
        )
        assertFalse(isDocumentMime("application/octet-stream", "file.exe"))
        assertFalse(isDocumentMime("image/jpeg", "file.jpg"))
    }

    @Test
    fun octetStreamFallsBackToAStrictDocumentExtensionAllowList() {
        assertTrue(isDocumentMime("application/octet-stream", "report.PDF"))
        assertTrue(isDocumentMime("application/octet-stream", "notes.md"))
        assertFalse(isDocumentMime("application/octet-stream", "payload.apk"))
        assertFalse(isDocumentMime("application/octet-stream", "archive.zip"))
    }

    @Test
    fun previewAndTypeHelpersDoNotClaimUnsupportedContent() {
        assertTrue(isImageMime("image/png"))
        assertTrue(isVideoMime("video/webm"))
        assertTrue(isPreviewableMime("application/pdf"))
        assertTrue(isPreviewableMime("text/plain"))
        assertFalse(isPreviewableMime("application/zip"))
        assertFalse(isImageMime("video/mp4"))
        assertFalse(isVideoMime("image/png"))
    }

    @Test
    fun displayNamesLosePathComponentsAndControlCharacters() {
        assertEquals("photo.jpg", sanitizeDisplayName("/storage/emulated/0/photo.jpg"))
        assertEquals("photo.jpg", sanitizeDisplayName("C:\\Users\\owner\\photo.jpg"))
        assertEquals("abc", sanitizeDisplayName("a\u0000b\u0007c"))
        assertEquals("Imported file", sanitizeDisplayName("   "))
        assertEquals("Imported file", sanitizeDisplayName(null))
    }

    @Test
    fun displayNamesAreBoundedWithoutLosingTheirExtensionMeaning() {
        val long = "x".repeat(500) + ".pdf"
        val sanitized = sanitizeDisplayName(long)
        assertEquals(120, sanitized.length)
        assertTrue(sanitized.isNotBlank())
    }
}
