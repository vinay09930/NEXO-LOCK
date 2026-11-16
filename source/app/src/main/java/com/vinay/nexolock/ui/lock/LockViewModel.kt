package com.vinay.nexolock.ui.lock

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
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.domain.VolumeCapture
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import com.vinay.nexolock.vault.CaptureState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One explicit lock stage. The stage carries its own data, so contradictory
 * combinations (unlocked + denied, PIN stage with a biometric factor, recovery
 * without a PIN stage) are unrepresentable.
 */
sealed interface LockPhase {
    data object Decoy : LockPhase
    data object Biometric : LockPhase
    data object VolumePattern : LockPhase
    data class MasterPin(val recovery: Boolean = false) : LockPhase
    data class Denied(val message: String) : LockPhase
    data object Unlocked : LockPhase
    data object Closing : LockPhase
}

data class LockUiState(
    val targetPackage: String,
    val targetLabel: String,
    val phase: LockPhase = LockPhase.Decoy,
    val ready: Boolean = false,
    val pinLength: Int = 6,
    val decoyEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val biometricReady: Boolean = false,
    val gestureEnabled: Boolean = false,
    val gestureLength: Int = 0,
    val hasRecoveryPin: Boolean = false,
    val hasRecoveryPattern: Boolean = false,
    /** Configured pre-PIN factors in fixed order: biometric, then volume pattern. */
    val requiredFactors: List<AuthFactor> = emptyList(),
    /** Human-readable factor count actually enforced by this session. */
    val protectionMode: String = "",
    /**
     * Non-null when fewer than three factors are available. The lock screen
     * shows this verbatim; the session is never logged as full protection.
     */
    val fallbackNotice: String? = null,
    val pinOnlyFallback: Boolean = false,
    val completedFactors: Set<AuthFactor> = emptySet(),
    /** Remaining time in the current stage; 0 when the stage has no app timer. */
    val stageRemainingMs: Long = 0L,
    /** Full window of the current stage; 0 when the stage has no app timer. */
    val stageTotalMs: Long = 0L,
    /** True while the current stage's fixed deadline is actually running. */
    val stageTimerActive: Boolean = false,
    val sessionId: String = "",
    val pin: String = "",
    val gestureFilled: Int = 0,
    val error: String? = null,
    val info: String? = null,
    val busy: Boolean = false,
    val lockoutRemainingMs: Long = 0L,
    val biometricRequest: Int = 0,
    val captureState: CaptureState? = null,
) {
    /** The factor currently asking for input; the PIN stage reports [AuthFactor.PIN]. */
    val activeFactor: AuthFactor?
        get() = when (val current = phase) {
            LockPhase.Biometric -> AuthFactor.BIOMETRIC
            LockPhase.VolumePattern -> AuthFactor.GESTURE
            is LockPhase.MasterPin -> AuthFactor.PIN
            else -> null
        }

    val showRecovery: Boolean
        get() = (phase as? LockPhase.MasterPin)?.recovery == true

    val unlocked: Boolean get() = phase is LockPhase.Unlocked
    val closeRequested: Boolean get() = phase is LockPhase.Closing
    val deniedMessage: String? get() = (phase as? LockPhase.Denied)?.message
    val denied: Boolean get() = phase is LockPhase.Denied
    val lockedOut: Boolean get() = lockoutRemainingMs > 0L
    val factorProgress: String
        get() = if (requiredFactors.isEmpty()) {
            ""
        } else {
            "${completedFactors.size}/${requiredFactors.size} factors"
        }
}

/**
 * Lock-screen state machine.
 *
 * ONE [AuthenticationSession] per lock launch. The session enforces the fixed
 * sequence:
 *
 *   decoy (4 s) → biometric (Android-managed) → volume pattern (15 s) → PIN (15 s)
 *
 * Each stage owns its own monotonic deadline. The volume-pattern deadline is
 * fixed when the stage opens and is never restarted by a key press. The PIN
 * deadline is fixed when the stage opens and is never restarted by a digit or
 * retry. Decoy, pattern and PIN expiry are terminal and deny access. Biometric
 * has no NEXO timer; only a genuine success advances, while failure or
 * cancellation denies.
 */
