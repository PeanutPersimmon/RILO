package io.github.peanutpersimmon.rilo.ui.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rilo.presentation.plan.isReviewInterval
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayPullState
import io.github.peanutpersimmon.rilo.ui.feature.today.PullSummarySurface
import io.github.peanutpersimmon.rilo.ui.feature.today.TimelineSummaryTypography
import io.github.peanutpersimmon.rilo.ui.components.*
import io.github.peanutpersimmon.rilo.ui.components.timelinePinchZoom
import io.github.peanutpersimmon.rilo.ui.components.TimelineDetailReveal
import io.github.peanutpersimmon.rilo.ui.theme.resolveSleepVisualColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

internal typealias PlanTimelineZoomState = TimelineZoomState
private val gutter = 26.dp
private val TimelineCardHorizontalInset = 2.dp
private val TimelineCompactTitle = androidx.compose.ui.text.TextStyle(
    fontSize = 10.sp,
    lineHeight = 13.sp,
)
private val TimelineCompactMetadata = androidx.compose.ui.text.TextStyle(
    fontSize = 9.sp,
    lineHeight = 12.sp,
)
private fun Modifier.timelineCardGeometry(): Modifier =
    padding(horizontal = TimelineCardHorizontalInset)
private fun dayStart(date: LocalDate, zoneId: ZoneId) =
    date.atStartOfDay(zoneId).toInstant().toEpochMilli()
private fun dayEnd(date: LocalDate, zoneId: ZoneId) = dayStart(date.plusDays(1), zoneId)
internal fun reviewIntervalOnDay(session: Session, date: LocalDate, zoneId: ZoneId): Pair<Long, Long>? {
    if (!isReviewInterval(session)) return null
    val start = maxOf(session.startAtMillis, date.atStartOfDay(zoneId).toInstant().toEpochMilli())
    val end = minOf(requireNotNull(session.endAtMillis),
        date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli())
    return (start to end).takeIf { start < end }
}
private fun Session.overlaps(date: LocalDate, zoneId: ZoneId): Boolean =
    reviewIntervalOnDay(this, date, zoneId) != null

internal fun reviewIntervalsByDate(
    records: List<Session>,
    dates: List<LocalDate>,
    zoneId: ZoneId,
): Map<LocalDate, List<Session>> {
    if (dates.isEmpty()) return emptyMap()
    val requested = dates.toHashSet()
    val indexed = linkedMapOf<LocalDate, MutableList<Session>>()
    records.forEach { session ->
        if (!isReviewInterval(session)) return@forEach
        var date = Instant.ofEpochMilli(session.startAtMillis).atZone(zoneId).toLocalDate()
        val last = Instant.ofEpochMilli(requireNotNull(session.endAtMillis) - 1L)
            .atZone(zoneId).toLocalDate()
        while (!date.isAfter(last)) {
            if (date in requested) indexed.getOrPut(date) { mutableListOf() }.add(session)
            date = date.plusDays(1)
        }
    }
    return indexed
}
private fun clockTime(millis: Long, zoneId: ZoneId): String =
    io.github.peanutpersimmon.rilo.domain.time.timelineClockLabel(millis, zoneId)
internal fun reviewTimeLabel(session: Session, date: LocalDate, zoneId: ZoneId): String? {
    val (start, end) = reviewIntervalOnDay(session, date, zoneId) ?: return null
    val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return "${clockTime(start, zoneId)}–${if (end == dayEnd) "24:00" else clockTime(end, zoneId)}"
}

internal fun dispatchRecordClick(
    session: Session,
    onRecordClick: (Long, SessionKind) -> Unit,
) {
    onRecordClick(session.id, session.kind)
}

internal fun planDayAxisHeight(availableHeight: Dp, zoom: Float): Dp =
    availableHeight.coerceAtLeast(1.dp) * zoom

internal fun planWeekAxisHeight(
    availableHeight: Dp,
    headerHeight: Dp,
    headerToGrid: Dp,
    zoom: Float,
): Dp = (availableHeight - headerHeight - headerToGrid).coerceAtLeast(1.dp) * zoom

