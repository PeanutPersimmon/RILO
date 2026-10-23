package io.github.peanutpersimmon.rilo.ui.theme

import org.junit.Assert.*
import org.junit.Test

class OverdueMarkerColorTest {
    @Test fun overdueMarkersUseDistinctRedInLightAndDarkThemes() {
        listOf(LightSemanticColors.overdueMarker, DarkSemanticColors.overdueMarker).forEach { color ->
            assertTrue(color.red > color.green)
            assertTrue(color.red > color.blue)
        }
        assertNotEquals(LightError, LightSemanticColors.overdueMarker)
        assertNotEquals(DarkError, DarkSemanticColors.overdueMarker)
    }
}
