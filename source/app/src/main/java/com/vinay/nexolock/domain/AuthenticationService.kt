package com.vinay.nexolock.domain

import com.vinay.nexolock.data.CredentialRepository
import com.vinay.nexolock.data.NexoPreferences

/**
 * Ties credential verification to the persistent lockout policy. Every call
 * checks the lockout before doing any expensive hashing, and every wrong answer
 * is counted. This is the single place where unlock attempts are admitted.
 */
class AuthenticationService(
    private val prefs: NexoPreferences,
    private val credentials: CredentialRepository,
) {

    data class Attempt(
        val accepted: Boolean,
        val lockedOutMs: Long = 0L,
        val attemptsUntilLockout: Int = 0,
        val failures: Int = 0,
    )

    suspend fun verify(
        kind: CredentialKind,
        secret: String,
        now: Long = System.currentTimeMillis(),
    ): Attempt {
        val scope = scopeFor(kind)
        val state = prefs.failureState(scope)
        val remaining = (state.lockoutUntil - now).coerceAtLeast(0L)
        if (remaining > 0L) {
            return Attempt(
                accepted = false,
                lockedOutMs = remaining,
                attemptsUntilLockout = LockoutPolicy.attemptsUntilFailures(state.failures),
                failures = state.failures,
            )
        }
        val accepted = credentials.verify(kind, secret)
        if (accepted) {
            prefs.clearFailures(scope)
            return Attempt(accepted = true, attemptsUntilLockout = LockoutPolicy.attemptsUntilFailures(0))
        }
        val afterFailure = prefs.registerFailure(scope, now)
        return Attempt(
            accepted = false,
            lockedOutMs = (afterFailure.lockoutUntil - now).coerceAtLeast(0L),
            attemptsUntilLockout = afterFailure.attemptsUntilLockout,
            failures = afterFailure.failures,
        )
    }

    suspend fun lockoutRemaining(recovery: Boolean, now: Long = System.currentTimeMillis()): Long {
        val scope = if (recovery) SCOPE_RECOVERY else SCOPE_MAIN
        return prefs.clearLockout(scope, now)
    }

    suspend fun lockoutRemaining(kind: CredentialKind, now: Long = System.currentTimeMillis()): Long =
        prefs.clearLockout(scopeFor(kind), now)

    suspend fun attemptsLeft(recovery: Boolean): Int {
        val state = prefs.failureState(if (recovery) SCOPE_RECOVERY else SCOPE_MAIN)
        return LockoutPolicy.attemptsUntilFailures(state.failures)
    }

    /**
     * Vault credentials and app-unlock credentials never share a failure budget.
     * Main PIN and the main volume gesture intentionally share "main": together
     * they are the app-unlock factor family.
     */
    private fun scopeFor(kind: CredentialKind): String = when (kind) {
        CredentialKind.MAIN_PIN,
        CredentialKind.MAIN_GESTURE,
        -> SCOPE_MAIN

        CredentialKind.RECOVERY_PIN,
        CredentialKind.RECOVERY_PATTERN,
        -> SCOPE_RECOVERY

        CredentialKind.PERSONAL_VAULT_PIN,
        CredentialKind.INTRUDER_VAULT_PIN,
        -> kind.prefix
    }

    companion object {
        private const val SCOPE_MAIN = "main"
        private const val SCOPE_RECOVERY = "recovery"
    }
}
