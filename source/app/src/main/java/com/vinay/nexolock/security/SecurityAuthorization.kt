package com.vinay.nexolock.security

import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoPreferences
import com.vinay.nexolock.data.SecurityLogRepository
import com.vinay.nexolock.domain.SecurityOperation
import com.vinay.nexolock.domain.SelfProtectionGate
import com.vinay.nexolock.domain.SelfProtectionPolicy
import com.vinay.nexolock.domain.UnlockMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single authorization checkpoint for NEXO LOCK's own interface.
 *
 * Nothing in the UI "hides" a control and calls it done: every sensitive
 * mutation runs through [mutate], which reads the persisted configuration to
 * decide whether Self-Protection applies, evaluates the pure
 * [SelfProtectionGate], and only then runs the caller's write.
 *
 * The mutex serializes all sensitive writes. Two rapid requests therefore
 * cannot interleave a check and a write, and a request that arrives after
 * [revoke] observes the revoked gate rather than a stale one.
 *
 * A grant can only come from [authorize], which callers invoke after a real
 * [com.vinay.nexolock.domain.AuthenticationSession] reaches AUTHORIZED with the
 * owner's configured factors. There is no method that grants access from a
 * boolean, a package name, an intent extra or a persisted flag.
 */
class SecurityAuthorization(
    private val prefs: NexoPreferences,
    private val logs: SecurityLogRepository,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val gate: SelfProtectionGate = SelfProtectionGate(),
) {

    /** Observable authorization state. Never persisted: a new process starts locked. */
    data class Snapshot(
        val required: Boolean = false,
        val authorized: Boolean = false,
        val remainingMs: Long = 0L,
        /** Non-null while the UI must ask the owner to verify again. */
        val stepUp: SecurityOperation? = null,
        /**
         * Identifies the current step-up request. A queued change is replayed
         * only when this value still matches the request it was queued for.
         */
        val stepUpId: Long = 0L,
        /**
         * Identifies the current grant. A queued change is replayed only after
         * a grant newer than the one that was already present at request time.
         */
        val grantId: Long = 0L,
    )

    /** The request id and grant id a queued change must be bound to. */
    data class StepUpRequest(val id: Long, val grantId: Long)

    sealed interface Outcome {
        data object Applied : Outcome
        data object AuthenticationRequired : Outcome
        data object FreshAuthenticationRequired : Outcome

        /** The write itself failed; the guard did not swallow the exception. */
        data class Failed(val message: String) : Outcome
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val mutations = Mutex()

    /**
     * Serializes the revocation epoch with the pre-write re-check. A change
     * that has not started its write when [revoke] runs cannot start it after.
     */
    private val revocationLock = Any()
    @Volatile
    private var revocationEpoch = 0L
    @Volatile
    private var grantGeneration = 0L
    private var nextStepUpId = 0L
    private var ticker: Job? = null

    /** Records that the owner completed a Self-Protection session. */
    fun authorize(method: UnlockMethod) {
        synchronized(revocationLock) {
            grantGeneration += 1
        }
        gate.grant(nowMs())
        publish()
        startTicker()
        scope.launch {
            logs.log(
                LogType.SELF_PROTECTION_GRANTED,
                "Self-Protection verified with ${method.label}",
                "NEXO LOCK",
            )
        }
    }

    /** Drops the grant. Called on backgrounding, expiry and explicit lock. */
    fun revoke(reason: String? = null) {
        val wasAuthorized: Boolean
        synchronized(revocationLock) {
            revocationEpoch += 1
            wasAuthorized = gate.isAuthorized(nowMs())
            gate.revoke()
            stopTicker()
        }
        _state.update {
            it.copy(authorized = false, remainingMs = 0L, stepUp = null, stepUpId = 0L)
        }
        if (wasAuthorized) {
            scope.launch {
                logs.log(
                    LogType.SELF_PROTECTION_LOCKED,
                    reason ?: "Self-Protection locked",
                    "NEXO LOCK",
                )
            }
        }
    }

    /**
     * Marks a guarded change that needs a fresh verification and returns the
     * request id the caller must attach to its queued change. The step-up and
     * its id are set before the published state is refreshed, so a collector
     * never observes an authorized session with a stale request id.
     */
    fun requestStepUp(operation: SecurityOperation): StepUpRequest {
        val request = synchronized(revocationLock) {
            nextStepUpId += 1
            StepUpRequest(id = nextStepUpId, grantId = grantGeneration)
        }
        _state.update { it.copy(stepUp = operation, stepUpId = request.id) }
        publish()
        return request
    }

    fun clearStepUp() {
        _state.update { it.copy(stepUp = null, stepUpId = 0L) }
    }

    /**
     * Clears only the step-up that still has [requestId]. A late observer must
     * never clear a newer request that replaced the one it saw.
     */
    fun clearStepUpIfCurrent(requestId: Long) {
        _state.update { current ->
            if (current.stepUpId == requestId) {
                current.copy(stepUp = null, stepUpId = 0L)
            } else {
                current
            }
        }
    }

    fun isAuthorized(): Boolean = gate.isAuthorized(nowMs())

    /** How long the current grant has been alive; 0 when there is none. */
    fun authorizedForMs(): Long = gate.authorizedForMs(nowMs())

    /**
     * Runs [block] only when the operation is authorized right now.
     *
     * The requirement is read from persisted state on every call rather than
     * cached, so a stale in-memory flag can never authorize a change. A failed
     * read fails closed.
     */
    suspend fun mutate(operation: SecurityOperation, block: suspend () -> Unit): Outcome =
        mutations.withLock {
            val epoch = revocationEpoch
            when (val decision = decide(operation)) {
                SelfProtectionGate.Decision.ALLOWED -> {
                    // A revoke that arrived after decide() but before the write
                    // must win. The persisted requirement and the gate are
                    // re-evaluated immediately before the write under the same
                    // lock revoke() uses. This also covers the case where
                    // protection was off at decide() and turned on in between:
                    // the fresh requirement then demands a grant.
                    val stillRequired = runCatching {
                        SelfProtectionPolicy.isRequired(prefs.snapshot())
                    }.getOrDefault(true)
                    val currentDecision = synchronized(revocationLock) {
                        if (stillRequired && epoch != revocationEpoch) {
                            SelfProtectionGate.Decision.AUTHENTICATION_REQUIRED
                        } else {
                            gate.decide(operation, stillRequired, nowMs())
                        }
                    }
                    when (currentDecision) {
                        SelfProtectionGate.Decision.ALLOWED -> try {
                            block()
                            publish()
                            Outcome.Applied
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            publish()
                            scope.launch {
                                logs.log(
                                    LogType.SENSITIVE_CHANGE_FAILED,
                                    "Sensitive change failed (${operation.label}): " +
                                        (error.message ?: error.javaClass.simpleName),
                                )
                            }
                            Outcome.Failed(error.message ?: "The change could not be saved.")
                        }

                        SelfProtectionGate.Decision.AUTHENTICATION_REQUIRED -> {
                            publish()
                            Outcome.AuthenticationRequired
                        }

                        SelfProtectionGate.Decision.FRESH_AUTHENTICATION_REQUIRED -> {
                            publish()
                            Outcome.FreshAuthenticationRequired
                        }
                    }
                }

                SelfProtectionGate.Decision.AUTHENTICATION_REQUIRED -> {
                    publish()
                    Outcome.AuthenticationRequired
                }

                SelfProtectionGate.Decision.FRESH_AUTHENTICATION_REQUIRED -> {
                    publish()
                    Outcome.FreshAuthenticationRequired
                }
            }
        }

    private suspend fun decide(operation: SecurityOperation): SelfProtectionGate.Decision {
        val config = runCatching { prefs.snapshot() }.getOrNull()
            ?: return SelfProtectionGate.Decision.AUTHENTICATION_REQUIRED
        val required = SelfProtectionPolicy.isRequired(config)
        _state.update { it.copy(required = required) }
        return gate.decide(operation, required, nowMs())
    }

    private fun publish() {
        val now = nowMs()
        val authorized = gate.isAuthorized(now)
        val required = runCatching { SelfProtectionPolicy.isRequired(currentConfig()) }.getOrDefault(false)
        _state.update {
            it.copy(
                required = required,
                authorized = authorized,
                remainingMs = gate.remainingMs(now),
                grantId = grantGeneration,
            )
        }
    }

    private var cachedConfig = com.vinay.nexolock.data.NexoConfig()

    /**
     * Best-effort synchronous mirror of the persisted flags, kept current by
     * [observeConfig]. Used only for publishing UI state; every authorization
     * decision re-reads the persisted snapshot.
     */
    private fun currentConfig() = cachedConfig

    /** Keeps the published `required` flag accurate without gating on it. */
    fun observeConfig() {
        scope.launch {
            var wasRequired = false
            prefs.config.collect { config ->
                cachedConfig = config
                val required = SelfProtectionPolicy.isRequired(config)
                // Turning protection off invalidates any grant issued while it
                // was on. Re-enabling protection in the same process must not
                // inherit that old authorization.
                if (wasRequired && !required) {
                    revoke("App Protection turned off")
                }
                wasRequired = required
                _state.update {
                    it.copy(
                        required = required,
                        authorized = gate.isAuthorized(nowMs()),
                    )
                }
            }
        }
    }

    /**
     * Re-evaluates expiry continuously while a grant is live, so the UI locks
     * itself the moment the session deadline passes instead of waiting for the
     * next user action.
     */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (!gate.isAuthorized(nowMs())) {
                    stopTicker()
                    _state.update {
                        it.copy(
                            authorized = false,
                            remainingMs = 0L,
                            stepUp = null,
                            stepUpId = 0L,
                        )
                    }
                    logs.log(
                        LogType.SELF_PROTECTION_LOCKED,
                        "Self-Protection session expired",
                        "NEXO LOCK",
                    )
                    return@launch
                }
                publish()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
