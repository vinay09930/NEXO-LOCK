package com.vinay.nexolock.data

import com.vinay.nexolock.core.crypto.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * The protected package list is not secret in the cryptographic sense, but it
 * reveals what the owner protects. It is therefore encrypted at rest with a
 * non-exportable Keystore key. Package names are newline separated before
 * encryption.
 */
class ProtectedAppsRepository(
    private val prefs: NexoPreferences,
    private val cipher: KeystoreCipher,
) {

    val packages: Flow<Set<String>> = prefs.protectedBlob
        .map { decode(it) }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    suspend fun snapshot(): Set<String> = withContext(Dispatchers.IO) {
        decode(prefs.protectedBlobSnapshot())
    }

    suspend fun setPackages(packages: Set<String>) {
        val payload = packages
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSortedSet()
            .joinToString("\n")
        val encoded = withContext(Dispatchers.Default) {
            if (payload.isEmpty()) "" else {
                Base64.getEncoder().encodeToString(
                    cipher.encrypt(payload.toByteArray(Charsets.UTF_8), AAD),
                )
            }
        }
        prefs.setProtectedBlob(encoded)
    }

    private fun decode(encoded: String?): Set<String> {
        if (encoded.isNullOrBlank()) return emptySet()
        return runCatching {
            val blob = Base64.getDecoder().decode(encoded)
            val plain = cipher.decrypt(blob, AAD)
            String(plain, Charsets.UTF_8)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrElse { emptySet() }
    }

    private companion object {
        val AAD = "com.vinay.nexolock.protected-apps.v1".toByteArray(Charsets.UTF_8)
    }
}
