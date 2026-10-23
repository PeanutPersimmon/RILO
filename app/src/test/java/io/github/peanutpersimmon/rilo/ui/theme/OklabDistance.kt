package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.sqrt

/** Test-only perceptual distance, multiplied by 100 for readable reports. */
internal fun okLabDistance(first: Color, second: Color): Double {
    fun channel(value: Float): Double {
        val srgb = value.toDouble()
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }
    fun lab(color: Color): Triple<Double, Double, Double> {
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }
    val a = lab(first)
    val b = lab(second)
    return 100 * sqrt((a.first - b.first).pow(2) + (a.second - b.second).pow(2) +
        (a.third - b.third).pow(2))
}
