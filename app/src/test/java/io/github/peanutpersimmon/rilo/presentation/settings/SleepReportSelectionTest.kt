package io.github.peanutpersimmon.rilo.presentation.settings

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepReportSelectionTest {
    private val current = YearMonth.of(2026, 10)

    @Test fun currentYearExcludesFutureMonthsAndPastYearHasAllMonths() {
        assertEquals((1..10).toList(), reportSelectableMonths(2026, current))
        assertEquals((1..12).toList(), reportSelectableMonths(2025, current))
    }

    @Test fun changingToCurrentYearClampsPreviouslySelectedMonth() {
        assertEquals(YearMonth.of(2026, 10),
            reportMonthForYear(YearMonth.of(2025, 12), 2026, current))
        assertEquals(YearMonth.of(2024, 10),
            reportMonthForYear(YearMonth.of(2026, 10), 2024, current))
    }

    @Test fun yearsRunFromEarliestSleepYearToCurrentAndNoDataStillShowsCurrent() {
        assertEquals(listOf(2024, 2025, 2026), reportSelectableYears(2024, current))
        assertEquals(listOf(2026), reportSelectableYears(2026, current))
    }
}
