package com.vinay.nexolock.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.nexolock.domain.AuthFactor
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.ui.components.AppIcon
import com.vinay.nexolock.ui.components.BannerTone
import com.vinay.nexolock.ui.components.GestureDots
import com.vinay.nexolock.ui.components.InfoBanner
import com.vinay.nexolock.ui.components.NexoBackdrop
import com.vinay.nexolock.ui.components.NexoCard
import com.vinay.nexolock.ui.components.NexoMark
import com.vinay.nexolock.ui.components.NexoWordmark
import com.vinay.nexolock.ui.components.PillTone
import com.vinay.nexolock.ui.components.PinDots
import com.vinay.nexolock.ui.components.PinKeypad
import com.vinay.nexolock.ui.components.StatusPill
import com.vinay.nexolock.ui.theme.DangerRed
import com.vinay.nexolock.ui.theme.WarningAmber
import com.vinay.nexolock.vault.CaptureStatus
import java.util.Locale

@Composable
fun LockScreen(
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onDecoyContinue: () -> Unit,
    onCloseApp: () -> Unit,
    onAppInfo: () -> Unit,
    onRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
    verifyRecoveryPin: (String, (String?) -> Unit) -> Unit,
    verifyRecoveryPattern: (String, (String?) -> Unit) -> Unit,
    onRecoverySuccess: (UnlockMethod) -> Unit,
    onResetGesture: () -> Unit,
    onUnlocked: () -> Unit,
) {
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    AnimatedContent(
        targetState = state.phase,
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(tween(durationMillis = 220)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 220))
                ).togetherWith(
                fadeOut(tween(durationMillis = 160)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 160)),
            )
        },
        modifier = Modifier.fillMaxSize(),
        label = "lock-stage",
    ) { phase ->
        when (phase) {
            LockPhase.Decoy -> DecoyScreen(
                state = state,
                onDecoyContinue = onDecoyContinue,
                onAppInfo = onAppInfo,
                onCloseApp = onCloseApp,
            )

            is LockPhase.Denied -> DeniedScreen(state)

            LockPhase.Biometric,
            LockPhase.VolumePattern,
            is LockPhase.MasterPin,
            -> AuthenticationScreen(
                state = state,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = onBiometric,
                onRecovery = onRecovery,
                onCancelRecovery = onCancelRecovery,
                verifyRecoveryPin = verifyRecoveryPin,
                verifyRecoveryPattern = verifyRecoveryPattern,
                onRecoverySuccess = onRecoverySuccess,
                onResetGesture = onResetGesture,
            )

            LockPhase.Unlocked, LockPhase.Closing -> Unit
        }
    }
}

/**
 * App-owned decoy surface, built natively in Compose.
 *
 * It is deliberately styled like a familiar Android error dialog — charcoal
 * card, rounded corners, native typography, information and close icons — but
 * it never claims that Android produced it or that the protected app crashed.
 * The footer states plainly that NEXO LOCK owns the screen.
 *
 * The 4 s window starts when this screen is first drawn. Tapping the card or
 * pressing a volume key continues the SAME window; it never restarts it.
 */
