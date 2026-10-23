package io.github.peanutpersimmon.rilo.presentation.statistics

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.service.ActualStatistics
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.StatisticsSources
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StatisticsUiStateTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val zone = ZoneOffset.UTC
    private val initial = StatisticsSelection().resetDates(today).withSection(StatisticsSection.CATEGORY)

    private fun request(selection: StatisticsSelection, revision: Long = 1, epoch: Long = 1) =
        StatisticsQueryRequest(StatisticsQueryKey(selection.period, selection.sleepMonth, zone, today,
            revision, epoch, 1), 60_000L)

    private fun response(selection: StatisticsSelection, revision: Long = 1, epoch: Long = 1): StatisticsQueryResponse =
        StatisticsQueryResponse(request(selection, revision, epoch), DomainResult.Success(StatisticsSources(
            ActualStatistics(emptyList(), selection.period, zone), emptyList(), emptyList(),
            ClassificationLabels(emptyMap()),
        )))

    private fun display(previous: StatisticsUiState, selection: StatisticsSelection,
        request: StatisticsQueryRequest?, response: StatisticsQueryResponse?, epoch: Long = 1,
    ) = statisticsDisplayedState(previous, selection, request, response, epoch)

    @Test fun firstLoadAndEmptyResult() {
        assertSame(StatisticsUiState.Loading, display(StatisticsUiState.Loading, initial, null, null))
        val ready = display(StatisticsUiState.Loading, initial, request(initial), response(initial)) as StatisticsUiState.Ready
        assertEquals(0L, ready.sources.actual.totalMillis)
    }

    @Test fun periodAndDateChangeKeepHeaderAndContentTogether() {
        var shown = display(StatisticsUiState.Loading, initial, request(initial), response(initial))
        for (granularity in listOf(StatisticsGranularity.MONTH, StatisticsGranularity.YEAR, StatisticsGranularity.DAY)) {
            val previousPeriod = (shown as StatisticsUiState.Ready).selection.period
            val next = initial.withGranularity(granularity)
            shown = display(shown, next, request(next), response(initial))
            assertEquals(previousPeriod, (shown as StatisticsUiState.Ready).selection.period)
            shown = display(shown, next, request(next), response(next))
            assertEquals(next.period, (shown as StatisticsUiState.Ready).sources.actual.period)
        }
        val day = initial.withGranularity(StatisticsGranularity.DAY)
        val shifted = day.withPeriod(day.period.copy(anchor = today.minusDays(1)))
        shown = display(shown, shifted, request(shifted), response(day))
        assertEquals(today, (shown as StatisticsUiState.Ready).selection.period.anchor)
        shown = display(shown, shifted, request(shifted), response(shifted))
        assertEquals(today.minusDays(1), (shown as StatisticsUiState.Ready).sources.actual.period.anchor)
    }

    @Test fun rapidSwitchAndDataRefreshDoNotExposeWrongResult() {
        val loaded = display(StatisticsUiState.Loading, initial, request(initial), response(initial))
        val month = initial.withGranularity(StatisticsGranularity.MONTH)
        val year = initial.withGranularity(StatisticsGranularity.YEAR)
        val pending = display(loaded, year, request(year), response(month)) as StatisticsUiState.Ready
        assertEquals(initial.period, pending.selection.period)
        val settled = display(pending, year, request(year), response(year)) as StatisticsUiState.Ready
        assertEquals(year.period, settled.sources.actual.period)
        val changedRequest = request(year, revision = 2)
        assertSame(settled, display(settled, year, changedRequest, response(year)))
        val refreshed = display(settled, year, changedRequest, response(year, revision = 2)) as StatisticsUiState.Ready
        assertEquals(year.period, refreshed.selection.period)
    }

    @Test fun sameQueryDetailAndNewPageEpoch() {
        val loaded = display(StatisticsUiState.Loading, initial, request(initial), response(initial))
        val detail = initial.copy(categoryDetailId = 9L)
        assertEquals(9L, (display(loaded, detail, request(initial), response(initial)) as StatisticsUiState.Ready)
            .selection.categoryDetailId)
        assertSame(StatisticsUiState.Loading,
            display(loaded, initial, request(initial, epoch = 2), response(initial), epoch = 2))
        assertEquals(2L, (display(StatisticsUiState.Loading, initial, request(initial, epoch = 2),
            response(initial, epoch = 2), epoch = 2) as StatisticsUiState.Ready).pageEpoch)
    }
}
