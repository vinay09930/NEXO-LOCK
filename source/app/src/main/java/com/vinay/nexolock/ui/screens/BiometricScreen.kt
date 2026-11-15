package com.vinay.nexolock.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vinay.nexolock.protection.PermissionChecks
import com.vinay.nexolock.ui.BiometricState
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigFilledIconContainer
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigSectionHeader
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureScreen
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoSpace

@Composable
fun BiometricSetupScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var testResult by remember { mutableStateOf<String?>(null) }

    SignatureScreen(
        title = "Biometric setup",
        subtitle = "Fingerprint or face, handled by Android.",
        eyebrow = "AUTHENTICATION",
        onBack = onBack,
    ) {
        Spacer(Modifier.height(NexoSpace.sm))
        SigCard(
            container = NexoPalette.SurfaceElevated,
            padding = NexoSpace.lg,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SigFilledIconContainer(
                    icon = NexoIcons.Fingerprint,
                    size = 44.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(NexoSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Biometric unlock",
                        style = MaterialTheme.typography.titleLarge,
                        color = NexoPalette.TextPrimary,
                    )
                    Text(
                        when (state.biometric) {
                            BiometricState.READY ->
                                "A biometric is enrolled and the hardware is ready."

                            BiometricState.NOT_ENROLLED ->
                                "The hardware exists, but no biometric is enrolled."

                            BiometricState.UNSUPPORTED ->
                                "No compatible biometric was reported by this device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoPalette.TextSecondary,
                    )
                }
                SigPill(
                    when (state.biometric) {
                        BiometricState.READY -> "READY"
                        BiometricState.NOT_ENROLLED -> "ENROLL"
                        BiometricState.UNSUPPORTED -> "N/A"
                    },
                    when (state.biometric) {
                        BiometricState.READY -> SigTone.SUCCESS
                        BiometricState.NOT_ENROLLED -> SigTone.WARNING
                        BiometricState.UNSUPPORTED -> SigTone.DANGER
                    },
                )
            }
            Spacer(Modifier.height(NexoSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Allow biometric unlock",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NexoPalette.TextPrimary,
                    )
                    Text(
                        "Applied to protected-app lock screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoPalette.TextSecondary,
                    )
                }
                Switch(
                    checked = state.config.biometricEnabled,
                    enabled = state.biometric == BiometricState.READY,
                    onCheckedChange = { vm.setBiometricEnabled(it) { failure ->
                        if (failure != null) vm.notify(failure, error = true)
                    } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NexoPalette.OnSilver,
                        checkedTrackColor = NexoPalette.Silver,
                        uncheckedThumbColor = NexoPalette.TextMuted,
                        uncheckedTrackColor = NexoPalette.SurfaceAlt,
                        uncheckedBorderColor = NexoPalette.Border,
                        disabledCheckedTrackColor = NexoPalette.Silver.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(NexoSpace.md))
        when (state.biometric) {
            BiometricState.NOT_ENROLLED -> {
                SigPrimaryButton(
                    text = "Open Android biometric enrollment",
                    onClick = {
                        vm.openSystemSettings(PermissionChecks.biometricEnrollIntent())
                    },
                )
                Spacer(Modifier.height(NexoSpace.sm))
            }
            BiometricState.UNSUPPORTED -> {
                SigBanner(
                    text = "This device does not expose compatible biometric hardware. " +
                        "NEXO LOCK will always fall back to your PIN.",
                    tone = SigTone.WARNING,
                )
                Spacer(Modifier.height(NexoSpace.sm))
            }
            BiometricState.READY -> {
                SigPrimaryButton(
                    text = "Test biometric authentication",
                    onClick = {
                        val activity = context as? FragmentActivity
                        if (activity == null) {
                            testResult = "This screen must run inside the NEXO LOCK activity."
                            return@SigPrimaryButton
                        }
                        val prompt = BiometricPrompt(
                            activity,
                            ContextCompat.getMainExecutor(activity),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult,
                                ) {
                                    testResult = "Biometric verified by Android. No app " +
                                        "credential was used."
                                }

                                override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence,
                                ) {
                                    testResult = "Biometric test: " +
                                        (errString.ifBlank { "unavailable" })
                                }

                                override fun onAuthenticationFailed() {
                                    testResult = "Not recognised. The prompt kept listening."
                                }
                            },
                        )
                        val info = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("NEXO LOCK biometric test")
                            .setSubtitle("This does not unlock any app")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .setNegativeButtonText("Cancel")
                            .build()
                        runCatching { prompt.authenticate(info) }.onFailure {
                            testResult = "This device could not start BiometricPrompt."
                        }
                    },
                )
                Spacer(Modifier.height(NexoSpace.sm))
            }
        }

        testResult?.let {
            SigBanner(
                text = it,
                tone = if (it.startsWith("Biometric verified")) {
                    SigTone.SUCCESS
                } else {
                    SigTone.INFO
                },
            )
            Spacer(Modifier.height(NexoSpace.sm))
        }

        SigSectionHeader("How fallbacks work")
        Spacer(Modifier.height(NexoSpace.xs))
        SigBanner(
            text = "If biometrics are unavailable, locked, not enrolled, or repeatedly fail, " +
                "the lock screen asks for your PIN. NEXO LOCK never reports a biometric " +
                "success unless Android's BiometricPrompt returned AuthenticationResult.",
            tone = SigTone.INFO,
        )
        Spacer(Modifier.height(NexoSpace.sm))
        SigBanner(
            text = "Biometric enrollment changes are owned by Android. The app re-checks " +
                "availability on every lock screen and asks Android to authenticate each " +
                "time; it never keeps an enrollment-bound secret or skips the PIN requirement.",
            tone = SigTone.INFO,
        )
        Spacer(Modifier.height(NexoSpace.md))
        Text(
            "Biometric data never enters the app. The app does not receive, store or log " +
                "fingerprints or face data.",
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NexoSpace.lg))
    }
}
