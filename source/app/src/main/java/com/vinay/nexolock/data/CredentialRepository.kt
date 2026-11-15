package com.vinay.nexolock.data

import com.vinay.nexolock.core.crypto.PinHasher
import com.vinay.nexolock.domain.CredentialKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates and verifies credential records. Hashing is CPU heavy and always runs
 * off the main thread. The caller's plaintext is wiped from the temporary char
 * array after use.
 */
class CredentialRepository(
    private val prefs: NexoPreferences,
    private val hasher: PinHasher,
) {

    suspend fun store(kind: CredentialKind, secret: String) {
        val chars = secret.toCharArray()
        try {
            val iterations = if (kind.pattern) {
                PinHasher.PATTERN_ITERATIONS
            } else {
                PinHasher.DEFAULT_ITERATIONS
            }
            val verifier = withContext(Dispatchers.Default) { hasher.create(chars, iterations) }
            prefs.saveVerifier(kind, verifier)
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun verify(kind: CredentialKind, secret: String): Boolean {
        val chars = secret.toCharArray()
        return try {
            val verifier = prefs.verifierSnapshot(kind) ?: return false
            withContext(Dispatchers.Default) { hasher.verify(chars, verifier) }
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun exists(kind: CredentialKind): Boolean = prefs.verifierSnapshot(kind) != null

    suspend fun clear(kind: CredentialKind) = prefs.clearVerifier(kind)
}
