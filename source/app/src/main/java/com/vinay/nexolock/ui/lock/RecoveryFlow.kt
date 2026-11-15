package com.vinay.nexolock.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.ui.components.BannerTone
import com.vinay.nexolock.ui.components.InfoBanner
import com.vinay.nexolock.ui.components.NexoButton
import com.vinay.nexolock.ui.components.PatternPad
import com.vinay.nexolock.ui.components.PillTone
import com.vinay.nexolock.ui.components.PinEntryPanel
import com.vinay.nexolock.ui.components.StatusPill

private enum class RecoveryStage { PIN, PATTERN }

/**
 * The shared, clearly branded recovery workflow:
 *   1. Master Recovery PIN (always required)
 *   2. Master Recovery Pattern (only when one is configured)
 *
 * The stage is an explicit enum; the PIN/pattern drafts are data belonging to
 * the active stage. The component never decides that authentication succeeded:
 * it reports the exact factor that was verified through [onSuccess]. Both
 * factors are verified by the repository, never by comparing values kept in
 * memory.
 */
@Composable
fun RecoveryFlow(
    hasRecoveryPin: Boolean,
    hasRecoveryPattern: Boolean,
    verifyPin: (String, (String?) -> Unit) -> Unit,
    verifyPattern: (String, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onSuccess: (UnlockMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var stage by rememberSaveable { mutableStateOf(RecoveryStage.PIN) }
    var pattern by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill("RECOVERY MODE", PillTone.WARNING)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "NEXO LOCK recovery",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (stage) {
                RecoveryStage.PIN ->
                    "Enter your Master Recovery PIN. This is separate from your main PIN and is never shown on screen."
                RecoveryStage.PATTERN ->
                    "Enter your Master Recovery Pattern to finish recovery."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        if (error != null) {
            InfoBanner(error ?: "", BannerTone.DANGER)
            Spacer(Modifier.height(12.dp))
        }

        when (stage) {
            RecoveryStage.PIN -> {
                if (!hasRecoveryPin) {
                InfoBanner(
                    "No recovery credential is configured. Open NEXO LOCK and set one in Recovery settings.",
                    BannerTone.WARNING,
                )
            } else {
                PinEntryPanel(
                    entered = pin,
                    slots = maxOf(6, pin.length),
                    enabled = !busy,
                    error = error != null,
                    onDigit = { digit ->
                        if (!busy && pin.length < 8) {
                            pin += digit.toString()
                            error = null
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                        error = null
                    },
                )
                Spacer(Modifier.height(16.dp))
                NexoButton(
                    text = if (busy) "Verifying…" else "Verify recovery PIN",
                    enabled = !busy && pin.length >= 4,
                    onClick = {
                        busy = true
                        verifyPin(pin) { failure ->
                            busy = false
                            if (failure == null) {
                                pin = ""
                                if (hasRecoveryPattern) {
                                    stage = RecoveryStage.PATTERN
                                    error = null
                                } else {
                                    onSuccess(UnlockMethod.RECOVERY_PIN)
                                }
                            } else {
                                error = failure
                                pin = ""
                            }
                        }
                    },
                )
            }
            }
            RecoveryStage.PATTERN -> {
            PatternPad(
                selected = pattern.map { it - '0' },
                enabled = !busy,
                onDot = { index ->
                    if (!busy && index !in pattern.map { it - '0' } && pattern.length < 9) {
                        pattern += index.toString()
                        error = null
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (pattern.isEmpty()) "Tap the dots in order" else "${pattern.length} dots connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            NexoButton(
                text = if (busy) "Verifying…" else "Verify recovery pattern",
                enabled = !busy && pattern.length >= 4,
                onClick = {
                    busy = true
                    verifyPattern(pattern) { failure ->
                        busy = false
                        if (failure == null) {
                            pattern = ""
                            onSuccess(UnlockMethod.RECOVERY_GESTURE)
                        } else {
                            error = failure
                            pattern = ""
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    enabled = !busy && pattern.isNotEmpty(),
                    onClick = {
                        pattern = ""
                        error = null
                    },
                ) {
                    Text("Clear dots")
                }
            }
        }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, enabled = !busy) {
            Text("Back to normal unlock")
        }
    }
}
