package io.github.peanutpersimmon.rilo.ui.components.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarSelectionMode { DAY, WEEK, MONTH, YEAR }

/** The plan and statistics pages share this one calendar and year/month-wheel dialog. */
@Composable
fun AppCalendarDialog(initialDate: LocalDate, mode: CalendarSelectionMode,
    onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit,
    confirmDayOnClick: Boolean = false) {
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var selected by remember(initialDate) { mutableStateOf(initialDate) }
    val pickerStyle = AppTheme.type.navigationSecondary
    val chosenDay = visibleMonth.atDay(selected.dayOfMonth.coerceAtMost(visibleMonth.lengthOfMonth()))
    val chosen = when (mode) {
        CalendarSelectionMode.DAY, CalendarSelectionMode.WEEK -> chosenDay
        CalendarSelectionMode.MONTH -> visibleMonth.atDay(1)
        CalendarSelectionMode.YEAR -> LocalDate.of(visibleMonth.year, 1, 1)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.90f).widthIn(max = 360.dp),
            shape = AppTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                        enabled = visibleMonth.year > PickerLogic.years.first || visibleMonth.monthValue > 1) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月")
                    }
                    Text(visibleMonth.year.toString() + " 年 " + visibleMonth.monthValue + " 月",
                        Modifier.weight(1f), textAlign = TextAlign.Center, style = pickerStyle)
                    IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                        enabled = visibleMonth.year < PickerLogic.years.last || visibleMonth.monthValue < 12) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = pickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val leading = visibleMonth.atDay(1).dayOfWeek.value - 1
                val cells = List<LocalDate?>(leading) { null } +
                    (1..visibleMonth.lengthOfMonth()).map(visibleMonth::atDay)
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        (week + List(7 - week.size) { null }).forEach { date ->
                            Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    Box(Modifier.size(30.dp)
                                        .background(if (date == chosenDay) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f), AppTheme.shapes.pill)
                                        .clickable {
                                            selected = date
                                            if (confirmDayOnClick && mode == CalendarSelectionMode.DAY) {
                                                onConfirm(date)
                                            }
                                        }, contentAlignment = Alignment.Center) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            style = pickerStyle,
                                            color = if (date == chosenDay) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth()) {
                    key(visibleMonth.year) {
                        WheelPicker(PickerLogic.years.toList(), visibleMonth.year,
                            { visibleMonth = YearMonth.of(it, visibleMonth.monthValue) },
                            { it.toString() + "年" }, Modifier.weight(1f), textStyle = pickerStyle)
                    }
                    key(visibleMonth.monthValue) {
                        WheelPicker(PickerLogic.months.toList(), visibleMonth.monthValue,
                            { visibleMonth = YearMonth.of(visibleMonth.year, it) },
                            { it.toString() + "月" }, Modifier.weight(1f), textStyle = pickerStyle)
                    }
                }
                Text(when (mode) {
                    CalendarSelectionMode.DAY, CalendarSelectionMode.WEEK ->
                        chosen.year.toString() + "年" + chosen.monthValue + "月" + chosen.dayOfMonth + "日"
                    CalendarSelectionMode.MONTH -> chosen.year.toString() + "年" + chosen.monthValue + "月"
                    CalendarSelectionMode.YEAR -> chosen.year.toString() + "年"
                }, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = pickerStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("今天", Modifier.clickable { onConfirm(LocalDate.now()) }.padding(AppTheme.dimens.spaceS),
                        style = pickerStyle, color = MaterialTheme.colorScheme.onSurface)
                    Text("✓", Modifier.clickable { onConfirm(chosen) }.padding(AppTheme.dimens.spaceS),
                        fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
