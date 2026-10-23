package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class AppShapeTokens(
    val small: RoundedCornerShape = RoundedCornerShape(8.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(16.dp),
    val large: RoundedCornerShape = RoundedCornerShape(24.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val bottomSheet: RoundedCornerShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
    ),
)

internal val AppMaterialShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

val LocalAppShapes = staticCompositionLocalOf { AppShapeTokens() }
