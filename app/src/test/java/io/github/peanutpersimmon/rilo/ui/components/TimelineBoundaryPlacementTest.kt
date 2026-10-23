package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineBoundaryPlacementTest {
    @Test fun endpointLabelAndMarkerStayInsideVisualGuide() {
        val guide = 600.dp
        val labelHeight = 12.dp
        val markerHeight = 5.dp

        val labelTop = timelineBoundaryElementTop(
            guide, labelHeight, TimelineBoundaryPlacement.INSIDE_BOTTOM)
        val markerTop = timelineBoundaryElementTop(
            guide, markerHeight, TimelineBoundaryPlacement.INSIDE_BOTTOM)

        assertEquals(guide, labelTop + labelHeight)
        assertEquals(guide, markerTop + markerHeight)
    }
}
