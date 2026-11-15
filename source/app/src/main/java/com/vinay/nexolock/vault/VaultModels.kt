package com.vinay.nexolock.vault

import com.vinay.nexolock.domain.CredentialKind

/**
 * The two vaults are independent by construction:
 * - different credential verifier
 * - different Android Keystore alias
 * - different AES-GCM associated data
 * - different directory
 *
 * A PIN accepted by one vault therefore cannot decrypt the other vault's index
 * or attachments, even if the same digits were somehow configured.
 */
enum class VaultKind(
    val id: String,
    val title: String,
    val subtitle: String,
    val credentialKind: CredentialKind,
    val keyAlias: String,
    val aad: String,
) {
    PERSONAL(
        id = "personal",
        title = "Personal Vault",
        subtitle = "Private encrypted notes and files",
        credentialKind = CredentialKind.PERSONAL_VAULT_PIN,
        keyAlias = "nexo.vault.personal.v1",
        aad = "com.vinay.nexolock.vault.personal.v1",
    ),
    INTRUDER(
        id = "intruder",
        title = "Intruder Vault",
        subtitle = "Encrypted failed-unlock captures",
        credentialKind = CredentialKind.INTRUDER_VAULT_PIN,
        keyAlias = "nexo.vault.intruder.v1",
        aad = "com.vinay.nexolock.vault.intruder.v1",
    ),
}

enum class VaultEntryType { NOTE, CAPTURE }

enum class CaptureStatus {
    CAPTURED,
    PERMISSION_MISSING,
    CAMERA_UNAVAILABLE,
    CAMERA_BUSY,
    FAILED,
    DISABLED,
}

data class VaultEntry(
    val id: String,
    val type: VaultEntryType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val attachmentId: String? = null,
    val mimeType: String? = null,
    val captureStatus: CaptureStatus? = null,
    /**
     * Structured incident fields added in V1.1.3. Older records decode with
     * null and the UI derives the stage/package from [body] without inventing
     * data. The index schema version is unchanged, so V1.1.2 can still read a
     * V1.1.3 index (it simply ignores these optional keys).
     */
    val failureStage: String? = null,
    val targetPackage: String? = null,
)

/**
 * Import lifecycle of a streaming vault file. A persisted [IMPORTING] record
 * is only ever visible after process death, because an active import holds the
 * store lock; readers normalise it to [FAILED] with an honest explanation.
 */
enum class VaultFileStatus { IMPORTING, COMPLETE, FAILED }

/**
 * Metadata row of the encrypted file database. The ciphertext itself lives in
 * `<storageName>` next to the index; only this metadata is in the index.
 */
data class VaultFile(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val createdAt: Long,
    val modifiedAt: Long,
    val encryptionVersion: Int,
    val integritySha256: String,
    val status: VaultFileStatus,
    val storageName: String,
    val errorMessage: String? = null,
)

data class VaultSnapshot(
    val entries: List<VaultEntry>,
    val files: List<VaultFile> = emptyList(),
    val version: Int = VaultStore.CURRENT_VERSION,
    val corrupted: Boolean = false,
    /**
     * Number of legacy rows repaired from the V1.1.2 "successful import stayed
     * IMPORTING/FAILED" defect. Repairs are best-effort and never discard data.
     */
    val repairedImports: Int = 0,
)

/** Result of an integrity verification pass over one stored file. */
data class VaultFileVerification(
    val ok: Boolean,
    val bytes: Long = 0L,
    val message: String,
)

/**
 * Honest on-disk footprint of one vault, measured instead of estimated.
 *
 * [encryptedBytes] is the real size of the vault directory (index plus every
 * ciphertext). [previewBytes] is the decrypted cache that is deleted whenever
 * the vault locks. [logicalBytes] is the sum of the plaintext file sizes stored
 * in the index, shown only as a convenience.
 */
data class VaultStorageStats(
    val encryptedBytes: Long = 0L,
    val previewBytes: Long = 0L,
    val logicalBytes: Long = 0L,
    val attachmentBytes: Long = 0L,
    val fileCount: Int = 0,
    val entryCount: Int = 0,
    val failedImportCount: Int = 0,
)
