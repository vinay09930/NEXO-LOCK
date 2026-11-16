package com.vinay.nexolock.vault

import android.content.Context
import com.vinay.nexolock.core.crypto.KeystoreCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/** Stage at which a streaming import failed; safe to show in diagnostics. */
enum class VaultImportStage {
    PICKER,
    METADATA,
    OPEN_STREAM,
    STORAGE,
    ENCRYPT,
    FINALIZE,
    DUPLICATE,
    COMMIT,
    UNKNOWN,
}

/** Thrown when the encrypted index cannot be authenticated/parsed. Never silently replaced. */
class VaultCorruptedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** User-facing streaming-import failure with a structured, non-secret stage. */
class VaultImportException(
    val stage: VaultImportStage,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Encrypted file store for one vault. The index is a versioned JSON envelope
 * encrypted with AES-256-GCM. Attachments written by V4 are separate encrypted
 * files; V5 files are chunked streaming containers (`<id>.nxf.enc`).
 *
 * An import is written to a temporary container first, fsynced and atomically
 * renamed, then the index row flips from IMPORTING to COMPLETE. If the process
 * dies mid-import the row stays IMPORTING on disk and readers surface it as an
 * interrupted (failed) import instead of pretending success.
 *
 * All disk access runs on [Dispatchers.IO] and index writes are atomic
 * (write temp file, fsync, rename) so a process death cannot leave a truncated
 * index behind.
 */
class VaultStore(
    context: Context,
    private val kind: VaultKind,
    private val cipher: KeystoreCipher,
) {

    private val directory = File(context.filesDir, "nexo_vault/${kind.id}")
    private val indexFile = File(directory, "index.v1.enc")
    private val mutex = Mutex()

    @Volatile
    private var sweptTemporaries = false

    @Volatile
    private var sweptOrphans = false

    fun exists(): Boolean = indexFile.exists()

    suspend fun read(): VaultSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            sweepTemporariesLocked()
            val snapshot = readLocked()
            persistRepairsLocked(snapshot)
            sweepOrphansLocked(snapshot)
            snapshot
        }
    }

    /** Replaces the note/capture entries, preserving the file database. */
    suspend fun write(entries: List<VaultEntry>): VaultSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            sweepTemporariesLocked()
            val current = readLocked()
            writeLocked(current.copy(entries = entries.sortedByDescending { it.createdAt }))
        }
    }

    /**
     * Atomically reads, transforms and writes the note/capture entries.
     *
     * A plain read-then-[write] pair from two coroutines can lose one of the
     * updates (for example a background intruder capture racing a foreground
     * delete). The transform runs under the same mutex as the index write, so
     * concurrent writers can never overwrite each other's committed rows.
     */
    suspend fun updateEntries(
        transform: (List<VaultEntry>) -> List<VaultEntry>,
    ): VaultSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            sweepTemporariesLocked()
            val current = readLocked()
            writeLocked(current.copy(entries = transform(current.entries)))
        }
    }

    // ------------------------------------------------------------------ legacy V4 attachments

    suspend fun saveAttachment(bytes: ByteArray, mimeType: String): String =
        withContext(Dispatchers.IO) {
            require(bytes.isNotEmpty()) { "An empty attachment cannot be stored." }
            mutex.withLock {
                directory.mkdirs()
                val id = UUID.randomUUID().toString()
                val encrypted = cipher.encrypt(bytes, attachmentAad(id))
                val target = attachmentFile(id)
                val temp = File(directory, "$id.tmp")
                temp.outputStream().use { stream ->
                    stream.write(encrypted)
                    stream.flush()
                    stream.fd.sync()
                }
                if (!temp.renameTo(target)) {
                    temp.delete()
                    throw VaultCorruptedException("The attachment could not be stored atomically.")
                }
                id
            }
        }

    suspend fun readAttachment(id: String): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = attachmentFile(id)
            if (!file.exists()) return@withLock null
            runCatching { cipher.decrypt(file.readBytes(), attachmentAad(id)) }.getOrNull()
        }
    }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { attachmentFile(id).delete(); Unit }
    }

    // ------------------------------------------------------------------ streaming V5 files

    /**
     * Transactional streaming import.
     *
     * 1. Stream the provider into `<id>.nxf.tmp` (bounded 64 KiB plaintext
     *    chunks, AES-256-GCM with a Keystore-generated unique IV per chunk).
     * 2. fsync and atomically rename the container to `<id>.nxf.enc`.
     * 3. Commit one COMPLETE row to the authenticated index.
     *
     * Nothing is visible in the vault until step 3 succeeds. If any step
     * fails, the temporary/final ciphertext is removed and no metadata row is
     * written, so the index can never claim a file that is not fully stored.
     *
     * @throws VaultImportException with a structured stage and user-facing
     * message on failure.
     */
    suspend fun importFile(
        displayName: String,
        mimeType: String,
        expectedBytes: Long?,
        openStream: () -> InputStream,
        onProgress: (Long) -> Unit = {},
    ): VaultFile = withContext(Dispatchers.IO) {
        val source = try {
            openStream()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw VaultImportException(
                VaultImportStage.OPEN_STREAM,
                VaultImportErrors.messageFor(error),
                error,
            )
        }
        source.use { input ->
            mutex.withLock {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw VaultImportException(
                        VaultImportStage.STORAGE,
                        "The encrypted vault directory could not be created.",
                    )
                }
                sweepTemporariesLocked()

                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val seed = VaultFile(
                    id = id,
                    displayName = sanitizeDisplayName(displayName),
                    mimeType = mimeType.ifBlank { "application/octet-stream" },
                    sizeBytes = expectedBytes?.coerceAtLeast(0L) ?: 0L,
                    chunkCount = 0,
                    createdAt = now,
                    modifiedAt = now,
                    encryptionVersion = VaultFileFormat.VERSION,
                    integritySha256 = "",
                    status = VaultFileStatus.IMPORTING,
                    storageName = "$id.nxf.enc",
                )
                val target = fileFor(seed)
                val temp = File(directory, "$id.nxf.tmp")
                var totalBytes = 0L
                var chunks = 0
                var sha = ByteArray(0)
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var lastReport = 0L
                    FileOutputStream(temp).use { fileOut ->
                        val out = DataOutputStream(BufferedOutputStream(fileOut, BUFFER_BYTES))
                        VaultFileFormat.writeHeader(out, id)
                        val buffer = ByteArray(VaultFileFormat.DEFAULT_CHUNK_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            digest.update(buffer, 0, read)
                            val blob = cipher.encrypt(
                                buffer.copyOf(read),
                                VaultFileFormat.chunkAad(kind.aad, id, chunks),
                            )
                            out.writeInt(blob.size)
                            out.write(blob)
                            totalBytes += read
                            chunks += 1
                            if (totalBytes - lastReport >= PROGRESS_STEP_BYTES) {
                                lastReport = totalBytes
                                runCatching { onProgress(totalBytes) }
                            }
                        }
                        sha = digest.digest()
                        val footerBlob = cipher.encrypt(
                            VaultFileFormat.encodeFooter(chunks.toLong(), totalBytes, sha),
                            VaultFileFormat.footerAad(kind.aad, id),
                        )
                        out.writeInt(VaultFileFormat.CHUNK_END)
                        out.writeInt(footerBlob.size)
                        out.write(footerBlob)
                        out.flush()
                        fileOut.fd.sync()
                    }

                    currentCoroutineContext().ensureActive()
                    val digestHex = VaultFileFormat.hex(sha)
                    val latest = try {
                        readLocked()
                    } catch (error: Exception) {
                        throw VaultImportException(
                            VaultImportStage.COMMIT,
                            "The vault index could not be read before the import was committed.",
                            error,
                        )
                    }
                    // Exact-content duplicates are rejected before the
                    // ciphertext is renamed into place, so neither an orphan
                    // container nor a duplicate metadata row can be committed.
                    val duplicate = VaultImportPolicy.duplicateOf(
                        files = latest.files,
                        sizeBytes = totalBytes,
                        sha256 = digestHex,
                    )
                    if (duplicate != null) {
                        throw VaultImportException(
                            VaultImportStage.DUPLICATE,
                            "\"${duplicate.displayName}\" already contains identical content. " +
                                "The duplicate was not imported.",
                        )
                    }

                    if (!temp.renameTo(target)) {
                        throw VaultImportException(
                            VaultImportStage.FINALIZE,
                            "The encrypted file could not be stored atomically.",
                        )
                    }

                    val complete = VaultImportPolicy.completed(
                        seed = seed,
                        totalBytes = totalBytes,
                        chunks = chunks,
                        sha256 = digestHex,
                        now = System.currentTimeMillis(),
                    )
                    try {
                        // Observe cancellation after the rename so a cancelled
                        // import cannot leave a committed ciphertext behind.
                        currentCoroutineContext().ensureActive()
                        writeLocked(
                            latest.copy(
                                files = latest.files + complete,
                                repairedImports = 0,
                            ),
                        )
                    } catch (error: CancellationException) {
                        target.delete()
                        throw error
                    } catch (error: Exception) {
                        target.delete()
                        throw VaultImportException(
                            VaultImportStage.COMMIT,
                            "The encrypted file was written but its vault record could not be committed.",
                            error,
                        )
                    }
                    complete
                } catch (error: CancellationException) {
                    temp.delete()
                    throw error
                } catch (error: Exception) {
                    temp.delete()
                    if (error is VaultImportException) throw error
                    throw VaultImportException(
                        VaultImportStage.ENCRYPT,
                        VaultImportErrors.messageFor(error),
                        error,
                    )
                }
            }
        }
    }

    /** Streams a verified decrypt into [output] (used for viewer previews). */
    suspend fun openFileTo(
        file: VaultFile,
        output: File,
        onProgress: (Long) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val temp = File(output.parentFile, "${output.name}.part")
            try {
                temp.parentFile?.mkdirs()
                temp.delete()
                val total = FileOutputStream(temp).use { stream ->
                    val written = decryptToSinkLocked(file, stream, onProgress)
                    stream.flush()
                    stream.fd.sync()
                    written
                }
                output.delete()
                if (!temp.renameTo(output)) {
                    throw VaultCorruptedException("The decrypted preview could not be stored.")
                }
                total
            } catch (error: CancellationException) {
                temp.delete()
                throw error
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        }
    }

    /**
     * Streams a verified decrypt into a caller-owned stream (SAF export). The
     * stream is flushed but never closed; the caller owns it and must delete a
     * partial destination if this throws.
     */
    suspend fun decryptToStream(
        file: VaultFile,
        output: OutputStream,
        onProgress: (Long) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        mutex.withLock { decryptToSinkLocked(file, output, onProgress) }
    }

    /** Decrypts and verifies without writing plaintext anywhere. */
    suspend fun verifyFile(file: VaultFile): Long = withContext(Dispatchers.IO) {
        mutex.withLock { decryptToSinkLocked(file, null, {}) }
    }

    /** Removes the row first, then the ciphertext; returns the deleted row. */
    suspend fun deleteFile(fileId: String): VaultFile? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = readLocked()
            val file = snapshot.files.firstOrNull { it.id == fileId } ?: return@withLock null
            writeLocked(snapshot.copy(files = snapshot.files.filterNot { it.id == fileId }))
            runCatching { fileFor(file).delete() }
            File(directory, "${file.id}.nxf.tmp").delete()
            file
        }
    }

    /**
     * Renames one file row. The ciphertext, chunk count and SHA-256 digest are
     * untouched; only the authenticated metadata in the encrypted index changes.
     */
    suspend fun renameFile(fileId: String, newName: String): VaultFile? =
        withContext(Dispatchers.IO) {
            val clean = newName.trim()
            require(clean.isNotEmpty()) { "The file name cannot be empty." }
            require(clean.length <= MAX_NAME_LENGTH) {
                "The file name cannot exceed $MAX_NAME_LENGTH characters."
            }
            mutex.withLock {
                val snapshot = readLocked()
                val file = snapshot.files.firstOrNull { it.id == fileId } ?: return@withLock null
                val updated = file.copy(
                    displayName = clean,
                    modifiedAt = System.currentTimeMillis(),
                )
                writeLocked(
                    snapshot.copy(
                        files = snapshot.files.map { if (it.id == fileId) updated else it },
                    ),
                )
                updated
            }
        }

    /** Real on-disk and logical footprint; never estimates. */
    suspend fun storageStats(): VaultStorageStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            sweepTemporariesLocked()
            val snapshot = readLocked()
            persistRepairsLocked(snapshot)
            sweepOrphansLocked(snapshot)
            val encrypted = directory.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
            val attachments = directory.listFiles { candidate ->
                candidate.isFile &&
                    candidate.name != indexFile.name &&
                    candidate.name.endsWith(".v1.enc")
            }?.sumOf { it.length() } ?: 0L
            VaultStorageStats(
                encryptedBytes = encrypted,
                logicalBytes = snapshot.files
                    .filter { it.status == VaultFileStatus.COMPLETE }
                    .sumOf { it.sizeBytes },
                attachmentBytes = attachments,
                fileCount = snapshot.files.size,
                entryCount = snapshot.entries.size,
                failedImportCount = snapshot.files.count { it.status != VaultFileStatus.COMPLETE },
            )
        }
    }

    suspend fun entryCount(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { readLocked().let { it.entries.size + it.files.size } }.getOrDefault(0)
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (directory.exists() && !directory.deleteRecursively()) {
                throw VaultCorruptedException("The vault directory could not be deleted.")
            }
            Unit
        }
    }

    // ------------------------------------------------------------------ internals

    private fun readLocked(): VaultSnapshot {
        if (!indexFile.exists()) return VaultSnapshot(emptyList())
        val raw = runCatching { cipher.decrypt(indexFile.readBytes(), indexAad()) }
            .getOrElse { throw VaultCorruptedException("The vault index failed authentication.", it) }
        val text = runCatching { String(raw, Charsets.UTF_8) }
            .getOrElse { throw VaultCorruptedException("The vault index is not valid text.", it) }
        return try {
            val root = JSONObject(text)
            val version = root.optInt("version", 1)
            if (version > CURRENT_VERSION) {
                throw VaultCorruptedException("This vault was written by a newer app version.")
            }
            val migrated = migrate(root, version)
            val entryArray = migrated.optJSONArray("entries") ?: JSONArray()
            val entries = ArrayList<VaultEntry>(entryArray.length())
            for (index in 0 until entryArray.length()) {
                val item = entryArray.optJSONObject(index) ?: continue
                entries += decodeEntry(item)
            }
            val fileArray = migrated.optJSONArray("files") ?: JSONArray()
            val files = ArrayList<VaultFile>(fileArray.length())
            var repairedImports = 0
            for (index in 0 until fileArray.length()) {
                val item = fileArray.optJSONObject(index) ?: continue
                val decoded = decodeFile(item)
                val ciphertextExists = if (decoded.status == VaultFileStatus.COMPLETE) {
                    true
                } else {
                    runCatching { fileFor(decoded).exists() }.getOrDefault(false)
                }
                val classified = VaultImportPolicy.classifyLegacy(decoded, ciphertextExists)
                if (classified.status == VaultFileStatus.COMPLETE &&
                    decoded.status != VaultFileStatus.COMPLETE
                ) {
                    repairedImports += 1
                }
                files += classified
            }
            VaultSnapshot(
                entries = entries.sortedByDescending { it.createdAt },
                files = files.sortedByDescending { it.createdAt },
                version = CURRENT_VERSION,
                repairedImports = repairedImports,
            )
        } catch (error: VaultCorruptedException) {
            throw error
        } catch (error: Exception) {
            throw VaultCorruptedException("The vault index could not be parsed.", error)
        }
    }

    /**
     * Versioned migration point. Version 1 is the V4 format (entries only);
     * version 2 adds the streaming file database. Old vaults keep opening.
     */
    private fun migrate(root: JSONObject, fromVersion: Int): JSONObject {
        if (fromVersion < 1) throw VaultCorruptedException("Unknown vault version $fromVersion.")
        val migrated = if (fromVersion < 2 && !root.has("files")) {
            root.put("files", JSONArray())
        } else {
            root
        }
        migrated.put("version", CURRENT_VERSION)
        return migrated
    }

    private fun writeLocked(snapshot: VaultSnapshot): VaultSnapshot {
        directory.mkdirs()
        val normalized = snapshot.copy(
            entries = snapshot.entries.sortedByDescending { it.createdAt },
            files = snapshot.files.sortedByDescending { it.createdAt },
            version = CURRENT_VERSION,
        )
        val plain = encode(normalized).toByteArray(Charsets.UTF_8)
        val encrypted = runCatching { cipher.encrypt(plain, indexAad()) }
            .getOrElse { throw VaultCorruptedException("The vault index could not be encrypted.", it) }
        plain.fill(0)
        val temp = File(directory, "index.tmp")
        temp.outputStream().use { stream ->
            stream.write(encrypted)
            stream.flush()
            stream.fd.sync()
        }
        if (!temp.renameTo(indexFile)) {
            temp.delete()
            throw VaultCorruptedException("The vault index could not be replaced atomically.")
        }
        return normalized
    }

    private fun encode(snapshot: VaultSnapshot): String {
        val entries = JSONArray()
        snapshot.entries.forEach { entry ->
            entries.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("type", entry.type.name)
                    put("title", entry.title)
                    put("body", entry.body)
                    put("createdAt", entry.createdAt)
                    entry.attachmentId?.let { put("attachmentId", it) }
                    entry.mimeType?.let { put("mimeType", it) }
                    entry.captureStatus?.let { put("captureStatus", it.name) }
                    entry.failureStage?.let { put("failureStage", it) }
                    entry.targetPackage?.let { put("targetPackage", it) }
                },
            )
        }
        val files = JSONArray()
        snapshot.files.forEach { file ->
            files.put(
                JSONObject().apply {
                    put("id", file.id)
                    put("name", file.displayName)
                    put("mime", file.mimeType)
                    put("size", file.sizeBytes)
                    put("chunks", file.chunkCount)
                    put("createdAt", file.createdAt)
                    put("modifiedAt", file.modifiedAt)
                    put("encryptionVersion", file.encryptionVersion)
                    put("sha256", file.integritySha256)
                    put("status", file.status.name)
                    put("storageName", file.storageName)
                    file.errorMessage?.let { put("error", it) }
                },
            )
        }
        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("entries", entries)
            .put("files", files)
            .toString()
    }

    private fun decodeEntry(item: JSONObject): VaultEntry = VaultEntry(
        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
        type = runCatching { VaultEntryType.valueOf(item.optString("type", "NOTE")) }
            .getOrDefault(VaultEntryType.NOTE),
        title = item.optString("title"),
        body = item.optString("body"),
        createdAt = item.optLong("createdAt", 0L),
        attachmentId = item.optString("attachmentId").takeIf { it.isNotBlank() },
        mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
        captureStatus = runCatching {
            CaptureStatus.valueOf(item.optString("captureStatus"))
        }.getOrNull(),
        failureStage = item.optString("failureStage").takeIf { it.isNotBlank() },
        targetPackage = item.optString("targetPackage").takeIf { it.isNotBlank() },
    )

    private fun decodeFile(item: JSONObject): VaultFile {
        val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
        return VaultFile(
            id = id,
            displayName = item.optString("name").ifBlank { "Encrypted file" },
            mimeType = item.optString("mime").ifBlank { "application/octet-stream" },
            sizeBytes = item.optLong("size", 0L),
            chunkCount = item.optInt("chunks", 0),
            createdAt = item.optLong("createdAt", 0L),
            modifiedAt = item.optLong("modifiedAt", 0L),
            encryptionVersion = item.optInt("encryptionVersion", VaultFileFormat.VERSION),
            integritySha256 = item.optString("sha256"),
            status = runCatching {
                VaultFileStatus.valueOf(item.optString("status", "FAILED"))
            }.getOrDefault(VaultFileStatus.FAILED),
            storageName = item.optString("storageName").ifBlank { "$id.nxf.enc" },
            errorMessage = item.optString("error").takeIf { it.isNotBlank() },
        )
    }

    /**
     * Decrypts [file] chunk by chunk and optionally writes the verified
     * plaintext to [output]. The sink is flushed but not closed. Every chunk is
     * authenticated with its index, and the whole-file digest is checked before
     * this returns, so callers only ever receive verified bytes.
     */
    private suspend fun decryptToSinkLocked(
        file: VaultFile,
        output: OutputStream?,
        onProgress: (Long) -> Unit,
    ): Long {
        if (file.status != VaultFileStatus.COMPLETE) {
            throw VaultCorruptedException(
                "${file.displayName} did not finish importing and cannot be opened.",
            )
        }
        val source = fileFor(file)
        if (!source.exists()) {
            throw VaultCorruptedException("The encrypted file for ${file.displayName} is missing.")
        }
        try {
            var total = 0L
            var chunks = 0
            var lastReport = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            DataInputStream(
                BufferedInputStream(FileInputStream(source), BUFFER_BYTES),
            ).use { input ->
                val header = VaultFileFormat.readHeader(input, file.id)
                val sink = output?.let { BufferedOutputStream(it, BUFFER_BYTES) }
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val length = input.readInt()
                    if (length == VaultFileFormat.CHUNK_END) break
                    if (!VaultFileFormat.validBlobLength(length, header.chunkSize)) {
                        throw VaultCorruptedException(
                            "${file.displayName} contains a malformed encrypted chunk.",
                        )
                    }
                    val blob = ByteArray(length)
                    input.readFully(blob)
                    val plain = cipher.decrypt(
                        blob,
                        VaultFileFormat.chunkAad(kind.aad, file.id, chunks),
                    )
                    digest.update(plain)
                    sink?.write(plain)
                    total += plain.size
                    chunks += 1
                    if (total - lastReport >= PROGRESS_STEP_BYTES) {
                        lastReport = total
                        runCatching { onProgress(total) }
                    }
                }
                val footerLength = input.readInt()
                if (footerLength !in VaultFileFormat.MIN_FOOTER_BLOB..VaultFileFormat.MAX_FOOTER_BLOB) {
                    throw VaultCorruptedException(
                        "${file.displayName} has a malformed integrity footer.",
                    )
                }
                val footerBlob = ByteArray(footerLength)
                input.readFully(footerBlob)
                if (input.read() != -1) {
                    throw VaultCorruptedException(
                        "${file.displayName} has unexpected trailing data.",
                    )
                }
                val footer = VaultFileFormat.decodeFooter(
                    cipher.decrypt(footerBlob, VaultFileFormat.footerAad(kind.aad, file.id)),
                )
                val sha = digest.digest()
                if (footer.totalChunks != chunks.toLong() || footer.totalBytes != total) {
                    throw VaultCorruptedException(
                        "${file.displayName} is incomplete: its footer does not match the decrypted content.",
                    )
                }
                if (!MessageDigest.isEqual(footer.sha256, sha)) {
                    throw VaultCorruptedException(
                        "${file.displayName} failed its SHA-256 integrity check.",
                    )
                }
                if (file.integritySha256.isNotBlank() &&
                    !VaultFileFormat.hex(sha).equals(file.integritySha256, ignoreCase = true)
                ) {
                    throw VaultCorruptedException(
                        "${file.displayName} does not match the digest recorded in the vault index.",
                    )
                }
                sink?.flush()
            }
            return total
        } catch (error: CancellationException) {
            throw error
        } catch (error: VaultCorruptedException) {
            throw error
        } catch (error: Exception) {
            throw VaultCorruptedException(
                "${file.displayName} could not be decrypted (${error.javaClass.simpleName}).",
                error,
            )
        }
    }

    /**
     * Best-effort persistence of legacy row repairs. A repair failure must not
     * prevent the vault from opening; the in-memory snapshot is still honest.
     */
    private fun persistRepairsLocked(snapshot: VaultSnapshot) {
        if (snapshot.repairedImports <= 0) return
        runCatching { writeLocked(snapshot.copy(repairedImports = 0)) }
    }

    private fun sweepTemporariesLocked() {
        if (sweptTemporaries) return
        sweptTemporaries = true
        runCatching {
            directory.listFiles { candidate ->
                candidate.name.endsWith(".nxf.tmp") || candidate.name.endsWith(".part")
            }?.forEach { it.delete() }
        }
    }

    /**
     * Deletes ciphertext containers that are not referenced by the
     * authenticated index. This only runs after a successful index parse, so a
     * corrupted index can never trigger data deletion. It cleans up the narrow
     * window between atomic rename and index commit after process death.
     */
    private fun sweepOrphansLocked(snapshot: VaultSnapshot) {
        if (sweptOrphans) return
        sweptOrphans = true
        val referenced = snapshot.files.map { it.storageName }.toSet()
        runCatching {
            directory.listFiles { candidate ->
                candidate.isFile && candidate.name.endsWith(".nxf.enc")
            }?.forEach { candidate ->
                if (candidate.name !in referenced) candidate.delete()
            }
        }
    }

    private fun fileFor(file: VaultFile): File {
        val name = file.storageName
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            throw VaultCorruptedException("The vault file path is invalid.")
        }
        val candidate = File(directory, name)
        if (candidate.canonicalFile.parentFile != directory.canonicalFile) {
            throw VaultCorruptedException("The vault file path escapes its directory.")
        }
        return candidate
    }

    private fun attachmentFile(id: String): File = File(directory, "$id.v1.enc")

    private fun indexAad(): ByteArray = "${kind.aad}|index|v1".toByteArray(Charsets.UTF_8)

    private fun attachmentAad(id: String): ByteArray =
        "${kind.aad}|attachment|$id".toByteArray(Charsets.UTF_8)

    companion object {
        const val CURRENT_VERSION = 2
        const val MAX_NAME_LENGTH = 120
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 1L * 1024 * 1024
    }
}
