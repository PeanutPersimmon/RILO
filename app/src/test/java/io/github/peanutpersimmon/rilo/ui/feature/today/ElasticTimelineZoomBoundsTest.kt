package io.github.peanutpersimmon.rilo.ui.feature.today

import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import io.github.peanutpersimmon.rilo.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class ElasticTimelineZoomBoundsTest {
    private val axisStart = 7L * 60
    private val axisEnd = 24L * 60
    private val viewport = 600f
    private fun minute(hour: Long, minute: Long = 0L) = hour * 60 + minute
    private fun item(name: String, start: Long, end: Long? = null, side: Boolean = true,
        contentHeight: Float = if (end == null) 14f else 16f,
    ) = ElasticTimelineItem(name, start, end, side, contentHeight,
        stableOrder = name.hashCode().toLong(), anchorLead = if (end == null) 7f else 0f)

    private fun bounds(items: List<ElasticTimelineItem<String>>, height: Float = viewport,
        full: Map<String, Float> = emptyMap()) = calculateElasticTimelineZoomBounds(
        items, full, axisStart, axisEnd, height, topBoundary = 54f,
        axisBottomInset = 0f, denseContentTail = 8f,
        itemGap = 4f, labelSpacing = 16f, blockInset = 4f)

    private fun resolved(items: List<ElasticTimelineItem<String>>, zoom: Float, height: Float) =
        arrangeElasticTimeline(items, axisStart, axisEnd,
            trueY = { 54f + (it - axisStart).toFloat() / (axisEnd - axisStart) *
                (height - 54f) * zoom },
            itemGap = 4f, labelSpacing = 16f, blockInset = 4f, bottomPadding = 0f)

    @Test fun visualAxisEndsAtViewportAndDenseTailOnlyExtendsScrollableContent() {
        val axisEndY = viewport
        assertEquals(viewport, axisEndY, 0f)
        assertEquals(viewport, timelineScrollableExtent(viewport, axisEndY, 8f), 0f)
        assertEquals(viewport + 28f,
            timelineScrollableExtent(viewport + 20f, axisEndY, 8f), 0f)
    }

    @Test fun sharedAutoFitNeverEnlargesButManualPinchStillCan() {
        assertEquals(1f, autoFitTimelineZoom(1.2f), 0f)
        assertEquals(1f, autoFitTimelineZoom(1f), 0f)
        assertEquals(0.8f, autoFitTimelineZoom(0.8f), 0f)

        val zoom = TimelineZoomState()
        zoom.updateAdaptiveBounds(1.2f, 2.5f)
        assertEquals(1f, zoom.minZoom, 0f)
        assertEquals(1f, zoom.scale, 0f)
        assertEquals(1.2f, zoom.zoomBy(1.2f), 0.001f)

        val roomy = bounds(listOf(item("事项", minute(14))))
        assertEquals(1f, roomy.minZoom, 0f)
        val crowded = bounds((1..20).map { item("事项$it", minute(14), contentHeight = 56f) },
            height = 200f)
        assertTrue(crowded.minZoom < 1f)
    }

    @Test fun sameDateAutoFitOnlyShrinksAndNewDateStartsFresh() {
        val sameDate = TimelineZoomState()
        sameDate.updateAdaptiveBounds(0.80f, 2.5f)
        sameDate.updateAdaptiveBounds(0.92f, 2.5f)
        assertEquals(0.80f, sameDate.minZoom, 0.001f)
        assertEquals(0.80f, sameDate.scale, 0.001f)
        sameDate.updateAdaptiveBounds(0.65f, 2.5f)
        assertEquals(0.65f, sameDate.minZoom, 0.001f)
        assertEquals(0.65f, sameDate.scale, 0.001f)

        val newDate = TimelineZoomState()
        newDate.updateAdaptiveBounds(0.92f, 2.5f)
        assertEquals(0.92f, newDate.scale, 0.001f)
    }

    @Test fun dataChangeDoesNotResetManualZoomEvenWhenComputedMaximumFalls() {
        val zoom = TimelineZoomState()
        zoom.updateAdaptiveBounds(0.80f, 2.5f)
        assertEquals(1.20f, zoom.zoomBy(1.5f), 0.001f)
        zoom.updateAdaptiveBounds(0.92f, 1.0f)
        assertEquals(1.20f, zoom.scale, 0.001f)
        assertEquals(1.20f, zoom.maxZoom, 0.001f)
        zoom.updateAdaptiveBounds(0.65f, 1.0f)
        assertEquals(1.20f, zoom.scale, 0.001f)
        assertEquals(0.65f, zoom.minZoom, 0.001f)
    }

    @Test fun screenshotItemsFitWhenReadableHeightIsBelowViewport() {
        val items = listOf(
            item("做家务", minute(9, 31), minute(9, 59), false),
            item("取快递1", minute(10), side = false),
            item("取快递2", minute(11, 30)),
            item("吃午饭", minute(11, 30), minute(12, 30)),
            item("小憩", minute(12, 40), minute(13, 30), false),
            item("测试", minute(13, 50), minute(14), false),
            item("写报告", minute(14), minute(15)),
            item("测试APP", minute(15), minute(17)),
            item("取快递3", minute(18)),
        )
        val result = bounds(items)
        val layout = resolved(items, result.minZoom, viewport)
        assertTrue(result.overviewFits)
        assertTrue(result.minZoom < 1f)
        assertTrue(layout.requiredHeight <= viewport)
        assertEquals(0f, (layout.requiredHeight - viewport).coerceAtLeast(0f), 0f)
        assertTrue(resolved(items, 1f, viewport).requiredHeight > viewport)
    }

    @Test fun crowdedPointsKeepReadableHeightsAndScrollAtAbsoluteMinimum() {
        val items = (1..20).map { item("多行标题$it", minute(10),
            contentHeight = 56f) }
        val result = bounds(items, height = 200f)
        val layout = resolved(items, result.minZoom, 200f)
        assertFalse(result.overviewFits)
        assertEquals(ELASTIC_ABSOLUTE_MIN_ZOOM, result.minZoom, 0f)
        assertTrue(layout.requiredHeight > 200f)
        layout.placements.zipWithNext().forEach { (a, b) ->
            assertTrue(b.contentTop >= a.contentTop + a.contentHeight + 4f)
        }
    }

    @Test fun completeDetailIsFiniteAndDurationCueRemainsOrdered() {
        val short = boundedElasticSpanHeight(16f, 10 * 60_000L, 1f)
        val hour = boundedElasticSpanHeight(16f, 60 * 60_000L, 1f)
        val twoHours = boundedElasticSpanHeight(16f, 120 * 60_000L, 1f)
        val eightHours = boundedElasticSpanHeight(16f, 480 * 60_000L, 1f)
        assertTrue(short < hour && hour < twoHours)
        assertTrue(eightHours <= 88f)
        val entries = listOf(item("写报告", minute(14), minute(15)))
        val result = bounds(entries, full = mapOf("写报告" to 16f))
        assertTrue(result.maxZoom < 4f)
        val limited = entries.map { it.copy(spanHeightLimit = hour) }
        val placement = resolved(limited, result.maxZoom, viewport).placements.single()
        assertTrue(placement.contentHeight <= hour)
    }

    @Test fun multipleMetadataLinesIncreaseTheContentCeilingAndFitAtMaximum() {
        val interval = item("测试APP", minute(15), minute(17))
        val point = item("取快递", minute(18))
        val items = listOf(interval, point)
        val compact = bounds(items)
        val detailed = bounds(items, full = mapOf("测试APP" to 96f, "取快递" to 54f))
        assertTrue(detailed.maxZoom > compact.maxZoom)
        assertTrue(detailed.maxZoom <= ELASTIC_PRODUCT_HARD_CAP)
        val fullItems = items.map { value ->
            value.copy(contentHeight = if (value.end == null) 54f else 96f,
                spanHeightLimit = boundedElasticSpanHeight(value.contentHeight,
                    ((value.end ?: value.start) - value.start) * 60_000L, 1f))
        }
        val layout = resolved(fullItems, detailed.maxZoom, viewport)
        assertTrue(layout.placements.single { it.value == "测试APP" }.contentHeight >= 96f)
        assertTrue(layout.placements.single { it.value == "取快递" }.contentHeight >= 54f)
    }

    @Test fun intervalCardsRetainOrderedDurationWithoutProportionalWhitespaceAtDetail() {
        val durations = listOf(10L, 60L, 120L)
        val items = durations.mapIndexed { index, minutes ->
            val start = minute(9 + index * 3L)
            item("$minutes", start, start + minutes).copy(
                spanHeightLimit = boundedElasticSpanHeight(16f, minutes * 60_000L, 1f))
        }
        val placements = resolved(items, 2.5f, viewport).placements.associateBy { it.value }
        val ten = placements.getValue("10").contentHeight
        val hour = placements.getValue("60").contentHeight
        val twoHours = placements.getValue("120").contentHeight
        assertTrue(ten < hour && hour < twoHours)
        assertTrue(twoHours < 90f)
    }

    @Test fun hardCapStillLeavesRoomForCompleteMeasuredContent() {
        val item = item("很长的详情", minute(14), minute(15))
        val result = bounds(listOf(item), full = mapOf(item.value to 1000f))
        assertEquals(ELASTIC_PRODUCT_HARD_CAP, result.maxZoom, 0f)
        val placement = resolved(listOf(item.copy(contentHeight = 1000f,
            spanHeightLimit = boundedElasticSpanHeight(16f, 60 * 60_000L, 1f))),
            result.maxZoom, viewport).placements.single()
        assertEquals(1000f, placement.contentHeight, 0f)
    }

    @Test fun activeSessionClockChangesDoNotChangeBoundSearchKey() {
        fun active(end: Long, duration: String) = TimelineEntryUiModel(
            sourceId = 1L, startAtMillis = 100L, endAtMillis = end,
            title = "正在写", timeLabel = "10:00", durationLabel = duration,
            entryType = TimelineEntryType.SESSION_BLOCK, state = TimelineState.ACTUAL,
            categoryLabel = null, location = null, note = null,
            isActive = true,
        )
        assertEquals(todayZoomStructuralEntries(listOf(active(200L, "1m"))),
            todayZoomStructuralEntries(listOf(active(300L, "2m"))))
    }
}
