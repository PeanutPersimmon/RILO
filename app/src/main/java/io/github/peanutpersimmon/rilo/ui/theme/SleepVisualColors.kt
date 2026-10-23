package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.peanutpersimmon.rilo.domain.model.SessionKind

@Immutable
data class SleepVisualColors(
    val base: Color,
    val medium: Color,
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
)

/** Sleep is a system visual role, independent of user Category colors. */
fun resolveSleepVisualColors(kind: SessionKind?, scheme: ColorScheme): SleepVisualColors? {
    val dark = scheme.surface.luminance() < 0.5f
    val tones = when (kind) {
        SessionKind.NIGHT_SLEEP -> if (dark) Triple(
            Color(0xFFA4ADBD), Color(0xFF657080), Color(0xFF48505D),
        ) else Triple(Color(0xFF7A859C), Color(0xFFB7BDC8), Color(0xFFD8DCE3))
        SessionKind.NAP -> if (dark) Triple(
            Color(0xFFC6B79A), Color(0xFF7C705D), Color(0xFF585044),
        ) else Triple(Color(0xFFB8A58A), Color(0xFFD3C7B8), Color(0xFFE6DED2))
        else -> return null
    }
    return SleepVisualColors(
        base = tones.first,
        medium = tones.second,
        container = tones.third,
        content = ensureContrast(scheme.onSurface, tones.third, 4.5),
        secondaryContent = ensureContrast(scheme.onSurfaceVariant, tones.third, 4.5),
    )
}
