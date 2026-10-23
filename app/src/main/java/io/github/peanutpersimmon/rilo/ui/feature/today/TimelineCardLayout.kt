package io.github.peanutpersimmon.rilo.ui.feature.today

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.presentation.today.AwakeDayUiModel
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import io.github.peanutpersimmon.rilo.ui.components.ElasticTimelineItem
import io.github.peanutpersimmon.rilo.ui.components.TimelineContentLevel
import io.github.peanutpersimmon.rilo.ui.components.arrangeElasticTimeline

internal val TimelineEntryUiModel.isPlan: Boolean get() = state == io.github.peanutpersimmon.rilo.domain.model.TimelineState.PLANNED
internal val TimelineEntryUiModel.isTimedSpan: Boolean get() =
    endAtMillis != null && endAtMillis > startAtMillis

internal data class TimelineCardPosition(
    val entry: TimelineEntryUiModel,
    /** Strict proportional coordinate before visual collision expansion. */
    val top: Float,
    val height: Float,
    val start: Long,
    val end: Long,
    /** Final card or point text geometry derived from the shared visual time anchors. */
    val visualTop: Float = top,
    val visualHeight: Float = height,
    val visualAnchor: Float = top,
    val contentLevel: io.github.peanutpersimmon.rilo.ui.components.TimelineContentLevel =
        io.github.peanutpersimmon.rilo.ui.components.TimelineContentLevel.COMPACT,
)

internal data class TimelineCardLayout(
    val rows: List<TimelineCardPosition>, val height: Float, val boundary: Float,
    val requiredHeight: Float,
    val maxZoom: Float,
    val awakeDay: AwakeDayUiModel, val anchors: List<Pair<Long, Float>>,
    val axisTimes: List<Long>,
    val axisEndOverride: Long? = null,
) {
    val axisStart: Long? get() = axisTimes.firstOrNull()
    val axisEnd: Long? get() = axisEndOverride ?: axisTimes.lastOrNull()
    fun timeY(time: Long): Float {
        if (anchors.isEmpty()) return boundary
        if (time <= anchors.first().first) return anchors.first().second
        anchors.zipWithNext().forEach { (a, b) ->
            if (time <= b.first) {
                val fraction = (time.toDouble() - a.first) / (b.first.toDouble() - a.first)
                return a.second + (b.second - a.second) * fraction.toFloat()
            }
        }
        return anchors.last().second
    }
}

internal object TimelineZoomPolicy {
    fun details(entry: TimelineEntryUiModel): List<String> {
        val category = entry.categoryLabel
        val location = entry.location?.takeIf(String::isNotBlank)
        val note = entry.note?.takeIf(String::isNotBlank)
        val primary = listOfNotNull(category, entry.sourceEventTitle?.let { "来自日程 · $it" }).joinToString(" · ").takeIf(String::isNotBlank)
        return when {
            entry.entryType == TimelineEntryType.EVENT_BLOCK ->
                listOfNotNull(location, category, note)
            else -> listOfNotNull(location, primary, note)
        }
    }
}

/** True time coordinates are retained in rows; all rendered geometry uses one elastic map. */
internal fun arrangeTimelineCards(awake: AwakeDayUiModel, entries: List<TimelineEntryUiModel>,
    viewportHeight: Float, itemGap: Float, boundary: Float, zoom: Float = 1f,
    nowMillis: Long? = null, axisBottomInset: Float = 0f,
    naturalCardHeight: Float,
    timedBlockInset: Float,
    detailedCardHeight: Float = naturalCardHeight,
    labelSpacing: Float = 0f,
    contentHeight: ((TimelineEntryUiModel) -> Float)? = null,
    pointAnchorLead: Float = 0f,
    spanHeightLimit: ((TimelineEntryUiModel) -> Float)? = null,
    maxZoom: Float = 4f,
): TimelineCardLayout {
    val eligible = entries.filter { it.entryType !in setOf(TimelineEntryType.WAKE_POINT, TimelineEntryType.SLEEP_POINT) &&
        it.sessionKind != SessionKind.NIGHT_SLEEP && it.axisTimes.isNotEmpty() }
    val points = eligible.flatMap { it.axisTimes }.distinct().sorted()
    val first = awake.startAtMillis
    val last = awake.endAtMillis
    val oneXAxisHeight = (viewportHeight - boundary - axisBottomInset).coerceAtLeast(1f)
    val axisHeight = oneXAxisHeight * zoom
    val geometry = io.github.peanutpersimmon.rilo.ui.components.TimelineGeometry(first, last, axisHeight)
    fun y(time: Long) = boundary + geometry.timeToY(time)
    val axis = (points.filter { it >= first && it < last } + listOf(first, last))
        .distinct().sorted()
    val visible = eligible.filter { entry ->
        if (entry.isTimedSpan) entry.startAtMillis < last && requireNotNull(entry.endAtMillis) > first
        else entry.startAtMillis >= first && entry.startAtMillis < last
    }.sortedBy { it.startAtMillis }
    val rawRows = visible.mapNotNull { entry ->
        val start = entry.startAtMillis.coerceIn(first, last)
        val end = if (entry.isTimedSpan) requireNotNull(entry.endAtMillis).coerceIn(first, last) else start
        if (entry.isTimedSpan && end <= start) null else TimelineCardPosition(
            entry = entry, top = y(start), height = if (entry.isTimedSpan) y(end) - y(start) else 0f,
            start = start, end = end)
    }
    val resolved = arrangeElasticTimeline(
        items = rawRows.mapIndexed { index, row ->
            ElasticTimelineItem(
                value = row,
                start = row.start,
                end = row.end.takeIf { row.entry.isTimedSpan },
                side = row.entry.isPlan,
                contentHeight = contentHeight?.invoke(row.entry) ?: if (
                    TimelineZoomPolicy.details(row.entry).isEmpty()
                ) naturalCardHeight else detailedCardHeight,
                stableOrder = row.entry.sourceId ?: index.toLong(),
                anchorLead = if (row.entry.isTimedSpan) 0f else pointAnchorLead,
                spanHeightLimit = if (row.entry.isTimedSpan)
                    spanHeightLimit?.invoke(row.entry) ?: Float.POSITIVE_INFINITY
                else Float.POSITIVE_INFINITY,
            )
        },
        axisStart = first,
        axisEnd = last,
        trueY = ::y,
        itemGap = itemGap,
        labelSpacing = labelSpacing,
        blockInset = timedBlockInset,
        bottomPadding = 0f,
    )
    val placed = resolved.placements.map { placement ->
        placement.value.copy(
            visualTop = placement.contentTop,
            visualHeight = placement.contentHeight,
            visualAnchor = placement.visualStart,
            contentLevel = TimelineContentLevel.FULL,
        )
    }
    return TimelineCardLayout(
        rows = placed,
        height = viewportHeight,
        boundary = boundary,
        requiredHeight = resolved.requiredHeight,
        maxZoom = maxZoom,
        awakeDay = awake,
        anchors = resolved.anchors,
        axisTimes = axis,
        axisEndOverride = null,
    )
}

/** Preserve all dots; only text competes for gutter space. Start/boundary labels win over ends. */
internal fun visibleTimelineLabels(layout: TimelineCardLayout, minSpacing: Float,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(), movingNow: Long? = null): Set<Long> {
    val start = layout.axisStart ?: return emptySet()
    val end = layout.axisEnd ?: return emptySet()
    return io.github.peanutpersimmon.rilo.ui.components.selectTimelineMinuteLabelTimes(
        layout.axisTimes, start, end, zone, layout::timeY, minSpacing, movingNow)
}
