package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.VolumeCapture
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigPatternPad
import com.vinay.nexolock.ui.components.SigPinPanel
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigSecondaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace

private enum class PinStage { CURRENT, NEW, CONFIRM }

@Composable
fun PinSetupScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    val config = state.config
    var stage by rememberSaveable { mutableStateOf(PinStage.CURRENT) }
    var currentPin by rememberSaveable { mutableStateOf("") }
    var firstNew by rememberSaveable { mutableStateOf("") }
    var buffer by rememberSaveable { mutableStateOf("") }
    var newLength by rememberSaveable { mutableIntStateOf(config.pinLength) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    SignatureScreen(
        title = "Main PIN",
        subtitle = "Change the root authentication factor.",
        eyebrow = "AUTHENTICATION",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            text = when (stage) {
                PinStage.CURRENT -> "Enter your current PIN"
                PinStage.NEW -> "Choose a new PIN"
                PinStage.CONFIRM -> "Confirm the new PIN"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = NexoPalette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            text = "Changing the PIN does not remove protected apps. The new verifier " +
                "replaces the old one.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.md))
        error?.let {
            SigBanner(text = it, tone = SigTone.DANGER)
            Spacer(Modifier.height(NexoSpace.sm))
        }

        if (stage == PinStage.NEW) {
            Text(
                "PIN length",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
                (4..8).forEach { candidate ->
                    SigChip(
                        text = "$candidate",
                        selected = newLength == candidate,
                        onClick = {
                            newLength = candidate
                            buffer = ""
                        },
                    )
                }
            }
            Spacer(Modifier.height(NexoSpace.md))
        }

        SigPinPanel(
            entered = buffer,
            slots = if (stage == PinStage.CURRENT) {
                config.pinLength
            } else {
                if (stage == PinStage.NEW) {
                    newLength
                } else {
                    firstNew.length.coerceAtLeast(newLength)
                }
            },
            enabled = !busy,
            error = error != null,
            onDigit = { digit ->
                if (!busy) {
                    val target = if (stage == PinStage.CURRENT) config.pinLength else newLength
                    if (buffer.length < target) {
                        buffer += digit.toString()
                        if (buffer.length == target) {
                            when (stage) {
                                PinStage.CURRENT -> {
                                    val value = buffer
                                    busy = true
                                    vm.verifyMainPin(value) { failure ->
                                        busy = false
                                        if (failure == null) {
                                            currentPin = value
                                            buffer = ""
                                            error = null
                                            stage = PinStage.NEW
                                        } else {
                                            error = failure
                                            buffer = ""
                                        }
                                    }
                                }
                                PinStage.NEW -> {
                                    firstNew = buffer
                                    buffer = ""
                                    error = null
                                    stage = PinStage.CONFIRM
                                }
                                PinStage.CONFIRM -> {
                                    if (buffer != firstNew) {
                                        error = "The PINs did not match. Enter a new PIN again."
                                        buffer = ""
                                        firstNew = ""
                                        stage = PinStage.NEW
                                    } else {
                                        val value = buffer
                                        busy = true
                                        vm.changeMainPin(currentPin, value) { failure ->
                                            busy = false
                                            if (failure == null) {
                                                vm.notify("Main PIN changed.")
                                                onBack()
                                            } else {
                                                error = failure
                                                buffer = ""
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onBackspace = { if (!busy) buffer = buffer.dropLast(1) },
        )
        Spacer(Modifier.height(NexoSpace.md))
        if (busy) {
            Text(
                "Verifying…",
                style = MaterialTheme.typography.labelMedium,
                color = NexoPalette.Silver,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "PBKDF2-HMAC-SHA256 · 600,000 iterations · random salt",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexoSpace.lg))
    }
}

private enum class PatternStage { AUTHORIZE, DRAW, CONFIRM }

@Composable
fun RecoveryPatternScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    val config = state.config
    var stage by rememberSaveable { mutableStateOf(PatternStage.AUTHORIZE) }
    var mainPin by rememberSaveable { mutableStateOf("") }
    var buffer by rememberSaveable { mutableStateOf("") }
    var firstPattern by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    SignatureScreen(
        title = "Recovery pattern",
        subtitle = "A 3×3 dot sequence used only for recovery.",
        eyebrow = "RECOVERY",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        Text(
            text = when (stage) {
                PatternStage.AUTHORIZE -> "Authorise with your main PIN"
                PatternStage.DRAW -> "Draw the new recovery pattern"
                PatternStage.CONFIRM -> "Draw it again to confirm"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = NexoPalette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            text = "The recovery pattern is separate from your main PIN and your volume " +
                "pattern. Stored only as a salted verifier.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.md))
        error?.let {
            SigBanner(text = it, tone = SigTone.DANGER)
            Spacer(Modifier.height(NexoSpace.sm))
        }

        when (stage) {
            PatternStage.AUTHORIZE -> {
                SigPinPanel(
                    entered = buffer,
                    slots = config.pinLength,
                    enabled = !busy,
                    error = error != null,
                    onDigit = { digit ->
                        if (!busy && buffer.length < config.pinLength) {
                            buffer += digit.toString()
                            if (buffer.length == config.pinLength) {
                                val value = buffer
                                busy = true
                                vm.verifyMainPin(value) { failure ->
                                    busy = false
                                    if (failure == null) {
                                        mainPin = value
                                        buffer = ""
                                        error = null
                                        stage = PatternStage.DRAW
                                    } else {
                                        error = failure
                                        buffer = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = { if (!busy) buffer = buffer.dropLast(1) },
                )
            }

            PatternStage.DRAW, PatternStage.CONFIRM -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SigPatternPad(
                        selected = pattern.map { it - '0' },
                        enabled = !busy,
                        onDot = { index ->
                            if (!busy && index !in pattern.map { it - '0' } &&
                                pattern.length < 9
                            ) {
                                pattern += index.toString()
                                error = null
                            }
                        },
                    )
                }
                Spacer(Modifier.height(NexoSpace.sm))
                Text(
                    if (pattern.isEmpty()) "Tap at least 4 dots in order" else "${pattern.length} dots",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(NexoSpace.lg))
                SigPrimaryButton(
                    text = when {
                        busy -> "Saving…"
                        stage == PatternStage.DRAW -> "Continue"
                        else -> "Save recovery pattern"
                    },
                    enabled = !busy && pattern.length >= 4,
                    loading = busy,
                    onClick = {
                        if (stage == PatternStage.DRAW) {
                            firstPattern = pattern
                            pattern = ""
                            stage = PatternStage.CONFIRM
                        } else if (pattern != firstPattern) {
                            error = "The patterns did not match. Draw it again."
                            firstPattern = ""
                            pattern = ""
                            stage = PatternStage.DRAW
                        } else {
                            busy = true
                            vm.changeRecoveryPattern(mainPin, pattern) { failure ->
                                busy = false
                                if (failure == null) {
                                    vm.notify("Recovery pattern updated.")
                                    onBack()
                                } else {
                                    error = failure
                                    pattern = ""
                                }
                            }
                        }
                    },
                )
                if (pattern.isNotEmpty()) {
                    SigGhostButton(
                        text = "Clear",
                        onClick = { pattern = "" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(NexoSpace.lg))
    }
}

private sealed interface GestureStage {
    data object Idle : GestureStage
    data class Recording(val length: Int) : GestureStage
    data class Confirming(val first: VolumePattern) : GestureStage
    data class Testing(val length: Int) : GestureStage
}

private val GestureStage.captureLength: Int
    get() = when (this) {
        is GestureStage.Recording -> length
        is GestureStage.Confirming -> first.presses.size
        is GestureStage.Testing -> length
        GestureStage.Idle -> 0
    }

@Composable
fun GestureSetupScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    val config = state.config
    var stage by remember { mutableStateOf<GestureStage>(GestureStage.Idle) }
    var pickerLength by rememberSaveable {
        mutableIntStateOf(
            config.mainGestureLength.takeIf {
                it in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH
            } ?: VolumePattern.MIN_LENGTH,
        )
    }
    var filled by rememberSaveable { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    val captureOwner = remember { Any() }

    val capture = remember(stage) {
        val current = stage
        when (current) {
            is GestureStage.Recording -> VolumeCapture.createOrNull(current.length)
            is GestureStage.Confirming -> VolumeCapture.createOrNull(current.first.presses.size)
            is GestureStage.Testing -> VolumeCapture.createOrNull(current.length)
            GestureStage.Idle -> null
        }
    }

    DisposableEffect(stage, capture) {
        if (capture != null && stage !is GestureStage.Idle) {
            vm.beginGestureCapture(captureOwner) { press ->
                handleGesturePress(
                    press = press,
                    capture = capture,
                    stage = stage,
                    onProgress = { filled = it },
                    onComplete = { pattern ->
                        filled = 0
                        when (val current = stage) {
                            is GestureStage.Recording -> {
                                error = null
                                stage = GestureStage.Confirming(pattern)
                            }
                            is GestureStage.Confirming -> {
                                if (pattern == current.first) {
                                    vm.saveGesture(pattern) { failure ->
                                        if (failure == null) {
                                            stage = GestureStage.Idle
                                            vm.notify("Volume pattern saved.")
                                        } else {
                                            error = failure
                                        }
                                    }
                                } else {
                                    error = "The two sequences did not match. Record again."
                                    stage = GestureStage.Recording(current.first.presses.size)
                                }
                            }
                            is GestureStage.Testing -> {
                                vm.testGesture(pattern) { ok ->
                                    testResult = if (ok) {
                                        "Volume pattern matched the saved sequence."
                                    } else {
                                        "Volume pattern did not match the saved sequence."
                                    }
                                    stage = GestureStage.Idle
                                }
                            }
                            GestureStage.Idle -> Unit
                        }
                    },
                )
            }
        }
        onDispose { vm.endGestureCapture(captureOwner) }
    }

    SignatureScreen(
        title = "Volume pattern",
        subtitle = "Secret hardware-key factor in the unlock sequence.",
        eyebrow = "AUTHENTICATION",
        onBack = {
            if (stage is GestureStage.Idle) {
                onBack()
            } else {
                stage = GestureStage.Idle
                filled = 0
            }
        },
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        testResult?.let {
            SigBanner(
                text = it,
                tone = if (it.startsWith("Volume pattern matched")) {
                    SigTone.SUCCESS
                } else {
                    SigTone.DANGER
                },
            )
            Spacer(Modifier.height(NexoSpace.sm))
        }

        when (val current = stage) {
            GestureStage.Idle -> {
                SigCard(padding = NexoSpace.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Volume pattern unlock",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoPalette.TextPrimary,
                            )
                            Text(
                                if (config.hasMainGesture) {
                                    "Saved sequence of ${config.mainGestureLength} presses"
                                } else {
                                    "No volume pattern is configured"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NexoPalette.TextSecondary,
                            )
                        }
                        CredentialSwitch(
                            checked = config.hasMainGesture && config.gestureEnabled,
                            enabled = config.hasMainGesture,
                            onCheckedChange = vm::setGestureEnabled,
                        )
                    }
                }
                Spacer(Modifier.height(NexoSpace.lg))
                Text(
                    "SEQUENCE LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
                Spacer(Modifier.height(NexoSpace.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
                    (VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH).forEach { candidate ->
                        SigChip(
                            text = "$candidate",
                            selected = pickerLength == candidate,
                            onClick = { pickerLength = candidate },
                        )
                    }
                }
                Spacer(Modifier.height(NexoSpace.lg))
                SigPrimaryButton(
                    text = if (config.hasMainGesture) {
                        "Record a new pattern"
                    } else {
                        "Record pattern"
                    },
                    onClick = {
                        stage = GestureStage.Recording(pickerLength)
                        filled = 0
                        error = null
                        testResult = null
                    },
                )
                if (config.hasMainGesture) {
                    Spacer(Modifier.height(NexoSpace.sm))
                    SigSecondaryButton(
                        text = "Test saved pattern",
                        onClick = {
                            val savedLength = config.mainGestureLength
                            if (savedLength !in
                                VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH
                            ) {
                                error = "The saved volume pattern length is invalid. " +
                                    "Record a new pattern."
                            } else {
                                stage = GestureStage.Testing(savedLength)
                                filled = 0
                                error = null
                                testResult = null
                            }
                        },
                    )
                    Spacer(Modifier.height(NexoSpace.sm))
                    SigGhostButton(
                        text = "Remove pattern",
                        destructive = true,
                        onClick = {
                            vm.clearGesture()
                            stage = GestureStage.Idle
                            filled = 0
                            testResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(NexoSpace.lg))
                SigBanner(
                    text = "Android only delivers volume keys to the focused app. The volume " +
                        "pattern is captured inside NEXO LOCK screens (lock screen and this " +
                        "test mode) — never globally. That is why configuring it can no " +
                        "longer break navigation.",
                    tone = SigTone.INFO,
                )
                Spacer(Modifier.height(NexoSpace.sm))
                Text(
                    "V2 requested global key filtering through an accessibility service, " +
                        "which could swallow volume keys system-wide and stopped responding " +
                        "when the service was not connected. V3 consumes the keys only while " +
                        "its own Activity is focused.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
            }

            is GestureStage.Recording,
            is GestureStage.Confirming,
            is GestureStage.Testing,
            -> {
                val length = current.captureLength
                val title = when (current) {
                    is GestureStage.Recording -> "Record the sequence"
                    is GestureStage.Confirming -> "Repeat the sequence"
                    is GestureStage.Testing -> "Test the saved sequence"
                    GestureStage.Idle -> ""
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexoPalette.TextPrimary,
                )
                Spacer(Modifier.height(NexoSpace.xs))
                Text(
                    text = "Press volume up or volume down $length times. The system volume " +
                        "panel is suppressed only while this capture is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
                Spacer(Modifier.height(NexoSpace.lg))
                SigCard(padding = NexoSpace.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Captured presses",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoPalette.TextPrimary,
                            )
                            Text(
                                "$filled of $length",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexoPalette.TextSecondary,
                            )
                        }
                        SigPill(
                            if (filled == 0) "WAITING" else "CAPTURING",
                            if (filled == 0) SigTone.NEUTRAL else SigTone.SILVER,
                        )
                    }
                    Spacer(Modifier.height(NexoSpace.md))
                    GestureSequence(expected = length, filled = filled)
                }
                Spacer(Modifier.height(NexoSpace.md))
                error?.let {
                    SigBanner(text = it, tone = SigTone.DANGER)
                    Spacer(Modifier.height(NexoSpace.sm))
                }
                SigBanner(
                    text = "Press the hardware volume buttons now. If they are not delivered, " +
                        "make sure no other app or accessibility service is filtering keys.",
                    tone = SigTone.INFO,
                )
                Spacer(Modifier.height(NexoSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
                    SigGhostButton(
                        text = "Cancel capture",
                        onClick = {
                            stage = GestureStage.Idle
                            filled = 0
                        },
                    )
                    SigGhostButton(
                        text = "Reset presses",
                        onClick = {
                            filled = 0
                            capture?.reset()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(NexoSpace.lg))
    }
}

@Composable
private fun GestureSequence(expected: Int, filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs)) {
        repeat(expected.coerceAtLeast(1)) { index ->
            val active = index < filled
            Box(
                Modifier
                    .size(if (active) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (active) NexoPalette.Silver else NexoPalette.SurfaceAlt),
            )
        }
    }
}

@Composable
private fun CredentialSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = NexoPalette.OnSilver,
            checkedTrackColor = NexoPalette.Silver,
            uncheckedThumbColor = NexoPalette.TextMuted,
            uncheckedTrackColor = NexoPalette.SurfaceAlt,
            uncheckedBorderColor = NexoPalette.Border,
            disabledCheckedTrackColor = NexoPalette.Silver.copy(alpha = 0.3f),
            disabledUncheckedTrackColor = NexoPalette.SurfaceAlt.copy(alpha = 0.5f),
        ),
    )
}

/**
 * Pure press handler kept outside composition state so the exact same logic can
 * be unit tested. The controller already filters repeats and key-up events.
 */
private fun handleGesturePress(
    press: VolumePress,
    capture: VolumeCapture,
    stage: GestureStage,
    onProgress: (Int) -> Unit,
    onComplete: (VolumePattern) -> Unit,
) {
    if (stage is GestureStage.Idle) return
    when (val result = capture.press(press, System.currentTimeMillis())) {
        is VolumeCapture.Result.Progress -> onProgress(result.count)
        is VolumeCapture.Result.Complete -> onComplete(result.pattern)
    }
}
