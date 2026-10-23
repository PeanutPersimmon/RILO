package io.github.peanutpersimmon.rilo.ui.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.ui.components.SegmentedControlLevel
import io.github.peanutpersimmon.rilo.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rilo.ui.components.discreteHorizontalSwipe
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.service.SleepCalendarMode
import io.github.peanutpersimmon.rilo.domain.service.SleepChartMode
import io.github.peanutpersimmon.rilo.domain.service.SleepDay
import io.github.peanutpersimmon.rilo.domain.service.SleepStatisticsCalculator
import io.github.peanutpersimmon.rilo.domain.service.SleepTrendWindow
import io.github.peanutpersimmon.rilo.presentation.statistics.SleepStatisticsView
import io.github.peanutpersimmon.rilo.presentation.statistics.SLEEP_CALENDAR_MODE_ORDER
import io.github.peanutpersimmon.rilo.presentation.statistics.SLEEP_CHART_MODE_ORDER
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal val sleepCalendarModes = SLEEP_CALENDAR_MODE_ORDER
internal val sleepChartModes = SLEEP_CHART_MODE_ORDER
private fun SleepChartMode.shortLabel() = when (this) {
    SleepChartMode.WAKE -> "起床"
    SleepChartMode.BED -> "入睡"
    SleepChartMode.NIGHT -> "睡眠"
    SleepChartMode.NAP -> "小憩"
}
private fun SleepChartMode.title() = when (this) {
    SleepChartMode.WAKE -> "起床时间分布"
    SleepChartMode.BED -> "入睡时间分布"
    SleepChartMode.NIGHT -> "睡眠时长分布"
    SleepChartMode.NAP -> "小憩时长分布"
}

private fun sleepCalendarDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0 && remainder > 0) hours.toString() + "h" + remainder + "m"
        else if (hours > 0) hours.toString() + "h" else remainder.toString() + "m"
}
private fun sleepChartDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) hours.toString() + "小时" + remainder + "分" else remainder.toString() + "分"
}
/** Sleep's semantic calendar palette, scoped to this feature while surfaces follow RILO's theme. */
internal object SleepVisualColors {
    private val primary = Color(0xFF8FA3C8)
    private val semanticCoolTint = Color(0xFFB9C7E6)
    private fun blend(first: Color, second: Color, ratio: Float): Color {
        val remaining = 1f - ratio
        return Color(first.red * remaining + second.red * ratio,
            first.green * remaining + second.green * ratio,
            first.blue * remaining + second.blue * ratio)
    }
    val success = blend(Color(0xFF6FA77A), semanticCoolTint, 0.12f)
    val normal = Color(0xFF7FA6CF)
    val warning = blend(Color(0xFFD7A25D), semanticCoolTint, 0.10f)
    val danger = blend(Color(0xFFD86B6B), primary, 0.08f)
}

@Composable
internal fun sleepChartColor(): Color = MaterialTheme.colorScheme.onSurface

@Composable
private fun SleepSurface(padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Surface(Modifier.fillMaxWidth(), shape = AppTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(0.5.dp, outline),
        shadowElevation = 1.dp, tonalElevation = 0.dp) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
internal fun SleepStatisticsScreen(
    month: YearMonth,
    nights: List<Session>,
    naps: List<Session>,
    onShiftMonth: (Long) -> Unit,
    selectedView: SleepStatisticsView = SleepStatisticsView.CALENDAR,
    selectedCalendarMode: SleepCalendarMode = SleepCalendarMode.BED,
    selectedChartMode: SleepChartMode = SleepChartMode.BED,
    onView: (SleepStatisticsView) -> Unit = {},
    onCalendarMode: (SleepCalendarMode) -> Unit = {},
    onChartMode: (SleepChartMode) -> Unit = {},
    modifier: Modifier = Modifier,
    onStepCalendarMode: (Int) -> Unit = {},
    onStepChartMode: (Int) -> Unit = {},
    zoneId: ZoneId = ZoneId.systemDefault(),
    trendWindow: SleepTrendWindow = SleepTrendWindow.current(LocalDate.now()),
    onShiftTrendWindow: (Int) -> Unit = {},
) {
    val d = AppTheme.dimens
    val zone = zoneId
    val nightDays = remember(nights, zone) {
        SleepStatisticsCalculator.nightDays(
            nights,
            zone,
            System.currentTimeMillis(),
        )
    }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(d.spaceS),
    ) {
        StatisticsTextTabs(
            SleepStatisticsView.entries,
            selectedView,
            { it.label },
            onView,
            SegmentedControlLevel.SECONDARY,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (selectedView) {
                SleepStatisticsView.CALENDAR -> SleepCalendarPage(
                    month,
                    nightDays,
                    zone,
                    onShiftMonth,
                    selectedCalendarMode,
                    onCalendarMode,
                    onStepCalendarMode,
                )
                SleepStatisticsView.CHART -> SleepChartPage(
                    month,
                    nightDays,
                    naps,
                    zone,
                    onShiftMonth,
                    selectedChartMode,
                    onChartMode,
                    onStepChartMode,
                    trendWindow,
                    onShiftTrendWindow,
                )
            }
        }
    }
}

