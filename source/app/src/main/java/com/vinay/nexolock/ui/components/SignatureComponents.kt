package com.vinay.nexolock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.nexolock.ui.theme.LocalNexoBottomPadding
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace

/** Semantic tone used by pills, banners, status cards and timeline rows. */
enum class SigTone { NEUTRAL, SILVER, SUCCESS, WARNING, DANGER, INFO }

fun SigTone.color(): Color = when (this) {
    SigTone.NEUTRAL -> NexoPalette.TextMuted
    SigTone.SILVER -> NexoPalette.Silver
    SigTone.SUCCESS -> NexoPalette.Success
    SigTone.WARNING -> NexoPalette.Warning
    SigTone.DANGER -> NexoPalette.Error
    SigTone.INFO -> NexoPalette.Info
}

/** Flat graphite backdrop with a restrained silver wash at the top. */
@Composable
fun SignatureBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexoPalette.Background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            NexoPalette.Silver.copy(alpha = 0.045f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

/**
 * Standard page shell: status-bar inset, compact header and a content slot.
 * [scrollable] adds vertical scrolling for simple settings-style pages.
 */
@Composable
fun SignatureScreen(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SignatureBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ScreenHeader(
                title = title,
                subtitle = subtitle,
                eyebrow = eyebrow,
                onBack = onBack,
                actions = actions,
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexoSpace.screenHorizontal)
                    .padding(bottom = LocalNexoBottomPadding.current + NexoSpace.lg),
            ) {
                if (scrollable) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                } else {
                    content()
                }
            }
        }
    }
}

/** Compact header used by every rebuilt screen. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexoSpace.lg, vertical = NexoSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(NexoSpace.sm))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(NexoSpace.md))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoPalette.TextMuted,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NexoPalette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Spacer(Modifier.width(NexoSpace.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun BrandHeader(
    title: String = "NEXO LOCK",
    subtitle: String = "PRIVATE SECURITY",
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexoSpace.lg, vertical = NexoSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(NexoPalette.SurfaceElevated)
                .border(1.dp, NexoPalette.Border, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                NexoIcons.ShieldCheck,
                contentDescription = null,
                tint = NexoPalette.Silver,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                ),
                color = NexoPalette.TextPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
                letterSpacing = 1.4.sp,
            )
        }
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** Layered graphite card with an optional press action. */
@Composable
fun SigCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = NexoSpace.lg,
    container: Color = NexoPalette.Surface,
    borderColor: Color = NexoPalette.Border.copy(alpha = 0.72f),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(NexoRadius.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SigSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NexoSpace.xs, bottom = NexoSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = NexoPalette.TextSecondary,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = NexoPalette.Silver,
                modifier = Modifier
                    .clip(RoundedCornerShape(NexoRadius.small))
                    .clickable(onClick = onAction)
                    .padding(horizontal = NexoSpace.sm, vertical = NexoSpace.xs),
            )
        }
    }
}

@Composable
fun SigDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = NexoPalette.Divider, thickness = 1.dp)
}

@Composable
fun SigPill(
    text: String,
    tone: SigTone = SigTone.NEUTRAL,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
) {
    val accent = tone.color()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.11f))
            .border(1.dp, accent.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun SigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val active = enabled && !loading
    Surface(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(NexoRadius.button),
        color = if (active) NexoPalette.Silver else NexoPalette.SurfaceAlt,
        contentColor = if (active) NexoPalette.OnSilver else NexoPalette.TextMuted,
        border = if (active) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            NexoPalette.Border,
        ),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = NexoPalette.OnSilver,
                )
                Spacer(Modifier.width(NexoSpace.sm))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NexoSpace.sm))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun SigSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val accent = if (destructive) NexoPalette.Error else NexoPalette.TextPrimary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(NexoRadius.button),
        color = NexoPalette.SurfaceElevated,
        contentColor = if (enabled) accent else NexoPalette.TextMuted,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (destructive) NexoPalette.Error.copy(alpha = 0.32f) else NexoPalette.Border,
        ),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(NexoSpace.sm))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun SigGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val accent = when {
        !enabled -> NexoPalette.TextMuted
        destructive -> NexoPalette.Error
        else -> NexoPalette.Silver
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(NexoRadius.small))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = NexoSpace.md, vertical = NexoSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(NexoSpace.xs))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = NexoPalette.TextSecondary,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(NexoRadius.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) tint else NexoPalette.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun SigSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoPalette.TextMuted,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = NexoPalette.TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        },
        trailingIcon = when {
            value.isNotEmpty() -> {
                {
                    IconAction(
                        icon = Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        onClick = { onValueChange("") },
                        size = 36.dp,
                    )
                }
            }

            trailing != null -> trailing
            else -> null
        },
        singleLine = true,
        shape = RoundedCornerShape(NexoRadius.search),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NexoPalette.TextPrimary,
            unfocusedTextColor = NexoPalette.TextPrimary,
            focusedContainerColor = NexoPalette.Surface,
            unfocusedContainerColor = NexoPalette.Surface,
            focusedBorderColor = NexoPalette.Silver.copy(alpha = 0.45f),
            unfocusedBorderColor = NexoPalette.Border,
            cursorColor = NexoPalette.Silver,
        ),
    )
}

