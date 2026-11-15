package com.vinay.nexolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.nexolock.ui.components.BrandHeader
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigCard
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureBackdrop
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace

@Composable
fun SplashScreen() {
    SignatureBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SigBrandMark(size = 96.dp)
            Spacer(Modifier.height(NexoSpace.xl))
            Text(
                text = "NEXO LOCK",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 6.sp,
                ),
                color = NexoPalette.TextPrimary,
            )
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                text = "PRIVATE APP LOCK",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp),
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.xxl))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = NexoPalette.Silver,
                trackColor = NexoPalette.SurfaceAlt,
                strokeWidth = 2.dp,
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = NexoSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SigPill("OFFLINE", SigTone.SILVER)
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                text = "Keystore-backed · No cloud",
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
    }
}

/**
 * Large brand emblem used by the splash and onboarding headers. Pure shapes and
 * the custom shield glyph, so it scales without a raster asset.
 */
@Composable
private fun SigBrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(NexoPalette.SurfaceElevated)
            .border(
                width = 1.dp,
                color = NexoPalette.Border,
                shape = RoundedCornerShape(size * 0.30f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(size * 0.14f)
                .clip(RoundedCornerShape(size * 0.22f))
                .background(NexoPalette.SurfaceAlt),
        )
        Icon(
            NexoIcons.ShieldCheck,
            contentDescription = null,
            tint = NexoPalette.Silver,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val points: List<String>,
)

private val onboardingPages = listOf(
    OnboardingPage(
        eyebrow = "WELCOME",
        title = "Your apps.\nYour lock.",
        body = "NEXO LOCK is an offline Android security app. Here is what it does:",
        points = listOf(
            "Protect applications with a real lock screen",
            "Secure personal files in an encrypted vault",
            "Detect failed authentication attempts",
            "Manage security settings from one dashboard",
        ),
    ),
    OnboardingPage(
        eyebrow = "PRIVACY",
        title = "Built to stay\non device.",
        body = "Credential verifiers are salted and hashed. The protected-app list, journal and two independent vaults are encrypted with separate non-exportable Android Keystore keys.",
        points = listOf(
            "No account, no analytics, no network use",
            "PINs and patterns are never stored as plain text",
            "Personal and Intruder Vaults use different PINs and keys",
        ),
    ),
    OnboardingPage(
        eyebrow = "AUTHENTICATION",
        title = "Three factors,\nthree fixed windows.",
        body = "Unlocking a protected app requires your biometric, your secret volume pattern and your master PIN — in that order, with an independent window for each stage.",
        points = listOf(
            "Decoy 4s · volume pattern 15s · master PIN 15s by default",
            "A stage window never restarts; expiry denies access",
            "Biometric uses Android's genuine prompt; cancellation denies",
        ),
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = onboardingPages[page]

    SignatureBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = NexoSpace.screenHorizontal),
        ) {
            BrandHeader(
                actions = {
                    SigGhostButton(text = "Skip", onClick = onComplete)
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(NexoSpace.lg))
                Column(Modifier.widthIn(max = 560.dp)) {
                    SigPill(current.eyebrow, SigTone.SILVER)
                    Spacer(Modifier.height(NexoSpace.md))
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = NexoPalette.TextPrimary,
                    )
                    Spacer(Modifier.height(NexoSpace.sm))
                    Text(
                        text = current.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NexoPalette.TextSecondary,
                    )

                    Spacer(Modifier.height(NexoSpace.xl))
                    SigCard(padding = NexoSpace.lg) {
                        current.points.forEachIndexed { index, point ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(NexoRadius.small))
                                        .background(NexoPalette.SurfaceAlt)
                                        .border(
                                            1.dp,
                                            NexoPalette.Border,
                                            RoundedCornerShape(NexoRadius.small),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexoPalette.Silver,
                                    )
                                }
                                Spacer(Modifier.width(NexoSpace.md))
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexoPalette.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (index != current.points.lastIndex) {
                                Spacer(Modifier.height(NexoSpace.md))
                            }
                        }
                    }

                    Spacer(Modifier.height(NexoSpace.md))
                    Text(
                        "No account · No analytics · No network use",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoPalette.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs),
                ) {
                    onboardingPages.indices.forEach { index ->
                        Box(
                            Modifier
                                .weight(if (index == page) 2f else 1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == page) {
                                        NexoPalette.Silver
                                    } else {
                                        NexoPalette.Border
                                    },
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(NexoSpace.lg))

                SigPrimaryButton(
                    text = if (page == onboardingPages.lastIndex) {
                        "Get started"
                    } else {
                        "Continue"
                    },
                    onClick = {
                        if (page == onboardingPages.lastIndex) {
                            onComplete()
                        } else {
                            page += 1
                        }
                    },
                )
                Spacer(Modifier.height(NexoSpace.xl))
            }
        }
    }
}