class LockViewModel(
    private val container: AppContainer,
    targetPackage: String,
    private val sessionToken: String? = null,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _state = MutableStateFlow(
        LockUiState(
            targetPackage = targetPackage,
            targetLabel = container.catalog.labelFor(targetPackage),
        ),
    )
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var config = NexoConfig()
    private var session: AuthenticationSession? = null
    private var capture: VolumeCapture? = null
    private var captureLength = 0
    private var lockoutJob: Job? = null
    private var stageJob: Job? = null
    private var biometricUnavailable = false

    /**
     * Recovery factors verified by the repository in the current recovery
     * flow. [onRecoverySuccess] cannot complete from the UI callback alone.
     */
    private var recoveryGeneration = 0L
    private val recoveryVerified = linkedSetOf<CredentialKind>()

    /**
     * True once the lock surface has actually drawn its first frame. The decoy
     * window (or the direct first stage) starts only after this flag is set.
     */
    private var surfaceVisible = false

    init {
        viewModelScope.launch {
            config = container.prefs.snapshot()
            val biometricAvailable = BiometricManager.from(container.appContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
            val safeGestureLength = config.mainGestureLength.takeIf {
                it in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH
            }
            captureLength = safeGestureLength ?: 0
            val gestureConfigured = config.gestureEnabled && config.hasMainGesture &&
                safeGestureLength != null
            val invalidGestureData = config.gestureEnabled && config.hasMainGesture &&
                safeGestureLength == null
            biometricUnavailable = config.biometricEnabled && !biometricAvailable

            // Fixed order: biometric, then volume pattern, then the PIN gate.
            val required = buildList {
                if (config.biometricEnabled && biometricAvailable) add(AuthFactor.BIOMETRIC)
                if (gestureConfigured) add(AuthFactor.GESTURE)
            }
            val fallbackNotice = when {
                invalidGestureData ->
                    "The saved volume pattern is unavailable because its length is invalid. " +
                        "Record it again in Settings → Volume pattern. This session uses the " +
                        "remaining factors and never reports full protection."
                biometricUnavailable ->
                    "Biometric is enabled but unavailable on this device right now. Only a " +
                        "successful biometric result can advance the sequence, so this session " +
                        "will be denied. Disable biometric in Settings or restore the sensor."
                !config.biometricEnabled && gestureConfigured ->
                    "Biometric is disabled. This session uses the volume pattern + master PIN."
                !config.biometricEnabled && !gestureConfigured ->
                    "Only the master PIN is configured. Full three-factor protection is " +
                        "unavailable for this session."
                !gestureConfigured ->
                    "The volume pattern is not configured or is disabled. This session uses " +
                        "biometric + master PIN. Full three-factor protection is unavailable."
                else -> null
            }
            val protectionMode = when (required.size) {
                2 -> "3 FACTORS"
                1 -> "2 FACTORS"
                else -> "1 FACTOR"
            }
            val session0 = AuthenticationSession(
                protectedPackage = targetPackage,
                configuredFactors = required,
                decoyTimeoutMs = AuthenticationSession.decoyTimeoutMsFor(config.decoyTimeoutSeconds),
                gestureTimeoutMs = AuthenticationSession.gestureTimeoutMsFor(config.gestureTimeoutSeconds),
                pinTimeoutMs = AuthenticationSession.pinTimeoutMsFor(config.pinTimeoutSeconds),
                sessionToken = sessionToken,
            )
            session = session0
            session0.appDetected()

            val firstPhase = firstStagePhase(required)
            _state.update {
                it.copy(
                    ready = true,
                    pinLength = AuthPolicy.safePinLength(config.pinLength),
                    decoyEnabled = config.decoyEnabled,
                    biometricEnabled = config.biometricEnabled,
                    biometricReady = biometricAvailable,
                    gestureEnabled = gestureConfigured,
                    gestureLength = safeGestureLength ?: 0,
                    hasRecoveryPin = config.hasRecoveryPin,
                    hasRecoveryPattern = config.hasRecoveryPattern,
                    requiredFactors = required,
                    protectionMode = protectionMode,
                    fallbackNotice = fallbackNotice,
                    pinOnlyFallback = required.isEmpty(),
                    // When the decoy is disabled the first authentication stage
                    // is composed immediately; its deadline still waits for the
                    // first rendered frame.
                    phase = if (config.decoyEnabled) LockPhase.Decoy else firstPhase,
                    info = null,
                    sessionId = session0.sessionId,
                    stageTotalMs = 0L,
                    stageRemainingMs = 0L,
                    stageTimerActive = false,
                )
            }
            container.logs.log(
                LogType.AUTH_SESSION_STARTED,
                "Session ${session0.sessionId.take(8)} enforces " +
                    (required.joinToString(" → ") { it.label }
                        .ifBlank { "master PIN only" }) +
                    " → master PIN, with independent stage windows",
                _state.value.targetLabel,
            )
            if (fallbackNotice != null) {
                container.logs.log(
                    LogType.DEGRADED_PROTECTION,
                    fallbackNotice,
                    _state.value.targetLabel,
                )
            }

            // A biometric factor that is enabled but cannot run would make the
            // required sequence impossible. Deny honestly instead of silently
            // skipping a configured factor.
            if (biometricUnavailable) {
                session0.deny()
                container.runtime.onLockResult(
                    packageName = targetPackage,
                    sessionToken = session0.sessionId,
                    unlocked = false,
                )
                _state.update {
                    it.copy(
                        phase = LockPhase.Denied(
                            "Access denied — biometric is enabled but unavailable. " +
                                "Disable biometric in Settings or restore the sensor, then try again.",
                        ),
                        stageTimerActive = false,
                        stageRemainingMs = 0L,
                    )
                }
                container.logs.log(
                    LogType.BIOMETRIC_UNAVAILABLE,
                    "Session denied: biometric is enabled but unavailable",
                    targetPackage,
                )
                return@launch
            }

            val remaining = container.auth.lockoutRemaining(recovery = false)
            if (remaining > 0L) startLockoutCountdown(remaining)
            // The stage clock deliberately does NOT start here. It starts from
            // onSurfaceVisible(), after the first Compose frame has been drawn,
            // so a cold start cannot consume the decoy or input window before
            // the owner can see the screen.
            if (surfaceVisible) startSurface()
        }
        viewModelScope.launch {
            container.intruderCapture.state.collect { captureState ->
                if (captureState.lastStatus != null) {
                    _state.update { it.copy(captureState = captureState) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ surface / stage origin

    /**
     * Report that the lock surface has actually rendered its first frame.
     * Idempotent across configuration changes and repeated frame callbacks.
     */
    fun onSurfaceVisible() {
        surfaceVisible = true
        if (!_state.value.ready) return
        startSurface()
    }

    private fun startSurface() {
        val active = session ?: return
        val current = _state.value
        if (!current.ready || current.denied || current.unlocked) return
        if (active.state == AuthenticationSession.AuthSessionState.APP_DETECTED) {
            if (config.decoyEnabled) {
                if (!active.decoyShown(nowMs())) return
                _state.update {
                    it.copy(
                        phase = LockPhase.Decoy,
                        stageRemainingMs = active.remainingStageMs(nowMs()),
                        stageTotalMs = active.stageTimeoutMs,
                        stageTimerActive = true,
                    )
                }
                startStageTicker()
                viewModelScope.launch {
                    container.logs.log(
                        LogType.DECOY_SHOWN,
                        "App-owned decoy rendered; ${config.decoyTimeoutSeconds}s window started",
                        _state.value.targetLabel,
                    )
                }
            } else {
                when (active.authenticateDirect(nowMs())) {
                    is AuthenticationSession.Result.Progress,
                    AuthenticationSession.Result.PinRequired,
                    -> applyStage(active, log = true)
                    else -> Unit
                }
            }
        } else {
            // Configuration change or repeated frame callback: resume the same
            // fixed stage deadline; never restart it.
            applyStage(active, log = false)
        }
    }

    private fun firstStagePhase(required: List<AuthFactor>): LockPhase = when {
        AuthFactor.BIOMETRIC in required -> LockPhase.Biometric
        AuthFactor.GESTURE in required -> LockPhase.VolumePattern
        else -> LockPhase.MasterPin()
    }

    /**
     * Publish the session's current stage. The deadline values come from the
     * session; this method never starts or moves a deadline.
     */
    private fun applyStage(active: AuthenticationSession, log: Boolean) {
        val current = _state.value
        val now = nowMs()
        val enteringBiometric = active.state ==
            AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION &&
            current.phase !is LockPhase.Biometric
        val enteringPattern = active.state ==
            AuthenticationSession.AuthSessionState.VOLUME_PATTERN &&
            current.phase !is LockPhase.VolumePattern

        val newPhase: LockPhase = when (active.state) {
            AuthenticationSession.AuthSessionState.DECOY_VISIBLE -> LockPhase.Decoy
            AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION -> LockPhase.Biometric
            AuthenticationSession.AuthSessionState.VOLUME_PATTERN -> LockPhase.VolumePattern
            AuthenticationSession.AuthSessionState.MASTER_PIN ->
                (current.phase as? LockPhase.MasterPin) ?: LockPhase.MasterPin()
            AuthenticationSession.AuthSessionState.AUTHORIZED -> LockPhase.Unlocked
            else -> current.phase
        }

        if (enteringPattern) {
            capture = VolumeCapture.createOrNull(captureLength)
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
                biometricRequest = if (enteringBiometric) {
                    it.biometricRequest + 1
                } else {
                    it.biometricRequest
                },
            )
        }
        if (log) {
            viewModelScope.launch {
                container.logs.log(
                    LogType.AUTH_FACTOR_OK,
                    "Stage opened: ${stageLabel(active.state)}",
                    _state.value.targetLabel,
                )
            }
        }
        if (active.stageTimerRunning()) startStageTicker()
    }

    private fun stageInfo(active: AuthenticationSession): String? = when (active.state) {
        AuthenticationSession.AuthSessionState.DECOY_VISIBLE ->
            "Tap the card or press a volume key within ${config.decoyTimeoutSeconds}s."
        AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION ->
            "Android owns the biometric prompt. Only a successful result continues."
        AuthenticationSession.AuthSessionState.VOLUME_PATTERN ->
            "Enter your ${captureLength}-press volume pattern within " +
                "${config.gestureTimeoutSeconds}s. The timer does not restart on key presses."
        AuthenticationSession.AuthSessionState.MASTER_PIN ->
            "Enter your master PIN within ${config.pinTimeoutSeconds}s. " +
                "The timer does not restart on a digit or a retry."
        else -> null
    }

    private fun stageLabel(state: AuthenticationSession.AuthSessionState): String = when (state) {
        AuthenticationSession.AuthSessionState.DECOY_VISIBLE -> "decoy"
        AuthenticationSession.AuthSessionState.BIOMETRIC_AUTHENTICATION -> "biometric"
        AuthenticationSession.AuthSessionState.VOLUME_PATTERN -> "volume pattern"
        AuthenticationSession.AuthSessionState.MASTER_PIN -> "master PIN"
        else -> "authentication"
    }

    // ------------------------------------------------------------------ decoy

    /**
     * Tap on the decoy card (or a volume key). Continues the SAME 4 s window;
     * it never restarts or extends it.
     */
    fun onDecoyContinue() {
        val current = _state.value
        if (!current.ready || current.phase !is LockPhase.Decoy ||
            current.unlocked || current.denied
        ) {
            return
        }
        val active = session ?: return
        when (active.decoyContinue(nowMs())) {
            is AuthenticationSession.Result.Progress,
            AuthenticationSession.Result.PinRequired,
            -> {
                viewModelScope.launch {
                    container.logs.log(
                        LogType.DECOY_WAIT,
                        "Decoy continued; next stage opened without restarting its window",
                        current.targetLabel,
                    )
                }
                applyStage(active, log = true)
            }
            AuthenticationSession.Result.TimedOut -> {
                viewModelScope.launch {
                    container.logs.log(
                        LogType.AUTH_SESSION_EXPIRED,
                        "The 4 s decoy window expired; access denied",
                        current.targetLabel,
                    )
                }
                denyExpired("decoy")
            }
            else -> Unit
        }
    }

    /** Close app: cancel the session and deny access. */
    fun onCloseApp() {
        val current = _state.value
        if (current.closeRequested || current.unlocked || current.denied) return
        session?.cancel()
        container.runtime.onLockResult(
            packageName = current.targetPackage,
            sessionToken = current.sessionId,
            unlocked = false,
        )
        _state.update { it.copy(phase = LockPhase.Closing) }
        viewModelScope.launch {
            if (current.phase is LockPhase.Decoy) {
                container.logs.log(
                    LogType.DECOY_CLOSE,
                    "Close app selected on decoy; session cancelled",
                    current.targetLabel,
                )
            } else {
                container.logs.log(
                    LogType.AUTH_SESSION_CANCELLED,
                    "Authentication cancelled (Close app); access denied",
                    current.targetLabel,
                )
            }
        }
    }

    // ------------------------------------------------------------------ stage ticker

    /** 100 ms resolution against the monotonic clock; fires expiry exactly once. */
    private fun startStageTicker() {
        stageJob?.cancel()
        val active = session ?: return
        if (!active.stageTimerRunning()) return
        stageJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                val current = session ?: return@launch
                val label = stageLabel(current.state)
                if (current.expireIfDue(nowMs())) {
                    denyExpired(label)
                    return@launch
                }
                if (!current.stageTimerRunning()) return@launch
                _state.update {
                    it.copy(stageRemainingMs = current.remainingStageMs(nowMs()))
                }
            }
        }
    }

    private fun denyExpired(stageLabel: String) {
        capture?.reset()
        recoveryGeneration += 1
        recoveryVerified.clear()
        container.runtime.onLockResult(
            packageName = _state.value.targetPackage,
            sessionToken = _state.value.sessionId,
            unlocked = false,
        )
        _state.update {
            it.copy(
                phase = LockPhase.Denied(
                    "Access denied — the $stageLabel step timed out.",
                ),
                completedFactors = emptySet(),
                gestureFilled = 0,
                pin = "",
                busy = false,
                stageRemainingMs = 0L,
                stageTimerActive = false,
                error = null,
                info = null,
            )
        }
        requestIntruderCapture("$stageLabel timeout")
        viewModelScope.launch {
            container.logs.log(
                LogType.AUTH_SESSION_EXPIRED,
                "The $stageLabel stage expired; access denied",
                _state.value.targetLabel,
            )
        }
    }

    // ------------------------------------------------------------------ PIN

    fun onDigit(digit: Int) {
        val current = _state.value
        if (!current.ready || current.busy || current.lockedOut || current.showRecovery) return
        if (current.phase !is LockPhase.MasterPin || current.denied || current.unlocked) return
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
        viewModelScope.launch {
            val active = session ?: return@launch
            if (_state.value.phase !is LockPhase.MasterPin) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, pin)
            if (attempt.accepted) {
                when (active.completePin(correct = true, now = nowMs())) {
                    AuthenticationSession.Result.PinAccepted ->
                        completeUnlock(unlockMethodFor())
                    AuthenticationSession.Result.TimedOut ->
                        denyExpired("master PIN")
                    else -> _state.update { it.copy(busy = false) }
                }
                return@launch
            }
            container.logs.log(LogType.MAIN_PIN_FAILED, "Wrong main PIN at the PIN stage")
            requestIntruderCapture("Wrong main PIN")
            val pinResult = active.completePin(correct = false, now = nowMs())
            if (pinResult == AuthenticationSession.Result.TimedOut) {
                denyExpired("master PIN")
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
        }
    }

    // ------------------------------------------------------------------ biometric

    private fun biometricStageActive(): Boolean {
        val current = _state.value
        return current.phase is LockPhase.Biometric && !current.unlocked && !current.denied
    }

    /** Called only after BiometricPrompt reports a genuine authentication success. */
    fun onBiometricSucceeded() {
        if (!biometricStageActive()) return // stale callback from an older stage/attempt
        val current = _state.value
        val active = session ?: return
        viewModelScope.launch {
            container.logs.log(
                LogType.BIOMETRIC_SUCCESS,
                "Biometric authentication succeeded",
                current.targetLabel,
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
     * Per the documented policy, a failed biometric result is terminal. The
     * system prompt is cancelled by the Activity so no second result can arrive.
     */
    fun onBiometricFailed() {
        if (!biometricStageActive()) return
        session?.biometricFailed()
        denyBiometric("biometric authentication failed")
    }

    /** Per the documented policy, cancelling the system prompt is terminal. */
    fun onBiometricCancelled() {
        if (!biometricStageActive()) return
        session?.biometricCancelled()
        denyBiometric("biometric authentication was cancelled")
    }

    fun onBiometricError(code: Int, message: String) {
        if (!biometricStageActive()) return
        val friendly = when (code) {
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "This device has no biometric sensor."
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "The biometric sensor is temporarily unavailable."
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "No biometric is enrolled in Android settings."
            BiometricPrompt.ERROR_LOCKOUT -> "Biometrics are temporarily locked out by Android."
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                "Biometrics are locked out by Android until the device is unlocked with its PIN."
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                "A biometric security update is required before authentication can continue."
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            -> "Biometric authentication was cancelled."
            BiometricPrompt.ERROR_TIMEOUT -> "The biometric prompt timed out."
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "Android could not process the biometric request."
            else -> "Biometric authentication was not completed."
        }
        session?.biometricFailed()
        val current = _state.value
        viewModelScope.launch {
            container.logs.log(
                LogType.BIOMETRIC_UNAVAILABLE,
                "Biometric stage denied: ${message.ifBlank { friendly }}",
                current.targetLabel,
            )
        }
        denyBiometric(friendly.trimEnd('.').lowercase())
    }

    private fun denyBiometric(reason: String) {
        capture?.reset()
        container.runtime.onLockResult(
            packageName = _state.value.targetPackage,
            sessionToken = _state.value.sessionId,
            unlocked = false,
        )
        _state.update {
            it.copy(
                phase = LockPhase.Denied(
                    "Access denied — $reason. Only a successful biometric result can " +
                        "advance the sequence; a new session is required.",
                ),
                completedFactors = emptySet(),
                gestureFilled = 0,
                pin = "",
                busy = false,
                stageRemainingMs = 0L,
                stageTimerActive = false,
                error = null,
                info = null,
            )
        }
        requestIntruderCapture("Biometric denied: $reason")
        viewModelScope.launch {
            container.logs.log(
                LogType.BIOMETRIC_FAILED,
                "Biometric stage denied: $reason",
                _state.value.targetLabel,
            )
        }
    }

    // ------------------------------------------------------------------ volume gesture

    fun onVolumePress(press: VolumePress) {
        val current = _state.value
        if (!current.ready || current.busy || current.lockedOut || current.showRecovery ||
            current.denied || current.unlocked
        ) {
            return
        }

        // On the decoy a volume key means "continue". It never restarts the
        // decoy window. If the next stage is the volume pattern, this same
        // physical press is counted as the first press of the pattern.
        if (current.phase is LockPhase.Decoy) {
            onDecoyContinue()
            if (_state.value.phase !is LockPhase.VolumePattern) return
        }
        if (_state.value.phase !is LockPhase.VolumePattern) return

        val active = session ?: return
        if (!active.noteGesturePress(nowMs())) {
            denyExpired("volume pattern")
            return
        }
        if (captureLength !in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH) {
            // Corrupt or legacy config: never construct VolumeCapture(0..3/9+).
            return
        }
        val captureState = capture ?: VolumeCapture.createOrNull(captureLength)?.also { capture = it }
            ?: return
        when (val result = captureState.press(press, nowMs())) {
            is VolumeCapture.Result.Progress -> {
                _state.update {
                    it.copy(
                        gestureFilled = result.count,
                        error = null,
                        info = "Volume pattern: ${result.count} of ${result.expected} presses. " +
                            "The ${config.gestureTimeoutSeconds}s window keeps running.",
                    )
                }
            }
            is VolumeCapture.Result.Complete -> {
                _state.update { it.copy(gestureFilled = 0, busy = true, info = null) }
                verifyGesture(result.pattern)
            }
        }
    }

    fun resetGesture() {
        // Only the in-progress press buffer is cleared. The stage deadline is
        // fixed and is deliberately not restarted.
        capture?.reset()
        _state.update { it.copy(gestureFilled = 0) }
    }

    private fun verifyGesture(pattern: VolumePattern) {
        viewModelScope.launch {
            val current = _state.value
            val active = session ?: return@launch
            if (current.phase !is LockPhase.VolumePattern) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            if (!active.noteGesturePress(nowMs())) {
                denyExpired("volume pattern")
                return@launch
            }
            val attempt = container.auth.verify(CredentialKind.MAIN_GESTURE, pattern.code)
            if (attempt.accepted) {
                when (active.completeGesture(nowMs())) {
                    is AuthenticationSession.Result.Progress,
                    AuthenticationSession.Result.PinRequired,
                    -> applyStage(active, log = true)
                    AuthenticationSession.Result.TimedOut ->
                        denyExpired("volume pattern")
                    else -> _state.update { it.copy(busy = false) }
                }
            } else {
                capture?.reset()
                container.logs.log(LogType.GESTURE_FAILED, "Wrong volume pattern on lock screen")
                requestIntruderCapture("Wrong volume pattern")
                val message = if (attempt.lockedOutMs > 0L) {
                    startLockoutCountdown(attempt.lockedOutMs)
                    "Too many attempts. Locked for ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                } else {
                    "Incorrect volume pattern. ${attempt.attemptsUntilLockout} attempts left before a delay."
                }
                _state.update { it.copy(busy = false, gestureFilled = 0, error = message) }
            }
        }
    }

    // ------------------------------------------------------------------ recovery (PIN stage only)

    fun openRecovery() {
        if (_state.value.phase !is LockPhase.MasterPin) return
        // Recovery is an alternative to the master PIN and is throttled by the
        // shared lockout policy. The PIN countdown must not replace the
        // recovery screen halfway through a legitimate recovery attempt.
        stageJob?.cancel()
        stageJob = null
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.update {
            it.copy(
                phase = LockPhase.MasterPin(recovery = true),
                error = null,
                info = null,
                pin = "",
                gestureFilled = 0,
            )
        }
        capture?.reset()
    }

    fun closeRecovery() {
        if (_state.value.phase !is LockPhase.MasterPin) return
        recoveryGeneration += 1
        recoveryVerified.clear()
        _state.update {
            it.copy(phase = LockPhase.MasterPin(recovery = false), error = null)
        }
        val active = session
        if (active != null && active.expireIfDue(nowMs())) {
            denyExpired("master PIN")
        } else if (active?.stageTimerRunning() == true) {
            startStageTicker()
        }
    }

    private fun recoveryActive(): Boolean =
        _state.value.showRecovery && _state.value.phase is LockPhase.MasterPin

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
                container.logs.log(LogType.RECOVERY_FAILED, "Recovery PIN rejected on lock screen")
                if (attempt.lockedOutMs > 0L) startLockoutCountdown(attempt.lockedOutMs)
                onResult(recoveryMessage(attempt.lockedOutMs, attempt.attemptsUntilLockout, "recovery PIN"))
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
                container.logs.log(LogType.RECOVERY_FAILED, "Recovery pattern rejected on lock screen")
                if (attempt.lockedOutMs > 0L) startLockoutCountdown(attempt.lockedOutMs)
                onResult(recoveryMessage(attempt.lockedOutMs, attempt.attemptsUntilLockout, "recovery pattern"))
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
                container.logs.log(
                    LogType.RECOVERY_FAILED,
                    "Recovery callback rejected: not every configured factor was verified",
                )
                return@launch
            }
            if (active.terminal || !active.authorizeByRecovery()) return@launch
            recoveryGeneration += 1
            recoveryVerified.clear()
            container.logs.log(
                LogType.RECOVERY_UNLOCKED,
                "Recovery unlock used: ${method.label}",
                _state.value.targetLabel,
            )
            completeUnlock(method)
        }
    }

    private fun recoveryMessage(lockedOutMs: Long, left: Int, factor: String): String =
        if (lockedOutMs > 0) {
            "Too many attempts. Try again in ${LockoutPolicy.describe(lockedOutMs)}."
        } else {
            "Incorrect $factor. $left attempts left before a delay."
        }

    // ------------------------------------------------------------------ completion

    private fun completeUnlock(method: UnlockMethod) {
        val target = _state.value.targetPackage
        stageJob?.cancel()
        lockoutJob?.cancel()
        recoveryGeneration += 1
        recoveryVerified.clear()
        container.runtime.onLockResult(
            packageName = target,
            sessionToken = _state.value.sessionId,
            unlocked = true,
        )
        _state.update {
            it.copy(
                phase = LockPhase.Unlocked,
                busy = false,
                error = null,
                pin = "",
                gestureFilled = 0,
                stageTimerActive = false,
                stageRemainingMs = 0L,
            )
        }
        val recovery = method == UnlockMethod.RECOVERY_PIN ||
            method == UnlockMethod.RECOVERY_GESTURE
        val verified = verifiedDescription(recovery)
        viewModelScope.launch {
            container.logs.log(
                LogType.AUTH_COMPLETED,
                "Verified: $verified",
                method.label,
            )
            container.logs.log(
                LogType.APP_UNLOCKED,
                "Unlocked with ${method.label} ($verified)",
                _state.value.targetLabel,
            )
        }
    }

    private fun unlockMethodFor(): UnlockMethod {
        val verifiedFactors = session?.verifiedFactors().orEmpty()
        val biometric = AuthFactor.BIOMETRIC in verifiedFactors
        val gesture = AuthFactor.GESTURE in verifiedFactors
        return when {
            biometric && gesture -> UnlockMethod.THREE_FACTOR
            biometric -> UnlockMethod.BIOMETRIC_THEN_PIN
            gesture -> UnlockMethod.GESTURE_THEN_PIN
            else -> UnlockMethod.PIN
        }
    }

    /** Exact, non-inflated description of what this session verified. */
    private fun verifiedDescription(recovery: Boolean): String {
        val factors = session?.verifiedFactors().orEmpty()
        val parts = buildList {
            if (AuthFactor.BIOMETRIC in factors) add("biometric")
            if (AuthFactor.GESTURE in factors) add("volume pattern")
            if (recovery) add("recovery credential") else add("master PIN")
        }
        return parts.joinToString(" + ")
    }

    // ------------------------------------------------------------------ helpers

    private fun requestIntruderCapture(reason: String) {
        if (config.intruderCaptureEnabled) {
            container.intruderCapture.requestCapture(
                "$reason · ${_state.value.targetPackage}",
                _state.value.targetLabel,
            )
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
        lockoutJob?.cancel()
        stageJob?.cancel()
    }

    companion object {
        private const val TICK_MS = 100L

        fun factory(
            container: AppContainer,
            targetPackage: String,
            sessionToken: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LockViewModel(container, targetPackage, sessionToken) as T
        }
    }
}
