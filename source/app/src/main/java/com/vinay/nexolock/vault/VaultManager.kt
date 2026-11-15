package com.vinay.nexolock.vault

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import com.vinay.nexolock.core.crypto.KeystoreCipher
import com.vinay.nexolock.data.CredentialRepository
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoPreferences
import com.vinay.nexolock.data.SecurityLogRepository
import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.domain.AuthenticationService
import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.domain.LockoutPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class VaultUnlockResult(
    val unlocked: Boolean,
    val snapshot: VaultSnapshot? = null,
    val message: String? = null,
    val lockedOutMs: Long = 0L,
)

/** Result of one streaming import, including the failing stage for diagnostics. */
data class VaultImportOutcome(
    val failure: String? = null,
    val stage: VaultImportStage? = null,
    val file: VaultFile? = null,
)

/** Result of restoring one vault file to a SAF destination. */
data class VaultExportFileResult(
    val fileId: String = "",
    val name: String = "",
    val bytes: Long = 0L,
    val destination: String? = null,
    val failure: String? = null,
) {
    val ok: Boolean get() = failure == null
}

/** Aggregate of a single- or multi-file export batch. */
data class VaultExportResult(
    val files: List<VaultExportFileResult> = emptyList(),
    val cancelled: Boolean = false,
) {
    val exported: Int get() = files.count { it.ok }
    val failed: Int get() = files.count { !it.ok }
    val bytes: Long get() = files.filter { it.ok }.sumOf { it.bytes }
}

/** Result of clearing the Intruder Vault incident history. */
data class VaultHistoryClearResult(
    val deleted: Int = 0,
    val failure: String? = null,
)

/**
 * Credential + session + storage orchestration for both vaults.
 *
 * The manager never stores the vault PIN and never exports a vault key. Each
 * vault's [VaultStore] owns a separate [KeystoreCipher] alias. The session is an
 * in-memory timestamp: process death or the timeout re-locks the vault.
 */
