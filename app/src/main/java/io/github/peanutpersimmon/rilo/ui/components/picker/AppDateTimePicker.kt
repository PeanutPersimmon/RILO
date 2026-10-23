package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.components.AppClickableSurface
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

data class DateTimePickerValue(val date: LocalDate, val time: LocalTime?)

@Composable
fun AppDateTimePicker(
    initialDate: LocalDate,
    initialTime: LocalTime?,
    timeOptional: Boolean,
    maxDateTime: LocalDateTime? = null,
    onDismiss: () -> Unit,
    onConfirm: (DateTimePickerValue) -> Unit,
) {
    val maximum = maxDateTime?.withSecond(0)?.withNano(0)
    val initialAllowedDate = maximum?.toLocalDate()?.let { maxDate -> minOf(initialDate, maxDate) } ?: initialDate
    val initialAllowedTime = initialTime?.let { time ->
        maximum?.takeIf { initialAllowedDate == it.toLocalDate() }?.toLocalTime()?.let { minOf(time, it) } ?: time
    }
    var selectedDate by remember(initialDate, maximum) { mutableStateOf(initialAllowedDate) }
    var month by remember(initialDate, maximum) { mutableStateOf(YearMonth.from(initialAllowedDate)) }
    var selectedTime by remember(initialTime, maximum) { mutableStateOf(initialAllowedTime) }
    val maximumMonth = maximum?.let { YearMonth.from(it) }
    val maximumTime = maximum?.takeIf { selectedDate == it.toLocalDate() }?.toLocalTime()
    AppBottomSheet(null, onDismiss,
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Special) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PickerTextAction("取消", onDismiss)
            if (timeOptional) PickerTextAction(if (selectedTime == null) "添加时间" else "仅日期", onClick = {
                selectedTime = if (selectedTime == null) {
                    val current = LocalTime.now().withSecond(0).withNano(0)
                    maximumTime?.let { minOf(current, it) } ?: current
                } else null
            })
            PickerTextAction("确定", { onConfirm(DateTimePickerValue(selectedDate, selectedTime)) })
        }
        MonthHeader(
            month,
            previous = { month = month.minusMonths(1) },
            next = { month = month.plusMonths(1) },
            nextEnabled = maximumMonth == null || month < maximumMonth,
        )
        CalendarGrid(month, selectedDate, maximum?.toLocalDate()) { date ->
            selectedDate = date
            maximum?.takeIf { date == it.toLocalDate() }?.toLocalTime()?.let { maxTime ->
                selectedTime = selectedTime?.let { minOf(it, maxTime) }
            }
        }
        selectedTime?.let { time ->
            val hours = PickerLogic.hours.filter { maximumTime == null || it <= maximumTime.hour }
            val minutes = PickerLogic.minutes.filter {
                maximumTime == null || time.hour < maximumTime.hour || it <= maximumTime.minute
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                WheelPicker(hours, time.hour, { hour ->
                    val changed = time.withHour(hour)
                    selectedTime = maximumTime?.takeIf { hour == it.hour }?.let { maxTime ->
                        changed.withMinute(minOf(changed.minute, maxTime.minute))
                    } ?: changed
                }, { "%02d".format(it) }, Modifier.weight(1f))
                WheelPicker(minutes, time.minute.coerceAtMost(minutes.last()),
                    { selectedTime = time.withMinute(it) }, { "%02d".format(it) }, Modifier.weight(1f))
            }
        }
        }
    }
}

@Composable
internal fun PickerTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(min = AppTheme.dimens.minimumTouchTarget)
            .height(AppTheme.dimens.minimumTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.itemTitle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, previous: () -> Unit, next: () -> Unit, nextEnabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppClickableSurface(previous, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月")
        }
        Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.Center, style = AppTheme.type.itemTitle)
        AppClickableSurface(next, enabled = nextEnabled, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月",
                tint = if (nextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, selected: LocalDate, maxDate: LocalDate?, onSelected: (LocalDate) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    Box(Modifier.weight(1f).height(AppTheme.dimens.calendarRowHeight), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val enabled = maxDate == null || date <= maxDate
                            AppClickableSurface(
                            onClick = { onSelected(date) },
                            enabled = enabled,
                            containerColor = if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = AppTheme.shapes.small,
                        ) {
                            Text(date.dayOfMonth.toString(), Modifier.padding(AppTheme.dimens.spaceS), style = AppTheme.type.caption,
                                color = when {
                                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    date == selected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                        }
                        }
                    }
                }
            }
        }
    }
}
