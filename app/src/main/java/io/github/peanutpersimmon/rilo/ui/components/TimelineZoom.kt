package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** A single continuous vertical scale shared by Today and Plan. */
internal fun autoFitTimelineZoom(computedFitZoom: Float): Float = computedFitZoom.coerceAtMost(1f)

@Stable
class TimelineZoomState(
    initialZoom: Float = 1f,
    initialMaxZoom: Float = 4f,
) {
    var minZoom by mutableFloatStateOf(1f)
        private set
    var maxZoom by mutableFloatStateOf(maxOf(1f, initialMaxZoom))
        private set
    var scale by mutableFloatStateOf(initialZoom.coerceIn(1f, maxZoom))
        private set
    val progress: Float get() = adaptiveTimelineDetailProgress(scale, minZoom, maxZoom)
    private var adaptiveBoundsInitialized = false
    private var autoFitFloor: Float? = null
    private var userAdjusted = false

    /** Used by the elastic views only; Day and Week retain the original 1x policy. */
    fun updateAdaptiveBounds(min: Float, max: Float, initialDetail: Boolean = false) {
        val computedFit = autoFitTimelineZoom(min.takeIf { it.isFinite() && it > 0f } ?: 1f)
        val nextMin = minOf(autoFitFloor ?: computedFit, computedFit)
        autoFitFloor = nextMin
        val computedMax = max.takeIf { it.isFinite() }?.coerceAtLeast(nextMin) ?: nextMin
        val nextMax = if (userAdjusted) maxOf(computedMax, scale) else computedMax
        minZoom = nextMin
        maxZoom = nextMax
        scale = if (adaptiveBoundsInitialized) {
            if (userAdjusted) scale.coerceIn(nextMin, nextMax) else nextMin
        } else if (initialDetail) nextMin + (nextMax - nextMin) * 0.7f else nextMin
        if (!adaptiveBoundsInitialized && initialDetail) userAdjusted = true
        adaptiveBoundsInitialized = true
    }

    fun updateMaxZoom(value: Float) {
        maxZoom = value.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
        scale = scale.coerceIn(minZoom, maxZoom)
    }

    fun zoomBy(factor: Float): Float {
        if (!factor.isFinite() || factor <= 0f) return scale
        val next = boundedAdaptiveTimelineScale(scale, factor, minZoom, maxZoom)
        if (next != scale) userAdjusted = true
        scale = next
        return scale
    }
}

/** Two fingers zoom; an optional shared [scroll] keeps the focal time stationary. */
fun Modifier.timelinePinchZoom(
    zoom: TimelineZoomState,
    scroll: ScrollState? = null,
    onGesture: (Boolean) -> Unit = {},
): Modifier = pointerInput(zoom, scroll) {
    try {
        awaitEachGesture {
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    if (!pinching) {
                        pinching = true
                        onGesture(true)
                    }
                    val before = zoom.scale
                    val focalY = pressed.map { it.position.y }.average().toFloat()
                    val after = zoom.zoomBy(event.calculateZoom())

                    if (scroll != null && after != before) {
                        val desired = (scroll.value + focalY) * after / before - focalY
                        scroll.dispatchRawDelta(desired - scroll.value)
                        if (after == zoom.minZoom) {
                            scroll.dispatchRawDelta(-scroll.value.toFloat())
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (pinching) onGesture(false)
        }
    } finally {
        onGesture(false)
    }
}

@Composable
private fun timelineDetailRowHeight(textStyle: TextStyle): Dp {
    val lineHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() }
    return lineHeight + AppTheme.dimens.spaceMicro
}

/** Existing zoom reveal for compact views that do not expose their measured block height. */
@Composable
fun TimelineDetailReveal(
    lines: List<String>,
    zoom: Float,
    maxZoom: Float = 4f,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppTheme.type.caption,
    textColor: Color? = null,
) {
    if (lines.isEmpty()) return
    val progress = timelineDetailProgress(zoom, maxZoom)
    val rowHeight = timelineDetailRowHeight(textStyle)
    Box(
        modifier
            .fillMaxWidth()
            .height(rowHeight * (lines.size * progress))
            .clipToBounds()
            .graphicsLayer { alpha = progress },
    ) {
        Column {
            lines.forEach { line ->
                TimelineDetailLine(line, rowHeight, textStyle,
                    textColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Continuously reveals detail rows within a time block's currently available height. */
@Composable
fun TimelineDetailsWithinHeight(
    lines: List<String>,
    availableHeight: Dp,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppTheme.type.timelineMetadata,
    revealProgress: Float = 1f,
    textColor: Color? = null,
) {
    if (lines.isEmpty()) return
    val progress = revealProgress.coerceIn(0f, 1f)
    val visible = lines.filter(String::isNotBlank)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
        val spacing = AppTheme.dimens.spaceMicro
        val fullContentHeight = with(density) {
            (visible.sumOf { line ->
                measurer.measure(line, textStyle,
                    constraints = Constraints(maxWidth = widthPx), softWrap = true,
                ).size.height.toDouble()
            }.toFloat() + (visible.size - 1).coerceAtLeast(0) * spacing.toPx()).toDp()
        }
        val revealedHeight = minOf(availableHeight, fullContentHeight * progress)
        if (revealedHeight > Dp.Hairline) Box(
            Modifier.fillMaxWidth().height(revealedHeight).clipToBounds()
                .graphicsLayer { alpha = progress },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                visible.forEach { line ->
                    Text(line, style = textStyle,
                        color = textColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TimelineDetailLine(
    line: String,
    rowHeight: Dp,
    textStyle: TextStyle,
    textColor: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            line,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
