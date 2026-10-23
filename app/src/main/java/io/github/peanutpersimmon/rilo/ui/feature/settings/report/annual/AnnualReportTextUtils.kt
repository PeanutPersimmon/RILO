package io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual

import kotlin.math.roundToInt

object AnnualReportTextUtils {
    fun formatPercent(percent: Float): String = "${(percent.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    fun formatDuration(minutes: Int): String =
        if (minutes > 0) "${minutes / 60}小时${minutes % 60}分" else "暂无数据"
    fun formatNapDuration(minutes: Int): String = if (minutes > 0) "${minutes}分钟" else "暂无数据"
    fun shortNapLabel(label: String): String = when (label) {
        "≤30m" -> "0–30m"
        "31–60m" -> "30–60m"
        "61–90m" -> "60–90m"
        ">90m" -> ">90m"
        else -> label
    }
}