@Composable
fun SigChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val background by animateColorAsState(
        targetValue = if (selected) NexoPalette.Silver else NexoPalette.Surface,
        animationSpec = tween(160),
        label = "sig-chip-bg",
    )
    val content = if (selected) NexoPalette.OnSilver else NexoPalette.TextSecondary
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                1.dp,
                if (selected) Color.Transparent else NexoPalette.Border,
                CircleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NexoSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) content else NexoPalette.TextMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) content else NexoPalette.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
fun SigEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NexoSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(NexoRadius.card))
                .background(NexoPalette.SurfaceElevated)
                .border(1.dp, NexoPalette.Border, RoundedCornerShape(NexoRadius.card)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = NexoPalette.TextSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(NexoSpace.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = NexoSpace.xl),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(NexoSpace.lg))
            SigSecondaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
fun SigLoadingState(
    message: String = "Loading…",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NexoSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = NexoPalette.Silver,
        )
        Spacer(Modifier.height(NexoSpace.md))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SigSkeletonList(
    rows: Int = 4,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NexoSpace.md)) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexoRadius.card))
                    .background(NexoPalette.Surface)
                    .border(1.dp, NexoPalette.Border.copy(alpha = 0.5f), RoundedCornerShape(NexoRadius.card))
                    .padding(NexoSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(NexoRadius.small))
                        .background(NexoPalette.SurfaceAlt),
                )
                Spacer(Modifier.width(NexoSpace.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(11.dp)
                            .alpha(alpha)
                            .clip(CircleShape)
                            .background(NexoPalette.SurfaceAlt),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(9.dp)
                            .alpha(alpha)
                            .clip(CircleShape)
                            .background(NexoPalette.SurfaceAlt),
                    )
                }
            }
        }
    }
}

@Composable
fun SigErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NexoSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(NexoRadius.card))
                .background(NexoPalette.Error.copy(alpha = 0.10f))
                .border(1.dp, NexoPalette.Error.copy(alpha = 0.26f), RoundedCornerShape(NexoRadius.card)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = NexoPalette.Error,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.height(NexoSpace.md))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = NexoPalette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = NexoSpace.lg),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(NexoSpace.md))
            SigGhostButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun SigBanner(
    text: String,
    tone: SigTone = SigTone.NEUTRAL,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val accent = tone.color()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.button))
            .background(accent.copy(alpha = 0.075f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(NexoRadius.button))
            .padding(horizontal = NexoSpace.md, vertical = NexoSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (tone) {
                    SigTone.DANGER, SigTone.WARNING -> Icons.Filled.Warning
                    SigTone.SUCCESS -> Icons.Filled.Check
                    else -> Icons.Filled.Info
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(NexoSpace.sm))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextPrimary.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
fun SigSettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.small))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = NexoSpace.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(NexoRadius.small))
                .background(
                    if (destructive) {
                        NexoPalette.Error.copy(alpha = 0.10f)
                    } else {
                        NexoPalette.SurfaceAlt
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) NexoPalette.Error else NexoPalette.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) NexoPalette.Error else NexoPalette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(NexoSpace.sm))
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NexoPalette.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SigMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: SigTone = SigTone.NEUTRAL,
) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = if (tone == SigTone.NEUTRAL) NexoPalette.TextPrimary else tone.color(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SigStatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    tone: SigTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = tone.color()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexoRadius.card))
            .background(NexoPalette.Surface)
            .border(1.dp, NexoPalette.Border.copy(alpha = 0.7f), RoundedCornerShape(NexoRadius.card))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(NexoSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.height(NexoSpace.md))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = NexoPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(NexoSpace.xs))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = NexoPalette.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SigShortcutTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: SigTone = SigTone.NEUTRAL,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.card))
            .background(NexoPalette.Surface)
            .border(1.dp, NexoPalette.Border.copy(alpha = 0.7f), RoundedCornerShape(NexoRadius.card))
            .clickable(onClick = onClick)
            .padding(NexoSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(NexoRadius.small))
                .background(tone.color().copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = NexoPalette.TextPrimary,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NexoPalette.TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun SigTimelineRow(
    icon: ImageVector,
    title: String,
    message: String,
    meta: String,
    tone: SigTone = SigTone.NEUTRAL,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.small))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = NexoSpace.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.color().copy(alpha = 0.11f))
                    .border(1.dp, tone.color().copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (trailing != null) {
                    Spacer(Modifier.width(NexoSpace.sm))
                    trailing()
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoPalette.TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
        }
    }
}

