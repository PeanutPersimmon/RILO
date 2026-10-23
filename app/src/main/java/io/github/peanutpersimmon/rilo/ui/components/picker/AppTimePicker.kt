package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalTime

@Composable
fun AppTimePicker(initialTime: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    var selected by remember(initialTime) { mutableStateOf(initialTime.withSecond(0).withNano(0)) }
    AppBottomSheet(title = null, onDismissRequest = onDismiss,
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Special) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PickerTextAction("取消", onDismiss)
                PickerTextAction("确定", { onConfirm(selected) })
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                WheelPicker(
                    values = PickerLogic.hours.toList(), selected = selected.hour,
                    onSelected = { selected = selected.withHour(it) }, label = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                )
                WheelPicker(
                    values = PickerLogic.minutes.toList(), selected = selected.minute,
                    onSelected = { selected = selected.withMinute(it) }, label = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
