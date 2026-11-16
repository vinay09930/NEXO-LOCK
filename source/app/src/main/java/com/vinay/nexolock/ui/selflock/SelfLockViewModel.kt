package com.vinay.nexolock.ui.selflock

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vinay.nexolock.AppContainer
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.domain.AuthFactor
import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.domain.AuthenticationSession
import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.domain.LockoutPolicy
import com.vinay.nexolock.domain.SelfProtectionPolicy
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.domain.VolumeCapture
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One explicit Self-Protection stage. */
sealed interface SelfLockPhase {
    /** The session is being built from persisted configuration. */
    data object Preparing : SelfLockPhase
    data object Biometric : SelfLockPhase
    data object VolumePattern : SelfLockPhase
    data object MasterPin : SelfLockPhase
    data class Denied(val message: String) : SelfLockPhase
    data object Authorized : SelfLockPhase
}

data class SelfLockUiState(
    val phase: SelfLockPhase = SelfLockPhase.Preparing,
    /** Pre-PIN factors this session enforces, in the fixed order. */
    val requiredFactors: List<AuthFactor> = emptyList(),
    val completedFactors: Set<AuthFactor> = emptySet(),
    val pinLength: Int = 6,
    val pin: String = "",
    val gestureLength: Int = 0,
    val gestureFilled: Int = 0,
    val stageRemainingMs: Long = 0L,
    val stageTotalMs: Long = 0L,
    val stageTimerActive: Boolean = false,
    val showRecovery: Boolean = false,
    val hasRecoveryPin: Boolean = false,
    val hasRecoveryPattern: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricReady: Boolean = false,
    /** Increments when a fresh system prompt must be shown. */
    val biometricRequest: Int = 0,
    val lockoutRemainingMs: Long = 0L,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val factorNotice: String? = null,
) {
    val authorized: Boolean get() = phase is SelfLockPhase.Authorized
    val deniedMessage: String? get() = (phase as? SelfLockPhase.Denied)?.message
    val lockedOut: Boolean get() = lockoutRemainingMs > 0L

    val activeFactor: AuthFactor?
        get() = when (phase) {
            SelfLockPhase.Biometric -> AuthFactor.BIOMETRIC
            SelfLockPhase.VolumePattern -> AuthFactor.GESTURE
            SelfLockPhase.MasterPin -> AuthFactor.PIN
            else -> null
        }
}

/**
 * Authentication for NEXO LOCK's own interface.
 *
 * It reuses the existing security engine rather than inventing a second one:
 * [AuthenticationSession] enforces the fixed factor order and per-stage
 * deadlines, [com.vinay.nexolock.domain.AuthenticationService] applies
 * verification and the shared lockout policy, [VolumeCapture] collects the
 * volume pattern, and the shared [com.vinay.nexolock.ui.lock.RecoveryFlow]
 * verifies recovery credentials.
 *
 * Differences from a protected app's lock screen are deliberate and honest:
 *  - No decoy. The decoy exists to hide another app's content; hiding NEXO LOCK
 *    from its own owner would only obstruct legitimate access.
 *  - The master PIN fallback is never blocked by a missing sensor: an
 *    unavailable biometric is dropped from the required factors and the stage
 *    can be skipped, because the PIN is still mandatory.
 *
 * Nothing here grants access on its own: a completed session calls
 * [com.vinay.nexolock.security.SecurityAuthorization.authorize], and the
 * business layer re-checks that grant before any change is written.
 */
