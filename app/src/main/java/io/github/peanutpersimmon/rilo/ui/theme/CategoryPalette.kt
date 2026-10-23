package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Immutable
data class CategoryColors(
    val base: Color,
    val medium: Color,
    val container: Color,
    /** Text on [medium], used by compact category summaries. */
    val mediumContent: Color,
    /** Text on [container]. */
    val content: Color,
    /** Secondary text on [container], corrected without changing its fixed color. */
    val secondaryContent: Color,
    /** Accessible emphasis on the surrounding theme surface. */
    val border: Color,
)

private data class PaletteTones(val base: Color, val medium: Color, val container: Color)
private data class PaletteEntry(val light: PaletteTones, val dark: PaletteTones)

private val CategoryPalette = mapOf(
    CategoryColorKey.NEUTRAL to PaletteEntry(
        PaletteTones(Color(0xFF73777F), Color(0xFFC5C8CD), Color(0xFFF0F1F3)),
        PaletteTones(Color(0xFFA9ADB5), Color(0xFF5E636B), Color(0xFF34373C)),
    ),
    CategoryColorKey.RED to PaletteEntry(
        PaletteTones(Color(0xFFC64A3F), Color(0xFFDD928C), Color(0xFFE2A49F)),
        PaletteTones(Color(0xFFD06B62), Color(0xFF983F37), Color(0xFF803934)),
    ),
    CategoryColorKey.ORANGE to PaletteEntry(
        PaletteTones(Color(0xFFD67B29), Color(0xFFE6B07F), Color(0xFFEBBD94)),
        PaletteTones(Color(0xFFDD9350), Color(0xFFA36228), Color(0xFF8A5527)),
    ),
    CategoryColorKey.AMBER to PaletteEntry(
        PaletteTones(Color(0xFFC6A623), Color(0xFFDDCA7B), Color(0xFFE2D291)),
        PaletteTones(Color(0xFFD0B64B), Color(0xFF988123), Color(0xFF806E23)),
    ),
    CategoryColorKey.OLIVE to PaletteEntry(
        PaletteTones(Color(0xFF7E9E32), Color(0xFFB2C584), Color(0xFFBECE98)),
        PaletteTones(Color(0xFF95AF57), Color(0xFF647B2E), Color(0xFF576A2C)),
    ),
    CategoryColorKey.GREEN to PaletteEntry(
        PaletteTones(Color(0xFF2D9557), Color(0xFF81BF9A), Color(0xFF96CAAB)),
        PaletteTones(Color(0xFF53A875), Color(0xFF297549), Color(0xFF286442)),
    ),
    CategoryColorKey.TEAL to PaletteEntry(
        PaletteTones(Color(0xFF188E9C), Color(0xFF74BBC4), Color(0xFF8CC6CE)),
        PaletteTones(Color(0xFF42A2AE), Color(0xFF1A6F7A), Color(0xFF1B606A)),
    ),
    CategoryColorKey.BLUE to PaletteEntry(
        PaletteTones(Color(0xFF356EC3), Color(0xFF86A8DB), Color(0xFF9AB6E1)),
        PaletteTones(Color(0xFF5988CE), Color(0xFF2F5896), Color(0xFF2C4E80)),
    ),
    CategoryColorKey.PURPLE to PaletteEntry(
        PaletteTones(Color(0xFF744DB5), Color(0xFFAC94D3), Color(0xFFBAA6DA)),
        PaletteTones(Color(0xFF8D6DC2), Color(0xFF5C418C), Color(0xFF513B78)),
    ),
    CategoryColorKey.PINK to PaletteEntry(
        PaletteTones(Color(0xFFB54791), Color(0xFFD391BD), Color(0xFFDAA3C8)),
        PaletteTones(Color(0xFFC268A5), Color(0xFF8B3C72), Color(0xFF763763)),
    ),
    CategoryColorKey.BROWN to PaletteEntry(
        PaletteTones(Color(0xFF8A6239), Color(0xFFB9A188), Color(0xFFC4B09C)),
        PaletteTones(Color(0xFF9F7E5D), Color(0xFF6C5033), Color(0xFF5D4730)),
    ),
)

/** Resolves a stable stored key to fixed, opaque tones for the current theme. */
fun resolveCategoryColors(colorKey: String, colorScheme: ColorScheme): CategoryColors {
    val key = CategoryColorKey.fromStorageOrNeutral(colorKey)
    val entry = requireNotNull(CategoryPalette[key])
    val dark = relativeLuminance(colorScheme.surface) < 0.5
    val tones = if (dark) entry.dark else entry.light
    val border = ensureContrast(tones.base, colorScheme.surface, minimumRatio = 4.5)
    val mediumContent = ensureContrast(colorScheme.onSurface, tones.medium, minimumRatio = 4.5)
    val content = ensureContrast(colorScheme.onSurface, tones.container, minimumRatio = 4.5)
    val secondaryContent = ensureContrast(colorScheme.onSurfaceVariant, tones.container, minimumRatio = 4.5)
    return CategoryColors(
        base = tones.base,
        medium = tones.medium,
        container = tones.container,
        mediumContent = mediumContent,
        content = content,
        secondaryContent = secondaryContent,
        border = border,
    )
}

/** WCAG contrast ratio for two colors after compositing the foreground over the background. */
fun contrastRatio(foreground: Color, background: Color): Double {
    val opaqueBackground = compositeOver(background, Color.Black)
    val opaqueForeground = compositeOver(foreground, opaqueBackground)
    val lighter = max(relativeLuminance(opaqueForeground), relativeLuminance(opaqueBackground))
    val darker = min(relativeLuminance(opaqueForeground), relativeLuminance(opaqueBackground))
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Returns the smallest sRGB blend toward black or white that reaches [minimumRatio].
 * The function is deterministic and side-effect free so every category surface shares
 * exactly the same accessibility correction.
 */
fun ensureContrast(foreground: Color, background: Color, minimumRatio: Double): Color {
    require(minimumRatio >= 1.0)
    if (contrastRatio(foreground, background) >= minimumRatio) return foreground

    val blackRatio = contrastRatio(Color.Black, background)
    val whiteRatio = contrastRatio(Color.White, background)
    val target = if (blackRatio >= whiteRatio) Color.Black else Color.White
    if (max(blackRatio, whiteRatio) < minimumRatio) return target

    var low = 0f
    var high = 1f
    repeat(24) {
        val amount = (low + high) / 2f
        if (contrastRatio(mixSrgb(foreground, target, amount), background) >= minimumRatio) {
            high = amount
        } else {
            low = amount
        }
    }
    return mixSrgb(foreground, target, high)
}

private fun mixSrgb(start: Color, end: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}

private fun compositeOver(foreground: Color, background: Color): Color {
    if (foreground.alpha >= 1f) return foreground
    val outAlpha = foreground.alpha + background.alpha * (1f - foreground.alpha)
    if (outAlpha <= 0f) return Color.Transparent
    return Color(
        red = (foreground.red * foreground.alpha + background.red * background.alpha * (1f - foreground.alpha)) / outAlpha,
        green = (foreground.green * foreground.alpha + background.green * background.alpha * (1f - foreground.alpha)) / outAlpha,
        blue = (foreground.blue * foreground.alpha + background.blue * background.alpha * (1f - foreground.alpha)) / outAlpha,
        alpha = outAlpha,
    )
}

private fun relativeLuminance(color: Color): Double {
    fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}
