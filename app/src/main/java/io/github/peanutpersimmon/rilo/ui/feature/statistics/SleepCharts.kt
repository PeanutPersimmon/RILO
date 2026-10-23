package io.github.peanutpersimmon.rilo.ui.feature.statistics

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.domain.service.SleepPoint
import io.github.peanutpersimmon.rilo.domain.service.SleepStatisticsCalculator
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
internal fun SleepLineChart(points: List<SleepPoint>) {
    val values = points.mapNotNull { it.valueMinutes }
    if (values.isEmpty()) {
        Text("暂无数据", style = AppTheme.type.bodyCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val color = sleepChartColor()
    val minValue = values.min() - 60
    val maxValue = values.max() + 60
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val left = 20.dp.toPx()
        val right = size.width - left
        val top = 28.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val chartHeight = bottom - top
        val step = if (points.size > 1) (right - left) / (points.size - 1) else 0f
        repeat(4) { index ->
            val y = top + chartHeight * index / 3f
            drawLine(grid, Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
        var line = Path()
        var area = Path()
        var started = false
        var firstX = 0f
        var lastX = 0f
        fun flush() {
            if (!started) return
            area.lineTo(lastX, bottom)
            area.lineTo(firstX, bottom)
            area.close()
            drawPath(area, Brush.verticalGradient(
                listOf(color.copy(alpha = 0.12f), color.copy(alpha = 0.02f)), top, bottom))
            drawPath(line, color.copy(alpha = 0.82f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            line = Path()
            area = Path()
            started = false
        }
        points.forEachIndexed { index, point ->
            val value = point.valueMinutes
            if (value == null) flush() else {
                val x = left + step * index
                val y = bottom - (value - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1) * chartHeight
                if (!started) {
                    line.moveTo(x, y)
                    area.moveTo(x, bottom)
                    area.lineTo(x, y)
                    firstX = x
                    started = true
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
                lastX = x
            }
        }
        flush()
        points.forEachIndexed { index, point ->
            val x = left + step * index
            val value = point.valueMinutes
            if (value != null) {
                val y = bottom - (value - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1) * chartHeight
                drawCircle(color.copy(alpha = 0.86f), 3.dp.toPx(), Offset(x, y))
                drawSleepText(textPaint, SleepStatisticsCalculator.clock(value), x, y - 10.dp.toPx(), color, 11.sp.toPx())
            }
            drawSleepText(textPaint, point.date.monthValue.toString() + "-" + point.date.dayOfMonth,
                x, size.height - 6.dp.toPx(), axis, 12.sp.toPx())
        }
    }
}

@Composable
internal fun SleepBarChart(points: List<SleepPoint>, nap: Boolean) {
    if (points.none { it.valueMinutes != null }) {
        Text("暂无数据", style = AppTheme.type.bodyCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val baseColor = MaterialTheme.colorScheme.onSurface
    val paleColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(if (nap) 110.dp else 176.dp)) {
        val left = 16.dp.toPx()
        val right = size.width - left
        val top = 16.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val max = maxOf(60, points.mapNotNull { it.valueMinutes }.maxOrNull() ?: 0)
        val slot = (right - left) / points.size
        val barWidth = (slot * 0.18f).coerceIn(3.dp.toPx(), 5.dp.toPx())
        drawLine(grid, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        points.forEachIndexed { index, point ->
            val x = left + slot * (index + 0.5f)
            point.valueMinutes?.let { minutes ->
                val height = minutes.toFloat() / max * (bottom - top)
                drawRoundRect(Brush.verticalGradient(
                    listOf(baseColor.copy(alpha = 0.92f), paleColor.copy(alpha = 0.08f)),
                    bottom - height, bottom),
                    Offset(x - barWidth / 2, bottom - height), Size(barWidth, height),
                    CornerRadius(barWidth / 2, barWidth / 2))
            }
            if (point.date.dayOfMonth in listOf(1, 10, 20, 30)) {
                drawSleepText(textPaint, point.date.dayOfMonth.toString(), x, size.height - 6.dp.toPx(),
                    axis, 12.sp.toPx())
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSleepText(
    paint: Paint, value: String, x: Float, y: Float, color: Color, size: Float,
) {
    paint.textSize = size
    paint.color = color.toArgb()
    paint.textAlign = Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)
}