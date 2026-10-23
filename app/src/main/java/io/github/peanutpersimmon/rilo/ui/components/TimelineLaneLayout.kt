package io.github.peanutpersimmon.rilo.ui.components

/**
 * An item expressed in the timeline's pixel coordinate space.
 *
 * [idealTop] is the exact start-time coordinate. A positive [idealHeight] is a timed span;
 * zero represents a point. [naturalHeight] describes the content's preferred fully-revealed
 * height. Card insets, readability expansion, and collision displacement are visual geometry
 * only and never change these time coordinates.
 */
internal data class TimelineLaneItem<T>(
    val value: T,
    val idealTop: Float,
    val idealHeight: Float,
    val naturalHeight: Float,
    val stableOrder: Long,
)

internal enum class TimelineContentLevel { COMPACT, FULL }

private const val NaturalHeightEpsilonFraction = 0.0001f

/** Final vertical placement. Ideal time geometry remains available for labels and tests. */
internal data class TimelineLanePlacement<T>(
    val value: T,
    val idealTop: Float,
    val idealHeight: Float,
    val visualTop: Float,
    val visualHeight: Float,
    val contentLevel: TimelineContentLevel,
)

internal data class TimelineLaneLayoutResult<T>(
    val placements: List<TimelineLanePlacement<T>>,
    /** Bottom of the final visual content, including [bottomPadding]. */
    val requiredHeight: Float,
    /** Zoom at which every timed card has enough visual height for its full content. */
    val maxZoom: Float,
)

/**
 * Orders one side by time and places each visual directly after the previous visual when their
 * ideal slots overlap. There are no horizontal lanes, overflow aggregation, or anchor guides.
 * [idealHeight] is already scaled by the shared time geometry. Timed cards are inset from their
 * endpoints and expanded to [minimumReadableHeight] when the proportional span would not fit a
 * title. Point items retain their exact anchor coordinate, consume no visual height, and therefore
 * never move later cards.
 */
internal fun <T> arrangeTimelineLanes(
    items: List<TimelineLaneItem<T>>,
    topBoundary: Float,
    itemGap: Float,
    timedBlockInset: Float,
    minimumReadableHeight: Float,
    zoomScale: Float,
    bottomPadding: Float = 0f,
): TimelineLaneLayoutResult<T> {
    require(itemGap >= 0f)
    require(timedBlockInset >= 0f)
    require(minimumReadableHeight > 0f)
    require(zoomScale >= 1f)
    require(bottomPadding >= 0f)
    items.forEach {
        require(it.idealHeight >= 0f)
        require(it.naturalHeight > 0f)
    }

    val sorted = items.sortedWith(
        compareBy<TimelineLaneItem<T>> { it.idealTop }
            .thenByDescending { it.idealTop + it.idealHeight }
            .thenBy { it.stableOrder },
    )

    var cursorBottom = topBoundary
    val placements = sorted.map { item ->
        if (item.idealHeight <= 0f) {
            return@map TimelineLanePlacement(
                value = item.value,
                idealTop = item.idealTop,
                idealHeight = 0f,
                visualTop = item.idealTop,
                visualHeight = 0f,
                contentLevel = TimelineContentLevel.COMPACT,
            )
        }
        val idealCardTop = item.idealTop + timedBlockInset
        val proportionalHeight = (item.idealHeight - 2f * timedBlockInset).coerceAtLeast(0f)
        val cardHeight = maxOf(minimumReadableHeight, proportionalHeight)
        val contentLevel = if (
            cardHeight >= item.naturalHeight * (1f - NaturalHeightEpsilonFraction)
        ) TimelineContentLevel.FULL else TimelineContentLevel.COMPACT
        val placedTop = maxOf(idealCardTop, cursorBottom)
        TimelineLanePlacement(
            value = item.value,
            idealTop = item.idealTop,
            idealHeight = item.idealHeight,
            visualTop = placedTop,
            visualHeight = cardHeight,
            contentLevel = contentLevel,
        ).also {
            cursorBottom = placedTop + cardHeight + itemGap
        }
    }

    return TimelineLaneLayoutResult(
        placements = placements,
        requiredHeight = maxOf(topBoundary, cursorBottom) + bottomPadding,
        maxZoom = items.asSequence()
            .filter { it.idealHeight > 0f }
            .map { item ->
                val oneXSpan = item.idealHeight / zoomScale
                if (minimumReadableHeight >= item.naturalHeight) 1f
                else if (oneXSpan > 0f) {
                    (item.naturalHeight + 2f * timedBlockInset) / oneXSpan
                } else 1f
            }
            .filter(Float::isFinite)
            .fold(1f, ::maxOf)
            .coerceAtLeast(1f),
    )
}

internal fun timelineRequiresScroll(zoomScale: Float): Boolean = zoomScale > 1.001f
