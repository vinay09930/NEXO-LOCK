package com.vinay.nexolock.domain

import java.util.UUID

/**
 * One authentication attempt as a pure, monotonic-time state machine.
 *
 * The configured sequence is always:
 *
 *   DECOY_VISIBLE → BIOMETRIC_AUTHENTICATION → VOLUME_PATTERN → MASTER_PIN → AUTHORIZED
 *
 * A stage that is not configured is skipped (for example biometric is disabled,
 * or the volume pattern was removed). The master PIN is always required.
 *
 * Timing rules:
 *
 *  - Every input stage owns its own deadline. The deadline is fixed when the
 *    stage opens and is never restarted by a key press, a digit, a retry,
 *    recomposition or a configuration change.
 *  - The decoy has a 4 s visibility window. Tapping the card or pressing a
 *    volume key continues immediately; letting the window expire denies access.
 *  - Biometric has no NEXO timer. Android's real BiometricPrompt owns the
 *    interaction. Per the app-level cancellation policy, only a successful
 *    result advances; failure, cancellation or a terminal error denies.
 *  - The volume-pattern and master-PIN stages each have a 15 s window. Expiry
 *    is terminal.
 *  - Time is monotonic. Callers pass SystemClock.elapsedRealtime() (or any
 *    injected monotonic clock in tests).
 */
