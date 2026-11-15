package com.vinay.nexolock.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM backed by a non-exportable Android Keystore key.
 *
 * Used to encrypt the protected-app list and the security log. Payload format:
 * 12-byte random IV followed by the ciphertext (which includes the GCM tag).
 */
class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) {

    class CryptoUnavailableException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(plain: ByteArray, aad: ByteArray = DEFAULT_AAD): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The key is generated with setRandomizedEncryptionRequired(true), so
        // the Keystore provider must generate the IV itself. Passing an IV
        // here throws InvalidAlgorithmParameterException on real devices.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return iv + encrypted
    }

    fun decrypt(blob: ByteArray, aad: ByteArray = DEFAULT_AAD): ByteArray {
        if (blob.size < IV_BYTES + TAG_BYTES) {
            throw CryptoUnavailableException("Encrypted payload is incomplete or unauthenticated.")
        }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    fun encryptString(value: String, aad: ByteArray = DEFAULT_AAD): ByteArray =
        encrypt(value.toByteArray(Charsets.UTF_8), aad)

    fun decryptString(blob: ByteArray, aad: ByteArray = DEFAULT_AAD): String =
        String(decrypt(blob, aad), Charsets.UTF_8)

    fun isAvailable(): Boolean = runCatching { key() }.isSuccess

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val key = loadOrCreateKey()
            cachedKey = key
            return key
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        try {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        } catch (error: Exception) {
            throw CryptoUnavailableException("The Android Keystore key is unavailable.", error)
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "nexo.v3.store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = 16
        private val DEFAULT_AAD = "com.vinay.nexolock.store.v1".toByteArray(Charsets.UTF_8)
    }
}
