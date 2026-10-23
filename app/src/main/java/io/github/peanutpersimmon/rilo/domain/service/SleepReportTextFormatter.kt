package io.github.peanutpersimmon.rilo.domain.service

import java.time.YearMonth

/** Sleep's report wording adapted to the time and Nap fields available in RILO. */
object SleepReportTextFormatter {
    fun monthly(@Suppress("UNUSED_PARAMETER") month: YearMonth, metrics: SleepReportMetrics): List<String> {
        if (metrics.recordedNightDays < 3) return listOf(
            "本月记录较少，暂时无法生成完整趋势。继续记录后可以在这里看到更清晰的睡眠变化。",
            "先继续积累记录，数据多一些后趋势会更清楚。",
        )
        val durationText = when {
            metrics.averageNightMinutes == null -> "夜间睡眠时长记录还不够完整"
            metrics.averageNightMinutes < 360 -> "夜间睡眠整体偏短"
            metrics.averageNightMinutes in 420..540 -> "夜间睡眠时长整体较稳定"
            else -> "夜间睡眠时长有一定波动"
        }
        val bedText = metrics.averageBedMinutes?.let {
            if (it >= 1440) "平均入睡 ${SleepStatisticsCalculator.clock(it)}，入睡时间略偏晚"
            else "平均入睡 ${SleepStatisticsCalculator.clock(it)}，作息节奏保持得比较平稳"
        } ?: "入睡时间记录还不够完整"
        val napText = when {
            metrics.napCount == 0 -> "本月几乎没有午睡记录，白天休息主要依赖夜间睡眠"
            (metrics.averageNapMinutes ?: 0) > 90 ->
                "午睡平均 ${formatDuration(metrics.averageNapMinutes)}，时长偏长"
            metrics.averageNapMinutes != null ->
                "午睡平均 ${formatDuration(metrics.averageNapMinutes)}，整体更像是白天的短时补充"
            else -> "午睡记录整体更像是白天的短时补充"
        }
        val conclusion = "本月${durationText}，${bedText}。${napText}。"
        val suggestions = buildList {
            metrics.averageBedMinutes?.takeIf { it >= 1440 }?.let {
                add("本月平均入睡 ${SleepStatisticsCalculator.clock(it)}，偏晚；建议先把目标设为 ${SleepStatisticsCalculator.clock(it - 20)} 前。")
            }
            metrics.averageNapMinutes?.takeIf { it > 90 }?.let {
                add("午睡平均 ${formatDuration(it)}，偏长；若夜间入睡困难，可尝试控制在 30-60 分钟。")
            }
            metrics.averageNightMinutes?.takeIf { it < 360 }?.let {
                add("本月平均睡眠 ${formatDuration(it)}，偏短；可以优先回看连续短睡眠的日期。")
            }
            if (metrics.napCount <= 2) add("本月午睡较少，白天休息主要依赖夜间睡眠。")
        }.take(2).ifEmpty { listOf("继续保持记录节奏，优先观察入睡时间和睡眠时长的稳定性。") }
        return listOf(conclusion) + suggestions
    }

    fun annual(@Suppress("UNUSED_PARAMETER") year: Int, metrics: SleepReportMetrics): List<String> = buildList {
        add(when {
            metrics.completedNightDays < 30 -> "该年度记录天数较少，年度结论仅供参考。"
            metrics.completionRate >= 0.8f -> "这一年的睡眠记录较稳定，完整记录占比很高。"
            metrics.completionRate >= 0.5f -> "这一年保留了不少睡眠记录，可以继续提高完整度。"
            else -> "这一年的记录还比较零散，可以从固定打卡开始。"
        })
        metrics.nightDurationBuckets.maxByOrNull { it.count }?.takeIf { it.count > 0 }?.let {
            add("睡眠时长主要集中在 ${it.label} 区间。")
        }
        add(when {
            metrics.napDays == 0 -> "该年度午睡记录较少，白天休息节奏比较轻。"
            metrics.napDays <= 2 -> "午睡记录较少，可继续观察习惯变化。"
            else -> {
                val main = metrics.napDurationBuckets.maxByOrNull { it.count }?.takeIf { it.count > 0 }
                if (main != null) "午睡习惯较稳定，多集中在 ${main.label}。"
                else "该年度有午睡记录。"
            }
        })
    }

    fun annualSleepWake(metrics: SleepReportMetrics): List<String> {
        if (metrics.averageBedMinutes == null && metrics.averageWakeMinutes == null) return listOf(
            "该年度的入睡与起床时间记录还不够完整。",
            "继续记录后，可以看到更清晰的作息节奏。",
        )
        val small = metrics.recordedNightDays < 30 || metrics.completedNightDays < 30
        return buildList {
            metrics.averageBedMinutes?.let {
                add(when {
                    small -> "当前记录显示入睡时间较规律。"
                    it >= 1500 -> "平均入睡时间明显偏晚，整体有晚睡倾向。"
                    it >= 1410 -> "平均入睡时间略偏晚，作息还有前移空间。"
                    else -> "平均入睡时间较规律，夜间节奏保持得不错。"
                })
            }
            metrics.averageWakeMinutes?.let {
                add(when {
                    small && it >= 570 -> "当前记录显示起床时间略偏晚。"
                    small -> "当前记录显示起床时间较稳定。"
                    it >= 570 -> "平均起床时间偏晚，适合循序渐进地调整。"
                    it < 510 -> "平均起床时间较早，早晨节奏比较清爽。"
                    else -> "平均起床时间较稳定，日常节奏比较平衡。"
                })
            }
            add(if (small) "建议继续记录，以便形成更稳定的年度判断。"
                else "建议每次提前 15-20 分钟入睡，循序调整作息。")
        }.take(3)
    }

    private fun formatDuration(minutes: Int?): String = if (minutes == null || minutes <= 0) "暂无数据"
        else if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分钟"
}
