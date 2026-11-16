package com.vinay.nexolock

import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.domain.AuthFactor
import com.vinay.nexolock.domain.SecurityOperation
import com.vinay.nexolock.domain.SelfProtectionPolicy
import com.vinay.nexolock.domain.stepUpIsOrphaned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-Protection is derived from App Protection, never a second flag. These
 * tests pin that down together with the factor set the gate enforces.
 */
class SelfProtectionPolicyTest {

    private fun config(
        protectionEnabled: Boolean = true,
        hasMainPin: Boolean = true,
        biometricEnabled: Boolean = false,
        gestureEnabled: Boolean = true,
        hasMainGesture: Boolean = true,
        gestureLength: Int = 5,
        setupComplete: Boolean = true,
    ) = NexoConfig(
        setupComplete = setupComplete,
        protectionEnabled = protectionEnabled,
        hasMainPin = hasMainPin,
        biometricEnabled = biometricEnabled,
        gestureEnabled = gestureEnabled,
        hasMainGesture = hasMainGesture,
        mainGestureLength = gestureLength,
    )

    // ---------------------------------------------------------------- requirement

    @Test
    fun selfProtectionIsRequiredExactlyWhileAppProtectionIsOn() {
        assertTrue(SelfProtectionPolicy.isRequired(config(protectionEnabled = true)))
        assertFalse(SelfProtectionPolicy.isRequired(config(protectionEnabled = false)))
    }

    @Test
    fun aHalfConfiguredInstallIsNeverLockedOut() {
        // App Protection cannot run without a master PIN; the policy must agree
        // so a partially written state cannot trap the owner.
        assertFalse(SelfProtectionPolicy.isRequired(config(hasMainPin = false)))
    }

    @Test
    fun setupCompletenessDoesNotChangeTheRequirement() {
        // The protection flag is the source of truth across restarts and reboots.
        assertTrue(SelfProtectionPolicy.isRequired(config(setupComplete = false)))
    }

    // ---------------------------------------------------------------- factors

    @Test
    fun biometricIsOnlyRequiredWhenItCanActuallyRun() {
        val enabled = config(biometricEnabled = true)
        assertEquals(
            listOf(AuthFactor.BIOMETRIC, AuthFactor.GESTURE),
            SelfProtectionPolicy.requiredFactors(enabled, biometricAvailable = true),
        )
        // A missing or locked sensor degrades to the remaining factors instead
        // of denying the owner their own app.
        assertEquals(
            listOf(AuthFactor.GESTURE),
            SelfProtectionPolicy.requiredFactors(enabled, biometricAvailable = false),
        )
    }

    @Test
    fun aCorruptGestureLengthIsNeverEnforced() {
        val corrupt = config(gestureLength = 2)
        assertFalse(SelfProtectionPolicy.gestureUsable(corrupt))
        assertEquals(
            emptyList<AuthFactor>(),
            SelfProtectionPolicy.requiredFactors(corrupt, biometricAvailable = false),
        )
    }

    @Test
    fun aDisabledGestureIsNotRequiredEvenWhenRecorded() {
        val enabled = SelfProtectionPolicy.requiredFactors(
            config(gestureEnabled = true, hasMainGesture = true),
            biometricAvailable = false,
        )
        assertEquals(listOf(AuthFactor.GESTURE), enabled)

        val disabled = SelfProtectionPolicy.requiredFactors(
            config(gestureEnabled = false, hasMainGesture = true),
            biometricAvailable = false,
        )
        assertTrue("A disabled pattern must not be enforced", disabled.isEmpty())
    }

    @Test
    fun theMasterPinIsNeverListedAsAPrePinFactor() {
        val required = SelfProtectionPolicy.requiredFactors(
            config(biometricEnabled = true),
            biometricAvailable = true,
        )
        assertFalse(AuthFactor.PIN in required)
    }

    // ---------------------------------------------------------------- step-up ownership

    @Test
    fun aStepUpWithoutItsQueuedChangeIsOrphaned() {
        // The request is process-wide, the change is ViewModel-scoped. After the
        // task is removed from recents the request can survive without the
        // change, and it must not keep the gate up forever.
        assertTrue(
            stepUpIsOrphaned(
                stepUp = SecurityOperation.CHANGE_SECURITY_SETTINGS,
                hasPendingChange = false,
                requestInFlight = false,
            ),
        )
    }

    @Test
    fun aStepUpWithItsQueuedChangeIsNotOrphaned() {
        assertFalse(
            stepUpIsOrphaned(
                stepUp = SecurityOperation.DISABLE_PROTECTION,
                hasPendingChange = true,
                requestInFlight = false,
            ),
        )
    }

    @Test
    fun aFreshRequestIsNotOrphanedBeforeItsChangeIsBound() {
        // The authorization state flow can deliver the new request while
        // guardedChange is still binding the pending change to it.
        assertFalse(
            stepUpIsOrphaned(
                stepUp = SecurityOperation.CHANGE_AUTHENTICATION,
                hasPendingChange = false,
                requestInFlight = true,
            ),
        )
    }

    @Test
    fun noStepUpIsNeverOrphaned() {
        assertFalse(
            stepUpIsOrphaned(
                stepUp = null,
                hasPendingChange = false,
                requestInFlight = false,
            ),
        )
    }
}
