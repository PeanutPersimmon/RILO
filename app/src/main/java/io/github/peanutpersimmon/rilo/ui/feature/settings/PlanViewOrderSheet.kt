package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.ReorderableList
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** 计划视图顺序设置：首项即计划页冷启动默认视图，松手后一次持久化。 */
@Composable
fun PlanViewOrderSheet(viewModel: PlanViewModel, onDismiss: () -> Unit) {
    val order by viewModel.viewOrder.collectAsStateWithLifecycle()
    val d = AppTheme.dimens
    val listHeight = d.reorderRowTouchHeight * order.size +
        d.reorderRowGap * (order.size - 1).coerceAtLeast(0)
    AppBottomSheet("计划视图顺序", onDismiss,
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Special) {
        Text("首项为进入计划页时的默认视图", Modifier.padding(bottom = d.spaceXs),
            style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReorderableList(
            items = order,
            key = { it.name },
            onOrderCommitted = viewModel::setViewOrder,
            modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
            contentPadding = PaddingValues(),
            moveUpLabel = "上移视图",
            moveDownLabel = "下移视图",
        ) { mode, index, isDragging, dragHandleModifier ->
            PlanViewOrderRow(mode, index == 0, isDragging, dragHandleModifier)
        }
    }
}

@Composable
private fun PlanViewOrderRow(
    mode: PlanViewMode,
    isDefault: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
) {
    val d = AppTheme.dimens
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(d.reorderRowTouchHeight)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(d.reorderRowHeight)
                .align(Alignment.Center),
            shape = AppTheme.shapes.small,
            color = colors.surfaceContainerLow,
            border = if (isDragging) BorderStroke(d.border, colors.primary) else null,
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(d.minimumTouchTarget))
                Text(mode.label, Modifier.weight(1f), style = AppTheme.type.fieldLabel)
                if (isDefault) {
                    Text(
                        "默认",
                        style = AppTheme.type.caption,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = d.spaceS),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(d.minimumTouchTarget)
                .align(Alignment.CenterStart)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier.size(d.reorderHandleIconSize),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
