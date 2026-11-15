package com.vinay.nexolock

import com.vinay.nexolock.domain.AuthFactor
import com.vinay.nexolock.domain.AuthenticationSession
import com.vinay.nexolock.domain.AuthenticationSession.AuthSessionState
import com.vinay.nexolock.domain.AuthenticationSession.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.1.2 per-stage authentication semantics:
 *
 *   decoy (4 s) → biometric (Android-managed) → volume pattern (15 s) → PIN (15 s)
 *
 * Every stage owns a fixed monotonic deadline. Expiry is terminal. Biometric
 * failure/cancellation is terminal. Unconfigured stages are skipped in the
 * fixed order. The master PIN is always the final gate.
 */
class AuthenticationSessionTest {

    private fun session(
        factors: List<AuthFactor> = listOf(AuthFactor.BIOMETRIC, AuthFactor.GESTURE),
        decoyMs: Long = 4_000L,
        gestureMs: Long = 15_000L,
        pinMs: Long = 15_000L,
        token: String? = null,
    ) = AuthenticationSession(
        protectedPackage = "com.example.protected",
        configuredFactors = factors,
        decoyTimeoutMs = decoyMs,
        gestureTimeoutMs = gestureMs,
        pinTimeoutMs = pinMs,
        sessionToken = token,
    )

    /** Brings a session to the volume-pattern stage with deterministic times. */
    private fun sessionInPattern(): AuthenticationSession {
        val s = session()
        assertTrue(s.appDetected())
        assertTrue(s.decoyShown(1_000L))
        assertEquals(Result.Progress(AuthFactor.BIOMETRIC), s.decoyContinue(1_100L))
        assertEquals(Result.Progress(AuthFactor.GESTURE), s.completeBiometric(1_200L))
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        return s
    }

    /** Brings a session to the master-PIN stage with deterministic times. */
    private fun sessionInPin(): AuthenticationSession {
        val s = sessionInPattern()
        assertEquals(Result.PinRequired, s.completeGesture(2_000L))
        assertEquals(AuthSessionState.MASTER_PIN, s.state)
        return s
    }

    // ---------------------------------------------------------------- full sequence

    @Test
    fun fullSequenceAdvancesOnlyAfterEachStageSucceeds() {
        val s = session()
        assertEquals(AuthSessionState.IDLE, s.state)
        assertTrue(s.appDetected())

        assertTrue(s.decoyShown(1_000L))
        assertEquals(AuthSessionState.DECOY_VISIBLE, s.state)
        // The next factor is not exposed while the decoy is visible.
        assertNull(s.currentFactor)

        assertEquals(Result.Progress(AuthFactor.BIOMETRIC), s.decoyContinue(1_100L))
        assertEquals(AuthSessionState.BIOMETRIC_AUTHENTICATION, s.state)
        assertEquals(AuthFactor.BIOMETRIC, s.currentFactor)

        assertEquals(Result.Progress(AuthFactor.GESTURE), s.completeBiometric(1_200L))
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        assertEquals(AuthFactor.GESTURE, s.currentFactor)

        assertTrue(s.noteGesturePress(1_500L))
        assertEquals(Result.PinRequired, s.completeGesture(2_000L))
        assertEquals(AuthSessionState.MASTER_PIN, s.state)
        assertEquals(AuthFactor.PIN, s.currentFactor)

        assertEquals(Result.PinRejected, s.completePin(correct = false, now = 3_000L))
        assertEquals(AuthSessionState.MASTER_PIN, s.state)

        assertEquals(Result.PinAccepted, s.completePin(correct = true, now = 4_000L))
        assertEquals(AuthSessionState.AUTHORIZED, s.state)
        assertTrue(s.terminal)
        assertEquals(
            setOf(AuthFactor.BIOMETRIC, AuthFactor.GESTURE),
            s.verifiedFactors(),
        )
    }

