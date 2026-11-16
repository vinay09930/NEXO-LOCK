package com.vinay.nexolock

import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.domain.LockoutPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPolicyTest {

    @Test
    fun pinMustBeFourToEightDigits() {
        assertTrue(AuthPolicy.validPin("1234"))
        assertTrue(AuthPolicy.validPin("12345678"))
        assertFalse(AuthPolicy.validPin("123"))
        assertFalse(AuthPolicy.validPin("123456789"))
        assertFalse(AuthPolicy.validPin("12a4"))
        assertFalse(AuthPolicy.validPin(""))
    }

    @Test
    fun recoveryPatternRequiresFourDistinctDots() {
        assertTrue(AuthPolicy.validPattern("0123"))
        assertTrue(AuthPolicy.validPattern("0482"))
        assertFalse(AuthPolicy.validPattern("012"))
        assertFalse(AuthPolicy.validPattern("0012"))
        assertFalse(AuthPolicy.validPattern("0189"))
        assertFalse(AuthPolicy.validPattern(""))
        assertFalse(AuthPolicy.validPattern("0123456789"))
    }

    @Test
    fun recoveryRequiresTheRecoveryPin() {
        assertTrue(AuthPolicy.canUseRecovery(hasRecoveryPin = true))
        assertFalse(AuthPolicy.canUseRecovery(hasRecoveryPin = false))
        assertTrue(AuthPolicy.recoverySteps(false, true).isEmpty())
    }

    @Test
    fun recoveryIsCompleteOnlyWhenEveryConfiguredFactorWasVerified() {
        assertTrue(
            AuthPolicy.recoverySatisfied(
                hasRecoveryPin = true,
                hasRecoveryPattern = false,
                verified = setOf(CredentialKind.RECOVERY_PIN),
            ),
        )
        assertFalse(
            AuthPolicy.recoverySatisfied(
                hasRecoveryPin = true,
                hasRecoveryPattern = false,
                verified = emptySet(),
            ),
        )
        assertFalse(
            AuthPolicy.recoverySatisfied(
                hasRecoveryPin = true,
                hasRecoveryPattern = true,
                verified = setOf(CredentialKind.RECOVERY_PIN),
            ),
        )
        assertTrue(
            AuthPolicy.recoverySatisfied(
                hasRecoveryPin = true,
                hasRecoveryPattern = true,
                verified = setOf(CredentialKind.RECOVERY_PIN, CredentialKind.RECOVERY_PATTERN),
            ),
        )
        // A pattern alone is never enough, and a half-configured install cannot
        // authorize recovery at all.
        assertFalse(
            AuthPolicy.recoverySatisfied(
                hasRecoveryPin = false,
                hasRecoveryPattern = true,
                verified = setOf(CredentialKind.RECOVERY_PATTERN),
            ),
        )
    }

    @Test
    fun aCorruptPersistedPinLengthIsClampedToTheUsableRange() {
        assertEquals(AuthPolicy.MIN_PIN_LENGTH, AuthPolicy.safePinLength(0))
        assertEquals(AuthPolicy.MIN_PIN_LENGTH, AuthPolicy.safePinLength(-5))
        assertEquals(6, AuthPolicy.safePinLength(6))
        assertEquals(AuthPolicy.MAX_PIN_LENGTH, AuthPolicy.safePinLength(99))
    }

    @Test
    fun recoveryPatternIsASecondFactor() {
        val steps = AuthPolicy.recoverySteps(hasRecoveryPin = true, hasRecoveryPattern = true)
        assertEquals(2, steps.size)
        assertEquals(AuthPolicy.RecoveryStep.RECOVERY_PIN, steps[0])
        assertEquals(AuthPolicy.RecoveryStep.RECOVERY_PATTERN, steps[1])

        val pinOnly = AuthPolicy.recoverySteps(hasRecoveryPin = true, hasRecoveryPattern = false)
        assertEquals(listOf(AuthPolicy.RecoveryStep.RECOVERY_PIN), pinOnly)
    }

    @Test
    fun strictModeRequiresPinAfterBiometricAndGesture() {
        assertEquals(AuthPolicy.AfterFactor.Unlock, AuthPolicy.afterBiometric(strictMode = false))
        assertEquals(AuthPolicy.AfterFactor.RequirePin, AuthPolicy.afterBiometric(strictMode = true))
        assertEquals(AuthPolicy.AfterFactor.Unlock, AuthPolicy.afterGesture(strictMode = false))
        assertEquals(AuthPolicy.AfterFactor.RequirePin, AuthPolicy.afterGesture(strictMode = true))
    }

    @Test
    fun recoveryPinMustDifferFromMainPin() {
        assertTrue(AuthPolicy.validNewRecoveryPin("4321", isEqualToMainPin = false))
        assertFalse(AuthPolicy.validNewRecoveryPin("4321", isEqualToMainPin = true))
        assertFalse(AuthPolicy.validNewRecoveryPin("12", isEqualToMainPin = false))
    }

    @Test
    fun lockoutDelaysEscalateAtTheDocumentedThresholds() {
        assertEquals(0L, LockoutPolicy.delayForFailures(0))
        assertEquals(0L, LockoutPolicy.delayForFailures(4))
        assertEquals(30_000L, LockoutPolicy.delayForFailures(5))
        assertEquals(30_000L, LockoutPolicy.delayForFailures(7))
        assertEquals(300_000L, LockoutPolicy.delayForFailures(8))
        assertEquals(300_000L, LockoutPolicy.delayForFailures(11))
        assertEquals(1_800_000L, LockoutPolicy.delayForFailures(12))
        assertEquals(1_800_000L, LockoutPolicy.delayForFailures(99))
    }

    @Test
    fun attemptsUntilLockoutTracksTheThresholds() {
        assertEquals(5, LockoutPolicy.attemptsUntilFailures(0))
        assertEquals(4, LockoutPolicy.attemptsUntilFailures(1))
        assertEquals(1, LockoutPolicy.attemptsUntilFailures(4))
        assertEquals(3, LockoutPolicy.attemptsUntilFailures(5))
        assertEquals(1, LockoutPolicy.attemptsUntilFailures(7))
        assertEquals(4, LockoutPolicy.attemptsUntilFailures(8))
        assertEquals(0, LockoutPolicy.attemptsUntilFailures(12))
    }
}
