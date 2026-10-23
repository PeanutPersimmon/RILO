package io.github.peanutpersimmon.rilo.presentation.statistics

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.service.ActualStatistics
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.StatisticsSources
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsQueryFlowTest {
    private val day = LocalDate.of(2026, 10, 19)
    private val zone = ZoneOffset.UTC
    private fun request(granularity: StatisticsGranularity, revision: Long = 1L): StatisticsQueryRequest {
        val selected = StatisticsSelection().resetDates(day).withGranularity(granularity)
        return StatisticsQueryRequest(StatisticsQueryKey(selected.period, selected.sleepMonth,
            zone, day, revision, 1L, 1L), 60_000L)
    }
    private fun sources(request: StatisticsQueryRequest) = StatisticsSources(
        ActualStatistics(emptyList(), request.key.period, zone), emptyList(), emptyList(),
        ClassificationLabels(emptyMap()),
    )

    @Test fun rapidSelectionsCancelOldQueriesAndDataChangeReloads() = runTest {
        val old = request(StatisticsGranularity.DAY)
        val latest = request(StatisticsGranularity.MONTH)
        val changedData = request(StatisticsGranularity.MONTH, revision = 2L)
        val requests = MutableStateFlow<StatisticsQueryRequest?>(null)
        val received = mutableListOf<StatisticsQueryResponse>()
        val loaded = mutableListOf<StatisticsQueryKey>()
        backgroundScope.launch {
            statisticsResponses(requests) { query ->
                loaded += query.key
                delay(if (query.key == old.key) 1_000L else 10L)
                DomainResult.Success(sources(query))
            }.collect(received::add)
        }
        runCurrent()
        requests.value = old
        runCurrent()
        requests.value = latest
        runCurrent()
        advanceTimeBy(10L)
        runCurrent()
        assertEquals(listOf(latest.key), received.map { it.request.key })
        requests.value = changedData
        runCurrent()
        advanceTimeBy(10L)
        runCurrent()
        assertEquals(listOf(latest.key, changedData.key), received.map { it.request.key })
        assertEquals(3, loaded.size)
    }
}
