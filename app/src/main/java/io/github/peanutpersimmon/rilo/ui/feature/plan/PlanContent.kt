package io.github.peanutpersimmon.rilo.ui.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.presentation.plan.MonthContent
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.CategoryColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal fun Event.appearsOn(date: LocalDate): Boolean = date.toEpochDay() == dateEpochDay

internal fun monthRecordsOnDate(records: List<Session>, date: LocalDate, zone: ZoneId): List<Session> {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return records.filter { record ->
        when (record.temporalType) {
            SessionTemporalType.POINT -> record.startAtMillis >= start && record.startAtMillis < end
            SessionTemporalType.INTERVAL -> record.endAtMillis?.let { it > start && record.startAtMillis < end } == true
        }
    }
}

internal fun monthRecordsByDate(
    records: List<Session>,
    firstDate: LocalDate,
    lastDate: LocalDate,
    zone: ZoneId,
): Map<LocalDate, List<Session>> {
    val indexed = linkedMapOf<LocalDate, MutableList<Session>>()
    records.forEach { record ->
        val rawFirst = java.time.Instant.ofEpochMilli(record.startAtMillis)
            .atZone(zone).toLocalDate()
        val rawLast = when (record.temporalType) {
            SessionTemporalType.POINT -> rawFirst
            SessionTemporalType.INTERVAL -> record.endAtMillis?.let { end ->
                java.time.Instant.ofEpochMilli(end - 1L).atZone(zone).toLocalDate()
            } ?: rawFirst
        }
        val recordFirst = rawFirst.coerceAtLeast(firstDate)
        val recordLast = rawLast.coerceAtMost(lastDate)
        if (recordFirst <= recordLast) {
            var date = recordFirst
            while (!date.isAfter(recordLast)) {
                indexed.getOrPut(date) { mutableListOf() }.add(record)
                date = date.plusDays(1)
            }
        }
    }
    return indexed
}

internal data class MonthSummaryItem(
    val title: AnnotatedString,
    val isEvent: Boolean = false,
    val pointRecordId: Long? = null,
    val categoryId: Long? = null,
)

internal fun monthSummaryItems(state: PlanUiState, date: LocalDate, zone: ZoneId): List<MonthSummaryItem> =
    if (state.monthContent == MonthContent.RECORD) {
        monthRecordsOnDate(state.records, date, zone).map { record ->
            MonthSummaryItem(
                title = AnnotatedString(record.title),
                pointRecordId = record.id.takeIf { record.temporalType == SessionTemporalType.POINT },
                categoryId = record.categoryId,
            )
        }
    } else {
        state.events.filter { it.appearsOn(date) }.map { event ->
            MonthSummaryItem(AnnotatedString(event.title), isEvent = true, categoryId = event.categoryId)
        }
    }

private fun minuteLabel(minute: Int?): String =
    minute?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()

