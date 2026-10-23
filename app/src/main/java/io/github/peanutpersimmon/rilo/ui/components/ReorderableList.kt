package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Handle-driven reorder list. Crossing rows only changes the local snapshot; a drag end
 * commits once, while cancellation restores the snapshot captured at drag start.
 */
@Composable
fun <T, K : Any> ReorderableList(
    items: List<T>,
    key: (T) -> K,
    onOrderCommitted: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    syncSignal: Any? = null,
    moveUpLabel: String? = null,
    moveDownLabel: String? = null,
    footer: (@Composable () -> Unit)? = null,
    row: @Composable (
        item: T,
        index: Int,
        isDragging: Boolean,
        dragHandleModifier: Modifier,
    ) -> Unit,
) {
    val d = AppTheme.dimens
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { d.reorderAutoScrollEdge.toPx() }
    val autoScrollStepPx = with(density) { d.reorderAutoScrollStep.toPx() }
    val localItems = remember { mutableStateListOf<T>().apply { addAll(items) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingKey by remember { mutableStateOf<K?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragStartItems by remember { mutableStateOf<List<T>?>(null) }

    fun replaceLocal(values: List<T>) {
        localItems.clear()
        localItems.addAll(values)
    }

    LaunchedEffect(items, syncSignal) {
        if (draggingKey == null) replaceLocal(items)
    }

    fun clearDrag() {
        draggingKey = null
        dragOffset = 0f
        dragStartItems = null
    }

    fun cancelDrag() {
        replaceLocal(dragStartItems ?: items)
        clearDrag()
    }

    fun finishDrag() {
        val reordered = localItems.toList()
        val changed = reordered.map(key) != items.map(key)
        clearDrag()
        if (changed) onOrderCommitted(reordered)
    }

    fun moveAndCommit(itemKey: K, offset: Int): Boolean {
        val source = localItems.indexOfFirst { key(it) == itemKey }
        val target = source + offset
        if (source !in localItems.indices || target !in localItems.indices) return false
        localItems.add(target, localItems.removeAt(source))
        onOrderCommitted(localItems.toList())
        return true
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(d.reorderRowGap),
    ) {
        itemsIndexed(
            items = localItems,
            key = { _, item -> key(item) },
        ) { index, item ->
            val itemKey = key(item)
            val isDragging = draggingKey == itemKey
            val actions = buildList {
                if (index > 0 && moveUpLabel != null) {
                    add(CustomAccessibilityAction(moveUpLabel) {
                        moveAndCommit(itemKey, -1)
                    })
                }
                if (index < localItems.lastIndex && moveDownLabel != null) {
                    add(CustomAccessibilityAction(moveDownLabel) {
                        moveAndCommit(itemKey, 1)
                    })
                }
            }
            val accessibilityModifier = if (actions.isEmpty()) Modifier else {
                Modifier.semantics { customActions = actions }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        alpha = if (isDragging) 0.9f else 1f
                    }
                    .then(accessibilityModifier)
            ) {
                row(
                    item,
                    index,
                    isDragging,
                    Modifier.pointerInput(itemKey, autoScrollEdgePx, autoScrollStepPx) {
                        detectDragGestures(
                            onDragStart = {
                                dragStartItems = localItems.toList()
                                draggingKey = itemKey
                                dragOffset = 0f
                            },
                            onDragCancel = ::cancelDrag,
                            onDragEnd = ::finishDrag,
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val layout = listState.layoutInfo
                                val source = layout.visibleItemsInfo.firstOrNull {
                                    it.key == itemKey
                                } ?: return@detectDragGestures
                                val draggedCenter = source.offset + source.size / 2f + dragOffset
                                val target = layout.visibleItemsInfo
                                    .filter { it.index in localItems.indices }
                                    .minByOrNull {
                                        abs(it.offset + it.size / 2f - draggedCenter)
                                    }
                                if (target != null && target.index != source.index) {
                                    val sourceIndex = localItems.indexOfFirst { key(it) == itemKey }
                                    if (sourceIndex >= 0) {
                                        val layoutDelta = target.offset - source.offset
                                        localItems.add(
                                            target.index,
                                            localItems.removeAt(sourceIndex),
                                        )
                                        dragOffset -= layoutDelta
                                    }
                                }
                                when {
                                    draggedCenter < layout.viewportStartOffset + autoScrollEdgePx ->
                                        scope.launch { listState.scrollBy(-autoScrollStepPx) }
                                    draggedCenter > layout.viewportEndOffset - autoScrollEdgePx ->
                                        scope.launch { listState.scrollBy(autoScrollStepPx) }
                                }
                            },
                        )
                    },
                )
            }
        }
        if (footer != null) {
            item { footer() }
        }
    }
}