@Composable
private fun DecoyScreen(
    state: LockUiState,
    onDecoyContinue: () -> Unit,
    onAppInfo: () -> Unit,
    onCloseApp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DecoyBackdrop)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = DecoyCard,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onDecoyContinue)
                        .semantics {
                            contentDescription =
                                "NEXO LOCK. Access temporarily paused. Continue authentication."
                        }
                        .padding(horizontal = 26.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(DecoyIconWell),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = DecoyAccent,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexoMark(size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "NEXO LOCK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.6.sp,
                            ),
                            color = DecoyMuted,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Access temporarily paused",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                        ),
                        color = DecoyTitle,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${state.targetLabel.ifBlank { "This app" }} is being held while " +
                            "the owner authenticates. It will continue after verification.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = DecoyBody,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Tap this card, or press a volume key, to continue.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = DecoyMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    DecoyCountdown(state)
                }
                HorizontalDivider(thickness = 1.dp, color = DecoyDivider)
                DecoyAction(
                    label = "App info",
                    description = "Open NEXO LOCK app info in Android settings",
                    onClick = onAppInfo,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = DecoyAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = DecoyDivider)
                DecoyAction(
                    label = "Close app",
                    description = "Cancel authentication and leave the protected app",
                    onClick = onCloseApp,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = DecoyMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    "App-owned protection screen · NEXO LOCK",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = DecoyMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The decoy window is a real, fixed deadline. Showing it is honest: the owner
 * can see exactly how long the pause remains before the session is denied.
 */
@Composable
private fun DecoyCountdown(state: LockUiState) {
    if (!state.stageTimerActive || state.stageTotalMs <= 0L) return
    val seconds = state.stageRemainingMs.toFloat() / 1000f
    LinearProgressIndicator(
        progress = {
            (state.stageRemainingMs.toFloat() / state.stageTotalMs.toFloat())
                .coerceIn(0f, 1f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50)),
        color = if (state.stageRemainingMs <= 1_000L) DangerRed else DecoyAccent,
        trackColor = DecoyDivider,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Paused for ${String.format(Locale.US, "%.1f", seconds)} s",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = DecoyMuted,
    )
}

private val DecoyBackdrop = Color(0xFF101215)
private val DecoyCard = Color(0xFF1C1F24)
private val DecoyTitle = Color(0xFFF2F3F5)
private val DecoyBody = Color(0xFFB8BEC7)
private val DecoyMuted = Color(0xFF8A9099)
private val DecoyAccent = Color(0xFF8AB4F8)
private val DecoyIconWell = Color(0xFF262A30)
private val DecoyDivider = Color(0xFF2A2E35)

@Composable
private fun DecoyAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = 58.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = DecoyMuted,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun AuthenticationScreen(
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
    verifyRecoveryPin: (String, (String?) -> Unit) -> Unit,
    verifyRecoveryPattern: (String, (String?) -> Unit) -> Unit,
    onRecoverySuccess: (UnlockMethod) -> Unit,
    onResetGesture: () -> Unit,
) {
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
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    NexoWordmark(subtitle = null)
                }
                StatusPill(
                    if (state.phase is LockPhase.MasterPin) {
                        "PIN GATE"
                    } else {
                        state.protectionMode.ifBlank { "SECURED" }
                    },
                    PillTone.ACCENT,
                )
            }
            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(18.dp))
                return@NexoBackdrop
            }

            AppIcon(
                packageName = state.targetPackage,
                label = state.targetLabel,
                size = 54.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.targetLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "is protected by NEXO LOCK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            state.fallbackNotice?.let { notice ->
                InfoBanner(notice, BannerTone.WARNING)
                Spacer(Modifier.height(10.dp))
            }

            FactorStrip(state)
            Spacer(Modifier.height(10.dp))

            StageTimer(state)
            Spacer(Modifier.height(10.dp))

            state.error?.let {
                InfoBanner(it, BannerTone.DANGER)
                Spacer(Modifier.height(8.dp))
            }
            state.info?.let {
                InfoBanner(it, BannerTone.INFO)
                Spacer(Modifier.height(8.dp))
            }
            state.captureState?.lastStatus?.let { status ->
                val text = when (status) {
                    CaptureStatus.CAPTURED -> "Intruder capture stored in the encrypted Intruder Vault."
                    CaptureStatus.DISABLED -> null
                    else -> "Intruder capture: ${state.captureState.lastMessage ?: status.name.lowercase()}"
                }
                if (text != null) {
                    InfoBanner(text, if (status == CaptureStatus.CAPTURED) BannerTone.WARNING else BannerTone.INFO)
                    Spacer(Modifier.height(8.dp))
                }
            }

            when (state.activeFactor) {
                AuthFactor.BIOMETRIC -> BiometricFactor(state, onBiometric)
                AuthFactor.GESTURE -> GestureFactor(state, onResetGesture)
                AuthFactor.PIN, null -> PinFactor(
                    state = state,
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                )
            }

            // Recovery replaces the master PIN step only; all pre-PIN factors
            // must already be verified (the session is in the PIN stage).
            if (state.phase is LockPhase.MasterPin && !state.showRecovery &&
                (state.hasRecoveryPin || state.hasRecoveryPattern)
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRecovery, enabled = !state.busy) {
                    Text("Forgot the PIN? Use recovery")
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * Per-stage timing, never a shared deadline. Biometric has no NEXO timer, so
 * the screen says so instead of inventing a countdown.
 */
@Composable
private fun StageTimer(state: LockUiState) {
    if (state.phase is LockPhase.Biometric) {
        Text(
            "Android owns this prompt. Only a successful result continues; " +
                "failure or cancellation denies access.",
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
            (state.stageRemainingMs.toFloat() / state.stageTotalMs.toFloat())
                .coerceIn(0f, 1f)
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
        LockPhase.VolumePattern -> "Volume-pattern window"
        is LockPhase.MasterPin -> "Master-PIN window"
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
private fun FactorStrip(state: LockUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.requiredFactors.forEach { factor ->
            val done = factor in state.completedFactors
            val active = factor == state.activeFactor
            val tone = when {
                done -> PillTone.SUCCESS
                active -> PillTone.ACCENT
                else -> PillTone.NEUTRAL
            }
            StatusPill(
                if (done) "✓ ${factor.shortLabel}" else factor.shortLabel,
                tone,
            )
        }
        val pinActive = state.phase is LockPhase.MasterPin
        StatusPill(
            if (state.unlocked) "✓ PIN" else "PIN",
            when {
                state.unlocked -> PillTone.SUCCESS
                pinActive -> PillTone.ACCENT
                else -> PillTone.NEUTRAL
            },
        )
    }
}

@Composable
private fun BiometricFactor(
    state: LockUiState,
    onBiometric: () -> Unit,
) {
    NexoCard(Modifier.widthIn(max = 340.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Verify your fingerprint",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Step 1 of ${state.requiredFactors.size + 1}. " +
                        "The volume pattern follows only after the system prompt closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onBiometric,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.biometricEnabled && state.biometricReady && !state.busy,
        ) {
            Text("Use fingerprint")
        }
    }
}

@Composable
private fun GestureFactor(
    state: LockUiState,
    onResetGesture: () -> Unit,
) {
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
        Text(
            "Step ${state.requiredFactors.indexOf(AuthFactor.GESTURE) + 1} of " +
                "${state.requiredFactors.size + 1}. The pattern window is fixed and does not restart.",
            style = MaterialTheme.typography.labelSmall,
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
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
) {
    PinDots(
        length = state.pinLength,
        filled = state.pin.length,
        error = state.error != null,
    )
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
            color = WarningAmber,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeniedScreen(state: LockUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D10))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2A2E33),
            tonalElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Access denied",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                    ),
                    color = Color(0xFFF2F3F5),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.deniedMessage ?: "The authentication session ended without success.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8BEC7),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No factors were carried over. A new session is required.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A9099),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
