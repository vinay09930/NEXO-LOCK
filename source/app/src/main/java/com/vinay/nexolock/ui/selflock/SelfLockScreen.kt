package com.vinay.nexolock.ui.selflock

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vinay.nexolock.domain.AuthFactor
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.ui.components.BannerTone
import com.vinay.nexolock.ui.components.GestureDots
import com.vinay.nexolock.ui.components.InfoBanner
import com.vinay.nexolock.ui.components.NexoBackdrop
import com.vinay.nexolock.ui.components.NexoButton
import com.vinay.nexolock.ui.components.NexoCard
import com.vinay.nexolock.ui.components.NexoMark
import com.vinay.nexolock.ui.components.NexoWordmark
import com.vinay.nexolock.ui.components.PillTone
import com.vinay.nexolock.ui.components.PinDots
import com.vinay.nexolock.ui.components.PinKeypad
import com.vinay.nexolock.ui.components.StatusPill
import com.vinay.nexolock.ui.findActivity
import com.vinay.nexolock.ui.lock.RecoveryFlow
import com.vinay.nexolock.ui.theme.DangerRed
import java.util.Locale

/**
 * The Self-Protection gate.
 *
 * It is rendered *instead of* the navigation graph, so no protected route is
 * ever composed before authentication — there is no route to deep-link to, no
 * back stack to pop, and no drawer or tab that can skip it.
 *
 * It reuses the app's own components and the app's own recovery workflow; it
 * adds no bypass, no hidden credential and no second authentication scheme.
 */
@Composable
fun SelfLockScreen(
    state: SelfLockUiState,
    operationLabel: String?,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onBiometricSucceeded: () -> Unit,
    onBiometricError: (Int, String) -> Unit,
    onRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
    verifyRecoveryPin: (String, (String?) -> Unit) -> Unit,
    verifyRecoveryPattern: (String, (String?) -> Unit) -> Unit,
    onRecoverySuccess: (UnlockMethod) -> Unit,
    onResetGesture: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    // Back must never close the app and must never reveal the dashboard. It is
    // consumed silently here: the only ways out of the gate are a completed
    // verification or the explicit control on the denied screen.
    BackHandler(enabled = true) { }

    val context = LocalContext.current
    var prompt by remember { mutableStateOf<BiometricPrompt?>(null) }

    // The gate can show PIN/recovery entry, so it owns FLAG_SECURE itself.
    // The nav graph's SecureWindow is not composed while the gate is visible.
    val hostActivity = context.findActivity()
    DisposableEffect(hostActivity) {
        hostActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            hostActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(state.biometricRequest, state.phase) {
        if (state.phase !is SelfLockPhase.Biometric || state.biometricRequest == 0) return@LaunchedEffect
        val activity = context.findActivity() as? FragmentActivity
        if (activity == null) {
            // Nothing can host a system prompt, so the remaining factors apply.
            onBiometricError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "no activity to host the prompt")
            return@LaunchedEffect
        }
        runCatching { prompt?.cancelAuthentication() }
        val created = runCatching {
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        // The ViewModel ignores callbacks that arrive outside the
                        // biometric stage, so a delayed result cannot skip a step.
                        prompt = null
                        onBiometricSucceeded()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        prompt = null
                        onBiometricError(errorCode, errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        // A single unreadable sample is not fatal here: the prompt
                        // stays up, and cancelling falls back to the master PIN.
                    }
                },
            )
        }.getOrElse {
            // A prompt cannot be hosted right now; use the remaining factors.
            onBiometricError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "the prompt could not start")
            return@LaunchedEffect
        }
        prompt = created
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify it's you")
            .setSubtitle("Self-Protection · NEXO LOCK")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use the master PIN")
            .setConfirmationRequired(false)
            .build()
        runCatching { created.authenticate(info) }.onFailure {
            prompt = null
            onBiometricError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "the prompt could not start")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { prompt?.cancelAuthentication() }
            prompt = null
        }
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexoMark(size = 30.dp)
                Column(
                    Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                ) {
                    NexoWordmark(subtitle = null)
                }
                StatusPill("SELF-PROTECTION", PillTone.ACCENT)
            }
            Spacer(Modifier.height(22.dp))

            if (state.phase is SelfLockPhase.Denied) {
                DeniedGate(state.deniedMessage ?: "Authentication did not complete.", onRetry, onExit)
                return@NexoBackdrop
            }

            if (state.showRecovery) {
                Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                    RecoveryFlow(
                        hasRecoveryPin = state.hasRecoveryPin,
                        hasRecoveryPattern = state.hasRecoveryPattern,
                        verifyPin = verifyRecoveryPin,
                        verifyPattern = verifyRecoveryPattern,
                        onCancel = onCancelRecovery,
                        onSuccess = onRecoverySuccess,
                    )
                }
                return@NexoBackdrop
            }

            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "NEXO LOCK is protected",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = operationLabel?.let { "Verify it's you to $it." }
                    ?: "App Protection is on, so NEXO LOCK asks for authentication before " +
                    "showing the dashboard and its security settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))

            state.factorNotice?.let { notice ->
                InfoBanner(notice, BannerTone.INFO)
                Spacer(Modifier.height(8.dp))
            }
            FactorStrip(state)
            Spacer(Modifier.height(10.dp))
            StageTimer(state)

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                InfoBanner(it, BannerTone.DANGER)
            }
            state.info?.let {
                Spacer(Modifier.height(8.dp))
                InfoBanner(it, BannerTone.INFO)
            }
            Spacer(Modifier.height(14.dp))

            when (state.phase) {
                SelfLockPhase.Preparing -> {
                    Text(
                        "Preparing your configured factors…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SelfLockPhase.Biometric -> BiometricFactor(state, onBiometric)
                SelfLockPhase.VolumePattern -> GestureFactor(state, onResetGesture)
                SelfLockPhase.MasterPin -> PinFactor(state, onDigit, onBackspace)
                is SelfLockPhase.Denied, SelfLockPhase.Authorized -> Unit
            }

            if (state.phase is SelfLockPhase.MasterPin && !state.showRecovery &&
                (state.hasRecoveryPin || state.hasRecoveryPattern)
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRecovery, enabled = !state.busy) {
                    Text("Forgot the PIN? Use recovery")
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Use the Home gesture to leave NEXO LOCK. It stays locked and will ask again.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Android cannot stop uninstalling, force-stopping, or clearing app data by " +
                    "someone with the device or a rooted system. Self-Protection guards the " +
                    "app interface and its settings, not the operating system.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun DeniedGate(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed)
        Spacer(Modifier.height(10.dp))
        Text(
            "Access denied",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No factor was carried into a new attempt, and the dashboard was never shown.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        NexoButton(text = "Try again", onClick = onRetry)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onExit) { Text("Close NEXO LOCK") }
    }
}

@Composable
private fun FactorStrip(state: SelfLockUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.requiredFactors.forEach { factor ->
            val done = factor in state.completedFactors
            val active = factor == state.activeFactor
            StatusPill(
                if (done) "✓ ${factor.shortLabel}" else factor.shortLabel,
                when {
                    done -> PillTone.SUCCESS
                    active -> PillTone.ACCENT
                    else -> PillTone.NEUTRAL
                },
            )
        }
        StatusPill(
            if (state.authorized) "✓ PIN" else "PIN",
            when {
                state.authorized -> PillTone.SUCCESS
                state.phase is SelfLockPhase.MasterPin -> PillTone.ACCENT
                else -> PillTone.NEUTRAL
            },
        )
    }
}

