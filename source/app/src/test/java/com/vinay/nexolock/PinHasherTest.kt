package com.vinay.nexolock

import com.vinay.nexolock.core.crypto.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun verifierAcceptsCorrectSecret() {
        val verifier = hasher.create("482913".toCharArray(), iterations = 1_000)
        assertTrue(hasher.verify("482913".toCharArray(), verifier))
        assertFalse(hasher.verify("482914".toCharArray(), verifier))
        assertFalse(hasher.verify("48291".toCharArray(), verifier))
        assertFalse(hasher.verify("".toCharArray(), verifier))
    }

    @Test
    fun verifierNeverContainsTheSecret() {
        val verifier = hasher.create("778899".toCharArray(), iterations = 1_000)
        assertFalse(verifier.salt.contains("778899"))
        assertFalse(verifier.hash.contains("778899"))
        assertNotEquals("778899", verifier.hash)
    }

    @Test
    fun saltIsRandomPerVerifier() {
        val first = hasher.create("123456".toCharArray(), iterations = 1_000)
        val second = hasher.create("123456".toCharArray(), iterations = 1_000)
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(hasher.verify("123456".toCharArray(), first))
        assertTrue(hasher.verify("123456".toCharArray(), second))
    }

    @Test
    fun iterationsArePersistedWithTheVerifier() {
        val verifier = hasher.create("0000".toCharArray(), iterations = 2_500)
        assertTrue(verifier.iterations == 2_500)
        assertTrue(hasher.verify("0000".toCharArray(), verifier))
    }

    @Test
    fun emptySecretIsRejectedAtCreation() {
        try {
            hasher.create("".toCharArray(), iterations = 1_000)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun weakIterationCountIsRejected() {
        try {
            hasher.create("1234".toCharArray(), iterations = 10)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun corruptedVerifierIsRejectedInsteadOfThrowing() {
        val valid = hasher.create("246810".toCharArray(), iterations = 1_000)
        val corrupted = valid.copy(hash = "not-base64!!")
        assertFalse(hasher.verify("246810".toCharArray(), corrupted))
    }
}
