package io.github.peanutpersimmon.rilo.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TimelineMinuteKey(val date: LocalDate, val hour: Int, val minute: Int, val offsetSeconds: Int = 0)

fun timelineMinuteKey(time: Long, zone: ZoneId): TimelineMinuteKey =
    Instant.ofEpochMilli(time).atZone(zone).let { TimelineMinuteKey(it.toLocalDate(), it.hour, it.minute, it.offset.totalSeconds) }

/** Earliest exact anchor represents each displayed local minute; exact item anchors remain untouched. */
fun timelineMinuteLabelAnchors(times: Collection<Long>, zone: ZoneId): List<Long> =
    times.distinct().groupBy { timelineMinuteKey(it, zone) }
        .values.map { it.min() }.sorted()

/** A moving NOW label cannot displace an earlier endpoint label when it crosses a minute. */
fun selectTimelineMinuteLabelTimes(
    times: Collection<Long>, axisStart: Long, axisEnd: Long, zone: ZoneId,
    y: (Long) -> Float, minimumSpacingPx: Float, movingNow: Long? = null,
): Set<Long> {
    val stable = timelineMinuteLabelAnchors(times.filter { it != movingNow }, zone)
    val selected = selectTimelineLabelTimes(stable, axisStart,
        if (movingNow == axisEnd) Long.MIN_VALUE else axisEnd, y, minimumSpacingPx)
    if (movingNow == null || movingNow !in times) return selected
    return if (stable.any { timelineMinuteKey(it, zone) == timelineMinuteKey(movingNow, zone) })
        selected else selected + movingNow
}

fun timelineDetailProgress(zoom: Float, maxZoom: Float = 4f): Float =
    if (maxZoom <= 1f) 1f else ((zoom - 1f) / (maxZoom - 1f)).coerceIn(0f, 1f)

fun adaptiveTimelineDetailProgress(zoom: Float, minZoom: Float, maxZoom: Float): Float =
    if (maxZoom <= minZoom) 1f else ((zoom - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f)

fun boundedTimelineScale(current: Float, factor: Float, maxZoom: Float = 4f): Float =
    if (factor.isFinite() && factor > 0f) (current * factor).coerceIn(1f, maxZoom)
    else current.coerceIn(1f, maxZoom)

fun boundedAdaptiveTimelineScale(current: Float, factor: Float, minZoom: Float, maxZoom: Float): Float =
    if (factor.isFinite() && factor > 0f) (current * factor).coerceIn(minZoom, maxZoom)
    else current.coerceIn(minZoom, maxZoom)

fun timelineFocalOffsetY(
    currentOffsetY: Float,
    beforeScale: Float,
    afterScale: Float,
    focalY: Float,
    viewportHeight: Float,
): Float {
    if (viewportHeight <= 0f || beforeScale <= 0f) return currentOffsetY
    val contentY = (focalY - currentOffsetY) / beforeScale
    return (focalY - contentY * afterScale)
        .coerceIn(viewportHeight - viewportHeight * afterScale, 0f)
}

enum class TimelineInformationDensity { COMPACT, NORMAL, DETAILED }

data class PositionedTimelineInterval(val topPx: Float, val heightPx: Float)

/** Linear epoch-time geometry shared by Today and Plan/Todo & Done. */
class TimelineGeometry(
    val axisStartMillis: Long,
    val axisEndMillis: Long,
    val availableHeightPx: Float,
) {
    init {
        require(axisStartMillis < axisEndMillis)
        require(availableHeightPx > 0f)
    }
    private val durationMillis = axisEndMillis - axisStartMillis
    fun timeToY(timeMillis: Long): Float {
        val fraction = (timeMillis - axisStartMillis).toDouble() / durationMillis.toDouble()
        return (fraction.coerceIn(0.0, 1.0) * availableHeightPx).toFloat()
    }
    fun durationToHeight(durationMillis: Long): Float =
        (durationMillis.coerceAtLeast(0).toDouble() / this.durationMillis * availableHeightPx).toFloat()
    fun position(startMillis: Long, endMillis: Long): PositionedTimelineInterval? {
        val start = maxOf(startMillis, axisStartMillis)
        val end = minOf(endMillis, axisEndMillis)
        if (start >= end) return null
        return PositionedTimelineInterval(timeToY(start), durationToHeight(end - start))
    }
}

object TimelineDisplayPolicy {
    const val NORMAL_MIN_HEIGHT_DP = 32f
    const val DETAILED_MIN_HEIGHT_DP = 64f
    fun informationDensity(blockHeightDp: Float): TimelineInformationDensity = when {
        blockHeightDp < NORMAL_MIN_HEIGHT_DP -> TimelineInformationDensity.COMPACT
        blockHeightDp < DETAILED_MIN_HEIGHT_DP -> TimelineInformationDensity.NORMAL
        else -> TimelineInformationDensity.DETAILED
    }
}


/** Collision filtering shared by every centered-axis timeline. Boundary labels win. */
fun selectTimelineLabelTimes(
    times: Collection<Long>,
    axisStart: Long,
    axisEnd: Long,
    y: (Long) -> Float,
    minimumSpacingPx: Float,
): Set<Long> {
    val accepted = mutableSetOf<Long>()
    times.distinct().sortedWith(compareByDescending<Long> {
        if (it == axisStart || it == axisEnd) 1 else 0
    }.thenBy { it }).forEach { time ->
        if (accepted.none { kotlin.math.abs(y(it) - y(time)) < minimumSpacingPx }) accepted += time
    }
    return accepted
}