@Composable
private fun StageTimer(state: SelfLockUiState) {
    if (state.phase is SelfLockPhase.Biometric) {
        Text(
            "Android owns this prompt. Cancelling falls back to your remaining factors.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    if (!state.stageTimerActive || state.stageTotalMs <= 0L) return
    val seconds = state.stageRemainingMs.toFloat() / 1000f
    val urgent = state.stageRemainingMs <= 1_000L
    LinearProgressIndicator(
        progress = {
            (state.stageRemainingMs.toFloat() / state.stageTotalMs.toFloat()).coerceIn(0f, 1f)
        },
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50)),
        color = if (urgent) DangerRed else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    val label = when (state.phase) {
        SelfLockPhase.VolumePattern -> "Volume-pattern window"
        is SelfLockPhase.MasterPin -> "Master-PIN window"
        else -> "Step window"
    }
    Text(
        "$label: ${String.format(Locale.US, "%.1f", seconds)} s remaining · fixed, not restarted",
        style = MaterialTheme.typography.labelSmall,
        color = if (urgent) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BiometricFactor(state: SelfLockUiState, onBiometric: () -> Unit) {
    NexoCard(Modifier.widthIn(max = 340.dp)) {
        Text(
            "Biometric verification",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Use the system prompt. If it cannot be completed, the remaining factors and " +
                "your master PIN still apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        NexoButton(
            text = "Use biometric",
            onClick = onBiometric,
            enabled = state.biometricEnabled && state.biometricReady && !state.busy,
        )
    }
}

@Composable
private fun GestureFactor(state: SelfLockUiState, onResetGesture: () -> Unit) {
    NexoCard(Modifier.widthIn(max = 340.dp)) {
        Text(
            "Volume pattern",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Press volume up / down in your saved sequence. Do not use the screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GestureDots(expected = state.gestureLength, filled = state.gestureFilled)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                "${state.gestureFilled}/${state.gestureLength}",
                if (state.gestureFilled > 0) PillTone.ACCENT else PillTone.NEUTRAL,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onResetGesture) { Text("Reset") }
        }
    }
}

@Composable
private fun PinFactor(
    state: SelfLockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
) {
    PinDots(length = state.pinLength, filled = state.pin.length, error = state.error != null)
    Spacer(Modifier.height(16.dp))
    Box(Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = !state.busy && !state.lockedOut,
        )
    }
    if (state.lockedOut) {
        Spacer(Modifier.height(10.dp))
        Text(
            "PIN entry is temporarily locked.",
            style = MaterialTheme.typography.labelSmall,
            color = com.vinay.nexolock.ui.theme.WarningAmber,
            textAlign = TextAlign.Center,
        )
    }
}
