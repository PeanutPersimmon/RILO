package io.github.peanutpersimmon.rilo.ui.feature.plan

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanCategoryColorsTest {
    @Test fun absentAndInvalidCategoriesStayUncoloredWhileNeutralIsReal() {
        val scheme = lightColorScheme()
        val keys = mapOf(1L to "neutral", 2L to "unknown")
        assertNull(planCategoryColors(null, keys, scheme))
        assertNull(planCategoryColors(3L, keys, scheme))
        assertNull(planCategoryColors(2L, keys, scheme))
        assertEquals(resolveCategoryColors("neutral", scheme), planCategoryColors(1L, keys, scheme))
    }

    @Test fun planColorUsesCurrentThemePalette() {
        val keys = mapOf(4L to "blue")
        assertEquals(resolveCategoryColors("blue", lightColorScheme()),
            planCategoryColors(4L, keys, lightColorScheme()))
        assertEquals(resolveCategoryColors("blue", darkColorScheme()),
            planCategoryColors(4L, keys, darkColorScheme()))
        assertNotEquals(planCategoryColors(4L, keys, lightColorScheme())?.container,
            planCategoryColors(4L, keys, darkColorScheme())?.container)
    }
}
