package io.github.peanutpersimmon.rilo.presentation.statistics

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod
import io.github.peanutpersimmon.rilo.domain.usecase.StatisticsSources
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest

internal data class StatisticsQueryKey(
    val period: StatisticsPeriod,
    val sleepMonth: YearMonth,
    val zoneId: ZoneId,
    val localDay: LocalDate,
    val dataRevision: Long,
    val pageEpoch: Long,
    val asOfMinute: Long,
)

internal data class StatisticsQueryRequest(val key: StatisticsQueryKey, val nowMillis: Long)
internal data class StatisticsQueryResponse(
    val request: StatisticsQueryRequest,
    val result: DomainResult<StatisticsSources>,
)

internal data class StatisticsDisplayInput(
    val selection: StatisticsSelection,
    val request: StatisticsQueryRequest?,
    val response: StatisticsQueryResponse?,
    val pageEpoch: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun statisticsResponses(
    requests: Flow<StatisticsQueryRequest?>,
    load: suspend (StatisticsQueryRequest) -> DomainResult<StatisticsSources>,
): Flow<StatisticsQueryResponse> = requests.filterNotNull().mapLatest { request ->
    val result = load(request)
    currentCoroutineContext().ensureActive()
    StatisticsQueryResponse(request, result)
}

/** Keep the last matched header and content together until the newest query has completed. */
internal fun statisticsDisplayedState(
    previous: StatisticsUiState,
    selection: StatisticsSelection,
    request: StatisticsQueryRequest?,
    response: StatisticsQueryResponse?,
    pageEpoch: Long,
): StatisticsUiState {
    if (request == null || request.key.pageEpoch != pageEpoch) return StatisticsUiState.Loading
    val matching = response?.takeIf {
        it.request.key == request.key &&
            request.key.period == selection.period &&
            request.key.sleepMonth == selection.sleepMonth
    }
    if (matching != null) {
        return when (val result = matching.result) {
            is DomainResult.Success -> StatisticsUiState.Ready(selection, result.value, request.key.zoneId, pageEpoch)
            is DomainResult.Failure -> StatisticsUiState.Error(result.error.toString())
        }
    }
    val ready = previous as? StatisticsUiState.Ready
    if (ready == null || ready.pageEpoch != pageEpoch) return StatisticsUiState.Loading
    return if (ready.selection.period == selection.period && ready.selection.sleepMonth == selection.sleepMonth) {
        if (ready.selection == selection) ready else ready.copy(selection = selection)
    } else ready
}
