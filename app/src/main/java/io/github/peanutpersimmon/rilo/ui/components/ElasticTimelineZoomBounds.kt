package io.github.peanutpersimmon.rilo.ui.components

import kotlin.math.ln

/** Geometry only: the caller measures real text once and supplies overview and full heights. */
internal data class ElasticTimelineZoomBounds(
    val minZoom: Float,
    val maxZoom: Float,
    val overviewFits: Boolean,
)

internal const val ELASTIC_ABSOLUTE_MIN_ZOOM = 0.2f
internal const val ELASTIC_PRODUCT_HARD_CAP = 2.5f

/** Dense cards may need a small scroll tail, but that tail never shortens the visual axis. */
internal fun timelineScrollableExtent(
    requiredHeight: Float,
    visualAxisEnd: Float,
    denseContentTail: Float,
): Float {
    require(requiredHeight >= 0f && visualAxisEnd >= 0f && denseContentTail >= 0f)
    return requiredHeight + if (requiredHeight > visualAxisEnd + 0.5f) denseContentTail else 0f
}

/** Keeps duration differences visible without turning long intervals into empty white slabs. */
internal fun boundedElasticSpanHeight(
    overviewHeight: Float,
    durationMillis: Long,
    density: Float,
): Float {
    val minutes = durationMillis.coerceAtLeast(0L) / 60_000.0
    val durationCueDp = (12.0 * ln(1.0 + minutes / 10.0) / ln(2.0)).coerceAtMost(72.0)
    return overviewHeight + (durationCueDp * density).toFloat()
}

internal fun elasticIntervalHeightLimit(
    overviewHeight: Float,
    durationMillis: Long,
    density: Float,
    oneXAxisHeight: Float,
    axisDurationMillis: Long,
    minZoom: Float,
    blockInset: Float,
): Float = maxOf(
    boundedElasticSpanHeight(overviewHeight, durationMillis, density),
    (durationMillis.toDouble() / axisDurationMillis * oneXAxisHeight * minZoom)
        .toFloat() - 2f * blockInset,
)

/** Maximal readable Overview scale, followed by a finite content-driven reveal interval. */
internal fun <T> calculateElasticTimelineZoomBounds(
    items: List<ElasticTimelineItem<T>>,
    fullDetailHeights: Map<T, Float>,
    axisStart: Long,
    axisEnd: Long,
    viewportHeight: Float,
    topBoundary: Float,
    axisBottomInset: Float,
    denseContentTail: Float,
    itemGap: Float,
    labelSpacing: Float,
    blockInset: Float,
    absoluteMinZoom: Float = ELASTIC_ABSOLUTE_MIN_ZOOM,
    productHardCap: Float = ELASTIC_PRODUCT_HARD_CAP,
): ElasticTimelineZoomBounds {
    require(axisEnd > axisStart && viewportHeight > 0f)
    val oneXHeight = (viewportHeight - topBoundary - axisBottomInset).coerceAtLeast(1f)
    fun heightAt(zoom: Float): Float {
        fun trueY(time: Long): Float = topBoundary +
            ((time - axisStart).toDouble() / (axisEnd - axisStart) * oneXHeight * zoom).toFloat()
        val layout = arrangeElasticTimeline(
            items = items,
            axisStart = axisStart,
            axisEnd = axisEnd,
            trueY = ::trueY,
            itemGap = itemGap,
            labelSpacing = labelSpacing,
            blockInset = blockInset,
            bottomPadding = 0f,
        )
        return timelineScrollableExtent(layout.requiredHeight, trueY(axisEnd), denseContentTail)
    }

    val fits = heightAt(absoluteMinZoom) <= viewportHeight
    val fitZoom = if (!fits) absoluteMinZoom else if (heightAt(1f) <= viewportHeight) 1f
    else {
        var low = absoluteMinZoom
        var high = 1f
        repeat(18) {
            val mid = (low + high) / 2f
            if (heightAt(mid) <= viewportHeight) low = mid else high = mid
        }
        low
    }
    val autoFitZoom = autoFitTimelineZoom(fitZoom)
    val largestExtraHeight = items.maxOfOrNull { item ->
        ((fullDetailHeights[item.value] ?: item.contentHeight) - item.contentHeight).coerceAtLeast(0f)
    } ?: 0f
    // The reveal endpoint is independent of the current zoom/progress. Even an item with no
    // metadata gets a short, bounded interval for inspecting its duration cue.
    val revealSpan = 0.35f + (largestExtraHeight / (viewportHeight * 0.35f))
        .coerceIn(0f, 1.25f)
    val maxZoom = (autoFitZoom + revealSpan).coerceAtMost(productHardCap)
    return ElasticTimelineZoomBounds(autoFitZoom, maxZoom.coerceAtLeast(autoFitZoom), fits)
}