@Composable
private fun SleepCalendarPage(
    month: YearMonth,
    nightDays: Map<LocalDate, SleepDay>,
    zone: ZoneId,
    onShiftMonth: (Long) -> Unit,
    selectedMode: SleepCalendarMode,
    onMode: (SleepCalendarMode) -> Unit,
    onStepMode: (Int) -> Unit,
) {
    val d = AppTheme.dimens

    Column(
        Modifier
            .fillMaxSize()
            .discreteHorizontalSwipe { direction ->
                onStepMode(direction.step)
            }
    ) {
        SleepCalendarToolbar(
            month,
            selectedMode,
            onMode,
            onShiftMonth,
        )
        Spacer(Modifier.height(d.spaceS))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val mode = selectedMode
            val visible = nightDays.values.filter {
                YearMonth.from(it.date) == month
            }
            val average = SleepStatisticsCalculator.averageMinutes(
                visible,
                mode,
                zone,
            )
            val value = average?.let {
                if (mode == SleepCalendarMode.DURATION) {
                    sleepCalendarDuration(it)
                } else {
                    SleepStatisticsCalculator.clock(it)
                }
            } ?: "--"
            val complete = visible.count { it.durationMinutes != null }
            val incomplete = visible.count { it.hasNightRecord } - complete
            val scroll = rememberScrollState()

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(bottom = maxOf(d.spaceL, LocalFloatingNavigationClearance.current)),
                verticalArrangement = Arrangement.spacedBy(d.spaceS),
            ) {
                SleepCalendarSummary(value, complete, incomplete)
                SleepCalendar(
                    SleepStatisticsCalculator.calendarDays(month, nightDays),
                    mode,
                    zone,
                )
            }
        }
    }
}

@Composable
private fun SleepChartPage(
    month: YearMonth,
    nightDays: Map<LocalDate, SleepDay>,
    naps: List<Session>,
    zone: ZoneId,
    onShiftMonth: (Long) -> Unit,
    selectedMode: SleepChartMode,
    onMode: (SleepChartMode) -> Unit,
    onStepMode: (Int) -> Unit,
    trendWindow: SleepTrendWindow,
    onShiftTrendWindow: (Int) -> Unit,
) {
    val d = AppTheme.dimens

    Column(
        Modifier
            .fillMaxSize()
            .discreteHorizontalSwipe { direction ->
                onStepMode(direction.step)
            }
    ) {
        StatisticsTextTabs(
            sleepChartModes,
            selectedMode,
            SleepChartMode::shortLabel,
            onMode,
            SegmentedControlLevel.SECONDARY,
        )
        Spacer(Modifier.height(d.spaceS))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val mode = selectedMode
            val line = mode == SleepChartMode.BED || mode == SleepChartMode.WAKE
            val rangeStart = if (line) trendWindow.start else month.atDay(1)
            val rangeEnd = if (line) trendWindow.end else month.atEndOfMonth()
            val averageValue: String
            val averageLabel: String

            if (line) {
                val averageMode = if (mode == SleepChartMode.BED) {
                    SleepCalendarMode.BED
                } else {
                    SleepCalendarMode.WAKE
                }
                val visible = nightDays.values.filter {
                    !it.date.isBefore(trendWindow.start) &&
                        !it.date.isAfter(trendWindow.end)
                }
                averageValue = SleepStatisticsCalculator.averageMinutes(
                    visible,
                    averageMode,
                    zone,
                )?.let(SleepStatisticsCalculator::clock) ?: "暂无数据"
                averageLabel = if (mode == SleepChartMode.BED) {
                    "平均入睡时间"
                } else {
                    "平均起床时间"
                }
            } else {
                val average = if (mode == SleepChartMode.NAP) {
                    SleepStatisticsCalculator.averageNapMinutes(
                        naps,
                        month,
                        zone,
                        System.currentTimeMillis(),
                    )
                } else {
                    SleepStatisticsCalculator.averageMinutes(
                        nightDays.values.filter {
                            YearMonth.from(it.date) == month
                        },
                        SleepCalendarMode.DURATION,
                        zone,
                    )
                }
                averageValue = average?.let(::sleepChartDuration) ?: "暂无数据"
                averageLabel = if (mode == SleepChartMode.NAP) {
                    "平均小憩时长"
                } else {
                    "平均睡眠时长"
                }
            }

            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(bottom = maxOf(d.spaceL, LocalFloatingNavigationClearance.current)),
            ) {
                SleepTrendCard(
                    mode.title(),
                    sleepChartRange(rangeStart, rangeEnd),
                    averageLabel,
                    averageValue,
                    onPrevious = {
                        if (line) {
                            onShiftTrendWindow(-1)
                        } else {
                            onShiftMonth(-1)
                        }
                    },
                    onNext = {
                        if (line) {
                            onShiftTrendWindow(1)
                        } else {
                            onShiftMonth(1)
                        }
                    },
                ) {
                    if (line) {
                        SleepLineChart(
                            SleepStatisticsCalculator.linePoints(
                                nightDays,
                                trendWindow.start,
                                trendWindow.end,
                                zone,
                                mode == SleepChartMode.BED,
                            )
                        )
                    } else {
                        SleepBarChart(
                            SleepStatisticsCalculator.durationPoints(
                                month,
                                nightDays,
                                naps,
                                zone,
                                mode == SleepChartMode.NAP,
                                System.currentTimeMillis(),
                            ),
                            mode == SleepChartMode.NAP,
                        )
                    }
                }
            }
        }
    }
}

