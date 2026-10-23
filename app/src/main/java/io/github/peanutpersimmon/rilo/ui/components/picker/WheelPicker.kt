package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private const val VirtualWheelItemCount = Int.MAX_VALUE - 2

@Composable
fun <T> WheelPicker(
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    if (values.isEmpty()) return
    key(values) {
        WheelPickerContent(values, selected, onSelected, label, modifier, textStyle)
    }
}

@Composable
private fun <T> WheelPickerContent(
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier,
    textStyle: TextStyle?,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val initialCenterIndex = remember(values, selectedIndex) {
        val middle = VirtualWheelItemCount / 2
        middle - Math.floorMod(middle, values.size) + selectedIndex
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialCenterIndex - 1)
    val fling = rememberSnapFlingBehavior(state)
    var centerIndex by remember { mutableIntStateOf(initialCenterIndex) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelected by rememberUpdatedState(onSelected)

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { centerIndex = it + 1 }
    }
    LaunchedEffect(state, values) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .filter { (isScrolling, _) -> !isScrolling }
            .map { (_, firstVisible) -> values[Math.floorMod(firstVisible + 1, values.size)] }
            .distinctUntilChanged()
            .collect { value ->
                if (value != currentSelected) currentOnSelected(value)
            }
        }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = modifier.height(AppTheme.dimens.pickerHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(VirtualWheelItemCount) { index ->
            val value = values[Math.floorMod(index, values.size)]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppTheme.dimens.pickerItemHeight)
                    .semantics { this.selected = index == centerIndex },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    style = textStyle ?: if (index == centerIndex) AppTheme.type.itemTitle else AppTheme.type.body,
                    color = if (index == centerIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