class AuthenticationSession(
    /** Package whose launch triggered this session, when known. */
    val protectedPackage: String?,
    /**
     * Factors required before the PIN, in any order. The machine normalizes
     * them to the fixed biometric → volume-pattern order. [AuthFactor.PIN] must
     * not be included; the PIN is the final gate.
     */
    configuredFactors: List<AuthFactor>,
    /** Decoy visibility window in milliseconds. */
    val decoyTimeoutMs: Long = DEFAULT_DECOY_TIMEOUT_MS,
    /** Volume-pattern stage window in milliseconds. */
    val gestureTimeoutMs: Long = DEFAULT_GESTURE_TIMEOUT_MS,
    /** Master-PIN stage window in milliseconds. */
    val pinTimeoutMs: Long = DEFAULT_PIN_TIMEOUT_MS,
    /**
     * Optional service-generated lock token. The lock screen uses it so a
     * callback from an old activity can never affect a newer session for the
     * same protected package.
     */
    sessionToken: String? = null,
) {

    init {
        require(decoyTimeoutMs in MIN_DECOY_TIMEOUT_MS..MAX_DECOY_TIMEOUT_MS) {
            "The decoy window must be between 2 and 10 seconds."
        }
        require(gestureTimeoutMs in MIN_INPUT_TIMEOUT_MS..MAX_INPUT_TIMEOUT_MS) {
            "The volume-pattern window must be between 5 and 60 seconds."
        }
        require(pinTimeoutMs in MIN_INPUT_TIMEOUT_MS..MAX_INPUT_TIMEOUT_MS) {
            "The master-PIN window must be between 5 and 60 seconds."
        }
        require(AuthFactor.PIN !in configuredFactors) {
            "The master PIN is the final gate and must not be listed as a pre-PIN factor."
        }
    }

    enum class AuthSessionState {
        IDLE,
        APP_DETECTED,
        DECOY_VISIBLE,
        BIOMETRIC_AUTHENTICATION,
        VOLUME_PATTERN,
        MASTER_PIN,
        AUTHORIZED,
        FAILED,
        TIMED_OUT,
        CANCELLED,
    }

    sealed interface Result {
        /** The stage was accepted; [next] is the factor that must be satisfied now. */
        data class Progress(val next: AuthFactor) : Result
        /** All pre-PIN stages are complete; the master-PIN window is open. */
        data object PinRequired : Result
        data object PinAccepted : Result
        data object PinRejected : Result
        /** The session was denied (biometric failure/cancellation). */
        data object Denied : Result
        /** The current stage's deadline expired; the session is over. */
        data object TimedOut : Result
        data object InvalidTransition : Result
    }

    val sessionId: String = sessionToken?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()

    /** Configured pre-PIN factors, normalized to the fixed biometric → gesture order. */
    val requiredFactors: List<AuthFactor> = buildList {
        if (AuthFactor.BIOMETRIC in configuredFactors) add(AuthFactor.BIOMETRIC)
        if (AuthFactor.GESTURE in configuredFactors) add(AuthFactor.GESTURE)
    }

    val biometricRequired: Boolean get() = AuthFactor.BIOMETRIC in requiredFactors
    val gestureRequired: Boolean get() = AuthFactor.GESTURE in requiredFactors
    val pinOnlyFallback: Boolean get() = requiredFactors.isEmpty()

    var state: AuthSessionState = AuthSessionState.IDLE
        private set

    /** Monotonic time the current stage opened. */
    var stageStartedAtMs: Long = 0L
        private set

    /** Monotonic time the current stage expires; 0 for biometric (no app timer). */
    var stageDeadlineAtMs: Long = 0L
        private set

    /** The current stage's full window; 0 for biometric (no app timer). */
    var stageTimeoutMs: Long = 0L
        private set

    /** Monotonic time the decoy window opened. */
    var decoyStartedAtMs: Long = 0L
        private set

    private val verified = LinkedHashSet<AuthFactor>()

    /** Factors that were deliberately bypassed without being verified. */
    private val skipped = LinkedHashSet<AuthFactor>()

    val terminal: Boolean
        get() = state in TERMINAL_STATES

    /** Immutable snapshot of the factors verified in this session. */
    fun verifiedFactors(): Set<AuthFactor> = verified.toSet()

    /** The factor that must be satisfied right now, or null outside an input stage. */
    val currentFactor: AuthFactor?
        get() = when (state) {
            AuthSessionState.BIOMETRIC_AUTHENTICATION -> AuthFactor.BIOMETRIC
            AuthSessionState.VOLUME_PATTERN -> AuthFactor.GESTURE
            AuthSessionState.MASTER_PIN -> AuthFactor.PIN
            else -> null
        }

    // ------------------------------------------------------------------ transitions

    /** A protected app was detected. The session becomes visible to the runtime. */
    fun appDetected(): Boolean {
        if (state != AuthSessionState.IDLE) return false
        state = AuthSessionState.APP_DETECTED
        return true
    }

    /**
     * The decoy was rendered. THIS starts the 4 s decoy window. Calling it again
     * never restarts the window.
     */
    fun decoyShown(now: Long): Boolean {
        if (state != AuthSessionState.APP_DETECTED && state != AuthSessionState.IDLE) return false
        state = AuthSessionState.DECOY_VISIBLE
        decoyStartedAtMs = now
        startStage(now, decoyTimeoutMs)
        return true
    }

    /**
     * Tap or volume key on the decoy. Continues the SAME decoy window; it never
     * restarts it. If the window already expired, the session is denied.
     */
    fun decoyContinue(now: Long): Result {
        if (state != AuthSessionState.DECOY_VISIBLE) return Result.InvalidTransition
        if (isStageExpired(now)) {
            state = AuthSessionState.TIMED_OUT
            return Result.TimedOut
        }
        return advanceToNextStage(now)
    }

    /**
     * Used when the decoy is disabled: the first configured authentication stage
     * opens immediately, with its own fixed deadline.
     */
    fun authenticateDirect(now: Long): Result {
        if (state != AuthSessionState.APP_DETECTED && state != AuthSessionState.IDLE) {
            return Result.InvalidTransition
        }
        return advanceToNextStage(now)
    }

    /**
     * Genuine BiometricPrompt success. A stale or duplicate callback is rejected
     * because the session is no longer in BIOMETRIC_AUTHENTICATION.
     */
    fun completeBiometric(now: Long): Result {
        if (state != AuthSessionState.BIOMETRIC_AUTHENTICATION) return Result.InvalidTransition
        verified += AuthFactor.BIOMETRIC
        return advanceToNextStage(now)
    }

    /**
     * The biometric stage could not be completed (cancelled, unavailable or a
     * failed read). The factor is NOT recorded as verified: the session simply
     * moves on to the remaining configured factors, which always end at the
     * mandatory master PIN. Self-Protection uses this so a missing or broken
     * sensor cannot lock the owner out of their own app; a protected app's lock
     * screen keeps the stricter terminal policy below.
     */
    fun skipBiometric(now: Long): Result {
        if (state != AuthSessionState.BIOMETRIC_AUTHENTICATION) return Result.InvalidTransition
        skipped += AuthFactor.BIOMETRIC
        return advanceToNextStage(now)
    }

    /** Per policy, a failed biometric result is terminal. */
    fun biometricFailed(): Result {
        if (state != AuthSessionState.BIOMETRIC_AUTHENTICATION) return Result.InvalidTransition
        state = AuthSessionState.FAILED
        return Result.Denied
    }

    /** Per policy, cancelling the system prompt is terminal. */
    fun biometricCancelled(): Result {
        if (state != AuthSessionState.BIOMETRIC_AUTHENTICATION) return Result.InvalidTransition
        state = AuthSessionState.CANCELLED
        return Result.Denied
    }

    /**
     * Records that a physical volume press arrived. It deliberately does NOT
     * move [stageDeadlineAtMs]; the 15 s pattern window starts once and keeps
     * running across every press.
     */
    fun noteGesturePress(now: Long): Boolean {
        if (state != AuthSessionState.VOLUME_PATTERN) return false
        if (isStageExpired(now)) {
            state = AuthSessionState.TIMED_OUT
            return false
        }
        return true
    }

    /** The full configured sequence was entered and matched. */
    fun completeGesture(now: Long): Result {
        if (state != AuthSessionState.VOLUME_PATTERN) return Result.InvalidTransition
        if (isStageExpired(now)) {
            state = AuthSessionState.TIMED_OUT
            return Result.TimedOut
        }
        verified += AuthFactor.GESTURE
        return advanceToNextStage(now)
    }

    /**
     * A wrong PIN leaves the session in MASTER_PIN so the owner can retry inside
     * the same fixed window. The lockout policy throttles attempts separately.
     */
    fun completePin(correct: Boolean, now: Long): Result {
        if (state != AuthSessionState.MASTER_PIN) return Result.InvalidTransition
        if (isStageExpired(now)) {
            state = AuthSessionState.TIMED_OUT
            return Result.TimedOut
        }
        return if (correct) {
            state = AuthSessionState.AUTHORIZED
            Result.PinAccepted
        } else {
            Result.PinRejected
        }
    }

    /**
     * Recovery credentials are an alternative to the master PIN inside the PIN
     * stage. This only advances the state machine: the caller must have already
     * verified every configured recovery factor through the credential
     * repository and checked [AuthPolicy.recoverySatisfied] first.
     */
    fun authorizeByRecovery(): Boolean {
        if (state != AuthSessionState.MASTER_PIN) return false
        state = AuthSessionState.AUTHORIZED
        return true
    }

    /**
     * Called by the stage ticker. Transitions to TIMED_OUT exactly once, when the
     * current stage's fixed deadline is due. Biometric has no app deadline.
     */
    fun expireIfDue(now: Long): Boolean {
        if (state !in TIMED_STAGES) return false
        if (stageDeadlineAtMs == 0L || now < stageDeadlineAtMs) return false
        state = AuthSessionState.TIMED_OUT
        return true
    }

    fun stageTimerRunning(): Boolean =
        state in TIMED_STAGES && stageDeadlineAtMs > 0L

    fun remainingStageMs(now: Long): Long =
        if (stageDeadlineAtMs == 0L) 0L else (stageDeadlineAtMs - now).coerceAtLeast(0L)

    /** Close app / back during any live phase. Terminal, denies access. */
    fun cancel(): Boolean {
        if (terminal) return false
        state = AuthSessionState.CANCELLED
        return true
    }

    /** Session could not complete through no fault of the owner. */
    fun deny(): Boolean {
        if (terminal) return false
        state = AuthSessionState.FAILED
        return true
    }

    // ------------------------------------------------------------------ internals

    private fun advanceToNextStage(now: Long): Result {
        if (biometricRequired &&
            AuthFactor.BIOMETRIC !in verified &&
            AuthFactor.BIOMETRIC !in skipped
        ) {
            state = AuthSessionState.BIOMETRIC_AUTHENTICATION
            // Android owns the biometric interaction; no NEXO timer.
            startStage(now, 0L)
            return Result.Progress(AuthFactor.BIOMETRIC)
        }
        if (gestureRequired && AuthFactor.GESTURE !in verified) {
            state = AuthSessionState.VOLUME_PATTERN
            startStage(now, gestureTimeoutMs)
            return Result.Progress(AuthFactor.GESTURE)
        }
        state = AuthSessionState.MASTER_PIN
        startStage(now, pinTimeoutMs)
        return Result.PinRequired
    }

    private fun startStage(now: Long, timeoutMs: Long) {
        stageStartedAtMs = now
        stageTimeoutMs = timeoutMs
        stageDeadlineAtMs = if (timeoutMs > 0L) now + timeoutMs else 0L
    }

    private fun isStageExpired(now: Long): Boolean =
        stageDeadlineAtMs > 0L && now >= stageDeadlineAtMs

    companion object {
        const val DEFAULT_DECOY_TIMEOUT_MS = 4_000L
        const val DEFAULT_GESTURE_TIMEOUT_MS = 15_000L
        const val DEFAULT_PIN_TIMEOUT_MS = 15_000L

        const val MIN_DECOY_TIMEOUT_MS = 2_000L
        const val MAX_DECOY_TIMEOUT_MS = 10_000L
        const val MIN_INPUT_TIMEOUT_MS = 5_000L
        const val MAX_INPUT_TIMEOUT_MS = 60_000L

        val TERMINAL_STATES = setOf(
            AuthSessionState.AUTHORIZED,
            AuthSessionState.FAILED,
            AuthSessionState.TIMED_OUT,
            AuthSessionState.CANCELLED,
        )

        private val TIMED_STAGES = setOf(
            AuthSessionState.DECOY_VISIBLE,
            AuthSessionState.VOLUME_PATTERN,
            AuthSessionState.MASTER_PIN,
        )

        /** Config seconds are clamped to the documented ranges. */
        fun decoyTimeoutMsFor(seconds: Int): Long =
            (seconds.toLong() * 1000L).coerceIn(MIN_DECOY_TIMEOUT_MS, MAX_DECOY_TIMEOUT_MS)

        fun gestureTimeoutMsFor(seconds: Int): Long =
            (seconds.toLong() * 1000L).coerceIn(MIN_INPUT_TIMEOUT_MS, MAX_INPUT_TIMEOUT_MS)

        fun pinTimeoutMsFor(seconds: Int): Long =
            (seconds.toLong() * 1000L).coerceIn(MIN_INPUT_TIMEOUT_MS, MAX_INPUT_TIMEOUT_MS)
    }
}
