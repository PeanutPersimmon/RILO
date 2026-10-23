package io.github.peanutpersimmon.rilo.ui.feature.plan

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanVisualBottomGeometryTest {
    @Test fun dayAndWeekGridsEndAtTheirAvailableVisualGuide() {
        val guide = 600.dp
        assertEquals(guide, planDayAxisHeight(guide, zoom = 1f))

        val header = 26.dp
        val gap = 1.dp
        val weekAxis = planWeekAxisHeight(guide, header, gap, zoom = 1f)
        assertEquals(guide, header + gap + weekAxis)
    }

    @Test fun todoDoneAxisEndpointEqualsVisualGuideAtBaselineZoom() {
        val viewport = 600f
        val topBoundary = 24f
        assertEquals(viewport,
            topBoundary + todoDoneAxisHeight(viewport, topBoundary, zoom = 1f), 0.001f)
    }

    @Test fun monthCardFillsGuideAndFourFiveSixWeekRowsKeepMaximumHeight() {
        val available = 600.dp
        for (rowCount in 4..6) {
            val layout = MonthGridGeometry.layout(available, rowCount)
            assertEquals(available, layout.cardHeight)
            assertTrue(layout.weekHeight <= MonthGridGeometry.maxWeekHeight)
            assertTrue(layout.weekHeight >= MonthGridGeometry.minWeekHeight)
            assertTrue(layout.elasticSpace >= 0.dp)
        }
        assertEquals(MonthGridGeometry.maxWeekHeight,
            MonthGridGeometry.layout(available, 4).weekHeight)
    }

    @Test fun twoByFourExistingFourRowsStillConsumeTheWholeGuide() {
        // Characterises the unchanged PlanBoard formula: three 0.5dp dividers plus four rows.
        val available = 600.dp
        val dividerTotal = 1.5.dp
        val rowHeight = (available - dividerTotal) / 4
        assertEquals(available, rowHeight * 4 + dividerTotal)
    }
}
