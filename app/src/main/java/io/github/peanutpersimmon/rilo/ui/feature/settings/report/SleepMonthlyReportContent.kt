package io.github.peanutpersimmon.rilo.ui.feature.settings.report

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.domain.service.SleepMonthlyReport
import io.github.peanutpersimmon.rilo.domain.service.SleepReportDay
import io.github.peanutpersimmon.rilo.domain.service.SleepStatisticsCalculator
import java.time.LocalDate
import kotlin.math.roundToInt

/** Sleep's default morning-mist palette, kept fixed for the exported report. */
internal object SleepReportPalette {
    private val backgroundTint = Color(0xFFEEF3FA)
    private val accent = Color(0xFFB9C7E6)
    private fun blend(first: Color, second: Color, ratio: Float): Color = Color(
        red = first.red * (1f - ratio) + second.red * ratio,
        green = first.green * (1f - ratio) + second.green * ratio,
        blue = first.blue * (1f - ratio) + second.blue * ratio,
    )
    val background = blend(backgroundTint, Color.White, 0.72f)
    val surface = Color.White
    val primary = Color(0xFF8FA3C8)
    val primaryDark = blend(primary, Color.Black, 0.28f)
    val chartSecondary = accent
    val awake = blend(Color(0xFFD09A63), accent, 0.12f)
    val text = Color(0xFF202124)
    val secondary = Color(0xFF68707D)
    val tertiary = Color(0xFFA3AAB6)
    val divider = blend(backgroundTint, Color(0xFFD8DEE8), 0.42f)
    val outline = blend(backgroundTint, Color(0xFFC9D0DD), 0.50f)
    val success = blend(Color(0xFF6FA77A), accent, 0.12f)
    val normal = Color(0xFF7FA6CF)
    val warning = blend(Color(0xFFD7A25D), accent, 0.10f)
    val danger = blend(Color(0xFFD86B6B), primary, 0.08f)
}

internal object MonthlyReportDesignTokens {
    val ReportWidth = 720.dp
    val PagePadding = 24.dp
    val SectionSpacing = 14.dp
    val CardInnerSpacing = 8.dp
    val TrendHeight = 240.dp
    val NightBarHeight = 230.dp
    val NapBarHeight = 200.dp
}

