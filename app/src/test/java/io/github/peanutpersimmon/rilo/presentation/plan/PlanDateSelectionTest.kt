package io.github.peanutpersimmon.rilo.presentation.plan

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PlanDateSelectionTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun initial() = initialPlanRuntimeSelection(PlanViewModel.DEFAULT_VIEW_ORDER, today)

    @Test fun monthDefaultsToRecordAndManualPlanSelectionSurvivesViewAndDateChanges() {
        assertEquals(MonthContent.RECORD, PlanUiState().monthContent)
        val manuallySelected = PlanUiState(mode = PlanViewMode.MONTH, monthContent = MonthContent.PLAN)
        assertEquals(MonthContent.PLAN, manuallySelected.copy(mode = PlanViewMode.DAY)
            .copy(mode = PlanViewMode.MONTH).monthContent)
        val reenteredDates = initial().withMode(PlanViewMode.MONTH).withDate(today.minusMonths(2))
            .resetDates(today.plusDays(1))
        assertEquals(PlanViewMode.MONTH, reenteredDates.mode)
        assertEquals(MonthContent.PLAN, manuallySelected.copy(selectedDate = reenteredDates.selectedDate).monthContent)
    }

    @Test fun fiveViewsKeepIndependentDatesAndSwitchingDoesNotReset() {
        var selected = initial()
        PlanViewMode.entries.forEachIndexed { index, mode ->
            selected = selected.withMode(mode).withDate(today.minusDays((index + 1).toLong()))
        }
        PlanViewMode.entries.forEachIndexed { index, mode ->
            assertEquals(today.minusDays((index + 1).toLong()), selected.withMode(mode).selectedDate)
        }
        assertEquals(5, selected.dates.size)
    }

    @Test fun eachViewStepsAndMonthSelectionOnlyChangeOwnAnchor() {
        val before = initial()
        val changed = before.withMode(PlanViewMode.WEEK).withStep(-1)
            .withMode(PlanViewMode.BOARD).withStep(-1)
            .withMode(PlanViewMode.MONTH).withMonth(YearMonth.of(2026, 8))
        assertEquals(today, changed.withMode(PlanViewMode.DAY).selectedDate)
        assertEquals(today, changed.withMode(PlanViewMode.TODO_DONE).selectedDate)
        assertEquals(today.minusWeeks(1), changed.withMode(PlanViewMode.WEEK).selectedDate)
        assertEquals(today.minusWeeks(1), changed.withMode(PlanViewMode.BOARD).selectedDate)
        assertEquals(YearMonth.of(2026, 8), YearMonth.from(changed.selectedDate))
    }

    @Test fun explicitMonthCellNavigationChangesOnlyDestinationDay() {
        val august = today.minusMonths(2)
        val target = LocalDate.of(2026, 8, 15)
        val fromMonth = initial().withMode(PlanViewMode.MONTH).withDate(august)
        val opened = fromMonth.withMode(PlanViewMode.TODO_DONE).withDate(target)
        assertEquals(target, opened.selectedDate)
        assertEquals(august, opened.withMode(PlanViewMode.MONTH).selectedDate)
        assertEquals(today, opened.withMode(PlanViewMode.DAY).selectedDate)
    }

    @Test fun rootReentryResetsAllDatesToLatestLocalDateAndPreservesMode() {
        val history = initial().withMode(PlanViewMode.DAY).withDate(today.minusDays(10))
            .withMode(PlanViewMode.MONTH).withDate(today.minusMonths(2))
        val tomorrow = today.plusDays(1)
        val reset = history.resetDates(tomorrow)
        assertEquals(PlanViewMode.MONTH, reset.mode)
        assertTrue(reset.dates.values.all { it == tomorrow })
        assertEquals(today.minusMonths(2), history.selectedDate)
        assertEquals(YearMonth.of(2026, 10), YearMonth.from(reset.selectedDate))
    }

    @Test fun backgroundAndMidnightDoNotMutateSelectionUntilRootReentry() {
        val history = initial().withMode(PlanViewMode.DAY).withDate(today.minusDays(3))
        assertEquals(today.minusDays(3), history.selectedDate)
        assertEquals(today.plusDays(1), history.resetDates(today.plusDays(1)).selectedDate)
    }

    @Test fun changedZoneReentryUsesDateFromNewZone() {
        val millis = java.time.Instant.parse("2026-10-19T16:30:00Z").toEpochMilli()
        val shanghai = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
        val honolulu = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("Pacific/Honolulu")).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 20), shanghai)
        assertEquals(LocalDate.of(2026, 10, 19), honolulu)
        assertTrue(initial().resetDates(honolulu).dates.values.all { it == honolulu })
    }

    @Test fun staleFrameUsesSelectedViewDateAndZone() {
        val selection = initial().withMode(PlanViewMode.DAY).withDate(today.minusDays(1))
        val zone = ZoneId.of("Asia/Shanghai")
        val old = PlanUiState(selectedDate = today, mode = PlanViewMode.DAY, zoneId = zone)
        assertTrue(selection.hasStaleVisibleState(old, zone))
        assertFalse(selection.hasStaleVisibleState(old.copy(selectedDate = today.minusDays(1)), zone))
        assertTrue(selection.hasStaleVisibleState(old.copy(selectedDate = today.minusDays(1)), ZoneId.of("Pacific/Honolulu")))
    }
}
