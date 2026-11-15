package com.vinay.nexolock.vault

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Pure framing code for the streaming vault container (`<id>.nxf.enc`).
 *
 * ```
 * header : "NXLF" | version:int | chunkSize:int | idLen:int | fileId:utf8
 * body   : repeated [ blobLen:int | blob = IV(12) || AES-GCM ciphertext+tag ]
 * footer : end=-1:int | blobLen:int | blob = encrypted FooterPayload
 * footer payload (after decrypt):
 *          "NXLF" | totalChunks:long | totalBytes:long | sha256:32 bytes
 * ```
 *
 * AAD for each chunk is `${kind.aad}|file|$id|chunk|$index`, so chunks cannot
 * be reordered or moved between files/vaults. The footer is bound with
 * `${kind.aad}|file|$id|footer` and carries the whole-file digest. The header
 * is structural: a tampered id no longer matches the index, a tampered chunk
 * size breaks parsing, and any content change fails a chunk tag or the digest.
 *
 * No Android types are used here, so the framing is unit-tested on the JVM.
 */
object VaultFileFormat {

    const val MAGIC = 0x4E584C46 // "NXLF"
    const val VERSION = 1
    const val DEFAULT_CHUNK_SIZE = 64 * 1024
    const val MIN_CHUNK_SIZE = 4 * 1024
    const val MAX_CHUNK_SIZE = 1024 * 1024
    const val CHUNK_END = -1

    const val IV_BYTES = 12
    const val TAG_BYTES = 16
    private const val FOOTER_PAYLOAD_BYTES = 4 + 8 + 8 + 32
    const val MIN_FOOTER_BLOB = IV_BYTES + FOOTER_PAYLOAD_BYTES + TAG_BYTES
    const val MAX_FOOTER_BLOB = 4096
    private const val MAX_ID_BYTES = 256

    data class Header(val version: Int, val chunkSize: Int, val fileId: String)

    data class Footer(val totalChunks: Long, val totalBytes: Long, val sha256: ByteArray)

    fun writeHeader(out: DataOutputStream, fileId: String, chunkSize: Int = DEFAULT_CHUNK_SIZE) {
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) { "Invalid chunk size." }
        val id = fileId.toByteArray(Charsets.UTF_8)
        require(id.isNotEmpty() && id.size <= MAX_ID_BYTES) { "Invalid file id." }
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(chunkSize)
        out.writeInt(id.size)
        out.write(id)
    }

    /** Validates the container header and that it belongs to [expectedFileId]. */
    fun readHeader(input: DataInputStream, expectedFileId: String): Header {
        if (input.readInt() != MAGIC) throw IOException("Not a NEXO vault container.")
        val version = input.readInt()
        if (version != VERSION) throw IOException("Unsupported vault container version $version.")
        val chunkSize = input.readInt()
        if (chunkSize !in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) {
            throw IOException("Invalid vault chunk size.")
        }
        val idLength = input.readInt()
        if (idLength !in 1..MAX_ID_BYTES) throw IOException("Invalid vault file id length.")
        val idBytes = ByteArray(idLength)
        input.readFully(idBytes)
        val fileId = String(idBytes, Charsets.UTF_8)
        if (fileId != expectedFileId) {
            throw IOException("The container belongs to a different vault record.")
        }
        return Header(version, chunkSize, fileId)
    }

    fun encodeFooter(totalChunks: Long, totalBytes: Long, sha256: ByteArray): ByteArray {
        require(sha256.size == 32) { "SHA-256 must be 32 bytes." }
        val buffer = java.io.ByteArrayOutputStream(FOOTER_PAYLOAD_BYTES)
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeLong(totalChunks)
            out.writeLong(totalBytes)
            out.write(sha256)
        }
        return buffer.toByteArray()
    }

    fun decodeFooter(payload: ByteArray): Footer {
        if (payload.size != FOOTER_PAYLOAD_BYTES) throw IOException("Invalid vault footer length.")
        val input = DataInputStream(payload.inputStream())
        if (input.readInt() != MAGIC) throw IOException("Invalid vault footer.")
        val chunks = input.readLong()
        val bytes = input.readLong()
        val digest = ByteArray(32)
        input.readFully(digest)
        if (chunks < 0 || bytes < 0) throw IOException("Invalid vault footer values.")
        return Footer(chunks, bytes, digest)
    }

    /** A blob is always IV + ciphertext(+tag); an empty chunk is never written. */
    fun validBlobLength(length: Int, chunkSize: Int): Boolean =
        length in (IV_BYTES + TAG_BYTES)..(chunkSize + IV_BYTES + TAG_BYTES)

    fun chunkAad(kindAad: String, fileId: String, index: Int): ByteArray =
        "$kindAad|file|$fileId|chunk|$index".toByteArray(Charsets.UTF_8)

    fun footerAad(kindAad: String, fileId: String): ByteArray =
        "$kindAad|file|$fileId|footer".toByteArray(Charsets.UTF_8)

    fun digestOf(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hex(bytes: ByteArray): String {
        val table = "0123456789abcdef"
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = table[value ushr 4]
            chars[index * 2 + 1] = table[value and 0x0F]
        }
        return String(chars)
    }
}
