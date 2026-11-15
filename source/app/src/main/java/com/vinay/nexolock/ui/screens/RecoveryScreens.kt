package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigEmptyState
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigPinPanel
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.lock.RecoveryFlow
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace

private enum class RecoveryPinStage { IDLE, AUTHORIZE, NEW, CONFIRM }

@Composable
fun RecoverySettingsScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onChangePattern: () -> Unit,
    onTestRecovery: () -> Unit,
) {
    val config = state.config
    var stage by rememberSaveable { mutableStateOf(RecoveryPinStage.IDLE) }
    var mainPin by rememberSaveable { mutableStateOf("") }
    var buffer by rememberSaveable { mutableStateOf("") }
    var firstNew by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var pendingReset by rememberSaveable { mutableStateOf(false) }

    SignatureScreen(
        title = "Recovery",
        subtitle = "A separate route back into your protected apps.",
        eyebrow = "RECOVERY",
        onBack = {
            if (stage == RecoveryPinStage.IDLE) {
                onBack()
            } else {
                stage = RecoveryPinStage.IDLE
                buffer = ""
                firstNew = ""
                error = null
                pendingReset = false
            }
        },
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        when (stage) {
            RecoveryPinStage.IDLE -> {
                SigCard(padding = NexoSpace.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Master Recovery PIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoPalette.TextPrimary,
                            )
                            Text(
                                "Must be different from the main PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexoPalette.TextSecondary,
                            )
                        }
                        SigPill(
                            if (config.hasRecoveryPin) "SET" else "MISSING",
                            if (config.hasRecoveryPin) SigTone.SUCCESS else SigTone.DANGER,
                        )
                    }
                    Spacer(Modifier.height(NexoSpace.xs))
                    SigGhostButton(
                        text = if (config.hasRecoveryPin) {
                            "Change recovery PIN"
                        } else {
                            "Set recovery PIN"
                        },
                        onClick = {
                            pendingReset = false
                            stage = RecoveryPinStage.AUTHORIZE
                            buffer = ""
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(NexoSpace.sm))
                SigCard(padding = NexoSpace.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Master Recovery Pattern",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoPalette.TextPrimary,
                            )
                            Text(
                                "Optional 3×3 dot sequence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexoPalette.TextSecondary,
                            )
                        }
                        SigPill(
                            if (config.hasRecoveryPattern) "SET" else "OPTIONAL",
                            if (config.hasRecoveryPattern) SigTone.SUCCESS else SigTone.NEUTRAL,
                        )
                    }
                    Spacer(Modifier.height(NexoSpace.xs))
                    SigGhostButton(
                        text = if (config.hasRecoveryPattern) {
                            "Change recovery pattern"
                        } else {
                            "Set recovery pattern"
                        },
                        onClick = onChangePattern,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(NexoSpace.sm))
                SigCard(padding = NexoSpace.md) {
                    Text(
                        "Test the recovery screen",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoPalette.TextPrimary,
                    )
                    Text(
                        "Walk through the same branded recovery flow used by the lock " +
                            "screen. Successful verification is written to the security log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoPalette.TextSecondary,
                    )
                    Spacer(Modifier.height(NexoSpace.xs))
                    SigGhostButton(
                        text = "Open NEXO LOCK recovery",
                        onClick = onTestRecovery,
                        enabled = config.hasRecoveryPin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(NexoSpace.md))
                SigBanner(
                    text = "The Master Recovery PIN cannot equal the main PIN. Recovery " +
                        "attempts share a separate rate limit and are recorded as recovery " +
                        "events, never as biometric successes.",
                    tone = SigTone.INFO,
                )
                Spacer(Modifier.height(NexoSpace.md))
                SigCard(padding = NexoSpace.md) {
                    Text(
                        "Reset recovery credentials",
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoPalette.TextPrimary,
                    )
                    Text(
                        "Removes both the Master Recovery PIN and the recovery pattern. " +
                            "Your main PIN, biometric setting, volume pattern and protected " +
                            "apps are kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoPalette.TextSecondary,
                    )
                    Spacer(Modifier.height(NexoSpace.xs))
                    SigGhostButton(
                        text = "Reset recovery credentials",
                        enabled = config.hasRecoveryPin || config.hasRecoveryPattern,
                        destructive = true,
                        onClick = {
                            pendingReset = true
                            stage = RecoveryPinStage.AUTHORIZE
                            buffer = ""
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            RecoveryPinStage.AUTHORIZE -> {
                Text(
                    "Authorise with your main PIN",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexoPalette.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(NexoSpace.md))
                error?.let {
                    SigBanner(text = it, tone = SigTone.DANGER)
                    Spacer(Modifier.height(NexoSpace.sm))
                }
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
                                    if (failure != null) {
                                        busy = false
                                        error = failure
                                        buffer = ""
                                        return@verifyMainPin
                                    }
                                    if (pendingReset) {
                                        vm.resetRecoveryCredentials(value) { resetFailure ->
                                            busy = false
                                            buffer = ""
                                            if (resetFailure == null) {
                                                pendingReset = false
                                                mainPin = ""
                                                firstNew = ""
                                                vm.notify("Recovery credentials reset.")
                                                stage = RecoveryPinStage.IDLE
                                            } else {
                                                error = resetFailure
                                            }
                                        }
                                    } else {
                                        busy = false
                                        mainPin = value
                                        buffer = ""
                                        error = null
                                        stage = RecoveryPinStage.NEW
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = { if (!busy) buffer = buffer.dropLast(1) },
                )
            }

            RecoveryPinStage.NEW, RecoveryPinStage.CONFIRM -> {
                Text(
                    if (stage == RecoveryPinStage.NEW) {
                        "New recovery PIN"
                    } else {
                        "Confirm recovery PIN"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexoPalette.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(NexoSpace.xs))
                Text(
                    "4 to 8 digits, different from the main PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
                Spacer(Modifier.height(NexoSpace.md))
                error?.let {
                    SigBanner(text = it, tone = SigTone.DANGER)
                    Spacer(Modifier.height(NexoSpace.sm))
                }
                SigPinPanel(
                    entered = buffer,
                    slots = maxOf(6, buffer.length),
                    enabled = !busy,
                    error = error != null,
                    onDigit = { digit ->
                        if (!busy && buffer.length < 8) {
                            buffer += digit.toString()
                            error = null
                        }
                    },
                    onBackspace = { if (!busy) buffer = buffer.dropLast(1) },
                )
                Spacer(Modifier.height(NexoSpace.lg))
                SigPrimaryButton(
                    text = when {
                        busy -> "Saving…"
                        stage == RecoveryPinStage.NEW -> "Continue"
                        else -> "Save recovery PIN"
                    },
                    enabled = !busy && AuthPolicy.validPin(buffer),
                    loading = busy,
                    onClick = {
                        if (stage == RecoveryPinStage.NEW) {
                            firstNew = buffer
                            buffer = ""
                            stage = RecoveryPinStage.CONFIRM
                        } else if (buffer != firstNew) {
                            error = "The recovery PINs did not match."
                            firstNew = ""
                            buffer = ""
                            stage = RecoveryPinStage.NEW
                        } else {
                            busy = true
                            vm.changeRecoveryPin(mainPin, buffer) { failure ->
                                busy = false
                                if (failure == null) {
                                    vm.notify("Recovery PIN updated.")
                                    stage = RecoveryPinStage.IDLE
                                    buffer = ""
                                    firstNew = ""
                                } else {
                                    error = failure
                                    buffer = ""
                                }
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.lg))
    }
}

@Composable
fun RecoveryAuthScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    var successMethod by rememberSaveable { mutableStateOf<String?>(null) }
    val config = state.config

    SignatureScreen(
        title = "Recovery authentication",
        subtitle = "Branded NEXO LOCK recovery route.",
        eyebrow = "RECOVERY",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        if (successMethod != null) {
            SigCard(padding = NexoSpace.md) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Recovery verified",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexoPalette.TextPrimary,
                        )
                        Text(
                            "Verified factor: $successMethod",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexoPalette.TextSecondary,
                        )
                    }
                    SigPill("SUCCESS", SigTone.SUCCESS)
                }
                Spacer(Modifier.height(NexoSpace.sm))
                Text(
                    "In a real locked-app session this result would unlock the app now. No " +
                        "biometric success is implied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
            }
            Spacer(Modifier.height(NexoSpace.md))
            SigPrimaryButton(text = "Done", onClick = onBack)
        } else if (!config.hasRecoveryPin) {
            SigEmptyState(
                icon = Icons.Filled.Lock,
                title = "Recovery is not configured",
                message = "Set a Master Recovery PIN in Recovery settings first.",
            )
            Spacer(Modifier.height(NexoSpace.md))
            SigPrimaryButton(text = "Back", onClick = onBack)
        } else {
            // This is deliberately the same flow the lock screen uses, so the
            // test walks the real UI rather than a look-alike copy.
            RecoveryFlow(
                hasRecoveryPin = config.hasRecoveryPin,
                hasRecoveryPattern = config.hasRecoveryPattern,
                verifyPin = vm::verifyRecoveryPin,
                verifyPattern = vm::verifyRecoveryPattern,
                onCancel = onBack,
                onSuccess = { method: UnlockMethod ->
                    vm.recordRecoveryVerification(method)
                    successMethod = method.label
                },
            )
            Spacer(Modifier.height(NexoSpace.md))
            SigBanner(
                text = "The Master Recovery PIN is validated first. When a recovery pattern " +
                    "is configured, it is required as the second factor.",
                tone = SigTone.INFO,
            )
        }
        Spacer(Modifier.height(NexoSpace.lg))
    }
}
