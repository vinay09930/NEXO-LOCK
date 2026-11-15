package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultFileFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Framing rules of the V5 streaming container. Crypto itself is Keystore-backed
 * and device-only; these tests pin the versioned header, footer and AAD layout
 * that guarantee chunks cannot be reordered, truncated or moved between files.
 */
class VaultFileFormatTest {

    private val id = "3f2f6e1e-7e2a-4f2e-9d1a-0f3b6a9c5d21"

    private fun headerBytes(
        fileId: String = id,
        chunkSize: Int = VaultFileFormat.DEFAULT_CHUNK_SIZE,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { VaultFileFormat.writeHeader(it, fileId, chunkSize) }
        return bytes.toByteArray()
    }

    @Test
    fun headerRoundTripKeepsVersionChunkSizeAndId() {
        val decoded = DataInputStream(ByteArrayInputStream(headerBytes())).use {
            VaultFileFormat.readHeader(it, id)
        }
        assertEquals(VaultFileFormat.VERSION, decoded.version)
        assertEquals(VaultFileFormat.DEFAULT_CHUNK_SIZE, decoded.chunkSize)
        assertEquals(id, decoded.fileId)
    }

    @Test
    fun headerRejectsAnotherVaultRecord() {
        val bytes = headerBytes()
        assertThrows(IOException::class.java) {
            DataInputStream(ByteArrayInputStream(bytes)).use {
                VaultFileFormat.readHeader(it, "some-other-id")
            }
        }
    }

    @Test
    fun headerRejectsUnknownMagic() {
        val bytes = headerBytes()
        bytes[0] = 0
        assertThrows(IOException::class.java) {
            DataInputStream(ByteArrayInputStream(bytes)).use {
                VaultFileFormat.readHeader(it, id)
            }
        }
    }

    @Test
    fun headerRejectsFutureVersion() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(VaultFileFormat.MAGIC)
            it.writeInt(VaultFileFormat.VERSION + 1)
            it.writeInt(VaultFileFormat.DEFAULT_CHUNK_SIZE)
            it.writeInt(id.length)
            it.write(id.toByteArray())
        }
        assertThrows(IOException::class.java) {
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
                VaultFileFormat.readHeader(it, id)
            }
        }
    }

    @Test
    fun headerRejectsAbsurdChunkSize() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(VaultFileFormat.MAGIC)
            it.writeInt(VaultFileFormat.VERSION)
            it.writeInt(VaultFileFormat.MAX_CHUNK_SIZE * 8)
            it.writeInt(id.length)
            it.write(id.toByteArray())
        }
        assertThrows(IOException::class.java) {
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
                VaultFileFormat.readHeader(it, id)
            }
        }
    }

    @Test
    fun headerRejectsTruncatedInput() {
        val bytes = headerBytes().copyOf(10)
        assertThrows(IOException::class.java) {
            DataInputStream(ByteArrayInputStream(bytes)).use {
                VaultFileFormat.readHeader(it, id)
            }
        }
    }

    @Test
    fun footerRoundTrip() {
        val sha = ByteArray(32) { index -> (index * 7).toByte() }
        val payload = VaultFileFormat.encodeFooter(12L, 789_456L, sha)
        val footer = VaultFileFormat.decodeFooter(payload)
        assertEquals(12L, footer.totalChunks)
        assertEquals(789_456L, footer.totalBytes)
        assertArrayEquals(sha, footer.sha256)
    }

    @Test
    fun emptyFileHasValidFooter() {
        val digest = VaultFileFormat.digestOf(ByteArray(0))
        val footer = VaultFileFormat.decodeFooter(
            VaultFileFormat.encodeFooter(0L, 0L, digest),
        )
        assertEquals(0L, footer.totalChunks)
        assertEquals(0L, footer.totalBytes)
        assertArrayEquals(digest, footer.sha256)
    }

    @Test
    fun footerRejectsTruncationAndExtraBytes() {
        val payload = VaultFileFormat.encodeFooter(1L, 2L, ByteArray(32))
        assertThrows(IOException::class.java) {
            VaultFileFormat.decodeFooter(payload.copyOf(payload.size - 1))
        }
        assertThrows(IOException::class.java) {
            VaultFileFormat.decodeFooter(payload + byteArrayOf(0))
        }
    }

    @Test
    fun footerRejectsBadMagic() {
        val payload = VaultFileFormat.encodeFooter(1L, 2L, ByteArray(32))
        payload[0] = 0
        assertThrows(IOException::class.java) {
            VaultFileFormat.decodeFooter(payload)
        }
    }

    @Test
    fun blobLengthsAreBoundedByTheDeclaredChunkSize() {
        val chunk = VaultFileFormat.DEFAULT_CHUNK_SIZE
        val min = VaultFileFormat.IV_BYTES + VaultFileFormat.TAG_BYTES
        assertFalse(VaultFileFormat.validBlobLength(min - 1, chunk))
        assertTrue(VaultFileFormat.validBlobLength(min, chunk))
        assertTrue(VaultFileFormat.validBlobLength(chunk + min, chunk))
        assertFalse(VaultFileFormat.validBlobLength(chunk + min + 1, chunk))
    }

    @Test
    fun aadBindsVaultFileAndChunkIndex() {
        val first = VaultFileFormat.chunkAad("vault-a", id, 0)
        val second = VaultFileFormat.chunkAad("vault-a", id, 1)
        val otherVault = VaultFileFormat.chunkAad("vault-b", id, 0)
        val otherFile = VaultFileFormat.chunkAad("vault-a", "other", 0)
        assertFalse(first.contentEquals(second))
        assertFalse(first.contentEquals(otherVault))
        assertFalse(first.contentEquals(otherFile))
        assertFalse(
            VaultFileFormat.footerAad("vault-a", id)
                .contentEquals(VaultFileFormat.chunkAad("vault-a", id, 0)),
        )
    }

    @Test
    fun hexEncodingIsLowercaseAndStable() {
        assertEquals("00ff10", VaultFileFormat.hex(byteArrayOf(0, -1, 16)))
        val digest = VaultFileFormat.digestOf("NEXO".toByteArray())
        assertEquals(64, VaultFileFormat.hex(digest).length)
        assertEquals(VaultFileFormat.hex(digest), VaultFileFormat.hex(digest))
    }
}