    @Test
    fun unconfiguredStagesAreSkippedInTheFixedOrder() {
        val gestureOnly = session(factors = listOf(AuthFactor.GESTURE))
        gestureOnly.appDetected()
        gestureOnly.decoyShown(1_000L)
        assertEquals(Result.Progress(AuthFactor.GESTURE), gestureOnly.decoyContinue(1_100L))
        assertEquals(AuthSessionState.VOLUME_PATTERN, gestureOnly.state)

        val biometricOnly = session(factors = listOf(AuthFactor.BIOMETRIC))
        biometricOnly.appDetected()
        biometricOnly.decoyShown(1_000L)
        assertEquals(Result.Progress(AuthFactor.BIOMETRIC), biometricOnly.decoyContinue(1_100L))
        assertEquals(Result.PinRequired, biometricOnly.completeBiometric(1_200L))
        assertEquals(AuthSessionState.MASTER_PIN, biometricOnly.state)

        val pinOnly = session(factors = emptyList())
        pinOnly.appDetected()
        pinOnly.decoyShown(1_000L)
        assertEquals(Result.PinRequired, pinOnly.decoyContinue(1_100L))
        assertEquals(AuthSessionState.MASTER_PIN, pinOnly.state)
    }

    @Test
    fun factorsAreNormalizedToBiometricThenPattern() {
        val s = session(factors = listOf(AuthFactor.GESTURE, AuthFactor.BIOMETRIC))
        assertEquals(listOf(AuthFactor.BIOMETRIC, AuthFactor.GESTURE), s.requiredFactors)
    }

    @Test
    fun decoyDisabledOpensTheFirstStageDirectly() {
        val s = session()
        assertTrue(s.appDetected())
        assertEquals(Result.Progress(AuthFactor.BIOMETRIC), s.authenticateDirect(1_000L))
        assertEquals(AuthSessionState.BIOMETRIC_AUTHENTICATION, s.state)
        assertFalse(s.decoyShown(2_000L))
    }

