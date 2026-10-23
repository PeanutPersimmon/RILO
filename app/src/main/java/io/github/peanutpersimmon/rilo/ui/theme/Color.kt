package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

internal val LightPrimary = Color(0xFF202124)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightBackground = Color(0xFFE8E9EA)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFF8F8F9)
internal val LightOutline = Color(0xFFBFC2C5)
internal val LightOutlineVariant = Color(0xFFD4D6D8)
internal val LightText = Color(0xFF202124)
internal val LightTextSecondary = Color(0xFF606469)
internal val LightError = Color(0xFF44484C)

internal val DarkPrimary = Color(0xFFE7E8EA)
internal val DarkOnPrimary = Color(0xFF202124)
internal val DarkBackground = Color(0xFF1B1C1E)
internal val DarkSurface = Color(0xFF252629)
internal val DarkSurfaceVariant = Color(0xFF303134)
internal val DarkOutline = Color(0xFF484A4D)
internal val DarkOutlineVariant = Color(0xFF3B3D40)
internal val DarkText = Color(0xFFE7E8EA)
internal val DarkTextSecondary = Color(0xFFA9ACB0)
internal val DarkError = Color(0xFFCCD0D4)

@Immutable
data class AppSemanticColors(
    val actual: Color,
    val planned: Color,
    val completed: Color,
    val inactive: Color,
    val border: Color,
    val plannedSurface: Color,
    val ambiguous: Color,
    val overdueMarker: Color,
    val nowIndicator: Color,
    val timelineAxis: Color,
    val timerBackground: Color,
    val timerPanel: Color,
    val timerPrimaryText: Color,
    val timerSecondaryText: Color,
    val timerDivider: Color,
)

internal val LightSemanticColors = AppSemanticColors(
    actual = LightText, planned = LightTextSecondary, completed = LightTextSecondary,
    inactive = LightTextSecondary, border = LightOutlineVariant, plannedSurface = LightSurface,
    ambiguous = LightTextSecondary, overdueMarker = Color(0xFFC62828),
    nowIndicator = LightPrimary, timelineAxis = LightOutline,
    timerBackground = DarkBackground,
    timerPanel = DarkSurface,
    timerPrimaryText = DarkText,
    timerSecondaryText = DarkTextSecondary,
    timerDivider = DarkOutlineVariant,
)

internal val DarkSemanticColors = AppSemanticColors(
    actual = DarkText, planned = DarkTextSecondary, completed = DarkTextSecondary,
    inactive = DarkTextSecondary, border = DarkOutlineVariant, plannedSurface = DarkSurface,
    ambiguous = DarkTextSecondary, overdueMarker = Color(0xFFFF6B6B),
    nowIndicator = DarkPrimary, timelineAxis = DarkOutline,
    timerBackground = DarkBackground,
    timerPanel = DarkSurface,
    timerPrimaryText = DarkText,
    timerSecondaryText = DarkTextSecondary,
    timerDivider = DarkOutlineVariant,
)