@Composable
fun SigPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    grantedLabel: String = "Granted",
    missingLabel: String = "Required",
    actionLabel: String? = null,
) {
    val tone = if (granted) SigTone.SUCCESS else SigTone.WARNING
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.card))
            .background(NexoPalette.Surface)
            .border(1.dp, NexoPalette.Border.copy(alpha = 0.7f), RoundedCornerShape(NexoRadius.card))
            .padding(NexoSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(NexoRadius.small))
                .background(tone.color().copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(NexoSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoPalette.TextPrimary,
                )
                SigPill(if (granted) grantedLabel else missingLabel, tone)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = NexoPalette.TextSecondary,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(NexoSpace.xs))
                SigGhostButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(start = 0.dp),
                )
            }
        }
    }
}

@Composable
fun SigKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NexoPalette.TextPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(128.dp),
            style = MaterialTheme.typography.labelSmall,
            color = NexoPalette.TextMuted,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
        )
    }
}

@Composable
fun SigDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    icon: ImageVector? = null,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexoPalette.SurfaceElevated,
        titleContentColor = NexoPalette.TextPrimary,
        textContentColor = NexoPalette.TextSecondary,
        shape = RoundedCornerShape(NexoRadius.dialog),
        icon = if (icon != null) {
            {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (destructive) {
                                NexoPalette.Error.copy(alpha = 0.12f)
                            } else {
                                NexoPalette.SurfaceAlt
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (destructive) NexoPalette.Error else NexoPalette.Silver,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
            null
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (content != null) {
                    Spacer(Modifier.height(NexoSpace.md))
                    content()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmLabel,
                    color = if (destructive) NexoPalette.Error else NexoPalette.Silver,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = NexoPalette.TextSecondary)
            }
        },
    )
}

@Composable
fun SigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = NexoPalette.TextMuted) }
        } else {
            null
        },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(NexoRadius.button),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NexoPalette.TextPrimary,
            unfocusedTextColor = NexoPalette.TextPrimary,
            focusedContainerColor = NexoPalette.Surface,
            unfocusedContainerColor = NexoPalette.Surface,
            focusedBorderColor = NexoPalette.Silver.copy(alpha = 0.5f),
            unfocusedBorderColor = NexoPalette.Border,
            focusedLabelColor = NexoPalette.Silver,
            unfocusedLabelColor = NexoPalette.TextMuted,
            cursorColor = NexoPalette.Silver,
            errorBorderColor = NexoPalette.Error,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexoPalette.SurfaceElevated,
        contentColor = NexoPalette.TextPrimary,
        scrimColor = NexoPalette.Scrim,
        shape = RoundedCornerShape(topStart = NexoRadius.panel, topEnd = NexoRadius.panel),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = NexoSpace.md, bottom = NexoSpace.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(NexoPalette.Border),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NexoSpace.xl)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
fun SigSelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexoRadius.button))
            .background(NexoPalette.SurfaceElevated)
            .border(1.dp, NexoPalette.Border, RoundedCornerShape(NexoRadius.button))
            .padding(horizontal = NexoSpace.md, vertical = NexoSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count selected",
            style = MaterialTheme.typography.labelLarge,
            color = NexoPalette.Silver,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(NexoSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        IconAction(
            icon = Icons.Filled.Close,
            contentDescription = "Clear selection",
            onClick = onClear,
            size = 36.dp,
        )
    }
}