class SelfLockViewModel(
    private val container: AppContainer,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _state = MutableStateFlow(SelfLockUiState())
    val state: StateFlow<SelfLockUiState> = _state.asStateFlow()

    private var config = NexoConfig()
    private var session: AuthenticationSession? = null
    private var capture: VolumeCapture? = null
    private var captureLength = 0
    private var preparing = false
    private var surfaceVisible = false
    private var prepareJob: Job? = null
    private var stageJob: Job? = null
    private var lockoutJob: Job? = null
    private val volumeOwner = Any()

    /**
     * Recovery factors verified by the repository in the current recovery
     * flow. The UI's success callback is not trusted on its own: it can only
     * complete when this set covers every configured recovery factor.
     */
    private var recoveryGeneration = 0L
    private val recoveryVerified = linkedSetOf<CredentialKind>()

    /**
     * Start (or resume) the self-authentication surface. Idempotent: a
     * configuration change or recomposition resumes the same fixed deadlines
     * instead of restarting a stage.
     */
    fun begin() {
        if (preparing) return
        val current = _state.value
        // A denial stays on screen until the owner chooses "Try again"; a
        // recomposition must not silently start a fresh attempt.
        if (current.deniedMessage != null) return
        val existing = session
        if (existing != null && current.phase !is SelfLockPhase.Authorized) {
            if (surfaceVisible) startSurface()
            return
        }
        preparing = true
        prepareJob = viewModelScope.launch {
            try {
                config = container.prefs.snapshot()
                prepareSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Configuration could not be read: fail closed and say so
                // instead of leaving the gate on a spinner forever.
                deny("Your security settings could not be read. Reopen NEXO LOCK.")
            } finally {
                preparing = false
            }
        }
    }

    /** The first Compose frame is on screen; stage windows may start now. */
    fun onSurfaceVisible() {
        surfaceVisible = true
        if (session != null) {
            startSurface()
        } else if (!preparing && _state.value.deniedMessage == null) {
            begin()
        }
    }

    /** Called when the gate leaves composition or the owner closes the app. */
    fun endSession() {
        prepareJob?.cancel()
        prepareJob = null
        stageJob?.cancel()
        stageJob = null
        lockoutJob?.cancel()
        lockoutJob = null
        capture?.reset()
        capture = null
        container.volumeCapture.end(volumeOwner)
        session = null
        surfaceVisible = false
        preparing = false
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.value = SelfLockUiState()
    }

    /** Explicit retry after a denial. Every credential budget still applies. */
    fun retry() {
        endSession()
        // The gate is still on screen, so the first stage may open at once.
        surfaceVisible = true
        begin()
    }

    // ------------------------------------------------------------------ setup

    private suspend fun prepareSession() {
        val biometricAvailable = canUseBiometric()
        captureLength = config.mainGestureLength.takeIf {
            it in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH
        } ?: 0
        val required = SelfProtectionPolicy.requiredFactors(config, biometricAvailable)
        val activeSession = AuthenticationSession(
            protectedPackage = container.appContext.packageName,
            configuredFactors = required,
            // The decoy stage is never opened for Self-Protection; the value
            // still has to be inside the documented range.
            decoyTimeoutMs = AuthenticationSession.DEFAULT_DECOY_TIMEOUT_MS,
            gestureTimeoutMs = AuthenticationSession.gestureTimeoutMsFor(config.gestureTimeoutSeconds),
            pinTimeoutMs = AuthenticationSession.pinTimeoutMsFor(config.pinTimeoutSeconds),
        )
        session = activeSession
        activeSession.appDetected()
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.value = SelfLockUiState(
            phase = SelfLockPhase.Preparing,
            requiredFactors = required,
            pinLength = AuthPolicy.safePinLength(config.pinLength),
            gestureLength = captureLength,
            hasRecoveryPin = config.hasRecoveryPin,
            hasRecoveryPattern = config.hasRecoveryPattern,
            biometricEnabled = config.biometricEnabled && biometricAvailable,
            biometricReady = biometricAvailable,
            factorNotice = factorNotice(required, biometricAvailable),
        )
        container.logs.log(
            LogType.AUTH_SESSION_STARTED,
            "Self-Protection session started: " +
                (required.joinToString(" → ") { it.label }.ifBlank { "master PIN only" }) +
                " → master PIN",
            "NEXO LOCK",
        )
        val remaining = container.auth.lockoutRemaining(recovery = false)
        if (remaining > 0L) startLockoutCountdown(remaining)
        if (surfaceVisible) startSurface()
    }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(container.appContext)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun factorNotice(required: List<AuthFactor>, biometricAvailable: Boolean): String? =
        when {
            config.biometricEnabled && !biometricAvailable ->
                "Biometric is enabled but unavailable right now, so this session uses the " +
                    "remaining factors and your master PIN."

            required.isEmpty() ->
                "Only the master PIN is configured for app unlocking."

            AuthFactor.GESTURE !in required ->
                "This session uses biometric and your master PIN."

            else -> null
        }

    // ------------------------------------------------------------------ stages

    private fun startSurface() {
        val active = session ?: return
        val current = _state.value
        if (current.authorized || current.deniedMessage != null) return
        when (active.state) {
            AuthenticationSession.AuthSessionState.AUTHORIZED -> {
                // The state machine finished but the UI did not receive the
                // completion (for example after a recomposition). Re-issue the
                // grant from the already-verified session.
                complete(unlockMethodFor())
                return
            }

            AuthenticationSession.AuthSessionState.FAILED,
            AuthenticationSession.AuthSessionState.TIMED_OUT,
            AuthenticationSession.AuthSessionState.CANCELLED,
            -> {
                deny("The Self-Protection session ended. Try again.")
                return
            }

            AuthenticationSession.AuthSessionState.APP_DETECTED -> Unit
            else -> {
                applyStage(active, log = false)
                return
            }
        }
        when (active.authenticateDirect(nowMs())) {
            is AuthenticationSession.Result.Progress,
            AuthenticationSession.Result.PinRequired,
            -> applyStage(active, log = true)

            else -> deny("Self-Protection could not start an authentication session.")
        }
    }

    private fun applyStage(active: AuthenticationSession, log: Boolean) {
        val current = _state.value
        val now = nowMs()
        val enteringBiometric = active.state ==
            AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION &&
            current.phase !is SelfLockPhase.Biometric
        val enteringPattern = active.state ==
            AuthenticationSession.AuthSessionState.VOLUME_PATTERN &&
            current.phase !is SelfLockPhase.VolumePattern

        val newPhase: SelfLockPhase = when (active.state) {
            AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION ->
                SelfLockPhase.Biometric

            AuthenticationSession.AuthSessionState.VOLUME_PATTERN ->
                SelfLockPhase.VolumePattern

            AuthenticationSession.AuthSessionState.MASTER_PIN ->
                (current.phase as? SelfLockPhase.MasterPin) ?: SelfLockPhase.MasterPin

            AuthenticationSession.AuthSessionState.AUTHORIZED -> SelfLockPhase.Authorized
            else -> current.phase
        }

        if (enteringPattern && captureLength in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH) {
            capture = VolumeCapture.createOrNull(captureLength)
            container.volumeCapture.begin(volumeOwner, ::onVolumePress)
        }
        if (newPhase !is SelfLockPhase.VolumePattern) {
            container.volumeCapture.end(volumeOwner)
        }

        _state.update {
            it.copy(
                phase = newPhase,
                completedFactors = active.verifiedFactors(),
                stageRemainingMs = active.remainingStageMs(now),
                stageTotalMs = active.stageTimeoutMs,
                stageTimerActive = active.stageTimerRunning(),
                pin = if (active.state == AuthenticationSession.AuthSessionState.MASTER_PIN) {
                    it.pin
                } else {
                    ""
                },
                gestureFilled = if (active.state ==
                    AuthenticationSession.AuthSessionState.VOLUME_PATTERN
                ) {
                    it.gestureFilled
                } else {
                    0
                },
                busy = false,
                error = null,
                info = stageInfo(active),
                biometricRequest = if (enteringBiometric) it.biometricRequest + 1 else it.biometricRequest,
            )
        }
        if (log) {
            viewModelScope.launch {
                container.logs.log(
                    LogType.AUTH_FACTOR_OK,
                    "Self-Protection stage opened: ${stageLabel(active.state)}",
                    "NEXO LOCK",
                )
            }
        }
        if (active.stageTimerRunning()) startStageTicker()
    }

    private fun stageInfo(active: AuthenticationSession): String? = when (active.state) {
        AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION ->
            "Android owns the biometric prompt. You can fall back to the remaining factors."

        AuthenticationSession.AuthSessionState.VOLUME_PATTERN ->
            "Enter your ${captureLength}-press volume pattern within " +
                "${config.gestureTimeoutSeconds}s. The window does not restart on a key press."

        AuthenticationSession.AuthSessionState.MASTER_PIN ->
            "Enter your master PIN within ${config.pinTimeoutSeconds}s. " +
                "The window does not restart on a digit or a retry."

        else -> null
    }

    private fun stageLabel(state: AuthenticationSession.AuthSessionState): String = when (state) {
        AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION -> "biometric"
        AuthenticationSession.AuthSessionState.VOLUME_PATTERN -> "volume pattern"
        AuthenticationSession.AuthSessionState.MASTER_PIN -> "master PIN"
        else -> "authentication"
    }

    private fun startStageTicker() {
        stageJob?.cancel()
        val active = session ?: return
        if (!active.stageTimerRunning()) return
        stageJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                val current = session ?: return@launch
                val label = stageLabel(current.state)
                if (current.state != AuthenticationSession.AuthSessionState.AUTHORIZED &&
                    current.expireIfDue(nowMs())
                ) {
                    deny("The $label step timed out. Self-Protection is still locked.")
                    return@launch
                }
                if (!current.stageTimerRunning()) return@launch
                _state.update { it.copy(stageRemainingMs = current.remainingStageMs(nowMs())) }
            }
        }
    }

    // ------------------------------------------------------------------ PIN

    fun onDigit(digit: Int) {
        val current = _state.value
        if (current.busy || current.lockedOut || current.showRecovery) return
        if (current.phase !is SelfLockPhase.MasterPin || current.deniedMessage != null) return
        if (current.pin.length >= current.pinLength) return
        val next = current.pin + digit
        if (next.length >= current.pinLength) {
            _state.update { it.copy(pin = next, busy = true) }
            submitPin(next)
        } else {
            _state.update { it.copy(pin = next) }
        }
    }

    fun onBackspace() {
        _state.update { current ->
            if (current.busy) current else current.copy(pin = current.pin.dropLast(1))
        }
    }

    private fun submitPin(pin: String) {
        // Judge the stage at the moment the last digit was entered. PBKDF2
        // verification takes real time on a phone, and checking the deadline
        // after it made a correct PIN entered inside the window fail as a
        // timeout: the owner was thrown back to the gate right after typing a
        // valid PIN. The ticker is paused for the same reason.
        val submittedAt = nowMs()
        stageJob?.cancel()
        stageJob = null
        viewModelScope.launch {
            val active = session ?: return@launch
            if (_state.value.phase !is SelfLockPhase.MasterPin) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            if (active.expireIfDue(submittedAt)) {
                deny("The master PIN step timed out. Self-Protection is still locked.")
                return@launch
            }
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, pin)
            if (attempt.accepted) {
                when (active.completePin(correct = true, now = submittedAt)) {
                    AuthenticationSession.Result.PinAccepted -> complete(unlockMethodFor())
                    AuthenticationSession.Result.TimedOut ->
                        deny("The master PIN step timed out. Self-Protection is still locked.")

                    else -> _state.update { it.copy(busy = false) }
                }
                return@launch
            }
            container.logs.log(
                LogType.MAIN_PIN_FAILED,
                "Wrong master PIN at the Self-Protection gate",
                "NEXO LOCK",
            )
            val pinResult = active.completePin(correct = false, now = submittedAt)
            if (pinResult == AuthenticationSession.Result.TimedOut) {
                deny("The master PIN step timed out. Self-Protection is still locked.")
                return@launch
            }
            if (attempt.lockedOutMs > 0L) {
                startLockoutCountdown(attempt.lockedOutMs)
                _state.update {
                    it.copy(
                        pin = "",
                        busy = false,
                        error = "Too many attempts. Locked for ${LockoutPolicy.describe(attempt.lockedOutMs)}.",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        pin = "",
                        busy = false,
                        error = "Incorrect PIN. ${attempt.attemptsUntilLockout} attempts left before a delay.",
                    )
                }
            }
            // A wrong PIN leaves the same stage open, so its fixed deadline must
            // keep running; it was paused only while verification ran.
            if (active.stageTimerRunning()) startStageTicker()
        }
    }

    // ------------------------------------------------------------------ biometric

    private fun biometricStageActive(): Boolean =
        _state.value.phase is SelfLockPhase.Biometric && _state.value.deniedMessage == null

    fun onBiometricSucceeded() {
        if (!biometricStageActive()) return
        val active = session ?: return
        viewModelScope.launch {
            container.logs.log(
                LogType.BIOMETRIC_SUCCESS,
                "Biometric verified for Self-Protection",
                "NEXO LOCK",
            )
        }
        when (active.completeBiometric(nowMs())) {
            is AuthenticationSession.Result.Progress,
            AuthenticationSession.Result.PinRequired,
            -> applyStage(active, log = true)

            else -> _state.update { it.copy(busy = false) }
        }
    }

    /**
     * A biometric prompt could not be completed. Unlike a protected app's lock
     * screen this is not terminal: the factor is skipped and the remaining
     * factors (which end at the mandatory master PIN) continue.
     */
    fun onBiometricUnavailable(reason: String) {
        if (!biometricStageActive()) return
        val active = session ?: return
        viewModelScope.launch {
            container.logs.log(
                LogType.BIOMETRIC_UNAVAILABLE,
                "Biometric skipped at the Self-Protection gate: $reason",
                "NEXO LOCK",
            )
        }
        capture?.reset()
        _state.update {
            it.copy(
                biometricEnabled = false,
                info = "Biometric was skipped ($reason). Continue with the remaining factors.",
            )
        }
        when (active.skipBiometric(nowMs())) {
            is AuthenticationSession.Result.Progress,
            AuthenticationSession.Result.PinRequired,
            -> applyStage(active, log = true)

            else -> _state.update { it.copy(busy = false) }
        }
    }

    /** The owner asked for the system prompt again from the gate screen. */
    fun manualBiometric() {
        if (!biometricStageActive()) return
        _state.update { it.copy(biometricRequest = it.biometricRequest + 1) }
    }

    fun onBiometricError(code: Int, message: String) {
        val friendly = when (code) {
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "no biometric sensor"
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "the sensor is temporarily unavailable"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "no biometric is enrolled"
            BiometricPrompt.ERROR_LOCKOUT -> "biometrics are temporarily locked by Android"
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                "biometrics are locked until the device PIN is used"

            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            -> "the prompt was cancelled"

            BiometricPrompt.ERROR_TIMEOUT -> "the prompt timed out"
            else -> message.ifBlank { "biometric authentication was not completed" }
        }
        onBiometricUnavailable(friendly)
    }

    // ------------------------------------------------------------------ volume pattern

    fun onVolumePress(press: VolumePress) {
        val current = _state.value
        if (current.busy || current.lockedOut || current.showRecovery || current.authorized) return
        if (current.phase !is SelfLockPhase.VolumePattern) return
        val active = session ?: return
        if (!active.noteGesturePress(nowMs())) {
            deny("The volume-pattern step timed out. Self-Protection is still locked.")
            return
        }
        val captureState = capture ?: VolumeCapture.createOrNull(captureLength)?.also { capture = it }
            ?: return
        when (val result = captureState.press(press, nowMs())) {
            is VolumeCapture.Result.Progress -> _state.update {
                it.copy(
                    gestureFilled = result.count,
                    error = null,
                    info = "Volume pattern: ${result.count} of ${result.expected} presses. " +
                        "The ${config.gestureTimeoutSeconds}s window keeps running.",
                )
            }

            is VolumeCapture.Result.Complete -> {
                _state.update { it.copy(gestureFilled = 0, busy = true) }
                verifyGesture(result.pattern)
            }
        }
    }

    fun resetGesture() {
        capture?.reset()
        _state.update { it.copy(gestureFilled = 0) }
    }

    private fun verifyGesture(pattern: VolumePattern) {
        // Same rule as the PIN: the last press decides whether the stage was
        // still inside its window, not the hashing time that follows it.
        val pressedAt = nowMs()
        stageJob?.cancel()
        stageJob = null
        viewModelScope.launch {
            val active = session ?: return@launch
            if (_state.value.phase !is SelfLockPhase.VolumePattern) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            if (!active.noteGesturePress(pressedAt)) {
                deny("The volume-pattern step timed out. Self-Protection is still locked.")
                return@launch
            }
            val attempt = container.auth.verify(CredentialKind.MAIN_GESTURE, pattern.code)
            if (attempt.accepted) {
                when (active.completeGesture(pressedAt)) {
                    is AuthenticationSession.Result.Progress,
                    AuthenticationSession.Result.PinRequired,
                    -> applyStage(active, log = true)

                    AuthenticationSession.Result.TimedOut ->
                        deny("The volume-pattern step timed out. Self-Protection is still locked.")

                    else -> _state.update { it.copy(busy = false) }
                }
            } else {
                capture?.reset()
                container.logs.log(
                    LogType.GESTURE_FAILED,
                    "Wrong volume pattern at the Self-Protection gate",
                    "NEXO LOCK",
                )
                val message = if (attempt.lockedOutMs > 0L) {
                    startLockoutCountdown(attempt.lockedOutMs)
                    "Too many attempts. Locked for ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                } else {
                    "Incorrect volume pattern. ${attempt.attemptsUntilLockout} attempts left before a delay."
                }
                _state.update { it.copy(busy = false, gestureFilled = 0, error = message) }
                if (active.stageTimerRunning()) startStageTicker()
            }
        }
    }

    // ------------------------------------------------------------------ recovery

    fun openRecovery() {
        if (_state.value.phase !is SelfLockPhase.MasterPin) return
        // Recovery is an alternative path with its own lockout policy. The
        // master-PIN countdown must not replace the recovery screen halfway
        // through a legitimate recovery attempt.
        stageJob?.cancel()
        stageJob = null
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.update {
            it.copy(
                showRecovery = true,
                error = null,
                info = null,
                pin = "",
                gestureFilled = 0,
            )
        }
        capture?.reset()
        container.volumeCapture.end(volumeOwner)
    }

    fun closeRecovery() {
        if (_state.value.phase !is SelfLockPhase.MasterPin) return
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.update { it.copy(showRecovery = false, error = null) }
        val active = session
        if (active != null && active.expireIfDue(nowMs())) {
            deny("The master PIN step timed out. Self-Protection is still locked.")
        } else if (active?.stageTimerRunning() == true) {
            startStageTicker()
        }
    }

    private fun recoveryActive(): Boolean =
        _state.value.showRecovery &&
            _state.value.phase is SelfLockPhase.MasterPin &&
            session != null

    fun verifyRecoveryPin(pin: String, onResult: (String?) -> Unit) {
        if (!recoveryActive() || !config.hasRecoveryPin) {
            onResult("No recovery PIN is available in this session.")
            return
        }
        val generation = recoveryGeneration
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.RECOVERY_PIN, pin)
            if (generation != recoveryGeneration || !recoveryActive()) {
                onResult("Recovery is no longer active.")
                return@launch
            }
            if (attempt.accepted) {
                recoveryVerified += CredentialKind.RECOVERY_PIN
                onResult(null)
            } else {
                container.logs.log(
                    LogType.RECOVERY_FAILED,
                    "Recovery PIN rejected at the Self-Protection gate",
                    "NEXO LOCK",
                )
                if (attempt.lockedOutMs > 0L) startLockoutCountdown(attempt.lockedOutMs)
                onResult(
                    recoveryMessage(attempt.lockedOutMs, attempt.attemptsUntilLockout, "recovery PIN"),
                )
            }
        }
    }

    fun verifyRecoveryPattern(pattern: String, onResult: (String?) -> Unit) {
        if (!recoveryActive() || !config.hasRecoveryPattern) {
            onResult("No recovery pattern is available in this session.")
            return
        }
        val generation = recoveryGeneration
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.RECOVERY_PATTERN, pattern)
            if (generation != recoveryGeneration || !recoveryActive()) {
                onResult("Recovery is no longer active.")
                return@launch
            }
            if (attempt.accepted) {
                recoveryVerified += CredentialKind.RECOVERY_PATTERN
                onResult(null)
            } else {
                container.logs.log(
                    LogType.RECOVERY_FAILED,
                    "Recovery pattern rejected at the Self-Protection gate",
                    "NEXO LOCK",
                )
                if (attempt.lockedOutMs > 0L) startLockoutCountdown(attempt.lockedOutMs)
                onResult(
                    recoveryMessage(attempt.lockedOutMs, attempt.attemptsUntilLockout, "recovery pattern"),
                )
            }
        }
    }

    fun onRecoverySuccess(method: UnlockMethod) {
        viewModelScope.launch {
            if (!recoveryActive()) return@launch
            val active = session ?: return@launch
            if (!AuthPolicy.recoverySatisfied(
                    hasRecoveryPin = config.hasRecoveryPin,
                    hasRecoveryPattern = config.hasRecoveryPattern,
                    verified = recoveryVerified,
                )
            ) {
                deny("Recovery verification was incomplete. Self-Protection is still locked.")
                return@launch
            }
            if (active.terminal) {
                deny("The Self-Protection session ended. Try again.")
                return@launch
            }
            if (!active.authorizeByRecovery()) {
                deny("Recovery could not complete this session. Try again.")
                return@launch
            }
            recoveryGeneration += 1
            recoveryVerified.clear()
            container.logs.log(
                LogType.RECOVERY_UNLOCKED,
                "Recovery unlock used at the Self-Protection gate: ${method.label}",
                "NEXO LOCK",
            )
            complete(method)
        }
    }

    private fun recoveryMessage(lockedOutMs: Long, left: Int, factor: String): String =
        if (lockedOutMs > 0L) {
            "Too many attempts. Try again in ${LockoutPolicy.describe(lockedOutMs)}."
        } else {
            "Incorrect $factor. $left attempts left before a delay."
        }

    // ------------------------------------------------------------------ completion

    private fun complete(method: UnlockMethod) {
        stageJob?.cancel()
        lockoutJob?.cancel()
        capture?.reset()
        container.volumeCapture.end(volumeOwner)
        recoveryGeneration += 1
        recoveryVerified.clear()
        // The grant is issued here and nowhere else, and only after the session
        // engine reached AUTHORIZED with the owner's configured factors.
        container.security.authorize(method)
        _state.update {
            it.copy(
                phase = SelfLockPhase.Authorized,
                showRecovery = false,
                busy = false,
                error = null,
                pin = "",
                gestureFilled = 0,
                stageTimerActive = false,
                stageRemainingMs = 0L,
            )
        }
    }

    private fun deny(message: String) {
        // A late tick must never undo a verification that already succeeded: the
        // stage ticker can resume after the job was cancelled, and revoking here
        // would drop the owner straight back to the gate right after unlocking.
        val current = _state.value
        if (current.authorized) return
        if (session?.state == AuthenticationSession.AuthSessionState.AUTHORIZED) return
        stageJob?.cancel()
        lockoutJob?.cancel()
        capture?.reset()
        container.volumeCapture.end(volumeOwner)
        recoveryGeneration += 1
        recoveryVerified.clear()
        session?.cancel()
        container.security.revoke("Self-Protection session denied")
        _state.update {
            it.copy(
                phase = SelfLockPhase.Denied(message),
                showRecovery = false,
                completedFactors = emptySet(),
                gestureFilled = 0,
                pin = "",
                busy = false,
                stageTimerActive = false,
                stageRemainingMs = 0L,
                error = null,
                info = null,
            )
        }
        viewModelScope.launch {
            container.logs.log(
                LogType.AUTH_SESSION_CANCELLED,
                "Self-Protection denied: $message",
                "NEXO LOCK",
            )
        }
    }

    private fun unlockMethodFor(): UnlockMethod {
        val factors = session?.verifiedFactors().orEmpty()
        val biometric = AuthFactor.BIOMETRIC in factors
        val gesture = AuthFactor.GESTURE in factors
        return when {
            biometric && gesture -> UnlockMethod.THREE_FACTOR
            biometric -> UnlockMethod.BIOMETRIC_THEN_PIN
            gesture -> UnlockMethod.GESTURE_THEN_PIN
            else -> UnlockMethod.PIN
        }
    }

    private fun startLockoutCountdown(initial: Long) {
        lockoutJob?.cancel()
        _state.update { it.copy(lockoutRemainingMs = initial, busy = true, pin = "") }
        lockoutJob = viewModelScope.launch {
            var remaining = initial
            while (remaining > 0L) {
                delay(1_000L)
                remaining = container.auth.lockoutRemaining(recovery = false)
                _state.update {
                    it.copy(
                        lockoutRemainingMs = remaining,
                        busy = remaining > 0L,
                        error = if (remaining > 0L) {
                            "Too many attempts. Try again in ${LockoutPolicy.describe(remaining)}."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        prepareJob?.cancel()
        stageJob?.cancel()
        lockoutJob?.cancel()
        container.volumeCapture.end(volumeOwner)
    }

    companion object {
        private const val TICK_MS = 100L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SelfLockViewModel(container) as T
            }
    }
}
