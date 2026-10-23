package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepVisualColorsTest {
    @Test fun sleepAndNapUseDistinctOpaqueSystemTonesInBothThemes() {
        val expected = listOf(
            Triple(SessionKind.NIGHT_SLEEP, lightColorScheme(), listOf(0xFF7A859C, 0xFFB7BDC8, 0xFFD8DCE3)),
            Triple(SessionKind.NIGHT_SLEEP, darkColorScheme(), listOf(0xFFA4ADBD, 0xFF657080, 0xFF48505D)),
            Triple(SessionKind.NAP, lightColorScheme(), listOf(0xFFB8A58A, 0xFFD3C7B8, 0xFFE6DED2)),
            Triple(SessionKind.NAP, darkColorScheme(), listOf(0xFFC6B79A, 0xFF7C705D, 0xFF585044)),
        )
        expected.forEach { (kind, scheme, values) ->
            val colors = requireNotNull(resolveSleepVisualColors(kind, scheme))
            assertEquals(Color(values[0]), colors.base)
            assertEquals(Color(values[1]), colors.medium)
            assertEquals(Color(values[2]), colors.container)
            assertTrue(listOf(colors.base, colors.medium, colors.container).all { it.alpha == 1f })
            assertTrue(contrastRatio(colors.content, colors.container) >= 4.5)
            assertTrue(contrastRatio(colors.secondaryContent, colors.container) >= 4.5)
        }
        assertNull(resolveSleepVisualColors(SessionKind.ACTIVITY, lightColorScheme()))
    }

    @Test fun reportNearestCategoryBaseAndContainerForSleepAndNap() {
        val keys = CategoryColorKey.entries.filterNot { it == CategoryColorKey.NEUTRAL }
        listOf("Light" to lightColorScheme(), "Dark" to darkColorScheme()).forEach { (theme, scheme) ->
            listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP).forEach { kind ->
                val sleep = requireNotNull(resolveSleepVisualColors(kind, scheme))
                listOf("Base" to { colors: CategoryColors -> colors.base to sleep.base },
                    "Container" to { colors: CategoryColors -> colors.container to sleep.container })
                    .forEach { (tone, colors) ->
                        val distances = keys.map { key ->
                            val (categoryTone, sleepTone) = colors(resolveCategoryColors(key.storageKey, scheme))
                            key.storageKey to okLabDistance(sleepTone, categoryTone)
                        }.sortedBy { it.second }
                        assertEquals(10, distances.size)
                        val nearest = distances.first()
                        assertTrue(nearest.second > 0.0)
                        println("$theme ${kind.name} $tone nearest: ${nearest.first} " +
                            "%.2f".format(java.util.Locale.ROOT, nearest.second))
                    }
            }
        }
    }
}