private fun sleepChartRange(start: LocalDate, end: LocalDate): String {
    val month = start.year.toString() + "/" +
        start.monthValue.toString().padStart(2, '0')
    val first = start.monthValue.toString() + "/" + start.dayOfMonth
    val last = if (start.year == end.year) {
        end.monthValue.toString() + "/" + end.dayOfMonth
    } else {
        end.year.toString() + "/" + end.monthValue + "/" + end.dayOfMonth
    }
    return month + " · " + first + "–" + last
}

@Composable
private fun SleepCalendarToolbar(
    month: YearMonth,
    selected: SleepCalendarMode,
    onMode: (SleepCalendarMode) -> Unit,
    onShiftMonth: (Long) -> Unit,
) {
    val d = AppTheme.dimens
    val controlHeight = d.navigationSecondaryHeight
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                SleepCalendarMode.WAKE to Icons.Rounded.LightMode,
                SleepCalendarMode.BED to Icons.Rounded.Bedtime,
                SleepCalendarMode.DURATION to Icons.Rounded.AccessTime,
            ).forEach { (mode, icon) ->
                val active = selected == mode
                Box(
                    Modifier
                        .size(controlHeight)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            AppTheme.shapes.pill,
                        )
                        .clickable { onMode(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        mode.label,
                        Modifier.size(16.dp),
                        tint = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier
                .width(140.dp)
                .height(controlHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    AppTheme.shapes.pill,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .clickable { onShiftMonth(-1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    "上个月",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                month.year.toString() + "/" +
                    month.monthValue.toString().padStart(2, '0'),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = AppTheme.type.navigationSecondary,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Box(
                Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .clickable { onShiftMonth(1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    "下个月",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SleepCalendarSummary(average: String, complete: Int, incomplete: Int) {
    SleepSurface(PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(average, Modifier.weight(1f), style = AppTheme.type.secondary, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text("完整 " + complete + "天", Modifier.weight(1f), style = AppTheme.type.assistive,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("不完整 " + incomplete + "天", Modifier.weight(1f), style = AppTheme.type.assistive,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SleepCalendar(days: List<SleepDay?>, mode: SleepCalendarMode, zone: ZoneId) {
    SleepSurface(PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp,
        bottom = if (mode == SleepCalendarMode.DURATION) 10.dp else 6.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(68.dp), contentAlignment = Alignment.Center) {
                                if (day != null) SleepCalendarCell(day, mode, zone)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepCalendarCell(day: SleepDay, mode: SleepCalendarMode, zone: ZoneId) {
    val future = day.date.isAfter(LocalDate.now())
    val minutes = when (mode) {
        SleepCalendarMode.BED -> day.bedMillis?.let { SleepStatisticsCalculator.minuteOfDay(it, zone) }
        SleepCalendarMode.WAKE -> day.wakeMillis?.let { SleepStatisticsCalculator.minuteOfDay(it, zone) }
        SleepCalendarMode.DURATION -> null
    }
    val value = if (future) null else when (mode) {
        SleepCalendarMode.BED, SleepCalendarMode.WAKE -> minutes?.let(SleepStatisticsCalculator::clock)
        SleepCalendarMode.DURATION -> day.durationMinutes?.let(::sleepCalendarDuration)
    }
    val level = when (mode) {
        SleepCalendarMode.BED -> minutes?.let { if (it < 720) it + 1440 else it }
            ?.let { if (it < 1380) 0 else if (it < 1500) 1 else if (it < 1620) 2 else 3 } ?: 1
        SleepCalendarMode.WAKE -> minutes?.let { if (it < 480) 0 else if (it < 600) 1 else if (it < 720) 2 else 3 } ?: 1
        SleepCalendarMode.DURATION -> 1
    }
    val semantic = if (mode == SleepCalendarMode.DURATION) when (day.durationMinutes ?: 0) {
        in 420..540 -> SleepVisualColors.success
        in 360..419, in 541..600 -> SleepVisualColors.normal
        else -> SleepVisualColors.danger
    } else listOf(SleepVisualColors.success, SleepVisualColors.normal,
        SleepVisualColors.warning, SleepVisualColors.danger)[level]
    val today = day.date == LocalDate.now()
    Column(Modifier.fillMaxSize().then(if (today) Modifier.border(1.dp,
        sleepChartColor().copy(alpha = 0.45f), RoundedCornerShape(12.dp)) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top) {
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = if (future) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(1.dp))
        if (value == null) {
            Spacer(Modifier.size(26.dp))
        } else {
            if (mode == SleepCalendarMode.DURATION) {
                val progress = (day.durationMinutes!!.toFloat() / 600f).coerceIn(0f, 1f)
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(Modifier.size(26.dp)) {
                    val stroke = 4.dp.toPx()
                    val inset = stroke / 2f
                    drawCircle(trackColor, radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(semantic, -90f, progress * 360f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            } else Icon(painterResource(sleepIcon(mode, level)), null, Modifier.size(26.dp), tint = semantic)
            Spacer(Modifier.height(1.dp))
            Text(value, modifier = if (mode == SleepCalendarMode.DURATION) Modifier.fillMaxWidth() else Modifier,
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (mode == SleepCalendarMode.DURATION && value.length >= 6) 7.5.sp
                else if (mode == SleepCalendarMode.DURATION) 8.sp else 9.sp,
                lineHeight = if (mode == SleepCalendarMode.DURATION) 9.sp
                    else MaterialTheme.typography.labelSmall.lineHeight,
                color = semantic.copy(alpha = 0.85f), maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center, overflow = TextOverflow.Clip)
        }
    }
}

private fun sleepIcon(mode: SleepCalendarMode, level: Int): Int = when (mode) {
    SleepCalendarMode.BED -> listOf(R.drawable.ic_sleep_bed_early, R.drawable.ic_sleep_bed_normal,
        R.drawable.ic_sleep_bed_late, R.drawable.ic_sleep_bed_very_late)[level]
    SleepCalendarMode.WAKE -> listOf(R.drawable.ic_sleep_wake_early, R.drawable.ic_sleep_wake_normal,
        R.drawable.ic_sleep_wake_late, R.drawable.ic_sleep_wake_very_late)[level]
    SleepCalendarMode.DURATION -> error("duration uses a ring")
}

@Composable
private fun SleepTrendCard(
    title: String,
    range: String,
    averageLabel: String,
    averageValue: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    chart: @Composable () -> Unit,
) {
    val d = AppTheme.dimens
    SleepSurface(PaddingValues(horizontal = d.spaceM, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = AppTheme.type.compactCardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(d.spaceMicro)) {
                    SleepChartArrowButton(true, onPrevious)
                    SleepChartArrowButton(false, onNext)
                }
            }
            Text(
                range,
                modifier = Modifier.fillMaxWidth(),
                style = AppTheme.type.assistive,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            chart()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    averageLabel + "  ",
                    style = AppTheme.type.bodyCompact,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    averageValue,
                    style = AppTheme.type.navigationSecondary,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SleepChartArrowButton(left: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(24.dp)
            .clip(AppTheme.shapes.pill),
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Rounded.KeyboardArrowLeft
            else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            if (left) "上一个时间范围" else "下一个时间范围",
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
