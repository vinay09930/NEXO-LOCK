package com.vinay.nexolock.domain

/**
 * Pure authentication rules. Keeping these out of the ViewModel makes the
 * security behaviour directly unit-testable.
 */
object AuthPolicy {

    /** The only PIN lengths the setup and credential screens can create. */
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    sealed interface AfterFactor {
        data object Unlock : AfterFactor
        data object RequirePin : AfterFactor
    }

    enum class RecoveryStep { RECOVERY_PIN, RECOVERY_PATTERN }

    fun afterBiometric(strictMode: Boolean): AfterFactor =
        if (strictMode) AfterFactor.RequirePin else AfterFactor.Unlock

    fun afterGesture(strictMode: Boolean): AfterFactor =
        if (strictMode) AfterFactor.RequirePin else AfterFactor.Unlock

    /** Recovery always starts with the recovery PIN; the pattern is an additional step. */
    fun recoverySteps(hasRecoveryPin: Boolean, hasRecoveryPattern: Boolean): List<RecoveryStep> {
        if (!hasRecoveryPin) return emptyList()
        return buildList {
            add(RecoveryStep.RECOVERY_PIN)
            if (hasRecoveryPattern) add(RecoveryStep.RECOVERY_PATTERN)
        }
    }

    fun canUseRecovery(hasRecoveryPin: Boolean): Boolean = hasRecoveryPin

    /**
     * Recovery is complete only when every configured recovery factor was
     * verified by the repository in the current flow. The recovery PIN is
     * mandatory; the pattern is a second factor when one is configured.
     */
    fun recoverySatisfied(
        hasRecoveryPin: Boolean,
        hasRecoveryPattern: Boolean,
        verified: Set<CredentialKind>,
    ): Boolean {
        val steps = recoverySteps(hasRecoveryPin, hasRecoveryPattern)
        if (steps.isEmpty()) return false
        return steps.all { step ->
            when (step) {
                RecoveryStep.RECOVERY_PIN -> CredentialKind.RECOVERY_PIN in verified
                RecoveryStep.RECOVERY_PATTERN -> CredentialKind.RECOVERY_PATTERN in verified
            }
        }
    }

    /** A corrupted persisted length must never produce an unusable PIN pad. */
    fun safePinLength(length: Int): Int = length.coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH)

    fun validPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * Recovery pattern is a 3x3 dot sequence (index 0..8). At least 4 dots are
     * required and a dot cannot be used twice, mirroring platform guidance.
     */
    fun validPattern(pattern: String): Boolean =
        pattern.length in 4..9 &&
            pattern.all { it in '0'..'8' } &&
            pattern.toSet().size == pattern.length

    /**
     * The recovery PIN must be different from the main PIN. Equality is checked
     * against the stored verifier by the caller, never against a stored value.
     */
    fun validNewRecoveryPin(pin: String, isEqualToMainPin: Boolean): Boolean =
        validPin(pin) && !isEqualToMainPin
}
