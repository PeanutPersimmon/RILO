package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual.asSleepPoster
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SleepReportPosterTest {
    @Test fun monthlyKeepsSleepTimeAndNapWordingWithoutMissingSubjectiveFields() {
        val metrics = SleepReportMetrics(31, 3, 3, 0, 28, 1500, 450, 330,
            listOf(SleepDurationBucket("<6h", 3)), 1, 1, 100,
            listOf(SleepDurationBucket(">90m", 1)))
        val lines = SleepReportTextFormatter.monthly(YearMonth.of(2026, 10), metrics)
        assertTrue(lines.first().contains("夜间睡眠整体偏短"))
        assertTrue(lines.any { it.contains("建议先把目标设为") })
        assertTrue(lines.none { it.contains("梦") || it.contains("夜醒") || it.contains("醒来状态") })
    }

    @Test fun annualPosterUsesExistingTimeMetricsAndRecordedDayCompletionRate() {
        val metrics = SleepReportMetrics(365, 2, 1, 1, 363, 1470, 450, 480,
            listOf(SleepDurationBucket("7–9h", 1)), 2, 1, 45,
            listOf(SleepDurationBucket("31–60m", 2)))
        val poster = SleepAnnualReport(2025, metrics, listOf("客观记录。")).asSleepPoster()
        assertEquals(2, poster.totalRecordDays)
        assertEquals(1, poster.completeNightDays)
        assertEquals(0.5f, poster.completeRate)
        assertEquals("00:30", poster.averageBedTimeText)
        assertEquals("07:30", poster.averageWakeTimeText)
        assertEquals("8小时0分", poster.averageNightDurationText)
        assertEquals(1, poster.napDays)
        assertEquals(2, poster.napCount)
        assertEquals(1f, poster.nightDurationDistribution.single().percent)
        assertTrue(poster.annualSummary.none { "未记录" in it })
    }

    @Test fun monthlyObjectiveTextDoesNotAddMissingDayMetricToSleepLayout() {
        val report = SleepReportCalculator.monthly(emptyList(), emptyList(), YearMonth.of(2026, 9),
            ZoneId.of("Asia/Shanghai"), LocalDate.of(2026, 10, 15), Long.MAX_VALUE)
        assertEquals(30, report.metrics.unrecordedDays)
        assertTrue(report.summary.none { "未记录" in it })
    }
}
