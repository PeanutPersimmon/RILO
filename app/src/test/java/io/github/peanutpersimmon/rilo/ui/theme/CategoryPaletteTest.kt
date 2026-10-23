package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPaletteTest {
    private val chromaticKeys = CategoryColorKey.entries.filterNot { it == CategoryColorKey.NEUTRAL }
    @Test
    fun tenColorsAndNeutralHaveFinalKeys() {
        assertEquals(10, chromaticKeys.size)
        assertEquals(11, CategoryColorKey.entries.size)
        assertEquals(11, CategoryColorKey.entries.map { it.storageKey }.toSet().size)
        assertEquals(setOf("red", "orange", "amber", "olive", "green", "teal", "blue",
            "purple", "pink", "brown", "neutral"),
            CategoryColorKey.entries.map { it.storageKey }.toSet())
        listOf("emerald", "indigo", "berry", "unknown").forEach {
            assertNull(CategoryColorKey.fromStorage(it))
        }
        assertNotNull(CategoryColorKey.fromStorage("brown"))
    }

    @Test
    fun everyToneIsOpaqueAndDistinctInBothThemes() {
        listOf(
            lightColorScheme() to false,
            darkColorScheme() to true,
        ).forEach { (scheme, isDark) ->
            val colors = CategoryColorKey.entries.associateWith {
                resolveCategoryColors(it.storageKey, scheme)
            }
            colors.values.forEach { resolved ->
                listOf(resolved.base, resolved.medium, resolved.container, resolved.mediumContent,
                    resolved.content, resolved.secondaryContent, resolved.border)
                    .forEach { assertEquals(1f, it.alpha, 0f) }
                assertTrue(contrastRatio(resolved.mediumContent, resolved.medium) >= 4.5)
                assertTrue(contrastRatio(resolved.content, resolved.container) >= 4.5)
                assertTrue(contrastRatio(resolved.secondaryContent, resolved.container) >= 4.5)
                assertTrue(contrastRatio(resolved.border, scheme.surface) >= 4.5)
                val baseBrightness = contrastRatio(resolved.base, Color.Black)
                val mediumBrightness = contrastRatio(resolved.medium, Color.Black)
                val containerBrightness = contrastRatio(resolved.container, Color.Black)
                if (!isDark) {
                    assertTrue(baseBrightness < mediumBrightness)
                    assertTrue(mediumBrightness < containerBrightness)
                } else {
                    assertTrue(baseBrightness > mediumBrightness)
                    assertTrue(mediumBrightness > containerBrightness)
                }
            }
            listOf<(CategoryColors) -> Color>({ it.base }, { it.medium }, { it.container })
                .forEach { tone ->
                    assertEquals(10, chromaticKeys.map { tone(colors.getValue(it)) }.toSet().size)
                }
        }
    }

    @Test
    fun suppliedLightAndDarkThreeLayerValuesStayExact() {
        data class Tones(val base: Long, val medium: Long, val container: Long)
        val expected = mapOf(
            CategoryColorKey.RED to (Tones(0xFFC64A3F, 0xFFDD928C, 0xFFE2A49F) to Tones(0xFFD06B62, 0xFF983F37, 0xFF803934)),
            CategoryColorKey.ORANGE to (Tones(0xFFD67B29, 0xFFE6B07F, 0xFFEBBD94) to Tones(0xFFDD9350, 0xFFA36228, 0xFF8A5527)),
            CategoryColorKey.AMBER to (Tones(0xFFC6A623, 0xFFDDCA7B, 0xFFE2D291) to Tones(0xFFD0B64B, 0xFF988123, 0xFF806E23)),
            CategoryColorKey.OLIVE to (Tones(0xFF7E9E32, 0xFFB2C584, 0xFFBECE98) to Tones(0xFF95AF57, 0xFF647B2E, 0xFF576A2C)),
            CategoryColorKey.GREEN to (Tones(0xFF2D9557, 0xFF81BF9A, 0xFF96CAAB) to Tones(0xFF53A875, 0xFF297549, 0xFF286442)),
            CategoryColorKey.TEAL to (Tones(0xFF188E9C, 0xFF74BBC4, 0xFF8CC6CE) to Tones(0xFF42A2AE, 0xFF1A6F7A, 0xFF1B606A)),
            CategoryColorKey.BLUE to (Tones(0xFF356EC3, 0xFF86A8DB, 0xFF9AB6E1) to Tones(0xFF5988CE, 0xFF2F5896, 0xFF2C4E80)),
            CategoryColorKey.PURPLE to (Tones(0xFF744DB5, 0xFFAC94D3, 0xFFBAA6DA) to Tones(0xFF8D6DC2, 0xFF5C418C, 0xFF513B78)),
            CategoryColorKey.PINK to (Tones(0xFFB54791, 0xFFD391BD, 0xFFDAA3C8) to Tones(0xFFC268A5, 0xFF8B3C72, 0xFF763763)),
            CategoryColorKey.BROWN to (Tones(0xFF8A6239, 0xFFB9A188, 0xFFC4B09C) to Tones(0xFF9F7E5D, 0xFF6C5033, 0xFF5D4730)),
        )
        assertEquals(chromaticKeys.toSet(), expected.keys)
        expected.forEach { (key, pair) ->
            listOf(lightColorScheme() to pair.first, darkColorScheme() to pair.second).forEach { (scheme, tones) ->
                val colors = resolveCategoryColors(key.storageKey, scheme)
                assertEquals(key.storageKey, Color(tones.base), colors.base)
                assertEquals(key.storageKey, Color(tones.medium), colors.medium)
                assertEquals(key.storageKey, Color(tones.container), colors.container)
            }
        }
    }

    @Test
    fun unknownKeyFallsBackToNeutralInBothThemes() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { scheme: ColorScheme ->
            val neutral = resolveCategoryColors("neutral", scheme)
            listOf("unknown", "", "emerald", "indigo", "berry").forEach {
                assertEquals(neutral, resolveCategoryColors(it, scheme))
            }
        }
    }

    @Test
    fun neutralKeepsItsExistingFixedTones() {
        val light = resolveCategoryColors("neutral", lightColorScheme())
        val dark = resolveCategoryColors("neutral", darkColorScheme())
        assertEquals(Color(0xFF73777F), light.base)
        assertEquals(Color(0xFFC5C8CD), light.medium)
        assertEquals(Color(0xFFF0F1F3), light.container)
        assertEquals(Color(0xFFA9ADB5), dark.base)
        assertEquals(Color(0xFF5E636B), dark.medium)
        assertEquals(Color(0xFF34373C), dark.container)
    }

    @Test
    fun reportThreeNearestPairsForEachToneWithoutDistanceThreshold() {
        listOf("Light" to lightColorScheme(), "Dark" to darkColorScheme()).forEach { (theme, scheme) ->
            val colors = chromaticKeys.associateWith { resolveCategoryColors(it.storageKey, scheme) }
            listOf(
                "Base" to { value: CategoryColors -> value.base },
                "Medium" to { value: CategoryColors -> value.medium },
                "Container" to { value: CategoryColors -> value.container },
            ).forEach { (toneName, tone) ->
                val pairs = chromaticKeys.flatMapIndexed { index, first ->
                    chromaticKeys.drop(index + 1).map { second ->
                        Triple(first.storageKey, second.storageKey,
                            okLabDistance(tone(colors.getValue(first)), tone(colors.getValue(second))))
                    }
                }.sortedBy { it.third }
                assertEquals(45, pairs.size)
                println("$theme $toneName nearest: " + pairs.take(3).joinToString { (first, second, distance) ->
                    "$first/$second ${"%.2f".format(java.util.Locale.ROOT, distance)}"
                })
            }
        }
    }

}
