package com.vinay.nexolock.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigChip
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigPinPanel
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureBackdrop
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace

/**
 * First-run setup has exactly two mandatory steps:
 *
 *  1. record the volume pattern and confirm it;
 *  2. create the master PIN and confirm it.
 *
 * Optional capabilities (biometric, vaults, protected apps, monitoring
 * permission) are never forced here. They are configured later from Settings /
 * Protection Settings and are reported honestly by the dashboard.
 *
 * The flow is an explicit state machine. The previous implementation mixed a
 * step index with independent `confirming` / `first…` / `busy` flags, which
 * allowed impossible combinations such as confirming an empty pattern. Here the
 * step owns the draft data, and the confirmation stage cannot exist without a
 * first value.
 */
private sealed interface SetupStep {
    data class PatternEntry(val code: String = "") : SetupStep
    data class PatternConfirm(val first: String, val code: String = "") : SetupStep
    data class PinEntry(val length: Int = 6, val buffer: String = "") : SetupStep
    data class PinConfirm(val length: Int, val first: String, val buffer: String = "") : SetupStep
    data object Saving : SetupStep
    data object Complete : SetupStep
}

private data class SetupUiState(
    val step: SetupStep = SetupStep.PatternEntry(),
    val initialized: Boolean = false,
    val error: String? = null,
) {
    val stepNumber: Int
        get() = when (step) {
            is SetupStep.PatternEntry, is SetupStep.PatternConfirm -> 1
            is SetupStep.PinEntry, is SetupStep.PinConfirm -> 2
            SetupStep.Saving, SetupStep.Complete -> 2
        }

    val capturingVolume: Boolean
        get() = step is SetupStep.PatternEntry || step is SetupStep.PatternConfirm
}

