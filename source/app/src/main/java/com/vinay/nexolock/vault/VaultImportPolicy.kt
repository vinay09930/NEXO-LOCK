package com.vinay.nexolock.vault

/**
 * Pure import lifecycle decisions, kept free of Android types so the exact
 * behaviour that caused the V1.1.2 defect is unit-tested on the JVM.
 */
object VaultImportPolicy {

    /**
     * Builds the index row that is committed after the encrypted container has
     * been fully written and fsynced. The V1.1.2 implementation copied the
     * seed row but forgot to flip [VaultFileStatus.IMPORTING] to
     * [VaultFileStatus.COMPLETE]; every successful import was therefore
     * normalised to FAILED on the next index read.
     */
    fun completed(
        seed: VaultFile,
        totalBytes: Long,
        chunks: Int,
        sha256: String,
        now: Long,
    ): VaultFile = seed.copy(
        sizeBytes = totalBytes.coerceAtLeast(0L),
        chunkCount = chunks.coerceAtLeast(0),
        modifiedAt = now,
        integritySha256 = sha256,
        status = VaultFileStatus.COMPLETE,
        errorMessage = null,
    )

    /**
     * Returns an existing COMPLETE row whose plaintext is byte-identical to the
     * incoming import, or null. SHA-256 is compared case-insensitively; a blank
     * digest (legacy or interrupted row) never matches. Size is compared first
     * so the index scan stays cheap for large vaults.
     */
    fun duplicateOf(
        files: List<VaultFile>,
        sizeBytes: Long,
        sha256: String,
    ): VaultFile? {
        val digest = sha256.trim()
        if (digest.isBlank() || sizeBytes < 0) return null
        return files.firstOrNull { file ->
            file.status == VaultFileStatus.COMPLETE &&
                file.sizeBytes == sizeBytes &&
                file.integritySha256.isNotBlank() &&
                file.integritySha256.equals(digest, ignoreCase = true)
        }
    }

    /**
     * Repairs a row written by V1.1.2. A non-COMPLETE row is only treated as a
     * real successful import when the encrypted container exists *and* the row
     * already carries a whole-file SHA-256. A genuinely interrupted import has
     * an empty digest and no final container (only a swept temporary), so it
     * stays FAILED with an honest explanation.
     */
    fun classifyLegacy(file: VaultFile, ciphertextExists: Boolean): VaultFile {
        if (file.status == VaultFileStatus.COMPLETE) return file
        val finalized = file.integritySha256.isNotBlank() && ciphertextExists
        return if (finalized) {
            file.copy(status = VaultFileStatus.COMPLETE, errorMessage = null)
        } else {
            file.copy(
                status = VaultFileStatus.FAILED,
                errorMessage = file.errorMessage
                    ?: "The import was interrupted before it completed.",
            )
        }
    }
}
