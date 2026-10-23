package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { Light, Dark }

@Immutable
data class ResolvedRidoTheme(
    val materialColors: ColorScheme,
    val timelineColors: AppSemanticColors,
    val darkSystemBarIcons: Boolean,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = LightError,
    onError = LightOnPrimary,
    errorContainer = LightSurfaceVariant,
    onErrorContainer = LightText,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightText,
    inversePrimary = LightTextSecondary,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightText,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = LightText,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightText,
    inverseOnSurface = LightBackground,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    scrim = androidx.compose.ui.graphics.Color.Black,
    surfaceBright = LightSurface,
    surfaceDim = LightBackground,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightBackground,
    primaryFixed = LightSurfaceVariant,
    primaryFixedDim = LightSurfaceVariant,
    onPrimaryFixed = LightText,
    onPrimaryFixedVariant = LightTextSecondary,
    secondaryFixed = LightSurfaceVariant,
    secondaryFixedDim = LightSurfaceVariant,
    onSecondaryFixed = LightText,
    onSecondaryFixedVariant = LightTextSecondary,
    tertiaryFixed = LightSurfaceVariant,
    tertiaryFixedDim = LightSurfaceVariant,
    onTertiaryFixed = LightText,
    onTertiaryFixedVariant = LightTextSecondary,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnPrimary,
    errorContainer = DarkSurfaceVariant,
    onErrorContainer = DarkText,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkText,
    inversePrimary = DarkTextSecondary,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkText,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = DarkText,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkText,
    inverseOnSurface = DarkBackground,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    scrim = androidx.compose.ui.graphics.Color.Black,
    surfaceBright = DarkSurface,
    surfaceDim = DarkBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    primaryFixed = DarkSurfaceVariant,
    primaryFixedDim = DarkSurfaceVariant,
    onPrimaryFixed = DarkText,
    onPrimaryFixedVariant = DarkTextSecondary,
    secondaryFixed = DarkSurfaceVariant,
    secondaryFixedDim = DarkSurfaceVariant,
    onSecondaryFixed = DarkText,
    onSecondaryFixedVariant = DarkTextSecondary,
    tertiaryFixed = LightSurfaceVariant,
    tertiaryFixedDim = LightSurfaceVariant,
    onTertiaryFixed = LightText,
    onTertiaryFixedVariant = LightTextSecondary,
)

private val LightResolvedTheme = ResolvedRidoTheme(LightColorScheme, LightSemanticColors, true)
private val DarkResolvedTheme = ResolvedRidoTheme(DarkColorScheme, DarkSemanticColors, false)
private val LocalResolvedRidoTheme = staticCompositionLocalOf { LightResolvedTheme }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RidoTheme(
    themeMode: ThemeMode = ThemeMode.Light,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val resolvedTheme = if (darkTheme) DarkResolvedTheme else LightResolvedTheme
    CompositionLocalProvider(
        LocalIndication provides NoRippleIndication,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = resolvedTheme.materialColors.primary,
            backgroundColor = resolvedTheme.materialColors.primary.copy(alpha = 0.25f),
        ),
        androidx.compose.material3.LocalRippleConfiguration provides null,
        LocalResolvedRidoTheme provides resolvedTheme,
        LocalAppDimens provides AppDimens(),
        LocalAppShapes provides AppShapeTokens(),
        LocalAppTypography provides AppTypeTokens,
    ) {
        MaterialTheme(
            colorScheme = resolvedTheme.materialColors,
            typography = AppTypography,
            shapes = AppMaterialShapes,
            content = content,
        )
    }
}

object AppTheme {
    val resolved: ResolvedRidoTheme
        @Composable get() = LocalResolvedRidoTheme.current
    val dimens: AppDimens
        @Composable get() = LocalAppDimens.current
    val shapes: AppShapeTokens
        @Composable get() = LocalAppShapes.current
    val type: AppTypographyTokens
        @Composable get() = LocalAppTypography.current
    val colors: AppSemanticColors
        @Composable get() = LocalResolvedRidoTheme.current.timelineColors
}
