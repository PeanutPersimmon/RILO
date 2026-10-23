package io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual

import io.github.peanutpersimmon.rilo.domain.service.SleepAnnualReport
import io.github.peanutpersimmon.rilo.domain.service.SleepDurationBucket
import io.github.peanutpersimmon.rilo.domain.service.SleepStatisticsCalculator
import io.github.peanutpersimmon.rilo.domain.service.SleepReportTextFormatter

data class PieSliceData(val label: String, val value: Int, val percent: Float)

data class AnnualSleepReport(
    val year: Int,
    val totalRecordDays: Int,
    val completeNightDays: Int,
    val completeRate: Float,
    val averageBedTimeText: String,
    val averageWakeTimeText: String,
    val averageNightDurationText: String,
    val nightDurationDistribution: List<PieSliceData>,
    val napDays: Int,
    val napCount: Int,
    val averageNapDurationText: String,
    val napDurationDistribution: List<PieSliceData>,
    val sleepWakeSummary: List<String>,
    val annualSummary: List<String>,
)

fun SleepAnnualReport.asSleepPoster(): AnnualSleepReport = AnnualSleepReport(
    year = year,
    totalRecordDays = metrics.recordedNightDays,
    completeNightDays = metrics.completedNightDays,
    completeRate = metrics.completionRate,
    averageBedTimeText = metrics.averageBedMinutes?.let(SleepStatisticsCalculator::clock) ?: "暂无数据",
    averageWakeTimeText = metrics.averageWakeMinutes?.let(SleepStatisticsCalculator::clock) ?: "暂无数据",
    averageNightDurationText = metrics.averageNightMinutes?.let(AnnualReportTextUtils::formatDuration) ?: "暂无数据",
    nightDurationDistribution = metrics.nightDurationBuckets.slices(),
    napDays = metrics.napDays,
    napCount = metrics.napCount,
    averageNapDurationText = metrics.averageNapMinutes?.let(AnnualReportTextUtils::formatNapDuration) ?: "暂无数据",
    napDurationDistribution = metrics.napDurationBuckets.slices(),
    sleepWakeSummary = SleepReportTextFormatter.annualSleepWake(metrics),
    annualSummary = summary,
)

private fun List<SleepDurationBucket>.slices(): List<PieSliceData> {
    val total = sumOf { it.count }
    return filter { it.count > 0 }.map {
        PieSliceData(it.label, it.count, if (total > 0) it.count.toFloat() / total else 0f)
    }
}
