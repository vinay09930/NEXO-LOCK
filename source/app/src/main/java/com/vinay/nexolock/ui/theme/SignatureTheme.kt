package com.vinay.nexolock.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * NEXO LOCK SIGNATURE DESIGN tokens.
 *
 * The management UI is always dark. These tokens are intentionally separate
 * from the legacy V1.1.4 theme in Color.kt/Theme.kt: LockActivity still wraps
 * the frozen decoy/lock surface in the old theme, so the decoy keeps its exact
 * V1.1.4 appearance while every management screen uses the Signature palette.
 */
object NexoPalette {
    val Background = Color(0xFF0B0C0F)
    val BackgroundSecondary = Color(0xFF111318)
    val Surface = Color(0xFF171A20)
    val SurfaceElevated = Color(0xFF1D2129)
    val SurfaceAlt = Color(0xFF242932)
    val TextPrimary = Color(0xFFF5F6F8)
    val TextSecondary = Color(0xFFA5ABB5)
    val TextMuted = Color(0xFF737B87)
    val Silver = Color(0xFFD9DDE4)
    val White = Color(0xFFFFFFFF)
    val Border = Color(0xFF303640)
    val Divider = Color(0xFF252B34)
    val Success = Color(0xFF57B98C)
    val Warning = Color(0xFFD6A44A)
    val Error = Color(0xFFD9646B)
    val Info = Color(0xFF8FB4D9)
    val OnSilver = Color(0xFF101216)
    val Scrim = Color(0xCC050608)
}

/** Spacing scale. Use these instead of ad-hoc values on rebuilt screens. */
object NexoSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 40.dp
    val screenHorizontal: Dp = 20.dp
    val navReserve: Dp = 108.dp
}

/** Radius scale requested by the Signature design brief. */
object NexoRadius {
    val small: Dp = 12.dp
    val button: Dp = 16.dp
    val search: Dp = 18.dp
    val card: Dp = 20.dp
    val panel: Dp = 24.dp
    val dialog: Dp = 24.dp
    val pill: Dp = 999.dp
}

private val SignatureScheme = darkColorScheme(
    primary = NexoPalette.Silver,
    onPrimary = NexoPalette.OnSilver,
    primaryContainer = NexoPalette.SurfaceAlt,
    onPrimaryContainer = NexoPalette.TextPrimary,
    secondary = NexoPalette.TextSecondary,
    onSecondary = NexoPalette.OnSilver,
    secondaryContainer = NexoPalette.SurfaceElevated,
    onSecondaryContainer = NexoPalette.TextPrimary,
    tertiary = NexoPalette.TextMuted,
    onTertiary = NexoPalette.TextPrimary,
    background = NexoPalette.Background,
    onBackground = NexoPalette.TextPrimary,
    surface = NexoPalette.Surface,
    onSurface = NexoPalette.TextPrimary,
    surfaceVariant = NexoPalette.SurfaceElevated,
    onSurfaceVariant = NexoPalette.TextSecondary,
    outline = NexoPalette.Border,
    outlineVariant = NexoPalette.Divider,
    error = NexoPalette.Error,
    onError = NexoPalette.White,
    errorContainer = Color(0xFF3A1D20),
    onErrorContainer = Color(0xFFF2C6C9),
    inverseSurface = NexoPalette.Silver,
    inverseOnSurface = NexoPalette.OnSilver,
)

private val SignatureTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.9).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp,
    ),
)

private val SignatureShapes = Shapes(
    extraSmall = RoundedCornerShape(NexoRadius.small),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(NexoRadius.search),
    large = RoundedCornerShape(NexoRadius.card),
    extraLarge = RoundedCornerShape(NexoRadius.panel),
)

/** Space reserved above the floating navigation pill for top-level screens. */
val LocalNexoBottomPadding = staticCompositionLocalOf<Dp> { 0.dp }

@Composable
fun NexoSignatureTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Always-dark surface: keep system icons light regardless of the
            // legacy theme preference retained for the frozen lock surface.
            window.statusBarColor = NexoPalette.Background.toArgb()
            window.navigationBarColor = NexoPalette.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = SignatureScheme,
        typography = SignatureTypography,
        shapes = SignatureShapes,
    ) {
        CompositionLocalProvider(LocalNexoBottomPadding provides 0.dp, content = content)
    }
}
