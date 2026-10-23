package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

internal enum class HorizontalSwipeDirection(val step: Int) {
    LEFT(1),
    RIGHT(-1),
}

internal fun detectedHorizontalSwipe(
    horizontalDistance: Float,
    verticalDistance: Float,
    thresholdPx: Float,
    multiplePointers: Boolean,
): HorizontalSwipeDirection? {
    if (multiplePointers || abs(horizontalDistance) < thresholdPx) return null
    if (abs(horizontalDistance) <= abs(verticalDistance) * 1.25f) return null
    return if (horizontalDistance < 0f) {
        HorizontalSwipeDirection.LEFT
    } else {
        HorizontalSwipeDirection.RIGHT
    }
}

internal fun <T> adjacentAfterSwipe(
    items: List<T>,
    current: T,
    direction: HorizontalSwipeDirection,
): T {
    val currentIndex = items.indexOf(current)
    if (currentIndex < 0) return current
    val targetIndex = currentIndex + direction.step
    return items.getOrNull(targetIndex) ?: current
}

/**
 * Detects one completed horizontal swipe without consuming pointer changes or moving content.
 * Multi-touch is ignored so feature-specific pinch handling remains authoritative.
 */
internal fun Modifier.discreteHorizontalSwipe(
    onSwipe: (HorizontalSwipeDirection) -> Unit,
): Modifier = pointerInput(onSwipe) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var previous = down.position
        var horizontal = 0f
        var vertical = 0f
        var multiplePointers = false
        var event = awaitPointerEvent(PointerEventPass.Initial)
        do {
            if (event.changes.count { it.pressed } > 1) multiplePointers = true
            event.changes.firstOrNull { it.id == down.id }?.let { change ->
                val delta = change.position - previous
                horizontal += delta.x
                vertical += delta.y
                previous = change.position
            }
            if (event.changes.any { it.pressed }) {
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
        } while (event.changes.any { it.pressed })

        val threshold = max(viewConfiguration.touchSlop * 2f, 48.dp.toPx())
        detectedHorizontalSwipe(horizontal, vertical, threshold, multiplePointers)?.let(onSwipe)
    }
}