@Composable
fun SetupScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val config = state.config
    var ui by remember { mutableStateOf(SetupUiState()) }
    val captureOwner = remember { Any() }

    // Resume truthfully from what is already configured. This runs once, so a
    // config update caused by saving the pattern cannot rewind the UI.
    LaunchedEffect(state.loading) {
        if (!ui.initialized && !state.loading) {
            ui = ui.copy(
                initialized = true,
                step = when {
                    config.hasMainPin && config.hasMainGesture -> SetupStep.Complete
                    config.hasMainGesture -> SetupStep.PinEntry()
                    else -> SetupStep.PatternEntry()
                },
            )
        }
    }

    // Volume keys are owned by this screen only while a pattern stage is
    // visible. The owner token prevents a disposing screen from clearing a
    // newer capture. No Accessibility service is involved.
    DisposableEffect(ui.capturingVolume) {
        if (ui.capturingVolume) {
            vm.beginGestureCapture(captureOwner) { press ->
                ui = ui.appendVolumePress(press)
            }
        }
        onDispose { vm.endGestureCapture(captureOwner) }
    }

    fun back(): Unit = when (val step = ui.step) {
        is SetupStep.PatternConfirm -> ui = ui.copy(
            step = SetupStep.PatternEntry(),
            error = null,
        )
        is SetupStep.PinEntry -> ui = ui.copy(
            step = SetupStep.PatternEntry(),
            error = null,
        )
        is SetupStep.PinConfirm -> ui = ui.copy(
            step = SetupStep.PinEntry(step.length),
            error = null,
        )
        is SetupStep.PatternEntry, SetupStep.Complete -> onBack()
        SetupStep.Saving -> Unit
    }

    BackHandler(enabled = ui.step != SetupStep.Saving) { back() }

    SignatureBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SetupHeader(
                stepNumber = ui.stepNumber,
                totalSteps = 2,
                title = when (ui.step) {
                    is SetupStep.PatternEntry -> "Volume pattern"
                    is SetupStep.PatternConfirm -> "Confirm volume pattern"
                    is SetupStep.PinEntry -> "Master PIN"
                    is SetupStep.PinConfirm -> "Confirm master PIN"
                    SetupStep.Saving -> "Saving"
                    SetupStep.Complete -> "Setup complete"
                },
                canGoBack = ui.step != SetupStep.Saving && ui.step != SetupStep.Complete,
                onBack = ::back,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NexoSpace.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(NexoSpace.sm))
                ui.error?.let {
                    SigBanner(text = it, tone = SigTone.DANGER)
                    Spacer(Modifier.height(NexoSpace.sm))
                }

                when (val step = ui.step) {
                    is SetupStep.PatternEntry -> PatternEntryStep(
                        code = step.code,
                        onClear = { ui = ui.copy(step = SetupStep.PatternEntry(), error = null) },
                        onContinue = {
                            val pattern = VolumePattern.parse(step.code)
                            if (pattern == null) {
                                ui = ui.copy(error = "Use between 4 and 8 presses.")
                            } else {
                                ui = ui.copy(
                                    step = SetupStep.PatternConfirm(step.code),
                                    error = null,
                                )
                            }
                        },
                    )

                    is SetupStep.PatternConfirm -> PatternConfirmStep(
                        first = step.first,
                        code = step.code,
                        onRestart = {
                            ui = ui.copy(step = SetupStep.PatternEntry(), error = null)
                        },
                        onConfirm = {
                            if (step.code != step.first) {
                                ui = ui.copy(
                                    step = SetupStep.PatternEntry(),
                                    error = "The patterns did not match. Record it again.",
                                )
                            } else {
                                val pattern = VolumePattern.parse(step.code)
                                if (pattern == null) {
                                    ui = ui.copy(
                                        step = SetupStep.PatternEntry(),
                                        error = "Use between 4 and 8 presses.",
                                    )
                                } else {
                                    ui = ui.copy(step = SetupStep.Saving, error = null)
                                    vm.saveGesture(pattern) { failure ->
                                        ui = if (failure == null) {
                                            ui.copy(step = SetupStep.PinEntry(), error = null)
                                        } else {
                                            ui.copy(
                                                step = SetupStep.PatternEntry(),
                                                error = failure,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )

                    is SetupStep.PinEntry -> PinEntryStep(
                        length = step.length,
                        buffer = step.buffer,
                        onLength = { candidate ->
                            ui = ui.copy(step = SetupStep.PinEntry(candidate), error = null)
                        },
                        onDigit = { digit ->
                            if (step.buffer.length < step.length) {
                                val next = step.buffer + digit
                                ui = if (next.length == step.length) {
                                    ui.copy(
                                        step = SetupStep.PinConfirm(
                                            length = step.length,
                                            first = next,
                                        ),
                                        error = null,
                                    )
                                } else {
                                    ui.copy(step = step.copy(buffer = next), error = null)
                                }
                            }
                        },
                        onBackspace = {
                            ui = ui.copy(
                                step = step.copy(buffer = step.buffer.dropLast(1)),
                            )
                        },
                    )

                    is SetupStep.PinConfirm -> PinConfirmStep(
                        length = step.length,
                        buffer = step.buffer,
                        onDigit = { digit ->
                            if (step.buffer.length < step.length) {
                                val next = step.buffer + digit
                                if (next.length == step.length && next != step.first) {
                                    ui = ui.copy(
                                        step = SetupStep.PinEntry(step.length),
                                        error = "Those PINs did not match. Create the PIN again.",
                                    )
                                } else if (next.length == step.length) {
                                    ui = ui.copy(step = SetupStep.Saving, error = null)
                                    savePinAndFinish(vm, next) { failure ->
                                        ui = if (failure == null) {
                                            ui.copy(step = SetupStep.Complete, error = null)
                                        } else {
                                            ui.copy(
                                                step = SetupStep.PinEntry(step.length),
                                                error = failure,
                                            )
                                        }
                                    }
                                } else {
                                    ui = ui.copy(step = step.copy(buffer = next), error = null)
                                }
                            }
                        },
                        onBackspace = {
                            ui = ui.copy(
                                step = step.copy(buffer = step.buffer.dropLast(1)),
                            )
                        },
                        onRestart = {
                            ui = ui.copy(step = SetupStep.PinEntry(step.length), error = null)
                        },
                    )

                    SetupStep.Saving -> SavingStep()

                    SetupStep.Complete -> CompleteStep(onFinished)
                }
                Spacer(Modifier.height(NexoSpace.lg))
            }
        }
    }
}

private fun SetupUiState.appendVolumePress(press: VolumePress): SetupUiState {
    val character = if (press == VolumePress.UP) "U" else "D"
    return when (val step = step) {
        is SetupStep.PatternEntry ->
            if (step.code.length < VolumePattern.MAX_LENGTH) {
                copy(step = step.copy(code = step.code + character), error = null)
            } else {
                this
            }
        is SetupStep.PatternConfirm ->
            if (step.code.length < VolumePattern.MAX_LENGTH) {
                copy(step = step.copy(code = step.code + character), error = null)
            } else {
                this
            }
        else -> this
    }
}

private fun savePinAndFinish(
    vm: NexoViewModel,
    pin: String,
    onResult: (String?) -> Unit,
) {
    vm.createMainPin(pin) { failure ->
        if (failure != null) {
            onResult(failure)
        } else {
            vm.finishSetup(onResult)
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun SetupHeader(
    stepNumber: Int,
    totalSteps: Int,
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    Column(Modifier.padding(horizontal = NexoSpace.lg)) {
        Spacer(Modifier.height(NexoSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.width(NexoSpace.xs))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "NEXO LOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoPalette.TextPrimary,
                )
            }
            SigPill("STEP $stepNumber/$totalSteps", SigTone.SILVER)
        }
        Spacer(Modifier.height(NexoSpace.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..totalSteps).forEach { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                index == stepNumber -> NexoPalette.Silver
                                index < stepNumber -> NexoPalette.Silver.copy(alpha = 0.42f)
                                else -> NexoPalette.Border
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.md))
    }
}

// ---------------------------------------------------------------- step 1: pattern

@Composable
private fun PatternEntryStep(
    code: String,
    onClear: () -> Unit,
    onContinue: () -> Unit,
) {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Text(
            "Record your volume pattern",
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "Press the physical volume up / volume down keys in an order you choose. " +
                "Use 4 to 8 presses. The order matters.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
        )
        Spacer(Modifier.height(NexoSpace.lg))
        VolumeSequence(code)
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "${code.length}/${VolumePattern.MAX_LENGTH} presses",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigGhostButton(
                text = "Clear",
                enabled = code.isNotEmpty(),
                onClick = onClear,
            )
            Spacer(Modifier.weight(1f))
            SigPrimaryButton(
                text = "Continue",
                enabled = code.length in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH,
                onClick = onContinue,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
    Spacer(Modifier.height(NexoSpace.sm))
    Text(
        "Volume keys are captured only while this screen is in front. The pattern is " +
            "stored as a salted verifier — never as plain text and never in the security " +
            "journal.",
        style = MaterialTheme.typography.labelSmall,
        color = NexoPalette.TextMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PatternConfirmStep(
    first: String,
    code: String,
    onRestart: () -> Unit,
    onConfirm: () -> Unit,
) {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Text(
            "Press the same sequence again",
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "The first recording is held only in memory. It is cleared after the pattern " +
                "is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
        )
        Spacer(Modifier.height(NexoSpace.lg))
        VolumeSequence(code)
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "${code.length}/${first.length} presses",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SigGhostButton(text = "Record again", onClick = onRestart)
            Spacer(Modifier.weight(1f))
            SigPrimaryButton(
                text = "Confirm pattern",
                enabled = code.length == first.length &&
                    code.length in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH,
                onClick = onConfirm,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Composable
private fun VolumeSequence(code: String) {
    if (code.isEmpty()) {
        Text(
            "Waiting for volume keys…",
            style = MaterialTheme.typography.bodyMedium,
            color = NexoPalette.Silver,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs)) {
        code.forEachIndexed { index, character ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(NexoRadius.small))
                    .background(NexoPalette.SurfaceAlt)
                    .padding(horizontal = NexoSpace.sm, vertical = NexoSpace.xs),
            ) {
                Text(
                    "${index + 1}${if (character == 'U') "↑" else "↓"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoPalette.Silver,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- step 2: PIN

@Composable
private fun PinEntryStep(
    length: Int,
    buffer: String,
    onLength: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
) {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Text(
            "PIN LENGTH",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm)) {
            (4..8).forEach { candidate ->
                SigChip(
                    text = "$candidate",
                    selected = length == candidate,
                    onClick = { onLength(candidate) },
                )
            }
        }
        Spacer(Modifier.height(NexoSpace.lg))
        Text(
            "Create your master PIN",
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.md))
        SigPinPanel(
            entered = buffer,
            slots = length,
            onDigit = onDigit,
            onBackspace = onBackspace,
        )
    }
    Spacer(Modifier.height(NexoSpace.md))
    SigBanner(
        text = "The PIN is verified through a salted PBKDF2 verifier — the digits are " +
            "never stored. A recovery PIN can be added later in Settings → Recovery.",
        tone = SigTone.INFO,
    )
}

@Composable
private fun PinConfirmStep(
    length: Int,
    buffer: String,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onRestart: () -> Unit,
) {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Text(
            "Confirm the master PIN",
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            "Enter the same $length digits again.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
        )
        Spacer(Modifier.height(NexoSpace.md))
        SigPinPanel(
            entered = buffer,
            slots = length,
            onDigit = onDigit,
            onBackspace = onBackspace,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        SigGhostButton(
            text = "Start over",
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- completion

@Composable
private fun SavingStep() {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
                color = NexoPalette.Silver,
            )
            Spacer(Modifier.width(NexoSpace.md))
            Text(
                "Saving the security setup…",
                style = MaterialTheme.typography.bodyLarge,
                color = NexoPalette.TextPrimary,
            )
        }
    }
}

@Composable
private fun CompleteStep(onFinished: () -> Unit) {
    SigCard(Modifier.widthIn(max = 420.dp), padding = NexoSpace.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Security setup complete",
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoPalette.TextPrimary,
                )
                Text(
                    "The two mandatory factors are configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                )
            }
            SigPill("COMPLETE", SigTone.SUCCESS)
        }
        Spacer(Modifier.height(NexoSpace.md))
        SetupSummaryRow("Volume pattern", "Configured and confirmed")
        SetupSummaryRow("Master PIN", "Configured and confirmed")
    }
    Spacer(Modifier.height(NexoSpace.md))
    SigBanner(
        text = "Biometric, vaults, protected apps and monitoring permission are optional and " +
            "are not part of first-run setup. Configure them in Settings / Protection " +
            "Settings; the dashboard reports their real state.",
        tone = SigTone.INFO,
    )
    Spacer(Modifier.height(NexoSpace.md))
    SigPrimaryButton(text = "Open dashboard", onClick = onFinished)
}

@Composable
private fun SetupSummaryRow(title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexoSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = NexoPalette.TextPrimary,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextSecondary,
            )
        }
        SigPill("ENABLED", SigTone.SUCCESS)
    }
}
