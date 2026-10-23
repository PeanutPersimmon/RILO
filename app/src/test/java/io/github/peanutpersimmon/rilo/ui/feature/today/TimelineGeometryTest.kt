package io.github.peanutpersimmon.rilo.ui.feature.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGeometryTest {
    @Test fun awakeDayMapsToTopAndBottom() {
        val geometry = TimelineGeometry(1_000, 11_000, 500f)
        assertEquals(0f, geometry.timeToY(1_000), 0.001f)
        assertEquals(500f, geometry.timeToY(11_000), 0.001f)
    }

    @Test fun midpointMapsToHalfHeight() {
        assertEquals(250f, TimelineGeometry(0, 10_000, 500f).timeToY(5_000), 0.001f)
    }

    @Test fun oneHourBlockUsesAwakeDayProportion() {
        val hour = 3_600_000L
        val geometry = TimelineGeometry(0, 16 * hour, 800f)
        assertEquals(50f, geometry.durationToHeight(hour), 0.001f)
    }

    @Test fun crossMidnightUsesContinuousEpochRange() {
        val hour = 3_600_000L
        val geometry = TimelineGeometry(23 * hour, 31 * hour, 400f)
        assertEquals(200f, geometry.timeToY(27 * hour), 0.001f)
    }

    @Test fun veryShortBlockRemainsProportionalAndCompact() {
        val geometry = TimelineGeometry(0, 10_000, 1_000f)
        val position = requireNotNull(geometry.position(1_000, 1_010))
        assertEquals(1f, position.heightPx, 0.001f)
        assertEquals(TimelineInformationDensity.COMPACT, TimelineDisplayPolicy.informationDensity(1f))
    }

    @Test fun intervalsOutsideAwakeDayAreClippedOrDropped() {
        val geometry = TimelineGeometry(1_000, 11_000, 500f)
        assertEquals(50f, requireNotNull(geometry.position(0, 2_000)).heightPx, 0.001f)
        assertNull(geometry.position(12_000, 13_000))
    }

    @Test fun changingAvailableHeightScalesEverythingEqually() {
        val small = TimelineGeometry(0, 10_000, 500f).position(2_000, 4_000)!!
        val large = TimelineGeometry(0, 10_000, 1_000f).position(2_000, 4_000)!!
        assertEquals(small.topPx * 2, large.topPx, 0.001f)
        assertEquals(small.heightPx * 2, large.heightPx, 0.001f)
    }

    @Test fun zoomIsBoundedAndKeepsTheFocalContentStable() {
        assertEquals(4f, io.github.peanutpersimmon.rilo.ui.components.boundedTimelineScale(3f, 2f), 0f)
        assertEquals(1f, io.github.peanutpersimmon.rilo.ui.components.boundedTimelineScale(1.5f, 0.1f), 0f)
        val offset = io.github.peanutpersimmon.rilo.ui.components.timelineFocalOffsetY(
            currentOffsetY = 0f, beforeScale = 1f, afterScale = 2f,
            focalY = 200f, viewportHeight = 600f)
        assertEquals(-200f, offset, 0.001f)
        val oldContentY = (200f - 0f) / 1f
        val newContentY = (200f - offset) / 2f
        assertEquals(oldContentY, newContentY, 0.001f)
    }

    @Test fun zoomStateAcceptsDynamicCeilingAboveFourAndClampsWhenItShrinks() {
        val state = io.github.peanutpersimmon.rilo.ui.components.TimelineZoomState()
        state.updateMaxZoom(8f)
        state.zoomBy(20f)

        assertEquals(8f, state.maxZoom, 0f)
        assertEquals(8f, state.scale, 0f)
        state.updateMaxZoom(2f)
        assertEquals(2f, state.scale, 0f)
        assertTrue(state.progress in 0f..1f)
    }

    @Test fun detailProgressChangesContinuouslyAndZoomStopsAtItsContentCeiling() {
        val state = io.github.peanutpersimmon.rilo.ui.components.TimelineZoomState()
        state.updateMaxZoom(5f)

        state.zoomBy(2f)
        assertEquals(2f, state.scale, 0f)
        assertEquals(0.25f, state.progress, 0.001f)

        state.zoomBy(1.5f)
        assertEquals(3f, state.scale, 0f)
        assertEquals(0.5f, state.progress, 0.001f)

        state.zoomBy(100f)
        assertEquals(5f, state.scale, 0f)
        assertEquals(1f, state.progress, 0f)
        state.zoomBy(2f)
        assertEquals(5f, state.scale, 0f)

        state.zoomBy(0.01f)
        assertEquals(1f, state.scale, 0f)
        assertEquals(0f, state.progress, 0f)
    }
}