internal fun todoDoneAxisHeight(
    viewportHeight: Float,
    topBoundary: Float,
    zoom: Float,
): Float = (viewportHeight - topBoundary).coerceAtLeast(1f) * zoom

@Composable
internal fun RecordDay(date: LocalDate, records: List<Session>, categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit,
    zoom: PlanTimelineZoomState, modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()) {
    val scroll = rememberScrollState()
    val ticks = remember(date, zoneId) { dayTimelineTicks(date, zoneId) }
    val labelWidth = if (ticks.any { it.label.length > 2 }) 76.dp else gutter
    BoxWithConstraints(modifier.padding(horizontal = 8.dp)) {
        val axisHeight = planDayAxisHeight(maxHeight, zoom.scale)
        Column(Modifier.fillMaxSize().timelinePinchZoom(zoom, scroll)
            .verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().height(axisHeight)) {
                DayHourLabels(ticks, axisHeight, Modifier.width(labelWidth))
                Box(Modifier.weight(1f).fillMaxHeight()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clipToBounds()) {
                    DayAxisGrid(ticks, axisHeight, Modifier.fillMaxWidth())
                    RecordColumn(date, axisHeight, records.filter { it.overlaps(date, zoneId) }, categoryColorKeys,
                        onRecordClick, false, zoom.scale, zoneId, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun RecordWeek(date: LocalDate, records: List<Session>, categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit,
    zoom: PlanTimelineZoomState, modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()) {
    val monday = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val dates = (0L..6L).map(monday::plusDays)
    val recordsByDate = remember(records, dates, zoneId) {
        reviewIntervalsByDate(records, dates, zoneId)
    }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.padding(horizontal = 8.dp)) {
        val density = LocalDensity.current
        val headerHeight = 26.dp * maxOf(1f, density.fontScale)
        val headerToGrid = AppTheme.dimens.spaceMicro

        val axisHeight = planWeekAxisHeight(maxHeight, headerHeight, headerToGrid, zoom.scale)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(headerHeight)) {
                Spacer(Modifier.width(gutter))
                Row(Modifier.weight(1f).fillMaxHeight()) {
                    dates.forEach { day ->
                        Column(Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1,
                                color = if (day == date) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(day.dayOfMonth.toString(), fontSize = 10.sp, lineHeight = 11.sp,
                                maxLines = 1, color = if (day == date) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(headerToGrid))
            Column(Modifier.fillMaxSize().timelinePinchZoom(zoom, scroll)
                .verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth().height(axisHeight)) {
                    HourLabels(axisHeight, Modifier.width(gutter))
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clipToBounds()) {
                        Row(Modifier.fillMaxSize()) {
                            dates.forEachIndexed { index, day ->
                                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                    if (index > 0) Box(
                                        Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = (-0.25).dp)
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                    AxisGrid(axisHeight, Modifier.fillMaxWidth())
                                    RecordColumn(day, axisHeight, recordsByDate[day].orEmpty(), categoryColorKeys,
                                        onRecordClick, true, zoom.scale, zoneId, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHourLabels(ticks: List<DayTimelineTick>, axisHeight: Dp, modifier: Modifier) {
    Box(modifier.height(axisHeight)) {
        ticks.forEach { tick ->
            Text(tick.label, Modifier.fillMaxWidth().padding(end = 3.dp)
                .offset(y = axisHeight * tick.fraction + 1.dp), textAlign = TextAlign.End,
                fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayAxisGrid(ticks: List<DayTimelineTick>, axisHeight: Dp, modifier: Modifier) {
    Box(modifier.height(axisHeight)) {
        ticks.drop(1).forEach { tick ->
            Box(Modifier.fillMaxWidth().offset(y = axisHeight * tick.fraction)
                .height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun HourLabels(axisHeight: Dp, modifier: Modifier = Modifier) {
    val hourHeight = axisHeight / 24
    Box(modifier.height(axisHeight)) {
        for (hour in 0..23) {
            Text("%02d".format(hour), Modifier.fillMaxWidth().padding(end = 3.dp)
                .offset(y = hourHeight * hour + 1.dp), textAlign = TextAlign.End,
                fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AxisGrid(axisHeight: Dp, modifier: Modifier = Modifier) {
    val hourHeight = axisHeight / 24
    Box(modifier.height(axisHeight)) {
        for (hour in 1..23) {
            Box(Modifier.fillMaxWidth().offset(y = hourHeight * hour)
                .height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun RecordColumn(date: LocalDate, axisHeight: Dp, records: List<Session>,
    categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit, compact: Boolean, zoom: Float,
    zoneId: ZoneId, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val start = dayStart(date, zoneId)
    val end = dayEnd(date, zoneId)
    val geometry = remember(start, end, axisHeight, density) {
        TimelineGeometry(start, end, with(density) { axisHeight.toPx() })
    }
    Box(modifier.height(axisHeight)) {
        records.forEach { session ->
            val colors = if (session.kind == SessionKind.ACTIVITY)
                planCategoryColors(session.categoryId, categoryColorKeys, MaterialTheme.colorScheme)
            else null
            val sleepColors = resolveSleepVisualColors(session.kind, MaterialTheme.colorScheme)
            val (visibleStart, visibleEnd) = reviewIntervalOnDay(session, date, zoneId) ?: return@forEach
            val position = geometry.position(visibleStart, visibleEnd)
                ?: return@forEach
            Column(Modifier.fillMaxWidth().offset(y = with(density) { position.topPx.toDp() })
                .height(with(density) { position.heightPx.toDp() })
                .timelineCardGeometry()
                .clip(RoundedCornerShape(8.dp))
                .background(sleepColors?.container ?: colors?.container ?: MaterialTheme.colorScheme.surfaceContainer)
                .clickable { dispatchRecordClick(session, onRecordClick) }
                .padding(horizontal = if (compact) 2.dp else 5.dp, vertical = 1.dp)) {
                Text(session.title, style = TimelineCompactTitle,
                    color = sleepColors?.content ?: colors?.content ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                    maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Text(requireNotNull(reviewTimeLabel(session, date, zoneId)), style = TimelineCompactMetadata,
                        maxLines = 1, color = sleepColors?.secondaryContent ?: colors?.secondaryContent
                            ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    TimelineDetailReveal(
                        listOfNotNull(session.location, session.note).filter { it.isNotBlank() },
                        zoom,
                        textStyle = TimelineCompactMetadata,
                        textColor = sleepColors?.secondaryContent ?: colors?.secondaryContent,
                    )
                } else {
                    TimelineDetailReveal(listOf(clockTime(visibleStart, zoneId)), zoom,
                        textStyle = TimelineCompactMetadata,
                        textColor = sleepColors?.secondaryContent ?: colors?.secondaryContent)
                }
            }
        }
    }
}

internal data class TodoItem(
    val title: String,
    val atMillis: Long?,
    val endAtMillis: Long?,
    val id: Long,
    val event: Boolean,
    val detail: String? = null,
    val note: String? = null,
    val categoryId: Long? = null,
)

internal val TodoItem.isTimedSpan: Boolean
    get() = atMillis != null && endAtMillis != null && endAtMillis > atMillis

internal fun todoDoneEventItem(event: Event, zoneId: ZoneId): TodoItem {
    val at = event.startMinuteOfDay?.let {
        epochDayMinuteToMillis(event.dateEpochDay, it, zoneId)
    }
    val end = event.endMinuteOfDay?.let {
        epochDayMinuteToMillis(event.endDateEpochDay ?: event.dateEpochDay, it, zoneId)
    }
    return TodoItem(event.title, at, end, event.id, true,
        detail = event.location, note = event.note, categoryId = event.categoryId)
}

internal fun todoDoneDetails(item: TodoItem): List<String> =
    listOfNotNull(item.detail, item.note).filter(String::isNotBlank)

internal fun todoDoneRecordDetails(session: Session): List<String> =
    listOfNotNull(session.location, session.note).filter(String::isNotBlank)

internal fun visibleTodoDoneRecords(
    records: List<Session>,
    axisStart: Long,
    axisEnd: Long,
): List<Session> = records.filter { session ->
    session.kind in setOf(SessionKind.ACTIVITY, SessionKind.NAP) && when (session.temporalType) {
        SessionTemporalType.POINT -> session.startAtMillis >= axisStart && session.startAtMillis < axisEnd
        SessionTemporalType.INTERVAL -> session.endAtMillis?.let { end ->
            end > axisStart && session.startAtMillis < axisEnd
        } == true
    }
}

internal sealed interface TodoDoneLaneValue {
    data class Todo(val item: TodoItem) : TodoDoneLaneValue
    data class Record(val session: Session) : TodoDoneLaneValue
}

internal fun todoDoneRecordInput(
    session: Session,
    axisStart: Long,
    axisEnd: Long,
    contentHeight: Float,
    pointAnchorLead: Float,
): ElasticTimelineItem<TodoDoneLaneValue>? {
    if (visibleTodoDoneRecords(listOf(session), axisStart, axisEnd).isEmpty()) return null
    val point = session.temporalType == SessionTemporalType.POINT
    return ElasticTimelineItem(
        value = TodoDoneLaneValue.Record(session),
        start = if (point) session.startAtMillis else session.startAtMillis.coerceIn(axisStart, axisEnd),
        end = if (point) null else requireNotNull(session.endAtMillis).coerceIn(axisStart, axisEnd),
        side = false,
        contentHeight = contentHeight,
        stableOrder = session.id,
        anchorLead = if (point) pointAnchorLead else 0f,
    )
}

@Composable
internal fun TodoDoneDay(
    state: PlanUiState,
    onEventClick: (Long) -> Unit,
    onRecordClick: (Long, SessionKind) -> Unit,
    zoom: PlanTimelineZoomState,
    modifier: Modifier = Modifier,
) {
    val date = state.selectedDate
    val zone = state.zoneId
    val todos = state.events.filter { it.dateEpochDay == date.toEpochDay() }
        .map { todoDoneEventItem(it, zone) }
    val displayWindow = requireNotNull(state.awakeDisplayWindow) {
        "TodoDone requires an AwakeDisplayWindow."
    }
    val axisStart = displayWindow.displayInterval.startMillis
    val axisEnd = displayWindow.displayInterval.endMillis
    val records = visibleTodoDoneRecords(state.records, axisStart, axisEnd)
    val untimed = todos.filter { it.atMillis == null }
    val scope = rememberCoroutineScope()
    val timelineScroll = rememberScrollState()

    BoxWithConstraints(modifier.padding(horizontal = AppTheme.dimens.pageHorizontal)) {
        val density = LocalDensity.current
        val maxReminderHeight = maxHeight * 0.35f
        val range = with(density) { maxReminderHeight.toPx() }
        val reminder = remember(date, range) { TodayPullState(range) }
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val topBoundary = timelineTopBoundary(untimed.isNotEmpty())
                val boundaryPx = with(density) { topBoundary.toPx() }
                val denseContentTailPx = with(density) {
                    AppTheme.dimens.timelineDenseContentTailSpace.toPx()
                }
                val geometry = remember(
                    axisStart,
                    axisEnd,
                    maxHeight,
                    zoom.scale,
                    density,
                    boundaryPx,
                ) {
                    TimelineGeometry(
                        axisStart,
                        axisEnd,
                        todoDoneAxisHeight(viewportHeightPx, boundaryPx, zoom.scale),
                    )
                }
                val boundary = topBoundary
                val axisX = maxWidth / 2
                val sideGap = AppTheme.dimens.timelineAxisGutter
                val sideWidth = (axisX - sideGap).coerceAtLeast(0.dp)
                val textMeasurer = rememberTextMeasurer()
                val contentMeasurer = rememberTimelineContentMeasurer()
                val fullItemWidth = (sideWidth - AppTheme.dimens.spaceXs).coerceAtLeast(0.dp)
                val endpointTimes = buildList {
                    add(axisStart)
                    add(axisEnd)
                    todos.forEach { item ->
                        item.atMillis?.let(::add)
                        item.endAtMillis?.let(::add)
                    }
                    records.forEach { record ->
                        add(record.startAtMillis)
                        record.endAtMillis?.let(::add)
                    }
                }.filter { it in axisStart..axisEnd }
                val timedBlockInsetPx = with(density) { AppTheme.dimens.spaceXs.toPx() }
                val boundaryOffsetPx = with(density) { boundary.toPx() }
                val labelSpacingPx = with(density) {
                    timelineTimeLabelHeight().toPx() + AppTheme.dimens.spaceXs.toPx()
                }
                val itemWidthPx = with(density) { fullItemWidth.toPx().toInt().coerceAtLeast(1) }
                val pointAnchorLead = maxOf(
                    textMeasurer.measure("Ag", AppTheme.type.timelineTitle, maxLines = 1).size.height,
                    textMeasurer.measure("2h", AppTheme.type.timelineDuration, maxLines = 1).size.height,
                ) / 2f
                val values: List<TodoDoneLaneValue> =
                    todos.map(TodoDoneLaneValue::Todo) +
                    records.map(TodoDoneLaneValue::Record)
                // Neither nowMillis nor zoom.scale is a measurement/search key.
                val measuredBoundsHeights = remember(date, state.events,
                    state.records, todos.map { it.id to it.detail }, records.map { it.id },
                    itemWidthPx, contentMeasurer, textMeasurer) {
                    values.map { value ->
                        val title: String
                        val duration: String?
                        val details: List<String>
                        val point: Boolean
                        when (value) {
                            is TodoDoneLaneValue.Todo -> {
                                val item = value.item
                                title = item.title
                                duration = if (item.isTimedSpan) formatTimelineDuration(
                                    requireNotNull(item.atMillis), requireNotNull(item.endAtMillis))
                                else null
                                details = todoDoneDetails(item)
                                point = !item.isTimedSpan
                            }
                            is TodoDoneLaneValue.Record -> {
                                val session = value.session
                                title = session.title
                                duration = if (session.temporalType == SessionTemporalType.POINT) null
                                    else formatTimelineDuration(session.startAtMillis,
                                        requireNotNull(session.endAtMillis))
                                details = todoDoneRecordDetails(session)
                                point = session.temporalType == SessionTemporalType.POINT
                            }
                        }
                        contentMeasurer.height(title, duration, emptyList(), itemWidthPx,
                            0f, textMeasurer, point) to
                        contentMeasurer.height(title, duration, details, itemWidthPx,
                            1f, textMeasurer, point)
                    }
                }
                val overviewHeights = values.indices.associate { index ->
                    values[index] to measuredBoundsHeights[index].first
                }
                val fullHeights = values.indices.associate { index ->
                    values[index] to measuredBoundsHeights[index].second
                }
                val heights = values.associateWith { value ->
                    val overview = overviewHeights.getValue(value)
                    overview + (fullHeights.getValue(value) - overview) * zoom.progress
                }
                val plannedInputs: List<ElasticTimelineItem<TodoDoneLaneValue>> = buildList {
                    todos.forEach { item ->
                        val at = item.atMillis ?: return@forEach
                        if (at !in axisStart..axisEnd) return@forEach
                        val end = item.endAtMillis?.takeIf { item.isTimedSpan }
                        val value = TodoDoneLaneValue.Todo(item)
                        if (end != null) {
                            geometry.position(at, end)?.let { position ->
                                add(
                                    ElasticTimelineItem(
                                        value = value,
                                        start = at.coerceIn(axisStart, axisEnd),
                                        end = end.coerceIn(axisStart, axisEnd),
                                        side = true,
                                        contentHeight = heights.getValue(value),
                                        stableOrder = item.id,
                                    ),
                                )
                            }
                        } else {
                            add(
                                ElasticTimelineItem(
                                    value = value,
                                    start = at,
                                    end = null,
                                    side = true,
                                    contentHeight = heights.getValue(value),
                                    stableOrder = item.id,
                                    anchorLead = pointAnchorLead,
                                ),
                            )
                        }
                    }
                }
                val actualInputs: List<ElasticTimelineItem<TodoDoneLaneValue>> = buildList {
                    records.forEach { session ->
                        val value = TodoDoneLaneValue.Record(session)
                        todoDoneRecordInput(session, axisStart, axisEnd,
                            heights.getValue(value), pointAnchorLead)?.let(::add)
                    }
                }
                val itemGapPx = with(density) { AppTheme.dimens.timelineItemGap.toPx() }
                val stableAxisEnd = axisEnd.takeUnless {
                    displayWindow.endSource == BoundarySource.NOW
                }
                val bounds = remember(state.selectedDate, state.events,
                    state.records, todos.map { it.id }, records.map { it.id },
                    axisStart, stableAxisEnd,
                    viewportHeightPx, itemWidthPx, boundaryOffsetPx, denseContentTailPx,
                    itemGapPx, labelSpacingPx, timedBlockInsetPx, pointAnchorLead, density) {
                    calculateElasticTimelineZoomBounds(
                        items = (plannedInputs + actualInputs).map { item ->
                            item.copy(contentHeight = overviewHeights.getValue(item.value),
                                spanHeightLimit = Float.POSITIVE_INFINITY)
                        },
                        fullDetailHeights = fullHeights,
                        axisStart = axisStart, axisEnd = axisEnd,
                        viewportHeight = viewportHeightPx,
                        topBoundary = boundaryOffsetPx,
                        axisBottomInset = 0f,
                        denseContentTail = denseContentTailPx,
                        itemGap = itemGapPx, labelSpacing = labelSpacingPx,
                        blockInset = timedBlockInsetPx,
                    )
                }
                androidx.compose.runtime.LaunchedEffect(zoom, bounds) {
                    zoom.updateAdaptiveBounds(bounds.minZoom, bounds.maxZoom)
                }
                val liveInputs = (plannedInputs + actualInputs).map { item ->
                    val end = item.end
                    if (end == null) item else item.copy(spanHeightLimit = elasticIntervalHeightLimit(
                        overviewHeights.getValue(item.value), end - item.start,
                        density.density, (viewportHeightPx - boundaryPx).coerceAtLeast(1f),
                        axisEnd - axisStart, bounds.minZoom, timedBlockInsetPx,
                    ))
                }
                val resolved = arrangeElasticTimeline(
                    items = liveInputs,
                    axisStart = axisStart,
                    axisEnd = axisEnd,
                    trueY = { boundaryOffsetPx + geometry.timeToY(it) },
                    itemGap = itemGapPx,
                    labelSpacing = labelSpacingPx,
                    blockInset = timedBlockInsetPx,
                    bottomPadding = 0f,
                )
                val plannedPlacements = resolved.placements.filter { it.value is TodoDoneLaneValue.Todo }
                val actualPlacements = resolved.placements.filter { it.value !is TodoDoneLaneValue.Todo }
                fun y(time: Long) = with(density) { resolved.timeY(time).toDp() }
                val shown = selectTimelineMinuteLabelTimes(
                    endpointTimes, axisStart, axisEnd, zone, resolved::timeY, labelSpacingPx,
                    state.nowMillis.takeIf { displayWindow.endSource == BoundarySource.NOW },
                )
                val visualAxisEndPx = resolved.timeY(axisEnd)
                val scrollExtentPx = timelineScrollableExtent(
                    resolved.requiredHeight,
                    visualAxisEndPx,
                    denseContentTailPx,
                )
                val contentHeight = maxOf(maxHeight, with(density) { scrollExtentPx.toDp() })
                val scrollEnabled = scrollExtentPx > viewportHeightPx

                androidx.compose.runtime.LaunchedEffect(scrollEnabled) {
                    if (!scrollEnabled && timelineScroll.value != 0) {
                        timelineScroll.scrollTo(0)
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .timelinePinchZoom(zoom, timelineScroll)
                        .verticalScroll(
                            timelineScroll,
                            enabled = scrollEnabled,
                        ),
                ) {
                    Box(Modifier.fillMaxWidth().height(contentHeight)) {
                        CentralTimelineAxis(
                            axisX,
                            y(axisStart),
                            y(axisEnd) - y(axisStart),
                            Modifier.zIndex(TimelineAxisLayer),
                        )
                        if (
                            date == Instant.ofEpochMilli(state.nowMillis)
                                .atZone(zone)
                                .toLocalDate() &&
                            state.nowMillis in axisStart..axisEnd
                        ) {
                            CurrentTimeLine(
                                axisX,
                                y(state.nowMillis),
                                Modifier.zIndex(TimelineCardLayer - 0.1f),
                            )
                            CurrentTimeDot(
                                axisX,
                                y(state.nowMillis),
                                Modifier.zIndex(TimelineNowLayer),
                            )
                        }
                        endpointTimes.distinct().forEach { time ->
                            CentralTimelineEndpoint(
                                axisX,
                                y(time),
                                Modifier.zIndex(TimelineAxisLayer),
                                placement = if (time == axisEnd) {
                                    TimelineBoundaryPlacement.INSIDE_BOTTOM
                                } else {
                                    TimelineBoundaryPlacement.CENTERED
                                },
                            )
                            CentralTimelineLabel(
                                clockTime(time, zone),
                                axisX,
                                y(time),
                                Modifier.zIndex(TimelineAxisLayer),
                                show = time in shown,
                                placement = if (time == axisEnd) {
                                    TimelineBoundaryPlacement.INSIDE_BOTTOM
                                } else {
                                    TimelineBoundaryPlacement.CENTERED
                                },
                            )
                        }

                        plannedPlacements.forEach { placement ->
                            val top = with(density) { placement.contentTop.toDp() }
                            val height = with(density) { placement.contentHeight.toDp() }
                            val itemWidth = fullItemWidth
                            val itemX = AppTheme.dimens.spaceXs
                            val item = (placement.value as TodoDoneLaneValue.Todo).item
                            val categoryColors = planCategoryColors(item.categoryId,
                                state.categoryColorKeys, MaterialTheme.colorScheme)
                            val at = requireNotNull(item.atMillis)
                            val end = item.endAtMillis?.takeIf { item.isTimedSpan }
                            if (end != null) {
                                TimelineBlockPlacement(
                                    top = top,
                                    visualHeight = height,
                                    onClick = { onEventClick(item.id) },
                                    modifier = Modifier
                                        .offset(x = itemX)
                                        .width(itemWidth)
                                        .zIndex(TimelineCardLayer),
                                    expandTouchTarget = false,
                                ) { visual ->
                                    TimelineTimeBlock(
                                        item.title,
                                        formatTimelineDuration(at, end),
                                        todoDoneDetails(item),
                                        TimelineVisualKind.PLANNED,
                                        visual,
                                        detailProgress = zoom.progress,
                                        backgroundColor = categoryColors?.container,
                                        titleColor = categoryColors?.content,
                                        metadataColor = categoryColors?.secondaryContent,
                                    )
                                }
                            } else {
                                TimelinePointItem(
                                    title = item.title,
                                    trailing = null,
                                    metadata = todoDoneDetails(item),
                                    zoom = zoom.scale,
                                    onClick = { onEventClick(item.id) },
                                    modifier = Modifier.offset(x = itemX,
                                        y = top)
                                        .width(itemWidth).zIndex(TimelinePointLayer),
                                    visualHeight = height,
                                    side = TimelinePointSide.PLANNED,
                                    overdue = false,
                                    prioritizeTitle = true,
                                    showMarker = categoryColors != null,
                                    markerColor = categoryColors?.base ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    metadataProgress = zoom.progress,
                                )
                            }
                        }

                        actualPlacements.forEach { placement ->
                            val top = with(density) { placement.contentTop.toDp() }
                            val height = with(density) { placement.contentHeight.toDp() }
                            val itemWidth = fullItemWidth
                            val itemX = axisX + sideGap
                            when (val value = placement.value) {
                                is TodoDoneLaneValue.Record -> {
                                    val session = value.session
                                    val categoryColors = if (session.kind == SessionKind.ACTIVITY)
                                        planCategoryColors(session.categoryId, state.categoryColorKeys,
                                            MaterialTheme.colorScheme) else null
                                    if (session.temporalType == SessionTemporalType.POINT) {
                                        TimelinePointItem(
                                            title = session.title,
                                            trailing = null,
                                            metadata = todoDoneRecordDetails(session),
                                            zoom = zoom.scale,
                                            onClick = { dispatchRecordClick(session, onRecordClick) },
                                            modifier = Modifier.offset(x = itemX, y = top)
                                                .width(itemWidth).zIndex(TimelinePointLayer),
                                            visualHeight = height,
                                            side = TimelinePointSide.ACTUAL,
                                            prioritizeTitle = true,
                                            showMarker = categoryColors != null,
                                            markerColor = categoryColors?.base ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            metadataProgress = zoom.progress,
                                        )
                                    } else {
                                        val end = requireNotNull(session.endAtMillis)
                                        TimelineBlockPlacement(
                                            top = top,
                                            visualHeight = height,
                                            onClick = { dispatchRecordClick(session, onRecordClick) },
                                            modifier = Modifier
                                                .offset(x = itemX)
                                                .width(itemWidth)
                                                .zIndex(TimelineCardLayer),
                                            expandTouchTarget = false,
                                        ) { visual ->
                                            TimelineTimeBlock(
                                                if (session.kind == SessionKind.NAP) "小憩" else session.title,
                                                formatTimelineDuration(session.startAtMillis, end),
                                                todoDoneRecordDetails(session),
                                                if (session.kind == SessionKind.NAP) TimelineVisualKind.NAP else TimelineVisualKind.ACTUAL,
                                                visual,
                                                detailProgress = zoom.progress,
                                                backgroundColor = categoryColors?.container,
                                                accentColor = categoryColors?.base,
                                                titleColor = categoryColors?.content,
                                                metadataColor = categoryColors?.secondaryContent,
                                            )
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            if (untimed.isNotEmpty()) {
                Column(Modifier.align(Alignment.TopCenter).zIndex(1f)) {
                    PullSummarySurface(
                        summaryText = "${date.monthValue} 月 ${date.dayOfMonth} 日 · 无时间计划 ${untimed.size} 项",
                        state = reminder,
                        maxExpandedBodyHeight = maxReminderHeight,
                        onToggle = {
                            scope.launch {
                                reminder.settle(expand = reminder.fraction < 0.5f)
                            }
                        },
                    ) {
                        untimed.forEach { item ->
                            val categoryColors = planCategoryColors(item.categoryId,
                                state.categoryColorKeys, MaterialTheme.colorScheme)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onEventClick(item.id) },
                                horizontalArrangement = Arrangement.spacedBy(
                                    AppTheme.dimens.spaceS,
                                ),
                            ) {
                                if (categoryColors != null) Box(Modifier.size(5.dp)
                                    .background(categoryColors.base, androidx.compose.foundation.shape.CircleShape))
                                Text(
                                    planItemTitle(item),
                                    Modifier.weight(1f),
                                    style = TimelineSummaryTypography.item(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.detail?.let {
                                    Text(
                                        it,
                                        style = TimelineSummaryTypography.metadata(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
private fun planItemTitle(item: TodoItem) = androidx.compose.ui.text.AnnotatedString(item.title)

