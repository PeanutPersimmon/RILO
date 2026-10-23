package io.github.peanutpersimmon.rilo.domain.service

import java.time.LocalDate
import java.time.YearMonth

data class SleepDurationBucket(val label: String, val count: Int)

data class SleepReportMetrics(
    val elapsedDays: Int,
    val recordedNightDays: Int,
    val completedNightDays: Int,
    val incompleteNightDays: Int,
    val unrecordedDays: Int,
    val averageBedMinutes: Int?,
    val averageWakeMinutes: Int?,
    val averageNightMinutes: Int?,
    val nightDurationBuckets: List<SleepDurationBucket>,
    val napCount: Int,
    val napDays: Int,
    val averageNapMinutes: Int?,
    val napDurationBuckets: List<SleepDurationBucket>,
) {
    /** Denominator is recorded Night days, never calendar days. */
    val completionRate: Float get() = if (recordedNightDays == 0) 0f
        else completedNightDays.toFloat() / recordedNightDays
}

data class SleepReportDay(
    val date: LocalDate,
    val bedMinutes: Int?,
    val wakeMinutes: Int?,
    val nightMinutes: Int?,
    val napMinutes: Int?,
)

sealed interface SleepReport {
    val metrics: SleepReportMetrics
    val summary: List<String>
}

data class SleepMonthlyReport(
    val month: YearMonth,
    override val metrics: SleepReportMetrics,
    val days: List<SleepReportDay>,
    override val summary: List<String>,
) : SleepReport

data class SleepAnnualReport(
    val year: Int,
    override val metrics: SleepReportMetrics,
    override val summary: List<String>,
) : SleepReport
