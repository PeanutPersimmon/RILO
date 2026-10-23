package io.github.peanutpersimmon.rilo.presentation.statistics

import io.github.peanutpersimmon.rilo.domain.service.SleepCalendarMode
import io.github.peanutpersimmon.rilo.domain.service.SleepChartMode
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class StatisticsSelectionTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun initial() = StatisticsSelection().resetDates(today)

    @Test fun categoryGranularitiesKeepIndependentAnchors() {
        var state = initial()
        StatisticsGranularity.entries.forEachIndexed { index, granularity ->
            state = state.withGranularity(granularity)
            state = state.withPeriod(state.period.copy(anchor = today.minusMonths((index + 1).toLong())))
        }
        StatisticsGranularity.entries.forEachIndexed { index, granularity ->
            assertEquals(today.minusMonths((index + 1).toLong()), state.withGranularity(granularity).period.anchor)
        }
    }

    @Test fun switchingCategoryGranularityDoesNotChangeOtherDates() {
        val week = initial().withPeriod(initial().period.copy(anchor = today.minusWeeks(2)))
        val month = week.withGranularity(StatisticsGranularity.MONTH)
        assertEquals(today, month.period.anchor)
        assertEquals(today.minusWeeks(2), month.withGranularity(StatisticsGranularity.WEEK).period.anchor)
    }

    @Test fun sleepCalendarChartAndLineWindowStayIndependent() {
        val state = initial().copy(
            sleepCalendarMonth = YearMonth.of(2026, 8),
            sleepChartMonth = YearMonth.of(2026, 9),
            sleepTrendWindow = initial().sleepTrendWindow.shifted(-2),
        )
        assertEquals(YearMonth.of(2026, 8), state.sleepMonth)
        val chart = state.withSleepView(SleepStatisticsView.CHART).withSleepChartMode(SleepChartMode.NAP)
        assertEquals(YearMonth.of(2026, 9), chart.sleepMonth)
        assertEquals(state.sleepTrendWindow, chart.withSleepChartMode(SleepChartMode.WAKE).sleepTrendWindow)
        assertEquals(YearMonth.of(2026, 8), chart.withSleepView(SleepStatisticsView.CALENDAR).sleepMonth)
    }

    @Test fun rootReentryResetsEveryDateButPreservesModesAndMetrics() {
        val history = initial().withGranularity(StatisticsGranularity.YEAR)
            .withPeriod(initial().withGranularity(StatisticsGranularity.YEAR).period.copy(anchor = LocalDate.of(2025, 1, 1)))
            .copy(section = StatisticsSection.CATEGORY,
                sleepView = SleepStatisticsView.CHART,
                sleepCalendarMode = SleepCalendarMode.DURATION,
                sleepChartMode = SleepChartMode.NIGHT,
                sleepCalendarMonth = YearMonth.of(2026, 8),
                sleepChartMonth = YearMonth.of(2026, 9))
        val next = history.resetDates(today.plusDays(1))
        assertTrue(next.categoryDates.values.all { it == today.plusDays(1) })
        assertEquals(YearMonth.of(2026, 10), next.sleepCalendarMonth)
        assertEquals(YearMonth.of(2026, 10), next.sleepChartMonth)
        assertEquals(StatisticsSection.CATEGORY, next.section)
        assertEquals(StatisticsGranularity.YEAR, next.period.granularity)
        assertEquals(SleepStatisticsView.CHART, next.sleepView)
        assertEquals(SleepCalendarMode.DURATION, next.sleepCalendarMode)
        assertEquals(SleepChartMode.NIGHT, next.sleepChartMode)
    }

    @Test fun midnightAndZoneOnlyAffectDatesWhenRootReenters() {
        val history = initial().withPeriod(initial().period.copy(anchor = today.minusDays(4)))
        assertEquals(today.minusDays(4), history.period.anchor)
        val instant = java.time.Instant.parse("2026-10-19T16:30:00Z")
        val shanghai = instant.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate()
        val honolulu = instant.atZone(java.time.ZoneId.of("Pacific/Honolulu")).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 20), history.resetDates(shanghai).period.anchor)
        assertEquals(today, history.resetDates(honolulu).period.anchor)
    }

    @Test fun changingSleepModesDoesNotResetChartPosition() {
        val state = initial().copy(sleepChartMonth = YearMonth.of(2026, 8))
            .withSleepView(SleepStatisticsView.CHART)
            .withSleepChartMode(SleepChartMode.BED)
            .withAdjacentSleepChartMode(1)
        assertEquals(YearMonth.of(2026, 8), state.sleepMonth)
        assertEquals(initial().sleepTrendWindow, state.sleepTrendWindow)
    }

    @Test fun lineNavigationChoosesItsMonthForBarsButKeepsSevenDayWindow() {
        val initial = initial().withSleepView(SleepStatisticsView.CHART)
        val afterLineNavigation = (1..8).fold(initial) { state, _ -> state.withShiftedTrendWindow(-1) }
        val augustWindow = afterLineNavigation.sleepTrendWindow
        assertEquals(YearMonth.from(augustWindow.start),
            afterLineNavigation.withSleepChartMode(SleepChartMode.NIGHT).sleepMonth)
        assertEquals(augustWindow,
            afterLineNavigation.withSleepChartMode(SleepChartMode.WAKE).sleepTrendWindow)
    }
}
