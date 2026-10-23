package io.github.peanutpersimmon.rilo.ui.feature.today

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.presentation.today.*
import io.github.peanutpersimmon.rilo.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class TimelineCardLayoutTest {
    private val awake = AwakeDayUiModel(0, 1_000, "07:00", "00:00")
    private fun entry(id: Long, start: Long, end: Long? = null) =
        TimelineEntryUiModel(id, start, end, "事项", "", null, TimelineEntryType.SESSION_BLOCK,
            TimelineState.ACTUAL, null, null, null, false,
            axisTimes = listOfNotNull(start, end))

    private fun layout(
        entries: List<TimelineEntryUiModel>,
        height: (TimelineEntryUiModel) -> Float = { 36f },
        labelSpacing: Float = 20f,
    ) = arrangeTimelineCards(
        awake, entries, 600f, 4f, 24f,
        naturalCardHeight = 1f, timedBlockInset = 4f,
        labelSpacing = labelSpacing, contentHeight = height,
        pointAnchorLead = 12f,
    )

    @Test fun visualAxisEndpointEqualsViewportGuideAndHoursRemainEquidistantAfterZoom() {
        val result = arrangeTimelineCards(
            awake = awake,
            entries = emptyList(),
            viewportHeight = 600f,
            itemGap = 4f,
            boundary = 24f,
            zoom = 1f,
            axisBottomInset = 0f,
            naturalCardHeight = 1f,
            timedBlockInset = 4f,
        )
        assertEquals(600f, result.timeY(awake.endAtMillis), 0.001f)

        val zoomed = arrangeTimelineCards(
            awake = awake,
            entries = emptyList(),
            viewportHeight = 600f,
            itemGap = 4f,
            boundary = 24f,
            zoom = 2f,
            axisBottomInset = 0f,
            naturalCardHeight = 1f,
            timedBlockInset = 4f,
        )
        val quarter = awake.endAtMillis / 4
        val gaps = (0..3).map { index ->
            zoomed.timeY((index + 1L) * quarter) - zoomed.timeY(index * quarter)
        }
        gaps.forEach { assertEquals(gaps.first(), it, 0.001f) }
    }

    @Test fun changingOnlyCategoryKeyKeepsTimelineAnchorsAndPointPlacement() {
        val block = entry(1, 100, 140).copy(sessionKind = SessionKind.ACTIVITY)
        val point = entry(2, 142).copy(entryType = TimelineEntryType.SESSION_POINT)
        val before = layout(listOf(block, point))
        val after = layout(listOf(block.copy(categoryColorKey = "blue"),
            point.copy(categoryColorKey = "red")))
        assertEquals(before.axisTimes, after.axisTimes)
        assertEquals(before.anchors, after.anchors)
        assertEquals(before.rows.map { it.visualTop to it.visualHeight },
            after.rows.map { it.visualTop to it.visualHeight })
        assertEquals(before.timeY(142), after.timeY(142), 0f)
    }

    @Test fun pointAndItsTimeLabelMoveTogetherAfterCollision() {
        val block = entry(1, 100, 110)
        val point = entry(2, 112).copy(entryType = TimelineEntryType.SESSION_POINT)
        val result = layout(listOf(block, point))
        val row = result.rows.single { it.entry.sourceId == 2L }
        assertTrue(row.visualTop > row.top)
        assertEquals(result.timeY(112), row.visualAnchor, 0.001f)
        assertEquals(row.visualAnchor, row.visualTop + 12f, 0.001f)
        assertEquals(112L, row.start)
        assertEquals(112L, row.end)
        assertEquals(0f, row.height, 0f)
    }

    @Test fun shortIntervalExpandsBothEndpointLabelsWithItsCard() {
        val row = layout(listOf(entry(1, 100, 110))).rows.single()
        val result = layout(listOf(entry(1, 100, 110)))
        assertTrue(result.timeY(110) - result.timeY(100) >= 44f)
        assertEquals(result.timeY(100) + 4f, row.visualTop, 0.001f)
        assertEquals(result.timeY(110) - 4f,
            row.visualTop + row.visualHeight, 0.001f)
        assertEquals(100L, row.start)
        assertEquals(110L, row.end)
        assertEquals(36f, row.visualHeight, 0.001f)
    }

    @Test fun oneLineTwoLineAndMetadataProduceDifferentNaturalHeights() {
        val oneLine = naturalTimelineContentHeight(24f, 0, 16f, 0f, 2f)
        val twoLines = naturalTimelineContentHeight(48f, 0, 16f, 0f, 2f)
        val metadata = naturalTimelineContentHeight(24f, 1, 16f, 1f, 2f)
        assertEquals(28f, oneLine, 0f)
        assertEquals(52f, twoLines, 0f)
        assertEquals(46f, metadata, 0f)
        assertTrue(twoLines > oneLine)
        assertTrue(metadata > oneLine)
    }

    @Test fun tenMinuteCardIsCompactAndTwoHourCardUsesTimeSpan() {
        val hour = 3_600_000L
        val day = AwakeDayUiModel(0, 24 * hour, "00:00", "00:00")
        val short = entry(1, 10 * hour, 10 * hour + 10 * 60_000L)
        val long = entry(2, 15 * hour, 17 * hour)
        val result = arrangeTimelineCards(
            day, listOf(short, long), 1_200f, 4f, 24f,
            naturalCardHeight = 1f, timedBlockInset = 4f,
            contentHeight = { 36f },
        )
        val shortRow = result.rows.single { it.entry.sourceId == 1L }
        val longRow = result.rows.single { it.entry.sourceId == 2L }
        assertEquals(36f, shortRow.visualHeight, 0.01f)
        assertTrue(longRow.visualHeight > shortRow.visualHeight * 2)
        assertEquals(result.timeY(shortRow.end) - 4f,
            shortRow.visualTop + shortRow.visualHeight, 0.01f)
    }

    @Test fun closePointsStayOrderedAndShareTheirMinuteLabel() {
        val first = entry(1, 500)
        val second = entry(2, 503)
        val third = entry(3, 506)
        val result = layout(listOf(first, second, third), height = { 28f })
        assertEquals(listOf(500L, 503L, 506L), result.rows.map { it.start })
        result.rows.zipWithNext().forEach { (a, b) ->
            assertTrue(b.visualTop >= a.visualTop + a.visualHeight + 4f)
        }
        result.rows.forEach { row ->
            assertEquals(result.timeY(row.start), row.visualAnchor, 0.01f)
            assertEquals(row.visualAnchor, row.visualTop + 12f, 0.01f)
        }
        assertEquals(1, visibleTimelineLabels(result, 20f).size)
    }

    @Test fun sameTimePointsStackUnderOneSharedAnchor() {
        val a = entry(1, 500).copy(state = TimelineState.PLANNED)
        val b = entry(2, 500).copy(state = TimelineState.PLANNED)
        val c = entry(3, 500).copy(state = TimelineState.ACTUAL)
        val result = layout(listOf(a, b, c), height = { 24f })
        val rows = result.rows.associateBy { it.entry.sourceId }
        val anchor = result.timeY(500)
        assertEquals(anchor - 12f, rows.getValue(1L).visualTop, 0.01f)
        assertEquals(anchor + 16f, rows.getValue(2L).visualTop, 0.01f)
        assertEquals(anchor - 12f, rows.getValue(3L).visualTop, 0.01f)
        assertEquals(anchor, rows.getValue(1L).visualAnchor, 0.01f)
        assertEquals(anchor, rows.getValue(2L).visualAnchor, 0.01f)
        assertEquals(1, result.axisTimes.count { it == 500L })
    }

    @Test fun overlappingSameSideIntervalsStackVertically() {
        val first = entry(1, 100, 400)
        val second = entry(2, 200, 300)
        val result = layout(listOf(first, second))
        val rows = result.rows.associateBy { it.entry.sourceId }
        val a = rows.getValue(1L)
        val b = rows.getValue(2L)
        assertTrue(b.visualTop >= a.visualTop + a.visualHeight + 4f)
        assertEquals(result.timeY(a.start), a.visualAnchor, 0.01f)
        assertEquals(result.timeY(b.start), b.visualAnchor, 0.01f)
    }

    @Test fun pointInsideIntervalKeepsAnchorAndMakesIntervalWaitBelowIt() {
        val result = layout(listOf(entry(1, 100, 400), entry(2, 200)))
        val rows = result.rows.associateBy { it.entry.sourceId }
        val block = rows.getValue(1L)
        val point = rows.getValue(2L)
        assertEquals(result.timeY(200), point.visualAnchor, 0.01f)
        assertEquals(point.visualAnchor, point.visualTop + 12f, 0.01f)
        assertTrue(block.visualTop >= point.visualTop + point.visualHeight + 4f)
        assertEquals(result.timeY(400) - 4f,
            block.visualTop + block.visualHeight, 0.01f)
    }

    @Test fun pointAtIntervalStartOwnsTimeAndIntervalBeginsBelow() {
        val hour = 3_600_000L
        val day = AwakeDayUiModel(0, 24 * hour, "00:00", "00:00")
        val point = entry(1, 10 * hour)
        val block = entry(2, 10 * hour, 12 * hour)
        val result = arrangeTimelineCards(
            day, listOf(point, block), 1_200f, 4f, 24f,
            naturalCardHeight = 1f, timedBlockInset = 4f,
            labelSpacing = 20f, contentHeight = { 36f }, pointAnchorLead = 12f,
        )
        val pointRow = result.rows.single { it.entry.sourceId == 1L }
        val blockRow = result.rows.single { it.entry.sourceId == 2L }
        assertEquals(result.timeY(10 * hour), pointRow.visualAnchor, 0.01f)
        assertEquals(pointRow.visualAnchor, pointRow.visualTop + 12f, 0.01f)
        assertTrue(blockRow.visualTop >= pointRow.visualTop + pointRow.visualHeight + 4f)
        assertEquals(result.timeY(12 * hour) - 4f,
            blockRow.visualTop + blockRow.visualHeight, 0.01f)
        assertEquals(listOf(10 * hour, 12 * hour), result.axisTimes.filter { it != 0L && it != 24 * hour })
    }

    @Test fun adjacentPointAndIntervalRemainChronologicalWithoutDetachingPoint() {
        val result = layout(listOf(entry(1, 100), entry(2, 101, 200)))
        val point = result.rows.single { it.entry.sourceId == 1L }
        val block = result.rows.single { it.entry.sourceId == 2L }
        assertEquals(result.timeY(100), point.visualAnchor, 0.01f)
        assertTrue(block.visualTop >= point.visualTop + point.visualHeight + 4f)
        assertTrue(result.timeY(100) < result.timeY(101))
    }

    @Test fun screenshotScenarioKeepsPointLabelsAndDurationDifference() {
        val minute = 60_000L
        val day = AwakeDayUiModel(0, 24 * 60 * minute, "00:00", "00:00")
        fun time(hour: Long, min: Long) = (hour * 60 + min) * minute
        val entries = listOf(
            entry(1, time(9, 31), time(9, 59)),
            entry(2, time(11, 30)),
            entry(3, time(13, 50), time(14, 0)),
            entry(4, time(15, 0), time(17, 0)),
            entry(5, time(18, 0)),
            entry(6, time(22, 51), time(23, 51)),
        )
        val result = arrangeTimelineCards(
            day, entries, 1_200f, 4f, 24f,
            naturalCardHeight = 1f, timedBlockInset = 4f,
            labelSpacing = 24f, contentHeight = { 36f }, pointAnchorLead = 12f,
        )
        for (id in listOf(2L, 5L)) {
            val row = result.rows.single { it.entry.sourceId == id }
            assertEquals(result.timeY(row.start), row.visualAnchor, 0.01f)
            assertEquals(row.visualAnchor, row.visualTop + 12f, 0.01f)
        }
        val tenMinutes = result.rows.single { it.entry.sourceId == 3L }
        val twoHours = result.rows.single { it.entry.sourceId == 4L }
        val oneHour = result.rows.single { it.entry.sourceId == 6L }
        assertEquals(36f, tenMinutes.visualHeight, 0.01f)
        assertTrue(twoHours.visualHeight > tenMinutes.visualHeight)
        assertTrue(oneHour.visualHeight > tenMinutes.visualHeight)
        assertEquals(entries.map { it.startAtMillis }, result.rows.map { it.start })
    }

    @Test fun currentTimeInterpolatesWithinResolvedOrder() {
        val result = layout(listOf(entry(1, 100, 110), entry(2, 150)))
        assertTrue(result.timeY(120) > result.timeY(110))
        assertTrue(result.timeY(120) < result.timeY(150))
    }

    @Test fun displayWindowAndNightSleepSemanticsStayUnchanged() {
        val sleep = entry(1, 100, 900).copy(sessionKind = SessionKind.NIGHT_SLEEP)
        val nap = entry(2, 200, 300).copy(sessionKind = SessionKind.NAP)
        val result = layout(listOf(sleep, nap))
        assertEquals(listOf(2L), result.rows.map { it.entry.sourceId })
        assertEquals(0L, result.axisStart)
        assertEquals(1_000L, result.axisEnd)
    }

    @Test fun dayWeekGeometryRemainsStrictlyProportional() {
        val hour = 3_600_000L
        val geometry = TimelineGeometry(0L, 24L * hour, 1_440f)
        assertEquals(60f, geometry.timeToY(hour), 0.01f)
        assertEquals(120f, geometry.timeToY(2L * hour), 0.01f)
    }
}