/** The same content is rendered for the in-app preview bitmap and exported PNG. */
@Composable
fun SleepMonthlyReportContent(report: SleepMonthlyReport, exportMode: Boolean, modifier: Modifier = Modifier) {
    val byDate = report.days.associateBy(SleepReportDay::date)
    val fullMonth = (1..report.month.lengthOfMonth()).map { day ->
        byDate[report.month.atDay(day)] ?: SleepReportDay(report.month.atDay(day), null, null, null, null)
    }
    val sharedDurationMax = maxOf(12 * 60, fullMonth.mapNotNull(SleepReportDay::nightMinutes).maxOrNull() ?: 0,
        fullMonth.mapNotNull(SleepReportDay::napMinutes).maxOrNull() ?: 0)
    Column(modifier.fillMaxWidth().background(SleepReportPalette.background)
        .padding(if (exportMode) MonthlyReportDesignTokens.PagePadding else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (exportMode) MonthlyReportDesignTokens.SectionSpacing else 14.dp)) {
        Text("${report.month.year}年${report.month.monthValue}月睡眠月报", color = SleepReportPalette.primaryDark,
            style = MaterialTheme.typography.headlineSmall, fontSize = 26.sp, lineHeight = 32.sp,
            fontWeight = FontWeight.Bold)
        SummarySection(report)
        ReportCard("作息趋势") { BedWakeTrend(report.days) }
        ReportCard("夜间睡眠时长") {
            DurationCaption(report.days.mapNotNull(SleepReportDay::nightMinutes), report.metrics.averageNightMinutes)
            DurationBars(fullMonth.map(SleepReportDay::nightMinutes), MonthlyReportDesignTokens.NightBarHeight,
                maxDurationMinutes = sharedDurationMax)
        }
        ReportCard("午睡趋势") {
            DurationCaption(report.days.mapNotNull(SleepReportDay::napMinutes), report.metrics.averageNapMinutes)
            DurationBars(fullMonth.map(SleepReportDay::napMinutes), MonthlyReportDesignTokens.NapBarHeight,
                SleepReportPalette.chartSecondary, sharedDurationMax)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("睡眠结构")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportSurface(Modifier.weight(1f).height(180.dp)) {
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("睡眠时长占比", color = SleepReportPalette.text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        DurationDonut(report)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
        CalendarSection(report)
        ReportCard("本月总结与小建议") {
            report.summary.forEachIndexed { index, line ->
                Text(if (index == 0) line else "- $line",
                    color = if (index == 0) SleepReportPalette.text else SleepReportPalette.secondary,
                    fontSize = if (index == 0) 14.sp else 13.sp,
                    lineHeight = if (index == 0) 21.sp else 20.sp)
            }
        }
    }
}

@Composable
private fun ReportSurface(modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SleepReportPalette.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SleepReportPalette.outline.copy(alpha = 0.35f)),
        shadowElevation = 1.dp) { Box(Modifier.padding(padding)) { content() } }
}

@Composable private fun SectionTitle(text: String) = Text(text, color = SleepReportPalette.primaryDark,
    style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, lineHeight = 24.sp,
    fontWeight = FontWeight.SemiBold)

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ReportSurface {
        Column(verticalArrangement = Arrangement.spacedBy(MonthlyReportDesignTokens.CardInnerSpacing)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun SummarySection(report: SleepMonthlyReport) {
    ReportCard("本月概览") {
        if (report.metrics.recordedNightDays == 0 && report.metrics.napCount == 0)
            Text("暂无记录", color = SleepReportPalette.secondary)
        listOf(
            listOf("夜晚记录" to "${report.metrics.recordedNightDays}天",
                "平均入睡" to (report.metrics.averageBedMinutes?.let(SleepStatisticsCalculator::clock) ?: "--"),
                "平均起床" to (report.metrics.averageWakeMinutes?.let(SleepStatisticsCalculator::clock) ?: "--")),
            listOf("平均睡眠" to report.metrics.averageNightMinutes.durationText(),
                "午睡次数" to "${report.metrics.napCount}次",
                "平均午睡" to report.metrics.averageNapMinutes.durationText())
        ).forEach { metrics ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(value, color = SleepReportPalette.text, fontSize = 22.sp, lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(label, color = SleepReportPalette.secondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

private fun Int?.durationText(): String = if (this == null) "--" else if (this >= 60)
    "${this / 60}小时${this % 60}分" else "${this}分钟"

/** Sleep's dual-series chart: shared night axis, grid, 1/10/20/30 labels and legend. */
@Composable
private fun BedWakeTrend(days: List<SleepReportDay>) {
    if (days.none { it.bedMinutes != null || it.wakeMinutes != null }) {
        EmptySection(); return
    }
    Canvas(Modifier.fillMaxWidth().height(MonthlyReportDesignTokens.TrendHeight)) {
        val left = 24.dp.toPx(); val right = size.width - 18.dp.toPx()
        val top = 24.dp.toPx(); val bottom = size.height - 28.dp.toPx()
        val values = days.flatMap { listOfNotNull(it.bedMinutes, it.wakeMinutes) }
        val rangeStart = (values.minOrNull() ?: 0) - 60
        val range = ((values.maxOrNull() ?: 1) + 60 - rangeStart).coerceAtLeast(1)
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(SleepReportPalette.divider.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
        fun series(select: (SleepReportDay) -> Int?, color: Color) {
            var path = Path(); var started = false
            val last = days.indexOfLast { select(it) != null }
            days.forEachIndexed { index, day ->
                val value = select(day)
                if (value == null) {
                    if (started) drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    path = Path(); started = false
                } else {
                    val x = left + (right - left) * index / (days.size - 1).coerceAtLeast(1)
                    val y = bottom - (value - rangeStart).toFloat() / range * (bottom - top)
                    if (started) path.lineTo(x, y) else path.moveTo(x, y)
                    started = true
                    drawCircle(color.copy(alpha = 0.9f), 3.dp.toPx(), Offset(x, y))
                    if (index == last) canvasText(SleepStatisticsCalculator.clock(value), x,
                        y - 8.dp.toPx(), 10.sp.toPx(), color)
                }
            }
            if (started) drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        series({ it.bedMinutes }, SleepReportPalette.primaryDark)
        series({ it.wakeMinutes }, SleepReportPalette.awake)
        days.forEachIndexed { index, day ->
            if (day.date.dayOfMonth in setOf(1, 10, 20, 30)) {
                val x = left + (right - left) * index / (days.size - 1).coerceAtLeast(1)
                canvasText(day.date.dayOfMonth.toString(), x, size.height - 7.dp.toPx(), 12.sp.toPx(), SleepReportPalette.tertiary)
            }
        }
        drawCircle(SleepReportPalette.primaryDark, 4.dp.toPx(), Offset(left, 8.dp.toPx()))
        canvasText("入睡", left + 30.dp.toPx(), 12.dp.toPx(), 11.sp.toPx(), SleepReportPalette.primaryDark)
        drawCircle(SleepReportPalette.awake, 4.dp.toPx(), Offset(left + 68.dp.toPx(), 8.dp.toPx()))
        canvasText("起床", left + 98.dp.toPx(), 12.dp.toPx(), 11.sp.toPx(), SleepReportPalette.awake)
    }
}

@Composable private fun EmptySection() = Text("暂无数据", color = SleepReportPalette.secondary,
    fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun DurationCaption(values: List<Int>, average: Int?) {
    if (values.isEmpty()) { EmptySection(); return }
    Text("平均 ${average.durationText()} · 最高 ${values.maxOrNull().durationText()}",
        color = SleepReportPalette.secondary, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun DurationBars(values: List<Int?>, height: androidx.compose.ui.unit.Dp,
    color: Color = SleepReportPalette.primaryDark, maxDurationMinutes: Int = 12 * 60) {
    if (values.none { it != null }) return
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val left = 16.dp.toPx(); val right = size.width - left
        val top = 16.dp.toPx(); val bottom = size.height - 24.dp.toPx()
        val max = maxOf(maxDurationMinutes, values.filterNotNull().maxOrNull() ?: 0)
        val slot = (right - left) / values.size.coerceAtLeast(1)
        val barWidth = (slot * 0.18f).coerceIn(3.dp.toPx(), 5.dp.toPx())
        drawLine(SleepReportPalette.tertiary.copy(alpha = 0.34f), Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val x = left + slot * (index + 0.5f)
            if (value != null) {
                val barHeight = value.toFloat() / max * (bottom - top)
                drawRoundRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color.copy(alpha = 0.08f)),
                    startY = bottom - barHeight, endY = bottom), Offset(x - barWidth / 2f, bottom - barHeight),
                    Size(barWidth, barHeight), androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
            }
            if (index + 1 in setOf(1, 10, 20, 30))
                canvasText((index + 1).toString(), x, size.height - 6.dp.toPx(), 12.sp.toPx(), SleepReportPalette.tertiary)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.canvasText(
    value: String, x: Float, y: Float, size: Float, color: Color) {
    drawContext.canvas.nativeCanvas.drawText(value, x, y,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color.toArgb(); textAlign = Paint.Align.CENTER })
}

@Composable
private fun DurationDonut(report: SleepMonthlyReport) {
    val slices = report.metrics.nightDurationBuckets.filter { it.count > 0 }
    val total = slices.sumOf { it.count }
    val palette = listOf(Color(0xFFFF8A8A), Color(0xFF6EA8FE), Color(0xFF7ED6A5),
        Color(0xFFFFC857), Color(0xFFB69CFF))
    Row(Modifier.fillMaxWidth().height(116.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(45.dp, Alignment.CenterHorizontally)) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 27.dp.toPx()
                if (total == 0) drawCircle(SleepReportPalette.divider, style = Stroke(stroke))
                else {
                    var start = -90f
                    slices.forEach { slice ->
                        val index = report.metrics.nightDurationBuckets.indexOf(slice)
                        val sweep = 360f * slice.count / total
                        drawArc(palette[index % palette.size], start, sweep - 1.2f,
                            useCenter = false, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Text(if (total > 0) "${total}天" else "--", fontSize = 14.sp,
                color = SleepReportPalette.primaryDark, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.width(94.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (slices.isEmpty()) EmptySection()
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).background(palette[report.metrics.nightDurationBuckets.indexOf(slice) % palette.size], CircleShape))
                    Text(slice.label, Modifier.weight(1f), color = SleepReportPalette.secondary, fontSize = 10.sp, maxLines = 1)
                    Text("${(slice.count * 100f / total).roundToInt()}%", color = SleepReportPalette.text,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private enum class MiniCalendarMode { BED, WAKE, DURATION, NAP }

@Composable
private fun CalendarSection(report: SleepMonthlyReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("本月日历回顾")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalendarCard("入睡日历", MiniCalendarMode.BED, report, Modifier.weight(1f))
            CalendarCard("起床日历", MiniCalendarMode.WAKE, report, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalendarCard("时长日历", MiniCalendarMode.DURATION, report, Modifier.weight(1f))
            CalendarCard("午睡日历", MiniCalendarMode.NAP, report, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalendarCard(title: String, mode: MiniCalendarMode, report: SleepMonthlyReport, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = SleepReportPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        ReportSurface(padding = PaddingValues(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(it, Modifier.weight(1f), color = SleepReportPalette.secondary,
                            fontSize = 8.sp, textAlign = TextAlign.Center)
                    }
                }
                val byDate = report.days.associateBy(SleepReportDay::date)
                val first = report.month.atDay(1)
                val offset = first.dayOfWeek.value - 1
                val cells = ((offset + report.month.lengthOfMonth() + 6) / 7) * 7
                (0 until cells).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { index ->
                            val day = index - offset + 1
                            val data = if (day in 1..report.month.lengthOfMonth())
                                byDate[report.month.atDay(day)] else null
                            MiniCalendarCell(data, mode, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCalendarCell(day: SleepReportDay?, mode: MiniCalendarMode, modifier: Modifier) {
    val value = when (mode) {
        MiniCalendarMode.BED -> day?.bedMinutes?.let(SleepStatisticsCalculator::clock)
        MiniCalendarMode.WAKE -> day?.wakeMinutes?.let(SleepStatisticsCalculator::clock)
        MiniCalendarMode.DURATION -> day?.nightMinutes?.shortDuration()
        MiniCalendarMode.NAP -> day?.napMinutes?.shortDuration()
    }
    Box(modifier.aspectRatio(1.05f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (value != null && day != null) {
            val palette = visual(day, mode)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (mode == MiniCalendarMode.DURATION) {
                    Canvas(Modifier.size(18.dp)) {
                        val width = 3.dp.toPx()
                        drawCircle(SleepReportPalette.divider, style = Stroke(width))
                        drawArc(palette.first, -90f, 360f * ((day.nightMinutes ?: 0) / 600f).coerceIn(0f, 1f),
                            false, style = Stroke(width, cap = StrokeCap.Round))
                    }
                } else Icon(painterResource(palette.second), contentDescription = null,
                    Modifier.size(18.dp), tint = palette.first)
                Text(value, Modifier.fillMaxWidth(), color = palette.first, fontSize = 8.sp,
                    lineHeight = 10.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun Int.shortDuration(): String = if (this >= 60) "${this / 60}h${this % 60}m" else "${this}m"

private fun visual(day: SleepReportDay, mode: MiniCalendarMode): Pair<Color, Int> = when (mode) {
    MiniCalendarMode.BED -> {
        val hour = (day.bedMinutes ?: 0) / 60
        when {
            hour < 23 -> SleepReportPalette.success to R.drawable.ic_sleep_bed_early
            hour < 25 -> SleepReportPalette.normal to R.drawable.ic_sleep_bed_normal
            hour < 27 -> SleepReportPalette.warning to R.drawable.ic_sleep_bed_late
            else -> SleepReportPalette.danger to R.drawable.ic_sleep_bed_very_late
        }
    }
    MiniCalendarMode.WAKE -> when ((day.wakeMinutes ?: 0) / 60) {
        in 0..7 -> SleepReportPalette.success to R.drawable.ic_sleep_wake_early
        in 8..9 -> SleepReportPalette.normal to R.drawable.ic_sleep_wake_normal
        in 10..11 -> SleepReportPalette.warning to R.drawable.ic_sleep_wake_late
        else -> SleepReportPalette.danger to R.drawable.ic_sleep_wake_very_late
    }
    MiniCalendarMode.DURATION -> when (day.nightMinutes ?: 0) {
        in 420..540 -> SleepReportPalette.success to R.drawable.ic_sleep_bed_normal
        in 360..600 -> SleepReportPalette.normal to R.drawable.ic_sleep_bed_normal
        else -> SleepReportPalette.danger to R.drawable.ic_sleep_bed_normal
    }
    MiniCalendarMode.NAP -> when (day.napMinutes ?: 0) {
        in 0..30 -> SleepReportPalette.success to R.drawable.ic_sleep_wake_early
        in 31..60 -> SleepReportPalette.normal to R.drawable.ic_sleep_wake_normal
        in 61..90 -> SleepReportPalette.warning to R.drawable.ic_sleep_wake_late
        else -> SleepReportPalette.danger to R.drawable.ic_sleep_wake_very_late
    }
}
