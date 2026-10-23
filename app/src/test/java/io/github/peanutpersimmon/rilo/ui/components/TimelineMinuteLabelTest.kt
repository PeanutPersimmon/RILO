package io.github.peanutpersimmon.rilo.ui.components

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TimelineMinuteLabelTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 4)
    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        day.atTime(hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    @Test fun sameMinutePointsAndEndpointsKeepEarliestExactAnchor() {
        val first = at(14, 20, 5)
        val second = at(14, 20, 50)
        assertEquals(listOf(first), timelineMinuteLabelAnchors(listOf(second, first, first), zone))
        val endpoint = at(15, 30)
        val point = at(15, 30, 35)
        assertEquals(listOf(endpoint), timelineMinuteLabelAnchors(listOf(point, endpoint), zone))
        assertEquals(listOf(first, endpoint),
            timelineMinuteLabelAnchors(listOf(first, second, endpoint, point), zone))
    }

    @Test fun nowSharesEndpointLabelButNeverMovesItsExactPosition() {
        val endpoint = at(23, 12)
        val now = at(23, 12, 37)
        val start = at(23, 0)
        fun y(time: Long) = (time - start).toFloat() / 1_000f
        val shown = selectTimelineMinuteLabelTimes(listOf(endpoint, now), start, now,
            zone, ::y, 10f, now)
        assertEquals(setOf(endpoint), shown)
        assertNotEquals(y(endpoint), y(now))
    }

    @Test fun nowCrossingMinuteLeavesHistoricalEndpointLabel() {
        val endpoint = at(23, 12)
        val now = at(23, 13)
        val start = at(23, 0)
        val shown = selectTimelineMinuteLabelTimes(listOf(endpoint, now), start, now,
            zone, { (it - start).toFloat() / 1_000f }, 10f, now)
        assertEquals(setOf(endpoint, now), shown)
    }

    @Test fun localDateIsPartOfMinuteKey() {
        val tomorrow = day.plusDays(1).atTime(23, 12).atZone(zone).toInstant().toEpochMilli()
        assertEquals(2, timelineMinuteLabelAnchors(listOf(at(23, 12), tomorrow), zone).size)
    }
}