class VaultManager(
    private val context: Context,
    private val prefs: NexoPreferences,
    private val credentials: CredentialRepository,
    private val auth: AuthenticationService,
    private val logs: SecurityLogRepository,
) {

    private val stores: Map<VaultKind, VaultStore> = VaultKind.entries.associateWith { kind ->
        VaultStore(context, kind, KeystoreCipher(kind.keyAlias))
    }

    private val unlockedAt = ConcurrentHashMap<VaultKind, Long>()

    /** Decoded thumbnails only; cleared with the preview cache on every lock. */
    private val thumbnails = LruCache<String, Bitmap>(24)

    init {
        // Decrypted viewer previews never survive a process restart.
        clearPreviewCache()
    }

    // ------------------------------------------------------------------ credentials

    suspend fun createPin(kind: VaultKind, pin: String): String? {
        if (!AuthPolicy.validPin(pin)) return "Use 4 to 8 digits."
        val duplicate = usedElsewhere(kind, pin)
        if (duplicate != null) return "This PIN is already used by $duplicate."
        return runCatching {
            credentials.store(kind.credentialKind, pin)
            if (kind == VaultKind.PERSONAL) {
                prefs.setPersonalVaultPinLength(pin.length)
            } else {
                prefs.setIntruderVaultPinLength(pin.length)
            }
            stores.getValue(kind).write(emptyList())
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_CREATED else LogType.INTRUDER_VAULT_CREATED,
                "${kind.title} credential configured",
            )
            null
        }.getOrElse { error ->
            runCatching { credentials.clear(kind.credentialKind) }
            "The ${kind.title} could not be created (${error.javaClass.simpleName})."
        }
    }

    suspend fun changePin(kind: VaultKind, currentPin: String, newPin: String): String? {
        if (!AuthPolicy.validPin(newPin)) return "Use 4 to 8 digits."
        val current = auth.verify(kind.credentialKind, currentPin)
        if (!current.accepted) {
            return if (current.lockedOutMs > 0L) {
                "Too many attempts. Try again in ${LockoutPolicy.describe(current.lockedOutMs)}."
            } else {
                "Current vault PIN is incorrect. ${current.attemptsUntilLockout} attempts left."
            }
        }
        val duplicate = usedElsewhere(kind, newPin)
        if (duplicate != null) return "This PIN is already used by $duplicate."
        return runCatching {
            credentials.store(kind.credentialKind, newPin)
            if (kind == VaultKind.PERSONAL) {
                prefs.setPersonalVaultPinLength(newPin.length)
            } else {
                prefs.setIntruderVaultPinLength(newPin.length)
            }
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_PIN_CHANGED else LogType.INTRUDER_VAULT_PIN_CHANGED,
                "${kind.title} PIN changed",
            )
            null
        }.getOrElse { "The new vault PIN could not be saved." }
    }

    private suspend fun usedElsewhere(kind: VaultKind, pin: String): String? {
        val main = CredentialKind.MAIN_PIN
        if (credentials.exists(main) && credentials.verify(main, pin)) return "the main PIN"
        val recovery = CredentialKind.RECOVERY_PIN
        if (credentials.exists(recovery) && credentials.verify(recovery, pin)) return "the recovery PIN"
        VaultKind.entries.filter { it != kind }.forEach { other ->
            if (credentials.exists(other.credentialKind) &&
                credentials.verify(other.credentialKind, pin)
            ) {
                return "the ${other.title}"
            }
        }
        return null
    }

    // ------------------------------------------------------------------ session

    fun isUnlocked(kind: VaultKind, now: Long = SystemClock.elapsedRealtime()): Boolean {
        val since = unlockedAt[kind] ?: return false
        if (now - since > SESSION_TIMEOUT_MS) {
            unlockedAt.remove(kind)
            clearPreviewCache()
            return false
        }
        return true
    }

    fun lock(kind: VaultKind) {
        if (unlockedAt.remove(kind) != null) {
            clearPreviewCache()
        }
    }

    fun lockAll() {
        if (unlockedAt.isNotEmpty()) {
            unlockedAt.clear()
            clearPreviewCache()
        }
    }

    suspend fun unlock(kind: VaultKind, pin: String): VaultUnlockResult {
        val attempt = auth.verify(kind.credentialKind, pin)
        if (!attempt.accepted) {
            return VaultUnlockResult(
                unlocked = false,
                message = if (attempt.lockedOutMs > 0L) {
                    "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                } else {
                    "Incorrect ${kind.title} PIN. ${attempt.attemptsUntilLockout} attempts left before a delay."
                },
                lockedOutMs = attempt.lockedOutMs,
            )
        }
        return try {
            val snapshot = stores.getValue(kind).read()
            unlockedAt[kind] = SystemClock.elapsedRealtime()
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_UNLOCKED else LogType.INTRUDER_VAULT_UNLOCKED,
                "${kind.title} unlocked",
            )
            VaultUnlockResult(unlocked = true, snapshot = snapshot)
        } catch (error: VaultCorruptedException) {
            logs.log(
                if (kind == VaultKind.PERSONAL) {
                    LogType.VAULT_CORRUPTED
                } else {
                    LogType.INTRUDER_VAULT_CORRUPTED
                },
                "${kind.title}: ${error.message}",
            )
            VaultUnlockResult(
                unlocked = false,
                message = "The ${kind.title} failed its integrity check. The data was not changed.",
            )
        }
    }

    suspend fun lockFromUi(kind: VaultKind) {
        if (unlockedAt.remove(kind) != null) {
            clearPreviewCache()
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_LOCKED else LogType.INTRUDER_VAULT_LOCKED,
                "${kind.title} locked",
            )
        }
    }

    // ------------------------------------------------------------------ entries

    /** Requires an unlocked session. */
    suspend fun entries(kind: VaultKind): VaultSnapshot {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        return stores.getValue(kind).read()
    }

    suspend fun addNote(kind: VaultKind, title: String, body: String): String? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        if (title.isBlank() && body.isBlank()) return "Write a title or some text first."
        return runCatching {
            val store = stores.getValue(kind)
            val snapshot = store.read()
            val entry = VaultEntry(
                id = UUID.randomUUID().toString(),
                type = VaultEntryType.NOTE,
                title = title.ifBlank { "Untitled note" }.take(120),
                body = body,
                createdAt = System.currentTimeMillis(),
            )
            store.write(snapshot.entries + entry)
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_ENTRY_ADDED else LogType.INTRUDER_ENTRY_ADDED,
                "Encrypted entry added to ${kind.title}",
            )
            null
        }.getOrElse { "The note could not be saved (${it.javaClass.simpleName})." }
    }

    suspend fun readAttachment(kind: VaultKind, entryId: String): ByteArray? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val store = stores.getValue(kind)
        val entry = store.read().entries.firstOrNull { it.id == entryId } ?: return null
        val attachment = entry.attachmentId ?: return null
        return store.readAttachment(attachment)
    }

    /**
     * Decrypts a legacy V4 entry attachment (for example an Intruder capture)
     * into an app-private preview so the real viewer can display it. AES-GCM
     * authenticates the whole attachment before a single byte is shown.
     */
    suspend fun openAttachment(kind: VaultKind, entryId: String): File {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val store = stores.getValue(kind)
        val entry = store.read().entries.firstOrNull { it.id == entryId }
            ?: throw VaultCorruptedException("The entry is no longer in the vault index.")
        val attachmentId = entry.attachmentId
            ?: throw VaultCorruptedException("This entry has no encrypted attachment.")
        val bytes = store.readAttachment(attachmentId)
            ?: throw VaultCorruptedException("The encrypted attachment failed authentication.")
        val previewDirectory = File(context.cacheDir, "vault_preview/${kind.id}")
        previewDirectory.mkdirs()
        previewDirectory.listFiles()?.forEach { existing ->
            if (existing.name.startsWith("entry-$entryId")) existing.delete()
        }
        val extension = when (entry.mimeType?.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val output = File(previewDirectory, "entry-$entryId$extension")
        output.outputStream().use { stream ->
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
        }
        bytes.fill(0)
        logs.log(
            if (kind == VaultKind.INTRUDER) {
                LogType.INTRUDER_ENTRY_OPENED
            } else {
                LogType.VAULT_FILE_OPENED
            },
            "${kind.title}: attachment for \"${entry.title}\" decrypted to a temporary preview",
        )
        return output
    }

    suspend fun deleteEntry(kind: VaultKind, entryId: String): String? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        return runCatching {
            val store = stores.getValue(kind)
            val snapshot = store.read()
            val entry = snapshot.entries.firstOrNull { it.id == entryId } ?: return null
            entry.attachmentId?.let { store.deleteAttachment(it) }
            store.write(snapshot.entries.filterNot { it.id == entryId })
            logs.log(
                if (kind == VaultKind.PERSONAL) LogType.VAULT_ENTRY_DELETED else LogType.INTRUDER_ENTRY_DELETED,
                "Encrypted entry deleted from ${kind.title}",
            )
            null
        }.getOrElse { "The entry could not be deleted." }
    }

    /**
     * Renames a note/capture entry. Only the authenticated title metadata in the
     * encrypted index changes; attachments and capture evidence are untouched.
     */
    suspend fun renameEntry(kind: VaultKind, entryId: String, newTitle: String): String? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val clean = newTitle.trim()
        if (clean.isEmpty()) return "Enter a name for this entry."
        if (clean.length > VaultStore.MAX_NAME_LENGTH) {
            return "Keep the name under ${VaultStore.MAX_NAME_LENGTH} characters."
        }
        return runCatching {
            val store = stores.getValue(kind)
            val snapshot = store.read()
            val entry = snapshot.entries.firstOrNull { it.id == entryId }
                ?: return "The entry is no longer in the vault index."
            store.write(
                snapshot.entries.map {
                    if (it.id == entryId) entry.copy(title = clean) else it
                },
            )
            logs.log(
                LogType.VAULT_FILE_RENAMED,
                "${kind.title}: entry \"${entry.title}\" renamed to \"$clean\"",
            )
            null
        }.getOrElse { "The entry could not be renamed (${it.javaClass.simpleName})." }
    }

    /** Renames a streaming vault file; the encrypted content is not modified. */
    suspend fun renameFile(kind: VaultKind, fileId: String, newName: String): String? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val clean = newName.trim()
        if (clean.isEmpty()) return "Enter a file name."
        if (clean.length > VaultStore.MAX_NAME_LENGTH) {
            return "Keep the file name under ${VaultStore.MAX_NAME_LENGTH} characters."
        }
        if (clean.any { it == '/' || it == '\\' || it.isISOControl() }) {
            return "The file name contains characters that are not allowed."
        }
        return runCatching {
            val renamed = stores.getValue(kind).renameFile(fileId, clean)
                ?: return "The file is no longer in the vault index."
            logs.log(
                LogType.VAULT_FILE_RENAMED,
                "${kind.title}: file renamed to \"${renamed.displayName}\" (ciphertext unchanged)",
            )
            null
        }.getOrElse { "The file could not be renamed (${it.javaClass.simpleName})." }
    }

    /**
     * Measured storage footprint for the unlocked vault, including the decrypted
     * preview cache that will be deleted on lock.
     */
    suspend fun storageStats(kind: VaultKind): VaultStorageStats {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val base = stores.getValue(kind).storageStats()
        val previewDirectory = File(context.cacheDir, "vault_preview/${kind.id}")
        val previewBytes = previewDirectory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        return base.copy(previewBytes = previewBytes)
    }

    // ------------------------------------------------------------------ streaming files

    /**
     * Streams a picked file into the vault without buffering it in memory and
     * without a size cap. Returns a structured outcome so the UI can show the
     * exact failing stage without leaking exception details.
     */
    suspend fun importFile(
        kind: VaultKind,
        displayName: String,
        mimeType: String,
        expectedBytes: Long?,
        openStream: () -> InputStream,
        onProgress: (Long) -> Unit = {},
    ): VaultImportOutcome {
        if (!isUnlocked(kind)) {
            return VaultImportOutcome(
                failure = "The ${kind.title} is locked. Unlock it and try again.",
                stage = VaultImportStage.PICKER,
            )
        }
        return try {
            val file = stores.getValue(kind).importFile(
                displayName = displayName,
                mimeType = mimeType,
                expectedBytes = expectedBytes,
                openStream = openStream,
                onProgress = onProgress,
            )
            logs.log(
                LogType.VAULT_FILE_IMPORTED,
                "${kind.title}: ${file.displayName} encrypted (${file.sizeBytes} bytes, ${file.chunkCount} chunks)",
            )
            VaultImportOutcome(file = file)
        } catch (error: CancellationException) {
            throw error
        } catch (error: VaultImportException) {
            logs.log(
                LogType.VAULT_FILE_IMPORT_FAILED,
                "${kind.title}: ${error.stage.name} · ${error.message ?: "import failed"}",
            )
            VaultImportOutcome(
                failure = error.message ?: "The file could not be imported.",
                stage = error.stage,
            )
        } catch (error: Exception) {
            val message = VaultImportErrors.messageFor(error)
            logs.log(
                LogType.VAULT_FILE_IMPORT_FAILED,
                "${kind.title}: ${error.javaClass.simpleName} · $message",
            )
            VaultImportOutcome(failure = message, stage = VaultImportStage.UNKNOWN)
        }
    }

    /** Full decrypt + SHA-256 verification; plaintext is never written. */
    suspend fun verifyFile(kind: VaultKind, fileId: String): VaultFileVerification {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val store = stores.getValue(kind)
        val file = store.read().files.firstOrNull { it.id == fileId }
            ?: return VaultFileVerification(false, message = "The file is no longer in the vault index.")
        return try {
            val bytes = store.verifyFile(file)
            logs.log(
                LogType.VAULT_FILE_VERIFIED,
                "${kind.title}: ${file.displayName} integrity verified ($bytes bytes)",
            )
            VaultFileVerification(
                ok = true,
                bytes = bytes,
                message = "Integrity verified: ${file.displayName} was decrypted and matched its SHA-256 digest.",
            )
        } catch (error: VaultCorruptedException) {
            logs.log(
                if (kind == VaultKind.PERSONAL) {
                    LogType.VAULT_CORRUPTED
                } else {
                    LogType.INTRUDER_VAULT_CORRUPTED
                },
                "${kind.title} file ${file.displayName}: ${error.message}",
            )
            VaultFileVerification(
                ok = false,
                message = error.message ?: "${file.displayName} failed its integrity check.",
            )
        }
    }

    suspend fun deleteFile(kind: VaultKind, fileId: String): String? {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        return runCatching {
            val removed = stores.getValue(kind).deleteFile(fileId)
                ?: return "The file is no longer in the vault index."
            logs.log(
                LogType.VAULT_FILE_DELETED,
                "${kind.title}: ${removed.displayName} deleted",
            )
            null
        }.getOrElse { "The file could not be deleted (${it.javaClass.simpleName})." }
    }

    /**
     * Restores one vault file to a document URI chosen through
     * ACTION_CREATE_DOCUMENT. The vault copy is never modified. A partial
     * destination is deleted if the stream fails or is cancelled.
     */
    suspend fun exportFileToUri(
        kind: VaultKind,
        fileId: String,
        target: Uri,
        onProgress: (Long) -> Unit = {},
    ): VaultExportFileResult = withContext(Dispatchers.IO) {
        if (!isUnlocked(kind)) {
            return@withContext VaultExportFileResult(
                fileId = fileId,
                failure = "The ${kind.title} is locked. Unlock it and try again.",
            )
        }
        val store = stores.getValue(kind)
        val file = runCatching {
            store.read().files.firstOrNull { it.id == fileId }
        }.getOrNull() ?: return@withContext VaultExportFileResult(
            fileId = fileId,
            failure = "The file is no longer in the vault index.",
        )
        try {
            val bytes = writeToUri(kind, file, target, onProgress)
            logs.log(
                LogType.VAULT_FILE_EXPORTED,
                "${kind.title}: ${file.displayName} restored ($bytes bytes)",
            )
            VaultExportFileResult(
                fileId = file.id,
                name = file.displayName,
                bytes = bytes,
                destination = displayNameOf(target) ?: "selected document",
            )
        } catch (cancelled: CancellationException) {
            logs.log(
                LogType.VAULT_EXPORT_CANCELLED,
                "${kind.title}: export of ${file.displayName} cancelled",
            )
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: "The file could not be exported."
            logs.log(LogType.VAULT_EXPORT_FAILED, "${kind.title}: ${file.displayName} · $message")
            VaultExportFileResult(fileId = file.id, name = file.displayName, failure = message)
        }
    }

    /**
     * Restores one vault file into a tree URI chosen through
     * ACTION_OPEN_DOCUMENT_TREE. Existing names are collected and a numbered
     * suffix is applied before creation, so an export never overwrites an
     * unrelated file in the chosen folder.
     */
    suspend fun exportFileToTree(
        kind: VaultKind,
        fileId: String,
        treeUri: Uri,
        onProgress: (Long) -> Unit = {},
    ): VaultExportFileResult = withContext(Dispatchers.IO) {
        if (!isUnlocked(kind)) {
            return@withContext VaultExportFileResult(
                fileId = fileId,
                failure = "The ${kind.title} is locked. Unlock it and try again.",
            )
        }
        val store = stores.getValue(kind)
        val file = runCatching {
            store.read().files.firstOrNull { it.id == fileId }
        }.getOrNull() ?: return@withContext VaultExportFileResult(
            fileId = fileId,
            failure = "The file is no longer in the vault index.",
        )
        var created: Uri? = null
        try {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val name = VaultExport.uniqueName(file.displayName, listTreeNames(resolver, treeUri))
            val mime = file.mimeType.ifBlank { "application/octet-stream" }
            created = DocumentsContract.createDocument(resolver, parent, mime, name)
                ?: return@withContext VaultExportFileResult(
                    fileId = file.id,
                    name = file.displayName,
                    failure = "The selected folder refused to create the file.",
                )
            val bytes = writeToUri(kind, file, created, onProgress)
            logs.log(
                LogType.VAULT_FILE_EXPORTED,
                "${kind.title}: ${file.displayName} restored as \"$name\" ($bytes bytes)",
            )
            VaultExportFileResult(
                fileId = file.id,
                name = file.displayName,
                bytes = bytes,
                destination = name,
            )
        } catch (cancelled: CancellationException) {
            created?.let { deletePartial(it) }
            logs.log(
                LogType.VAULT_EXPORT_CANCELLED,
                "${kind.title}: export of ${file.displayName} cancelled",
            )
            throw cancelled
        } catch (error: Exception) {
            created?.let { deletePartial(it) }
            val message = error.message ?: "The file could not be exported."
            logs.log(LogType.VAULT_EXPORT_FAILED, "${kind.title}: ${file.displayName} · $message")
            VaultExportFileResult(fileId = file.id, name = file.displayName, failure = message)
        }
    }

    /** Attempts to delete the provider-owned original after a verified import. */
    suspend fun deleteOriginal(uri: Uri): OriginalDeletionResult =
        OriginalFileDeletion.delete(context, uri)

    /** Records a deletion outcome, including one resolved after system consent. */
    suspend fun recordOriginalDeletion(
        displayName: String,
        result: OriginalDeletionResult,
    ) {
        when (result) {
            is OriginalDeletionResult.Deleted -> logs.log(
                LogType.VAULT_ORIGINAL_DELETED,
                "$displayName: original deleted via ${result.via}",
            )

            is OriginalDeletionResult.Failed -> logs.log(
                LogType.VAULT_ORIGINAL_DELETE_FAILED,
                "$displayName: ${result.message}",
            )

            is OriginalDeletionResult.Unsupported -> logs.log(
                LogType.VAULT_ORIGINAL_DELETE_FAILED,
                "$displayName: ${result.message}",
            )

            is OriginalDeletionResult.NeedsConsent -> Unit
        }
    }

    /**
     * Decrypts a stored file into the app-private cache for an in-app viewer.
     * The preview is deleted when the vault locks. The returned name is a
     * sanitised copy of the display name; the stored file is not modified.
     */
    suspend fun openFile(
        kind: VaultKind,
        fileId: String,
        onProgress: (Long) -> Unit = {},
    ): File = previewPath(kind, fileId, onProgress, logOpen = true)

    /**
     * Builds a bounded thumbnail for an image stored in the vault. The
     * plaintext never leaves the app-private cache and is deleted on lock. Very
     * large images are represented by a type icon instead of risking an
     * unbounded decode; imports themselves have no size cap.
     */
    suspend fun loadThumbnail(
        kind: VaultKind,
        fileId: String,
        maxPx: Int = 512,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isUnlocked(kind)) return@withContext null
        val file = runCatching {
            stores.getValue(kind).read().files.firstOrNull { it.id == fileId }
        }.getOrNull() ?: return@withContext null
        if (!isImageMime(file.mimeType)) return@withContext null
        if (file.sizeBytes <= 0L || file.sizeBytes > THUMBNAIL_MAX_SOURCE_BYTES) {
            return@withContext null
        }
        val key = "${kind.id}:${file.id}:${file.modifiedAt}:${maxPx.coerceAtLeast(64)}"
        thumbnails.get(key)?.let { return@withContext it }
        val path = runCatching {
            previewPath(kind, fileId, {}, logOpen = false)
        }.getOrNull() ?: return@withContext null
        decodeSampled(path, maxPx.coerceAtLeast(64))?.also { decoded ->
            thumbnails.put(key, decoded)
        }
    }

    /**
     * Builds a bounded thumbnail for a legacy V4 entry attachment (an Intruder
     * capture). The decrypted bytes are wiped immediately after decoding and
     * the bitmap is dropped when the vault locks.
     */
    suspend fun loadAttachmentThumbnail(
        kind: VaultKind,
        attachmentId: String,
        maxPx: Int = 320,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isUnlocked(kind)) return@withContext null
        val key = "attachment:${kind.id}:$attachmentId:${maxPx.coerceAtLeast(64)}"
        thumbnails.get(key)?.let { return@withContext it }
        val bytes = runCatching {
            stores.getValue(kind).readAttachment(attachmentId)
        }.getOrNull() ?: return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx &&
                bounds.outHeight / (sample * 2) >= maxPx
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { decoded ->
                thumbnails.put(key, decoded)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun previewPath(
        kind: VaultKind,
        fileId: String,
        onProgress: (Long) -> Unit,
        logOpen: Boolean,
    ): File {
        check(isUnlocked(kind)) { "${kind.title} is locked." }
        val store = stores.getValue(kind)
        val file = store.read().files.firstOrNull { it.id == fileId }
            ?: throw VaultCorruptedException("The file is no longer in the vault index.")
        if (file.status != VaultFileStatus.COMPLETE) {
            throw VaultCorruptedException("${file.displayName} did not finish importing.")
        }
        val previewDirectory = File(context.cacheDir, "vault_preview/${kind.id}")
        previewDirectory.mkdirs()
        previewDirectory.listFiles()?.forEach { existing ->
            if (existing.name.startsWith("${file.id}-")) existing.delete()
        }
        val safeName = file.displayName
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(80)
            .ifBlank { "preview" }
        val output = File(previewDirectory, "${file.id}-$safeName")
        store.openFileTo(file, output, onProgress)
        if (logOpen) {
            logs.log(
                LogType.VAULT_FILE_OPENED,
                "${kind.title}: ${file.displayName} decrypted to a temporary preview",
            )
        }
        return output
    }

    private suspend fun writeToUri(
        kind: VaultKind,
        file: VaultFile,
        target: Uri,
        onProgress: (Long) -> Unit,
    ): Long {
        val stream = context.contentResolver.openOutputStream(target, "w")
            ?: throw VaultCorruptedException("The destination did not provide a writable stream.")
        var complete = false
        try {
            val bytes = stores.getValue(kind).decryptToStream(file, stream, onProgress)
            stream.flush()
            complete = true
            return bytes
        } finally {
            runCatching { stream.close() }
            if (!complete) deletePartial(target)
        }
    }

    private fun deletePartial(uri: Uri) {
        val resolver = context.contentResolver
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
        runCatching { resolver.delete(uri, null, null) }
    }

    private fun listTreeNames(resolver: ContentResolver, treeUri: Uri): Set<String> {
        val names = HashSet<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { names += it.lowercase() }
            }
        }
        return names
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun decodeSampled(path: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx &&
            bounds.outHeight / (sample * 2) >= maxPx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path.absolutePath, options)
    }

    /** Deletes every decrypted preview (vault lock, app start, vault deletion). */
    fun clearPreviewCache() {
        thumbnails.evictAll()
        runCatching { File(context.cacheDir, "vault_preview").deleteRecursively() }
    }

    /**
     * System capture path. Intentionally does not require an unlocked session:
     * the monitor must be able to record an intruder while the owner is away.
     * The write is still encrypted with the Intruder Vault key.
     */
    suspend fun addCapture(
        reason: String,
        targetLabel: String,
        status: CaptureStatus,
        jpeg: ByteArray? = null,
        failureStage: String? = null,
        targetPackage: String? = null,
    ): String? {
        return runCatching {
            val store = stores.getValue(VaultKind.INTRUDER)
            val snapshot = store.read()
            val attachmentId = jpeg?.let { store.saveAttachment(it, "image/jpeg") }
            val entry = VaultEntry(
                id = UUID.randomUUID().toString(),
                type = VaultEntryType.CAPTURE,
                title = targetLabel.ifBlank { "Protected app" },
                body = reason,
                createdAt = System.currentTimeMillis(),
                attachmentId = attachmentId,
                mimeType = attachmentId?.let { "image/jpeg" },
                captureStatus = status,
                failureStage = failureStage,
                targetPackage = targetPackage,
            )
            store.write(snapshot.entries + entry)
            null
        }.getOrElse { "The intruder record could not be written (${it.javaClass.simpleName})." }
    }

    suspend fun count(kind: VaultKind): Int = withContext(Dispatchers.IO) {
        runCatching { stores.getValue(kind).entryCount() }.getOrDefault(0)
    }

    /** Available bytes on the device filesystem that hosts the vault. */
    fun availableStorageBytes(): Long? = runCatching {
        val vaultDir = File(context.filesDir, "nexo_vault")
        val space = if (vaultDir.exists()) vaultDir.usableSpace else context.filesDir.usableSpace
        space.takeIf { it > 0L }
    }.getOrNull()

    /**
     * Deletes every Intruder Vault incident and its encrypted attachment while
     * preserving secure notes. Requires an unlocked Intruder Vault session.
     */
    suspend fun clearIntruderHistory(): VaultHistoryClearResult {
        if (!isUnlocked(VaultKind.INTRUDER)) {
            return VaultHistoryClearResult(
                failure = "The Intruder Vault is locked. Unlock it and try again.",
            )
        }
        return try {
            val store = stores.getValue(VaultKind.INTRUDER)
            val snapshot = store.read()
            val incidents = snapshot.entries.filter { it.type == VaultEntryType.CAPTURE }
            incidents.forEach { entry ->
                entry.attachmentId?.let { attachment ->
                    runCatching { store.deleteAttachment(attachment) }
                }
            }
            store.write(snapshot.entries.filterNot { it.type == VaultEntryType.CAPTURE })
            logs.log(
                LogType.INTRUDER_HISTORY_CLEARED,
                "Intruder Vault history cleared (${incidents.size} incidents)",
            )
            VaultHistoryClearResult(deleted = incidents.size)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            VaultHistoryClearResult(
                failure = "The incident history could not be cleared (${error.javaClass.simpleName}).",
            )
        }
    }

    suspend fun deleteVault(kind: VaultKind): String? = runCatching {
        credentials.clear(kind.credentialKind)
        stores.getValue(kind).deleteAll()
        unlockedAt.remove(kind)
        clearPreviewCache()
        if (kind == VaultKind.INTRUDER) {
            prefs.setIntruderCaptureEnabled(false)
        }
        logs.log(
            if (kind == VaultKind.PERSONAL) LogType.VAULT_DELETED else LogType.INTRUDER_VAULT_DELETED,
            "${kind.title} and its credential were deleted",
        )
        null
    }.getOrElse { "The ${kind.title} could not be deleted." }

    private companion object {
        const val SESSION_TIMEOUT_MS = 3 * 60 * 1000L
        const val THUMBNAIL_MAX_SOURCE_BYTES = 96L * 1024 * 1024
    }
}