@Composable
internal fun PlanBoard(
    state: PlanUiState,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = listOf<LocalDate?>(null) + (0L..6L).map(monday::plusDays)
    val eventsByDate = remember(state.events) { state.events.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) } }
    BoxWithConstraints(modifier.padding(horizontal = 16.dp)) {
        val rowHeight = (maxHeight - 1.5.dp) / 4
        val tableShape = RoundedCornerShape(10.dp)
        Column(Modifier.fillMaxSize().clip(tableShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, tableShape)) {
            dates.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    row.forEachIndexed { columnIndex, date ->
                        // Event dates are required. The No Date cell remains empty for the 2×4 layout.
                        val events = if (date == null) emptyList()
                        else eventsByDate[date].orEmpty()
                        Column(Modifier.weight(1f).fillMaxHeight()
                            .padding(start = 7.dp, end = 6.dp, top = 4.dp, bottom = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (date == null) "No Date" else
                                    date.dayOfWeek.getDisplayName(
                                        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                                    fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (date != null) Text(" · ${date.monthValue}/${date.dayOfMonth}",
                                    fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Normal,
                                    color = if (date == state.selectedDate)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(2.dp))
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                events.forEach { event ->
                                    BoardItem(minuteLabel(event.startMinuteOfDay), AnnotatedString(event.title),
                                        true, planCategoryColors(event.categoryId, state.categoryColorKeys,
                                            MaterialTheme.colorScheme)) { onEventClick(event.id) }
                                }
                            }
                        }
                        if (columnIndex == 0) Box(Modifier.width(0.5.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                if (rowIndex < 3) Box(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun BoardItem(time: String, title: AnnotatedString, event: Boolean,
                      categoryColors: CategoryColors? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .then(if (categoryColors != null) Modifier.background(categoryColors.container,
            RoundedCornerShape(4.dp)) else Modifier)
        .clickable(onClick = onClick).padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
        if (time.isNotBlank()) Text(time, fontSize = 9.sp, lineHeight = 11.sp,
            color = categoryColors?.secondaryContent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
        Text(title, Modifier.weight(1f), fontSize = 10.sp, lineHeight = 11.sp,
            fontWeight = FontWeight.Normal,
            color = categoryColors?.content ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private object MonthTertiaryCompact {
    val height = 20.dp
    val horizontalPadding = 6.dp
    val gap = 2.dp
    val textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
}

internal object MonthGridGeometry {
    data class Layout(val cardHeight: Dp, val weekHeight: Dp, val elasticSpace: Dp)

    val modeToCalendarGap = 4.dp
    val gridPadding = 4.dp
    val weekdayHeight = 22.dp
    val minWeekHeight = 68.dp
    val maxWeekHeight = 116.dp
    val dateHeight = 20.dp
    val dateToSummaryGap = 2.dp
    val summaryHeight = 16.dp
    val summaryGap = 1.dp

    /** "+N" line: the 11sp text lineHeight plus a small buffer, so the digits are never
     *  cut by the cell's clip or crowded by the summary pill above. */
    val overflowHeight = 12.dp

    fun weekHeight(availableHeight: Dp, rowCount: Int): Dp {
        require(rowCount in 4..6)
        val gridChrome = weekdayHeight + gridPadding * 2
        return ((availableHeight - gridChrome) / rowCount)
            .coerceIn(minWeekHeight, maxWeekHeight)
    }

    fun layout(availableHeight: Dp, rowCount: Int): Layout {
        val weekHeight = weekHeight(availableHeight, rowCount)
        val occupied = weekdayHeight + gridPadding * 2 + weekHeight * rowCount
        return Layout(
            cardHeight = availableHeight,
            weekHeight = weekHeight,
            elasticSpace = (availableHeight - occupied).coerceAtLeast(0.dp),
        )
    }

    /** Maximum summary pills that fit inside one week cell, keeping room for the "+N"
     *  overflow line so it is never squeezed against the cell's clipped edge. */
    fun summaryLimit(weekHeight: Dp): Int {
        val available = weekHeight - dateHeight - dateToSummaryGap - overflowHeight
        val slot = summaryHeight + summaryGap
        return maxOf(1, ((available + summaryGap) / slot).toInt())
    }
}

@Composable
internal fun PlanMonth(
    state: PlanUiState,
    onContent: (MonthContent) -> Unit,
    onDate: (LocalDate) -> Unit,
    onPointRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = state.selectedDate.withDayOfMonth(1)
    var expandedRecordDate by remember(month, state.monthContent) { mutableStateOf<LocalDate?>(null) }
    val leading = month.dayOfWeek.value - 1
    val dates = List<LocalDate?>(leading) { null } +
            (1..month.lengthOfMonth()).map(month::withDayOfMonth)
    val rows = dates.chunked(7)
    val lastDate = month.withDayOfMonth(month.lengthOfMonth())
    val eventsByDate = remember(state.events) {
        state.events.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
    }
    val recordsByDate = remember(state.records, month, state.zoneId) {
        monthRecordsByDate(state.records, month, lastDate, state.zoneId)
    }

    Box(modifier.padding(horizontal = 8.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MonthTertiaryCompact.gap,
                    Alignment.CenterHorizontally,
                ),
            ) {
                MonthModeAction("计划", state.monthContent == MonthContent.PLAN) {
                    onContent(MonthContent.PLAN)
                }
                MonthModeAction("记录", state.monthContent == MonthContent.RECORD) {
                    onContent(MonthContent.RECORD)
                }
            }
            Spacer(Modifier.height(MonthGridGeometry.modeToCalendarGap))
            // The white card owns the full remaining viewport. Week rows retain the original
            // readable height bounds; surplus screen height stays as elastic space in the card.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gridLayout = MonthGridGeometry.layout(maxHeight, rows.size)
                val weekHeight = gridLayout.weekHeight
                val summaryLimit = MonthGridGeometry.summaryLimit(weekHeight)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(MonthGridGeometry.gridPadding),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(MonthGridGeometry.weekdayHeight),
                    ) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            MonthColumn {
                                Text(
                                    label,
                                    Modifier.align(Alignment.Center),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    rows.forEach { week ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(weekHeight),
                        ) {
                            (week + List(7 - week.size) { null }).forEach { date ->
                                MonthColumn {
                                    if (date != null) {
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .clipToBounds()
                                                .clickable { onDate(date) }
                                                .padding(horizontal = 2.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(MonthGridGeometry.dateHeight),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    date.dayOfMonth.toString(),
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = if (date == state.selectedDate) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                )
                                            }
                                            Spacer(Modifier.height(MonthGridGeometry.dateToSummaryGap))
                                            val items = if (state.monthContent == MonthContent.RECORD) {
                                                recordsByDate[date].orEmpty().map { record ->
                                                    MonthSummaryItem(
                                                        title = AnnotatedString(record.title),
                                                        pointRecordId = record.id.takeIf {
                                                            record.temporalType == SessionTemporalType.POINT
                                                        },
                                                        categoryId = record.categoryId,
                                                    )
                                                }
                                            } else {
                                                eventsByDate[date].orEmpty().map { event ->
                                                    MonthSummaryItem(
                                                        AnnotatedString(event.title),
                                                        isEvent = true,
                                                        categoryId = event.categoryId,
                                                    )
                                                }
                                            }
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                            ) {
                                                items.take(summaryLimit).forEach { item ->
                                                    MonthPill(item.title, item.isEvent, item.categoryId,
                                                        state.categoryColorKeys,
                                                        item.pointRecordId?.let { id -> { onPointRecordClick(id) } })
                                                }
                                                if (items.size > summaryLimit) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(MonthGridGeometry.overflowHeight)
                                                            .then(if (state.monthContent == MonthContent.RECORD)
                                                                Modifier.clickable { expandedRecordDate = date }
                                                            else Modifier),
                                                        contentAlignment = Alignment.CenterStart,
                                                    ) {
                                                        Text(
                                                            "+" + (items.size - summaryLimit),
                                                            fontSize = 9.sp,
                                                            lineHeight = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    expandedRecordDate?.let { expandedDate ->
        val records = recordsByDate[expandedDate].orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expandedRecordDate = null },
            title = { Text("${expandedDate.monthValue}月${expandedDate.dayOfMonth}日记录") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    records.forEach { record ->
                        MonthPill(AnnotatedString(record.title), false, record.categoryId,
                            state.categoryColorKeys) {
                            expandedRecordDate = null
                            if (record.temporalType == SessionTemporalType.POINT) onPointRecordClick(record.id)
                            else onDate(expandedDate)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { expandedRecordDate = null }) {
                    Text("关闭")
                }
            },
        )
    }
}
@Composable
private fun RowScope.MonthColumn(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight(), content = content)
}

@Composable
private fun MonthPill(title: AnnotatedString, isEvent: Boolean,
                      categoryId: Long?, categoryColorKeys: Map<Long, String>, onClick: (() -> Unit)? = null) {
    val colors = planCategoryColors(categoryId, categoryColorKeys, MaterialTheme.colorScheme)
    Box(Modifier.fillMaxWidth().height(MonthGridGeometry.summaryHeight)
        .background(colors?.medium ?: MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(5.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors?.mediumContent ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MonthModeAction(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(MonthTertiaryCompact.height)
        .background(
            if (selected) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.Transparent,
            AppTheme.shapes.pill,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = MonthTertiaryCompact.horizontalPadding),
        contentAlignment = Alignment.Center) {
        Text(label, style = MonthTertiaryCompact.textStyle,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

