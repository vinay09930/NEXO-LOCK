package com.vinay.nexolock.domain

import com.vinay.nexolock.data.NexoConfig

/**
 * Every mutation that can weaken protection or expose the owner's configuration.
 *
 * The list is exhaustive on purpose: the centralized guard in
 * `com.vinay.nexolock.security.SecurityAuthorization` refuses an operation it
 * does not know, so a new sensitive action cannot be added silently.
 *
 * Operations marked [requiresFreshAuthorization] must be preceded by a
 * verification that happened recently, not merely by an app session that was
 * opened some time ago. The master PIN remains the mandatory final factor for
 * every one of them; a session alone never authorizes a downgrade.
 */
enum class SecurityOperation(val label: String) {
    DISABLE_PROTECTION("turn App Protection off"),
    REMOVE_PROTECTED_APP("change protected applications"),
    CHANGE_AUTHENTICATION("change authentication methods"),
    CHANGE_RECOVERY("change recovery settings"),
    CHANGE_SECURITY_SETTINGS("change security settings"),
    MODIFY_PROTECTION_RULES("modify protection rules"),
    ;

    /**
     * Turning protection off, changing factors or changing recovery must be
     * confirmed again even inside an already unlocked app session.
     */
    val requiresFreshAuthorization: Boolean
        get() = this == DISABLE_PROTECTION ||
            this == CHANGE_AUTHENTICATION ||
            this == CHANGE_RECOVERY
}

/**
 * Pure Self-Protection rules. Self-Protection is not a second setting that can
 * drift out of sync: it is derived from App Protection, so it is on exactly
 * while App Protection is on, and it is re-derived from persisted DataStore
 * state after every process start and device reboot.
 */
object SelfProtectionPolicy {

    /**
     * NEXO LOCK protects itself whenever the owner has App Protection on and a
     * master PIN exists. The PIN check keeps a half-configured install from
     * locking the owner out of the app they have not finished setting up.
     */
    fun isRequired(config: NexoConfig): Boolean =
        config.protectionEnabled && config.hasMainPin

    /**
     * Pre-PIN factors available for this session. Biometric is included only
     * when it is enabled *and* the sensor can run right now, so an unavailable
     * sensor degrades to the volume pattern and master PIN instead of trapping
     * the owner. The master PIN is never listed here: it is the final gate.
     */
    fun requiredFactors(config: NexoConfig, biometricAvailable: Boolean): List<AuthFactor> = buildList {
        if (config.biometricEnabled && biometricAvailable) add(AuthFactor.BIOMETRIC)
        if (gestureUsable(config)) add(AuthFactor.GESTURE)
    }

    /** A recorded pattern with a corrupt length must never be enforced. */
    fun gestureUsable(config: NexoConfig): Boolean =
        config.gestureEnabled &&
            config.hasMainGesture &&
            config.mainGestureLength in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH
}

/**
 * Whether a step-up request has lost the change it was created for.
 *
 * The request lives in the process-wide authorization checkpoint, while the
 * queued change lives in the management ViewModel. A new ViewModel (for example
 * after the task was removed from recents while the protection service kept the
 * process alive) inherits the request but not the change. Such a request can
 * never be replayed, so it must not keep the gate on screen after a successful
 * verification.
 *
 * [requestInFlight] is true only while the current ViewModel is creating its own
 * request, before the pending change has been bound. That request is fresh, not
 * orphaned.
 */
fun stepUpIsOrphaned(
    stepUp: SecurityOperation?,
    hasPendingChange: Boolean,
    requestInFlight: Boolean,
): Boolean = stepUp != null && !hasPendingChange && !requestInFlight

/**
 * Authorization state for Self-Protection.
 *
 * The gate is a pure, monotonic-time state machine with no Android dependency,
 * so the security rules are unit-tested directly.
 *
 * Rules:
 *  - Nothing is authorized until a real session succeeds. A new process starts
 *    with no authorization, which is what makes "process death" and "device
 *    reboot" require authentication again.
 *  - A grant is bound to a monotonic deadline ([sessionTtlMs]). Reaching the
 *    deadline invalidates it; there is no unlimited session.
 *  - Sensitive operations additionally require a *fresh* grant, within
 *    [freshnessWindowMs]. A session that has been open for minutes is not a
 *    substitute for verifying again.
 *  - [revoke] invalidates the grant immediately. A delayed callback from an
 *    earlier authentication therefore cannot authorize a later change.
 *  - Repeated calls in any order cannot grant access: only [grant] changes
 *    state, and every read is evaluated against the same deadline.
 */
class SelfProtectionGate(
    val sessionTtlMs: Long = DEFAULT_SESSION_TTL_MS,
    val freshnessWindowMs: Long = DEFAULT_FRESHNESS_WINDOW_MS,
) {

    enum class Decision {
        ALLOWED,
        AUTHENTICATION_REQUIRED,
        FRESH_AUTHENTICATION_REQUIRED,
    }

    private var authorizedAtMs = 0L
    private var authorizedUntilMs = 0L

    /** Called only after a completed authentication session. */
    @Synchronized
    fun grant(nowMs: Long) {
        authorizedAtMs = nowMs
        authorizedUntilMs = nowMs + sessionTtlMs
    }

    /** Drop the grant: backgrounding, an expired session, or a completed change. */
    @Synchronized
    fun revoke() {
        authorizedAtMs = 0L
        authorizedUntilMs = 0L
    }

    @Synchronized
    fun isAuthorized(nowMs: Long): Boolean = authorizedLocked(nowMs)

    @Synchronized
    fun isFresh(nowMs: Long): Boolean =
        authorizedLocked(nowMs) && nowMs - authorizedAtMs <= freshnessWindowMs

    /** Age of the current grant; 0 when there is no valid grant. */
    @Synchronized
    fun authorizedForMs(nowMs: Long): Long =
        if (!authorizedLocked(nowMs)) 0L else (nowMs - authorizedAtMs).coerceAtLeast(0L)

    @Synchronized
    fun remainingMs(nowMs: Long): Long =
        if (!authorizedLocked(nowMs)) 0L else (authorizedUntilMs - nowMs).coerceAtLeast(0L)

    /**
     * @param required whether Self-Protection applies at all. When protection is
     *   off there is nothing to defend and the app behaves as it always did.
     */
    @Synchronized
    fun decide(
        operation: SecurityOperation,
        required: Boolean,
        nowMs: Long,
    ): Decision {
        if (!required) return Decision.ALLOWED
        if (!authorizedLocked(nowMs)) return Decision.AUTHENTICATION_REQUIRED
        if (operation.requiresFreshAuthorization && nowMs - authorizedAtMs > freshnessWindowMs) {
            return Decision.FRESH_AUTHENTICATION_REQUIRED
        }
        return Decision.ALLOWED
    }

    private fun authorizedLocked(nowMs: Long): Boolean =
        authorizedUntilMs > 0L && nowMs >= authorizedAtMs && nowMs < authorizedUntilMs

    companion object {
        /** A Self-Protection session never lasts longer than five minutes. */
        const val DEFAULT_SESSION_TTL_MS = 300_000L

        /** Sensitive changes need a verification from the last minute. */
        const val DEFAULT_FRESHNESS_WINDOW_MS = 60_000L
    }
}