/** Compact numeric keypad with graphite keys and a silver press state. */
@Composable
fun SigPinDots(
    length: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length.coerceAtLeast(1)) { index ->
            val active = index < filled
            val color by animateColorAsState(
                targetValue = when {
                    error -> NexoPalette.Error
                    active -> NexoPalette.Silver
                    else -> NexoPalette.Border
                },
                animationSpec = tween(140),
                label = "sig-pin-dot",
            )
            Box(
                Modifier
                    .size(if (active) 12.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (active || error) color else Color.Transparent)
                    .border(1.2.dp, color, CircleShape),
            )
        }
    }
}

@Composable
fun SigPinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back"),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexoSpace.sm),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexoSpace.sm),
            ) {
                row.forEach { label ->
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (label) {
                            "" -> Spacer(Modifier.size(60.dp))
                            "back" -> PinKey(
                                enabled = enabled,
                                onClick = onBackspace,
                                contentDescription = "Delete digit",
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = null,
                                    tint = NexoPalette.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            else -> PinKey(
                                enabled = enabled,
                                onClick = { onDigit(label.toInt()) },
                                contentDescription = label,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = NexoPalette.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(NexoRadius.button))
            .background(NexoPalette.SurfaceElevated)
            .border(1.dp, NexoPalette.Border.copy(alpha = 0.8f), RoundedCornerShape(NexoRadius.button))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(
                    color = NexoPalette.Silver,
                ),
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) content() else Box(Modifier.alpha(0.4f)) { content() }
    }
}

@Composable
fun SigPinPanel(
    entered: String,
    slots: Int,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SigPinDots(length = slots, filled = entered.length, error = error)
        Spacer(Modifier.height(NexoSpace.xl))
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
        ) {
            SigPinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun SigPatternPad(
    selected: List<Int>,
    enabled: Boolean,
    onDot: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 248.dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected.isEmpty()) 0f else 1f,
        animationSpec = tween(180),
        label = "sig-pattern",
    )
    Box(modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (selected.size >= 2) {
                val step = this.size.width / 3f
                fun center(index: Int) = androidx.compose.ui.geometry.Offset(
                    x = step * (index % 3 + 0.5f),
                    y = step * (index / 3 + 0.5f),
                )
                for (index in 1 until selected.size) {
                    drawLine(
                        color = NexoPalette.Silver.copy(alpha = 0.8f * progress),
                        start = center(selected[index - 1]),
                        end = center(selected[index]),
                        strokeWidth = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            repeat(3) { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val active = index in selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(enabled = enabled) { onDot(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (active) 26.dp else 20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) NexoPalette.Silver else NexoPalette.SurfaceElevated,
                                    )
                                    .border(
                                        1.2.dp,
                                        if (active) NexoPalette.Silver else NexoPalette.Border,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = NexoPalette.OnSilver,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One destination of the floating five-item navigation pill. */
data class NexoNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

@Composable
fun NexoFloatingNav(
    items: List<NexoNavItem>,
    currentRoute: String?,
    onSelect: (NexoNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.32f),
                spotColor = Color.Black.copy(alpha = 0.38f),
            ),
        shape = CircleShape,
        color = NexoPalette.Surface.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            NexoPalette.Border.copy(alpha = 0.8f),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = NexoSpace.sm, vertical = NexoSpace.xs + 2.dp)
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val background by animateColorAsState(
                    targetValue = if (selected) {
                        NexoPalette.SurfaceAlt.copy(alpha = 0.65f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "nav-bg",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) NexoPalette.Silver else NexoPalette.TextMuted,
                    animationSpec = tween(180),
                    label = "nav-tint",
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(NexoRadius.button))
                        .background(background)
                        .clickable(
                            role = Role.Tab,
                            onClick = { onSelect(item) },
                        )
                        .semantics {
                            contentDescription = item.label
                            if (selected) {
                                // Selected state is exposed to accessibility services.
                                this.selected = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .size(width = 10.dp, height = 2.dp)
                                .clip(CircleShape)
                                .background(NexoPalette.Silver.copy(alpha = 0.8f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SigFilledIconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: SigTone = SigTone.SILVER,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tone.color().copy(alpha = 0.11f))
            .border(1.dp, tone.color().copy(alpha = 0.18f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SigFooterNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NexoSpace.md, bottom = NexoSpace.sm),
        style = MaterialTheme.typography.labelSmall,
        color = NexoPalette.TextMuted,
        textAlign = TextAlign.Start,
    )
}

@Composable
fun SigScreenSpacer() {
    Spacer(Modifier.height(NexoSpace.xxl))
}
