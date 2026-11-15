package com.vinay.nexolock.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 credential verifier.
 *
 * The secret (PIN or volume pattern) is never stored. Only the salt, iteration
 * count and derived verifier are persisted. Verification uses a constant-time
 * comparison. The iteration count is stored alongside each verifier so it can
 * be raised in future versions without invalidating existing credentials.
 */
class PinHasher(private val defaultIterations: Int = DEFAULT_ITERATIONS) {

    data class Verifier(
        val salt: String,
        val hash: String,
        val iterations: Int,
    )

    fun create(secret: CharArray, iterations: Int = defaultIterations): Verifier {
        require(iterations >= MIN_ITERATIONS) { "Iteration count is too low." }
        require(secret.isNotEmpty()) { "Secret must not be empty." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(secret, salt, iterations)
        return try {
            Verifier(
                salt = encoder.encodeToString(salt),
                hash = encoder.encodeToString(derived),
                iterations = iterations,
            )
        } finally {
            derived.fill(0)
        }
    }

    fun verify(secret: CharArray, verifier: Verifier): Boolean {
        if (secret.isEmpty()) return false
        val salt = runCatching { decoder.decode(verifier.salt) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(verifier.hash) }.getOrNull() ?: return false
        val derived = derive(secret, salt, verifier.iterations)
        return try {
            MessageDigest.isEqual(expected, derived)
        } finally {
            derived.fill(0)
            expected.fill(0)
        }
    }

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 600_000
        const val PATTERN_ITERATIONS = 120_000
        const val MIN_ITERATIONS = 1_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"

        private val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
        private val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
