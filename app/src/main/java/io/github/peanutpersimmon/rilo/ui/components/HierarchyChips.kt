package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shared first-level chip row. The caller owns editing state and persistence. */
@Composable
fun <T> RootChipRow(
    items: List<T>,
    id: (T) -> Long,
    name: (T) -> String,
    selectedId: Long?,
    adding: Boolean,
    renamingId: Long?,
    errorMessage: String?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onCancelInput: () -> Unit,
    onDraftChange: () -> Unit,
    validateNew: (String) -> String?,
    validateRename: (T, String) -> String?,
    onReorder: (List<Long>) -> Unit,
) {
    val localItems = remember { mutableStateListOf<T>().apply { addAll(items) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentCancelInput by rememberUpdatedState(onCancelInput)
    val currentReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(items, errorMessage) {
        if (draggingId == null) {
            localItems.clear()
            localItems.addAll(items)
        }
    }
    LaunchedEffect(selectedId, items) {
        val index = items.indexOfFirst { id(it) == selectedId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.animateScrollToItem(index)
        }
    }

    fun cancelDrag() {
        draggingId = null
        dragOffset = 0f
        localItems.clear()
        localItems.addAll(items)
    }
    fun finishDrag() {
        val order = localItems.map(id)
        draggingId = null
        dragOffset = 0f
        if (order != currentItems.map(id)) currentReorder(order)
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
    ) {
        items(localItems, key = { "root-${id(it)}" }) { item ->
            val itemId = id(item)
            if (renamingId == itemId) {
                InlineNameEditor(
                    initialValue = name(item),
                    placeholder = "输入名称",
                    onSubmit = { onRename(itemId, it) },
                    onCancel = onCancelInput,
                    onDraftChange = onDraftChange,
                    validate = { validateRename(item, it) },
                    errorMessage = errorMessage,
                    primary = true,
                )
            } else {
                Box(
                    Modifier.heightIn(min = AppTheme.dimens.sheetRowVisualHeight - AppTheme.dimens.spaceXs).pointerInput(itemId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                currentCancelInput()
                                draggingId = itemId
                                dragOffset = 0f
                            },
                            onDragCancel = ::cancelDrag,
                            onDragEnd = ::finishDrag,
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                val layout = listState.layoutInfo
                                val source = layout.visibleItemsInfo.firstOrNull {
                                    it.key == "root-$itemId"
                                } ?: return@detectDragGesturesAfterLongPress
                                val center = source.offset + source.size / 2f + dragOffset
                                val target = layout.visibleItemsInfo
                                    .filter { it.index in localItems.indices }
                                    .minByOrNull { abs(it.offset + it.size / 2f - center) }
                                if (target != null && target.index != source.index) {
                                    val sourceIndex = localItems.indexOfFirst { id(it) == itemId }
                                    if (sourceIndex >= 0) {
                                        val delta = target.offset - source.offset
                                        localItems.add(target.index, localItems.removeAt(sourceIndex))
                                        dragOffset -= delta
                                    }
                                }
                                when {
                                    center < layout.viewportStartOffset + 44f ->
                                        scope.launch { listState.scrollBy(-18f) }
                                    center > layout.viewportEndOffset - 44f ->
                                        scope.launch { listState.scrollBy(18f) }
                                }
                            },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.padding(vertical = AppTheme.dimens.spaceXs / 2)) {
                        CompactChip(
                            label = name(item),
                            onClick = { if (draggingId == null) onSelect(itemId) },
                            modifier = Modifier.graphicsLayer {
                                translationX = if (draggingId == itemId) dragOffset else 0f
                            },
                            selected = itemId == selectedId,
                            primary = true,
                        )
                    }
                }
            }
        }
        item(key = "add-root") {
            if (adding) InlineNameEditor(
                initialValue = "",
                placeholder = "输入名称",
                onSubmit = onCreate,
                onCancel = onCancelInput,
                onDraftChange = onDraftChange,
                validate = validateNew,
                errorMessage = errorMessage,
                primary = true,
            ) else Box(Modifier.heightIn(min = AppTheme.dimens.sheetRowVisualHeight - AppTheme.dimens.spaceXs), contentAlignment = Alignment.Center) {
                Box(Modifier.padding(vertical = AppTheme.dimens.spaceXs / 2)) {
                    CompactChip("+", onAdd, primary = true)
                }
            }
        }
    }
}

@Composable
fun CurrentRootItemRow(
    name: String,
    detail: String,
    onRename: () -> Unit,
    actionIcon: ImageVector? = null,
    actionDescription: String = "",
    onAction: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onColor: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = AppTheme.type.fieldLabel
    val detailStyle = AppTheme.type.caption
    val detailGap = with(LocalDensity.current) { AppTheme.dimens.spaceXs.roundToPx() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = AppTheme.dimens.spaceXl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(AppTheme.dimens.spaceS))
        }
        BoxWithConstraints(Modifier.weight(1f)
            .padding(vertical = AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2)) {
            val oneLine = textMeasurer.measure(AnnotatedString(name), style = labelStyle,
                maxLines = 1).size.width + detailGap +
                textMeasurer.measure(AnnotatedString(detail), style = detailStyle,
                    maxLines = 1).size.width <= constraints.maxWidth
            if (oneLine) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = labelStyle)
                Text(detail, Modifier.padding(start = AppTheme.dimens.spaceXs), style = detailStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column {
                Text(name, style = labelStyle)
                Text(detail, style = detailStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CompactActionIcon(Icons.Outlined.Edit, "重命名 $name", onRename)
            if (onColor != null) CompactActionIcon(Icons.Outlined.Palette, "修改 $name 的颜色", onColor)
            if (onDelete != null) CompactActionIcon(Icons.Outlined.Delete, "删除 $name", onDelete)
            if (actionIcon != null && onAction != null) {
                CompactActionIcon(actionIcon, "$actionDescription $name", onAction)
            }
        }
    }
}

@Composable
private fun CompactActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp, AppTheme.dimens.minimumTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class MetadataAction(val label: String, val onClick: () -> Unit)

@Composable
fun MetadataActionDialog(title: String, actions: List<MetadataAction>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = AppTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(title, Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                actions.forEach { action ->
                    Text(action.label,
                        Modifier.fillMaxWidth().clickable(onClick = action.onClick)
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        style = AppTheme.type.fieldValue)
                }
            }
        }
    }
}
