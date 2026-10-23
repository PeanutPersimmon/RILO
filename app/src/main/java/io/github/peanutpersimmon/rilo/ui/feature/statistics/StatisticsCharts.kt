package io.github.peanutpersimmon.rilo.ui.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.domain.service.DurationPoint
import io.github.peanutpersimmon.rilo.domain.service.DurationRank
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import java.time.LocalDate
import java.time.YearMonth

internal fun formatHours(millis: Long): String {
    val hours = millis / 3_600_000.0
    return if (hours == 0.0) "0 h" else if (hours < 0.1) "<0.1 h" else "%.1f h".format(hours)
}

@Composable
internal fun DurationRanking(
    ranks: List<DurationRank>,
    names: Map<Long, String>,
    onClick: (Long?) -> Unit,
    categoryColorKeys: Map<Long, String> = emptyMap(),
) {
    val d = AppTheme.dimens
    if (ranks.isEmpty()) {
        Text("暂无可统计的实际时长", style = AppTheme.type.bodyCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = ranks.maxOf { it.durationMillis }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(d.spaceMicro)) {
        ranks.forEach { rank ->
            val rankColor = rank.id?.let(categoryColorKeys::get)?.let {
                resolveCategoryColors(it, MaterialTheme.colorScheme).border
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable { onClick(rank.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val name = rank.id?.let { names[it] } ?: "未分类"
                    Text(name, style = AppTheme.type.bodyCompact, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatHours(rank.durationMillis), style = AppTheme.type.bodyCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant, AppTheme.shapes.small)) {
                    Box(Modifier.fillMaxWidth((rank.durationMillis.toFloat() / max.toFloat()).coerceIn(0f, 1f))
                        .height(2.dp).background(rankColor ?: MaterialTheme.colorScheme.primary, AppTheme.shapes.small))
                }
            }
        }
    }
}

@Composable
internal fun DurationTrend(points: List<DurationPoint>, granularity: StatisticsGranularity) {
    if (points.isEmpty()) return
    val d = AppTheme.dimens
    val color = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOf { it.durationMillis }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val base = size.height - d.spaceXs.toPx()
            val slot = size.width / points.size
            drawLine(faint, Offset(0f, base), Offset(size.width, base), d.border.toPx())
            points.forEachIndexed { index, point ->
                if (point.durationMillis <= 0) return@forEachIndexed
                val h = (point.durationMillis.toFloat() / max.toFloat()) * (base - d.spaceM.toPx())
                val w = (slot * 0.36f).coerceAtMost(12.dp.toPx())
                drawRoundRect(color, Offset(slot * index + (slot - w) / 2f, base - h), Size(w, h),
                    CornerRadius(d.spaceXs.toPx(), d.spaceXs.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                val show = granularity != StatisticsGranularity.MONTH || index == 0 || (index + 1) % 5 == 0
                Text(if (show) point.label else "", Modifier.weight(1f), style = AppTheme.type.chartLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun DurationHeatmap(year: Int, month: Int?, totals: Map<LocalDate, Long>) {
    if (month == null) {
        YearDurationHeatmap(year, totals)
        return
    }
    val current = YearMonth.of(year, month)
    val max = totals.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val d = AppTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(d.spaceMicro)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(label, Modifier.weight(1f), style = AppTheme.type.chartLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        val offset = current.atDay(1).dayOfWeek.value - 1
        val cells = ((offset + current.lengthOfMonth() + 6) / 7) * 7
        (0 until cells).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceMicro)) {
                week.forEach { index ->
                    val day = index - offset + 1
                    val date = if (day in 1..current.lengthOfMonth()) current.atDay(day) else null
                    val duration = date?.let { totals[it] } ?: 0L
                    val alpha = if (duration == 0L) 0f else 0.10f + 0.55f * duration.toFloat() / max.toFloat()
                    Box(Modifier.weight(1f).height(30.dp).then(
                        if (alpha > 0f) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            AppTheme.shapes.small) else Modifier), contentAlignment = Alignment.Center) {
                        if (date != null) Text(day.toString(), style = AppTheme.type.chartLabel,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** Sleep's rounded day-cell/month-label vocabulary, arranged as twelve mini months. */
@Composable
private fun YearDurationHeatmap(year: Int, totals: Map<LocalDate, Long>) {
    val max = totals.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..12).chunked(3).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { month ->
                    MiniDurationMonth(YearMonth.of(year, month), totals, max, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniDurationMonth(month: YearMonth, totals: Map<LocalDate, Long>, max: Long, modifier: Modifier) {
    val shape = RoundedCornerShape(3.dp)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
    val today = LocalDate.now()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(listOf("一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月")[month.monthValue - 1], style = AppTheme.type.assistive,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                Text(label, Modifier.weight(1f), style = AppTheme.type.chartLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        val leading = month.atDay(1).dayOfWeek.value - 1
        val count = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        (0 until count).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { index ->
                    val day = index - leading + 1
                    val date = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                    val millis = date?.let { totals[it] } ?: 0L
                    val intensity = (millis.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    val color = if (date == null) MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        else if (date.isAfter(today)) empty.copy(alpha = 0.25f)
                        else if (millis == 0L) empty
                        else primary.copy(alpha = 0.18f + 0.62f * intensity)
                    val cell = Modifier.weight(1f).aspectRatio(1f)
                        .background(color, shape)
                    Box(cell.then(if (date == today) Modifier.border(1.dp,
                        primary.copy(alpha = 0.45f), shape) else Modifier)) {}
                }
            }
        }
    }
}
