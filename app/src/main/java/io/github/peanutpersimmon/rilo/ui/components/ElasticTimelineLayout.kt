package io.github.peanutpersimmon.rilo.ui.components

/** Time stays immutable; only the coordinates used by Today and Todo & Done are expanded. */
internal data class ElasticTimelineItem<T>(
    val value: T,
    val start: Long,
    val end: Long?,
    val side: Boolean,
    val contentHeight: Float,
    val stableOrder: Long,
    /** Distance from the visible text top to the centre of its first line (points only). */
    val anchorLead: Float = 0f,
    /** Caps visible whitespace in elastic interval cards; time anchors remain unchanged. */
    val spanHeightLimit: Float = Float.POSITIVE_INFINITY,
)

internal data class ElasticTimelinePlacement<T>(
    val value: T,
    val visualStart: Float,
    val visualEnd: Float,
    val contentTop: Float,
    val contentHeight: Float,
)

internal data class ElasticTimelineLayout<T>(
    val anchors: List<Pair<Long, Float>>,
    val placements: List<ElasticTimelinePlacement<T>>,
    val requiredHeight: Float,
) {
    fun timeY(time: Long): Float {
        if (anchors.isEmpty()) return 0f
        if (time <= anchors.first().first) return anchors.first().second
        for (i in 1 until anchors.size) {
            val left = anchors[i - 1]
            val right = anchors[i]
            if (time <= right.first) {
                val fraction = (time - left.first).toDouble() / (right.first - left.first).toDouble()
                return left.second + (right.second - left.second) * fraction.toFloat()
            }
        }
        return anchors.last().second
    }
}

/**
 * A monotone visual time map for Today and Todo & Done. Points own their anchors. Timed cards
 * release only after all same-side points within their span, then stack below earlier cards.
 * Their real start/end values remain unchanged even when the visual card top is delayed.
 */
internal fun <T> arrangeElasticTimeline(
    items: List<ElasticTimelineItem<T>>,
    axisStart: Long,
    axisEnd: Long,
    trueY: (Long) -> Float,
    itemGap: Float,
    labelSpacing: Float,
    blockInset: Float,
    bottomPadding: Float,
): ElasticTimelineLayout<T> {
    require(axisEnd > axisStart)
    require(itemGap >= 0f && labelSpacing >= 0f && blockInset >= 0f)
    items.forEach { require(it.contentHeight > 0f && it.anchorLead in 0f..it.contentHeight) }
    val times = (items.flatMap { listOfNotNull(it.start, it.end) } + axisStart + axisEnd)
        .filter { it in axisStart..axisEnd }.distinct().sorted()
    val indices = times.withIndex().associate { it.value to it.index }
    fun isPoint(item: ElasticTimelineItem<T>) = item.end == null || item.end <= item.start
    val ordered = items.withIndex().sortedWith(
        compareBy<IndexedValue<ElasticTimelineItem<T>>> { it.value.start }
            .thenBy { it.value.stableOrder },
    )
    val pointGroups = ordered.filter { isPoint(it.value) }
        .groupBy { it.value.start to it.value.side }
    fun pointBefore(time: Long, side: Boolean): Float =
        pointGroups[time to side]?.firstOrNull()?.value?.anchorLead ?: 0f
    fun pointAfter(time: Long, side: Boolean): Float {
        val group = pointGroups[time to side] ?: return 0f
        return group.sumOf { it.value.contentHeight.toDouble() }.toFloat() +
            itemGap * (group.size - 1) - pointBefore(time, side)
    }
    val timed = ordered.filterNot { isPoint(it.value) }
    val releaseAt = timed.groupBy { (_, item) ->
        val lastPoint = pointGroups.keys.asSequence()
            .filter { (time, side) -> side == item.side && time >= item.start && time < requireNotNull(item.end) }
            .maxOfOrNull { it.first }
        lastPoint ?: item.start
    }
    val pendingEndFloor = FloatArray(times.size)
    val visual = FloatArray(times.size)
    val content = mutableMapOf<Int, Pair<Float, Float>>()
    val sideBottom = mutableMapOf<Boolean, Float>()
    for (i in times.indices) {
        val time = times[i]
        val before = maxOf(pointBefore(time, true), pointBefore(time, false))
        visual[i] = if (i == 0) maxOf(trueY(time), before) else {
            val previous = times[i - 1]
            val sideClearance = maxOf(
                pointAfter(previous, true) + pointBefore(time, true),
                pointAfter(previous, false) + pointBefore(time, false),
            )
            maxOf(
                pendingEndFloor[i],
                visual[i - 1] + maxOf(
                    trueY(time) - trueY(previous), labelSpacing,
                    sideClearance + itemGap,
                ),
            )
        }
        for (side in listOf(true, false)) {
            var offset = 0f
            pointGroups[time to side].orEmpty().forEach { (index, point) ->
                content[index] = (visual[i] - point.anchorLead + offset) to point.contentHeight
                offset += point.contentHeight + itemGap
            }
        }
        releaseAt[time].orEmpty().sortedWith(
            compareBy<IndexedValue<ElasticTimelineItem<T>>> { it.value.start }
                .thenBy { it.value.stableOrder },
        ).forEach { (index, item) ->
            val side = item.side
            val endTime = requireNotNull(item.end)
            val endIndex = indices.getValue(endTime)
            val endInset = maxOf(blockInset, pointBefore(endTime, side) + itemGap)
            val pointBottom = visual[i] + pointAfter(time, side)
            val top = maxOf(
                visual[indices.getValue(item.start)] + blockInset,
                if (pointGroups[time to side].isNullOrEmpty()) visual[i] + blockInset
                else pointBottom + itemGap,
                (sideBottom[side] ?: Float.NEGATIVE_INFINITY) + itemGap,
            )
            val trueSpan = trueY(endTime) - trueY(item.start)
            val height = maxOf(item.contentHeight,
                minOf(trueSpan - blockInset - endInset, item.spanHeightLimit))
            content[index] = top to height
            sideBottom[side] = top + height
            pendingEndFloor[endIndex] = maxOf(pendingEndFloor[endIndex], top + height + endInset)
        }
    }
    val anchors = times.mapIndexed { i, time -> time to visual[i] }
    val placements = ordered.map { (index, item) ->
        val (top, height) = content.getValue(index)
        val start = visual[indices.getValue(item.start)]
        val end = item.end?.takeIf { it > item.start }?.let { visual[indices.getValue(it)] } ?: start
        ElasticTimelinePlacement(item.value, start, end, top, height)
    }
    val contentBottom = placements.maxOfOrNull { it.contentTop + it.contentHeight } ?: visual.last()
    return ElasticTimelineLayout(
        anchors, placements,
        maxOf(visual.last(), contentBottom) + bottomPadding,
    )
}