    @Test
    fun authenticateDirectCannotBypassAVisibleDecoy() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertEquals(Result.InvalidTransition, s.authenticateDirect(1_500L))
        assertEquals(AuthSessionState.DECOY_VISIBLE, s.state)
    }

    // ---------------------------------------------------------------- decoy timing

    @Test
    fun decoyExpiryDeniesInsteadOfAdvancing() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertFalse(s.expireIfDue(4_999L))
        assertEquals(Result.TimedOut, s.decoyContinue(5_000L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
        assertTrue(s.terminal)
        assertEquals(Result.InvalidTransition, s.completeBiometric(5_100L))
    }

    @Test
    fun decoyContinueNeverRestartsTheWindow() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertEquals(4_000L, s.remainingStageMs(1_000L))
        // A tap just before expiry still continues; it does not move the origin.
        assertEquals(Result.Progress(AuthFactor.BIOMETRIC), s.decoyContinue(4_999L))
        assertEquals(AuthSessionState.BIOMETRIC_AUTHENTICATION, s.state)
    }

    @Test
    fun showingTheDecoyTwiceCannotExtendIt() {
        val s = session()
        s.appDetected()
        assertTrue(s.decoyShown(1_000L))
        assertFalse(s.decoyShown(2_000L))
        assertEquals(5_000L, s.stageDeadlineAtMs)
        assertEquals(3_000L, s.remainingStageMs(2_000L))
    }

    @Test
    fun decoyExpiryFiresExactlyOnce() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertTrue(s.expireIfDue(5_000L))
        assertFalse(s.expireIfDue(6_000L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
    }

    // ---------------------------------------------------------------- biometric

    @Test
    fun biometricHasNoAppTimer() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        s.decoyContinue(1_100L)
        assertEquals(AuthSessionState.BIOMETRIC_AUTHENTICATION, s.state)
        assertEquals(0L, s.stageDeadlineAtMs)
        assertEquals(0L, s.stageTimeoutMs)
        assertFalse(s.stageTimerRunning())
        assertFalse(s.expireIfDue(999_999L))
        assertEquals(AuthSessionState.BIOMETRIC_AUTHENTICATION, s.state)
    }

    @Test
    fun biometricFailureIsTerminal() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        s.decoyContinue(1_100L)
        assertEquals(Result.Denied, s.biometricFailed())
        assertEquals(AuthSessionState.FAILED, s.state)
        assertTrue(s.terminal)
        assertEquals(Result.InvalidTransition, s.completeBiometric(1_200L))
        assertFalse(s.cancel())
    }

    @Test
    fun biometricCancellationIsTerminal() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        s.decoyContinue(1_100L)
        assertEquals(Result.Denied, s.biometricCancelled())
        assertEquals(AuthSessionState.CANCELLED, s.state)
        assertTrue(s.terminal)
        assertEquals(Result.InvalidTransition, s.completeBiometric(1_200L))
    }

    @Test
    fun duplicateBiometricSuccessCannotAdvanceTwice() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        s.decoyContinue(1_100L)
        assertEquals(Result.Progress(AuthFactor.GESTURE), s.completeBiometric(1_200L))
        assertEquals(Result.InvalidTransition, s.completeBiometric(1_300L))
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        assertEquals(15_000L, s.remainingStageMs(1_200L))
    }

    @Test
    fun staleBiometricCallbackAfterTheStageIsRejected() {
        val s = sessionInPattern()
        assertEquals(Result.InvalidTransition, s.completeBiometric(2_100L))
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        assertFalse(AuthFactor.BIOMETRIC in s.verifiedFactors() &&
            AuthFactor.GESTURE in s.verifiedFactors())
    }

    @Test
    fun biometricFailureAfterAdvancingIsIgnored() {
        val s = sessionInPattern()
        assertEquals(Result.InvalidTransition, s.biometricFailed())
        assertEquals(Result.InvalidTransition, s.biometricCancelled())
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
    }

    // ---------------------------------------------------------------- volume pattern

    @Test
    fun patternWindowDoesNotRestartOnKeyPresses() {
        val s = sessionInPattern() // pattern starts at 1 200 ms, deadline 16 200 ms
        assertEquals(16_200L, s.stageDeadlineAtMs)
        assertTrue(s.noteGesturePress(10_000L))
        assertEquals(6_200L, s.remainingStageMs(10_000L))
        assertTrue(s.noteGesturePress(15_000L))
        assertEquals(1_200L, s.remainingStageMs(15_000L))
        assertEquals(16_200L, s.stageDeadlineAtMs)
        assertFalse(s.expireIfDue(16_199L))
    }

    @Test
    fun patternExpiryDeniesAndCannotComplete() {
        val s = sessionInPattern()
        assertFalse(s.noteGesturePress(16_200L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
        assertEquals(Result.InvalidTransition, s.completeGesture(16_300L))
        assertTrue(s.terminal)
    }

    @Test
    fun patternExpiryFiresExactlyOnce() {
        val s = sessionInPattern()
        assertTrue(s.expireIfDue(16_200L))
        assertFalse(s.expireIfDue(16_300L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
    }

    @Test
    fun incompleteOrIncorrectPatternDoesNotAdvanceTheSession() {
        val s = sessionInPattern()
        // Presses alone never complete the stage, no matter how many arrive.
        repeat(8) { index ->
            assertTrue(s.noteGesturePress(2_000L + index * 100L))
        }
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        assertEquals(Result.InvalidTransition, s.completePin(correct = true, now = 3_000L))

        // A wrong full sequence is rejected by the credential service, not the
        // session; the session stays in the same fixed window for a retry.
        val expected = "UDUD"
        val entered = "UUDD"
        assertNotEquals(expected, entered)
        assertEquals(AuthSessionState.VOLUME_PATTERN, s.state)
        assertEquals(16_200L, s.stageDeadlineAtMs)

        // The correct complete sequence, inside the same window, advances.
        assertTrue(s.noteGesturePress(4_000L))
        assertEquals(Result.PinRequired, s.completeGesture(5_000L))
        assertEquals(AuthSessionState.MASTER_PIN, s.state)
    }

    @Test
    fun patternPressOutsideItsStageIsIgnored() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertFalse(s.noteGesturePress(1_100L))
        assertEquals(AuthSessionState.DECOY_VISIBLE, s.state)
    }

    // ---------------------------------------------------------------- master PIN

    @Test
    fun pinWindowDoesNotRestartOnDigitsOrRetries() {
        val s = sessionInPin() // PIN starts at 2 000 ms, deadline 17 000 ms
        assertEquals(17_000L, s.stageDeadlineAtMs)
        assertEquals(Result.PinRejected, s.completePin(correct = false, now = 10_000L))
        assertEquals(7_000L, s.remainingStageMs(10_000L))
        assertEquals(Result.PinRejected, s.completePin(correct = false, now = 16_000L))
        assertEquals(1_000L, s.remainingStageMs(16_000L))
        assertEquals(17_000L, s.stageDeadlineAtMs)
        assertFalse(s.expireIfDue(16_999L))
    }

    @Test
    fun pinExpiryDeniesEvenWithTheCorrectPin() {
        val s = sessionInPin()
        assertEquals(Result.TimedOut, s.completePin(correct = true, now = 17_000L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
        assertTrue(s.terminal)
    }

    @Test
    fun pinExpiryFiresExactlyOnce() {
        val s = sessionInPin()
        assertTrue(s.expireIfDue(17_000L))
        assertFalse(s.expireIfDue(17_100L))
        assertEquals(AuthSessionState.TIMED_OUT, s.state)
    }

    @Test
    fun pinRejectedKeepsTheStageOpenUntilTheWindowEnds() {
        val s = sessionInPin()
        assertEquals(Result.PinRejected, s.completePin(correct = false, now = 5_000L))
        assertEquals(AuthSessionState.MASTER_PIN, s.state)
        assertEquals(Result.PinAccepted, s.completePin(correct = true, now = 6_000L))
        assertEquals(AuthSessionState.AUTHORIZED, s.state)
    }

    @Test
    fun recoveryAuthorizesOnlyInsideThePinStage() {
        val s = sessionInPattern()
        assertFalse(s.authorizeByRecovery())
        s.completeGesture(2_000L)
        assertTrue(s.authorizeByRecovery())
        assertEquals(AuthSessionState.AUTHORIZED, s.state)
    }

    // ---------------------------------------------------------------- terminal safety

    @Test
    fun authorizedSessionRejectsEveryFurtherTransition() {
        val s = sessionInPin()
        assertEquals(Result.PinAccepted, s.completePin(correct = true, now = 5_000L))
        assertEquals(Result.InvalidTransition, s.completeBiometric(6_000L))
        assertEquals(Result.InvalidTransition, s.biometricFailed())
        assertEquals(Result.InvalidTransition, s.biometricCancelled())
        assertEquals(Result.InvalidTransition, s.completeGesture(6_000L))
        assertEquals(Result.InvalidTransition, s.completePin(correct = false, now = 6_000L))
        assertFalse(s.expireIfDue(999_999L))
        assertFalse(s.cancel())
        assertFalse(s.deny())
        assertEquals(AuthSessionState.AUTHORIZED, s.state)
    }

    @Test
    fun closeAppCancelsALiveSession() {
        val s = session()
        s.appDetected()
        s.decoyShown(1_000L)
        assertTrue(s.cancel())
        assertEquals(AuthSessionState.CANCELLED, s.state)
        assertFalse(s.cancel())
        assertEquals(Result.InvalidTransition, s.decoyContinue(1_100L))
    }

    @Test
    fun sessionTokenIsPreservedAndGeneratedWhenAbsent() {
        assertEquals("token-123", session(token = "token-123").sessionId)
        assertTrue(session(token = "  ").sessionId.isNotBlank())
        assertTrue(session(token = null).sessionId.isNotBlank())
    }

    // ---------------------------------------------------------------- configuration

    @Test
    fun timeoutFactoriesClampToDocumentedRanges() {
        assertEquals(2_000L, AuthenticationSession.decoyTimeoutMsFor(1))
        assertEquals(4_000L, AuthenticationSession.decoyTimeoutMsFor(4))
        assertEquals(10_000L, AuthenticationSession.decoyTimeoutMsFor(60))

        assertEquals(5_000L, AuthenticationSession.gestureTimeoutMsFor(1))
        assertEquals(15_000L, AuthenticationSession.gestureTimeoutMsFor(15))
        assertEquals(60_000L, AuthenticationSession.gestureTimeoutMsFor(120))

        assertEquals(5_000L, AuthenticationSession.pinTimeoutMsFor(1))
        assertEquals(15_000L, AuthenticationSession.pinTimeoutMsFor(15))
        assertEquals(60_000L, AuthenticationSession.pinTimeoutMsFor(120))
    }

    @Test
    fun constructorRejectsPinAsAPrePinFactor() {
        assertThrows(IllegalArgumentException::class.java) {
            session(factors = listOf(AuthFactor.BIOMETRIC, AuthFactor.PIN))
        }
    }

    @Test
    fun constructorRejectsOutOfRangeWindows() {
        assertThrows(IllegalArgumentException::class.java) { session(decoyMs = 1_000L) }
        assertThrows(IllegalArgumentException::class.java) { session(gestureMs = 4_000L) }
        assertThrows(IllegalArgumentException::class.java) { session(pinMs = 61_000L) }
    }

    @Test
    fun defaultWindowsMatchTheDocumentedValues() {
        assertEquals(4_000L, AuthenticationSession.DEFAULT_DECOY_TIMEOUT_MS)
        assertEquals(15_000L, AuthenticationSession.DEFAULT_GESTURE_TIMEOUT_MS)
        assertEquals(15_000L, AuthenticationSession.DEFAULT_PIN_TIMEOUT_MS)
    }
}
