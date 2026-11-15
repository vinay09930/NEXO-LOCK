package com.vinay.nexolock.domain

/**
 * Pure authentication rules. Keeping these out of the ViewModel makes the
 * security behaviour directly unit-testable.
 */
object AuthPolicy {

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

    fun validPin(pin: String): Boolean =
        pin.length in 4..8 && pin.all { it in '0'..'9' }

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
