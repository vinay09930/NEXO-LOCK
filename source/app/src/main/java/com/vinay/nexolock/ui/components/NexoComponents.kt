package com.vinay.nexolock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vinay.nexolock.R
import com.vinay.nexolock.ui.theme.DangerRed
import com.vinay.nexolock.ui.theme.SuccessGreen
import com.vinay.nexolock.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ambient gradient background used by every screen. */
@Composable
fun NexoBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val topGlow = Brush.radialGradient(
                colors = listOf(scheme.primary.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.88f, -size.height * 0.04f),
                radius = size.width * 0.85f,
            )
            drawCircle(
                brush = topGlow,
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.88f, -size.height * 0.04f),
            )
            val bottomGlow = Brush.radialGradient(
                colors = listOf(scheme.tertiary.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(-size.width * 0.05f, size.height * 0.92f),
                radius = size.width * 0.75f,
            )
            drawCircle(
                brush = bottomGlow,
                radius = size.width * 0.75f,
                center = Offset(-size.width * 0.05f, size.height * 0.92f),
            )
        }
        content()
    }
}

/** Brand mark: the shield/lock emblem from the NEXO LOCK launcher artwork. */
@Composable
fun NexoMark(
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.nexo_logo_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun NexoWordmark(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    subtitle: String? = "PRIVATE APP LOCK",
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "NEXO LOCK",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
        }
    }
}

/** Elevated surface with a subtle outline. */
@Composable
fun NexoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

enum class PillTone { NEUTRAL, SUCCESS, WARNING, DANGER, ACCENT }

@Composable
fun StatusPill(
    text: String,
    tone: PillTone = PillTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (fg, bg) = when (tone) {
        PillTone.NEUTRAL -> scheme.onSurfaceVariant to scheme.surfaceVariant
        PillTone.SUCCESS -> SuccessGreen to SuccessGreen.copy(alpha = 0.16f)
        PillTone.WARNING -> WarningAmber to WarningAmber.copy(alpha = 0.16f)
        PillTone.DANGER -> DangerRed to DangerRed.copy(alpha = 0.16f)
        PillTone.ACCENT -> scheme.primary to scheme.primary.copy(alpha = 0.16f)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Dots representing the length of a PIN, with the number of entered digits. */
@Composable
fun PinDots(
    length: Int,
    filled: Int,
    error: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length.coerceAtLeast(1)) { index ->
            val active = index < filled
            val color by animateColorAsState(
                targetValue = when {
                    error -> DangerRed
                    active -> scheme.primary
                    else -> scheme.outline
                },
                animationSpec = tween(160),
                label = "pin-dot",
            )
            Box(
                Modifier
                    .size(if (active) 15.dp else 12.dp)
                    .clip(CircleShape)
                    .background(if (active || error) color else Color.Transparent)
                    .border(1.5.dp, color, CircleShape),
            )
        }
    }
}

@Composable
fun PinKeypad(
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (label) {
                            "" -> Spacer(Modifier.size(64.dp))
                            "back" -> KeypadKey(
                                enabled = enabled,
                                onClick = onBackspace,
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Delete digit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            else -> KeypadKey(
                                enabled = enabled,
                                onClick = { onDigit(label.toInt()) },
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
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
private fun KeypadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(64.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun PinEntryPanel(
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
        PinDots(length = slots, filled = entered.length, error = error)
        Spacer(Modifier.height(22.dp))
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = enabled,
        )
    }
}

/** Progress indicator for a volume-press gesture (up/down presses). */
@Composable
fun GestureDots(
    expected: Int,
    filled: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(expected.coerceAtLeast(1)) { index ->
            val active = index < filled
            Box(
                Modifier
                    .size(if (active) 13.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (active) scheme.primary else scheme.outline.copy(alpha = 0.7f)),
            )
        }
    }
}

/**
 * 3x3 recovery pattern pad. Tap dots in order: this is fully accessible,
 * deterministic and avoids gesture-recognition failures on unusual hardware.
 */
@Composable
fun PatternPad(
    selected: List<Int>,
    enabled: Boolean,
    onDot: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 252.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val lineProgress by animateFloatAsState(
        targetValue = if (selected.isEmpty()) 0f else 1f,
        animationSpec = tween(220),
        label = "pattern-lines",
    )
    Box(modifier = modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            if (selected.size >= 2) {
                val step = this.size.width / 3f
                fun center(index: Int) = Offset(
                    x = step * (index % 3 + 0.5f),
                    y = step * (index / 3 + 0.5f),
                )
                for (index in 1 until selected.size) {
                    drawLine(
                        color = scheme.primary.copy(alpha = 0.85f * lineProgress),
                        start = center(selected[index - 1]),
                        end = center(selected[index]),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
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
                                    .size(if (active) 30.dp else 22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) scheme.primary else scheme.surfaceVariant,
                                    )
                                    .border(
                                        1.5.dp,
                                        if (active) scheme.primary else scheme.outline,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = scheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
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

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx().coerceAtLeast(1) }
    // PackageManager and drawable rasterisation can be slow on a cold start.
    // Keep them off the main thread so the decoy/lock frame is never blocked.
    var bitmap by remember(packageName, px) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(packageName, px) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(px, px)
            }.getOrNull()
        }
    }
    val iconBitmap = bitmap
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun NexoTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Standard screen shell: backdrop, top bar, and a padded content column. */
@Composable
fun NexoScreen(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            NexoTopBar(title = title, subtitle = subtitle, onBack = onBack, trailing = trailing)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun NexoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) {
                DangerRed.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (destructive) DangerRed else MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class BannerTone { INFO, SUCCESS, WARNING, DANGER }

@Composable
fun InfoBanner(
    text: String,
    tone: BannerTone = BannerTone.INFO,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (tone) {
        BannerTone.INFO -> scheme.secondary
        BannerTone.SUCCESS -> SuccessGreen
        BannerTone.WARNING -> WarningAmber
        BannerTone.DANGER -> DangerRed
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface.copy(alpha = 0.92f),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
