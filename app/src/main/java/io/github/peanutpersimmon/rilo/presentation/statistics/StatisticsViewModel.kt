package io.github.peanutpersimmon.rilo.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod
import io.github.peanutpersimmon.rilo.domain.service.SleepCalendarMode
import io.github.peanutpersimmon.rilo.domain.service.SleepChartMode
import io.github.peanutpersimmon.rilo.domain.service.SleepTrendWindow
import io.github.peanutpersimmon.rilo.domain.usecase.GetStatisticsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StatisticsSources
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveTimelineDataChangesUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.localDayRefreshes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatisticsSection(val label: String) { CATEGORY("分类"), SLEEP("睡眠") }
enum class SleepStatisticsView(val label: String) { CALENDAR("日历"), CHART("统计") }
internal val SLEEP_CALENDAR_MODE_ORDER = listOf(
    SleepCalendarMode.WAKE,
    SleepCalendarMode.BED,
    SleepCalendarMode.DURATION,
)
internal val SLEEP_CHART_MODE_ORDER = listOf(
    SleepChartMode.WAKE,
    SleepChartMode.BED,
    SleepChartMode.NIGHT,
    SleepChartMode.NAP,
)
data class StatisticsSelection(
    val section: StatisticsSection = StatisticsSection.SLEEP,
    val categoryGranularity: StatisticsGranularity = StatisticsGranularity.WEEK,
    val categoryDates: Map<StatisticsGranularity, LocalDate> = StatisticsGranularity.entries.associateWith { LocalDate.now() },
    val categoryDetailId: Long? = null,
    val sleepCalendarMonth: YearMonth = YearMonth.now(),
    val sleepChartMonth: YearMonth = YearMonth.now(),
    val sleepTrendWindow: SleepTrendWindow = SleepTrendWindow.current(LocalDate.now()),
    val sleepView: SleepStatisticsView = SleepStatisticsView.CALENDAR,
    val sleepCalendarMode: SleepCalendarMode = SleepCalendarMode.BED,
    val sleepChartMode: SleepChartMode = SleepChartMode.WAKE,
) {
    val period: StatisticsPeriod get() = StatisticsPeriod(categoryGranularity, requireNotNull(categoryDates[categoryGranularity]))
    val sleepMonth: YearMonth get() = if (sleepView == SleepStatisticsView.CALENDAR) sleepCalendarMonth else sleepChartMonth
    fun withPeriod(value: StatisticsPeriod) = copy(categoryDates = categoryDates + (categoryGranularity to value.anchor))
    fun resetDates(today: LocalDate) = copy(
        categoryDates = StatisticsGranularity.entries.associateWith { today },
        sleepCalendarMonth = YearMonth.from(today),
        sleepChartMonth = YearMonth.from(today),
        sleepTrendWindow = SleepTrendWindow.current(today),
    )
    fun detailId(section: StatisticsSection): Long? = when (section) {
        StatisticsSection.CATEGORY -> categoryDetailId
        StatisticsSection.SLEEP -> null
    }
}

internal fun StatisticsSelection.withSection(value: StatisticsSection) = copy(section = value)
internal fun StatisticsSelection.withGranularity(value: StatisticsGranularity) =
    copy(categoryGranularity = value)
internal fun StatisticsSelection.withSleepView(value: SleepStatisticsView) = copy(sleepView = value)
internal fun StatisticsSelection.withSleepCalendarMode(value: SleepCalendarMode) = copy(sleepCalendarMode = value)
internal fun StatisticsSelection.withSleepChartMode(value: SleepChartMode) = copy(sleepChartMode = value)
internal fun StatisticsSelection.withShiftedTrendWindow(direction: Int): StatisticsSelection {
    val window = sleepTrendWindow.shifted(direction)
    return copy(sleepTrendWindow = window, sleepChartMonth = YearMonth.from(window.start))
}
private fun <T> adjacentSelection(items: List<T>, current: T, step: Int): T {
    val currentIndex = items.indexOf(current)
    return items.getOrNull(currentIndex + step.coerceIn(-1, 1)) ?: current
}
internal fun StatisticsSelection.withAdjacentGranularity(step: Int) = withGranularity(
    adjacentSelection(StatisticsGranularity.entries, period.granularity, step)
)
internal fun StatisticsSelection.withAdjacentSleepCalendarMode(step: Int) = withSleepCalendarMode(
    adjacentSelection(SLEEP_CALENDAR_MODE_ORDER, sleepCalendarMode, step)
)
internal fun StatisticsSelection.withAdjacentSleepChartMode(step: Int) = withSleepChartMode(
    adjacentSelection(SLEEP_CHART_MODE_ORDER, sleepChartMode, step)
)
sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data class Error(val message: String) : StatisticsUiState
    data class Ready(
        val selection: StatisticsSelection,
        val sources: StatisticsSources,
        val zoneId: ZoneId = ZoneId.systemDefault(),
        val pageEpoch: Long = 0L,
    ) : StatisticsUiState
}

class StatisticsViewModel(
    observeChanges: ObserveTimelineDataChangesUseCase,
    private val getStatistics: GetStatisticsUseCase,
    zone: ZoneId? = null,
    private val timeProvider: CurrentTimeProvider = SystemCurrentTimeProvider,
) : ViewModel() {
    private val fixedZone = zone
    private val zoneIds = zone?.let(::flowOf) ?: RuntimeZoneId.changes
    private val initialZone = zone ?: RuntimeZoneId.current
    private val initialDate = Instant.ofEpochMilli(timeProvider.nowMillis())
        .atZone(initialZone)
        .toLocalDate()
    private val selection = MutableStateFlow(StatisticsSelection().resetDates(initialDate))
    val currentSelection = selection.asStateFlow()
    private val pageVisible = MutableStateFlow(false)
    private val pageEpoch = MutableStateFlow(0L)
    private val localDays = localDayRefreshes(pageVisible, zoneIds, timeProvider)
    // Room's invalidation Flow emits its initial snapshot; a second synthetic emission
    // would perform the same first query twice and invalidate a just-filled result.
    private val dataRevisions = observeChanges()
        .runningFold(0L) { revision, _ -> revision + 1L }.drop(1)
    private val requests: kotlinx.coroutines.flow.StateFlow<StatisticsQueryRequest?> = combine(
        dataRevisions,
        selection
            .map { it.period to it.sleepMonth }
            .distinctUntilChanged(),
        localDays,
        pageEpoch,
    ) { revision, query, day, epoch ->
        val now = timeProvider.nowMillis()
        StatisticsQueryRequest(
            StatisticsQueryKey(query.first, query.second, day.zoneId, day.date,
                revision, epoch, Math.floorDiv(now, 60_000L)),
            now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val responses = statisticsResponses(requests) { request ->
        val key = request.key
        getStatistics(key.period, key.sleepMonth, key.zoneId, request.nowMillis)
    }

    val uiState = combine(selection, requests, responses, pageEpoch) { selected, request, response, epoch ->
        StatisticsDisplayInput(selected, request, response, epoch)
    }.runningFold(StatisticsUiState.Loading as StatisticsUiState) { previous, input ->
        statisticsDisplayedState(previous, input.selection, input.request, input.response, input.pageEpoch)
    }.drop(1).catch { emit(StatisticsUiState.Error(it.message ?: "统计数据加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    fun selectSection(section: StatisticsSection) = selection.update { it.withSection(section) }
    fun selectGranularity(granularity: StatisticsGranularity) = selection.update { it.withGranularity(granularity) }
    fun stepGranularity(step: Int) = selection.update { it.withAdjacentGranularity(step) }
    fun shiftPeriod(direction: Long) = selection.update { it.withPeriod(it.period.shifted(direction)) }
    fun selectDate(date: LocalDate) = selection.update { it.withPeriod(it.period.copy(anchor = date)) }
    fun openDetail(section: StatisticsSection, id: Long?) = selection.update {
        when (section) {
            StatisticsSection.CATEGORY -> it.copy(categoryDetailId = id)
            StatisticsSection.SLEEP -> it
        }
    }
    fun closeDetail(section: StatisticsSection) = openDetail(section, null)
    fun shiftSleepMonth(direction: Long) = selection.update {
        if (it.sleepView == SleepStatisticsView.CALENDAR) it.copy(sleepCalendarMonth = it.sleepCalendarMonth.plusMonths(direction))
        else it.copy(sleepChartMonth = it.sleepChartMonth.plusMonths(direction))
    }
    fun shiftSleepTrendWindow(direction: Int) = selection.update { it.withShiftedTrendWindow(direction) }
    fun selectSleepView(view: SleepStatisticsView) = selection.update { it.withSleepView(view) }
    fun selectSleepCalendarMode(mode: SleepCalendarMode) = selection.update { it.withSleepCalendarMode(mode) }
    fun selectSleepChartMode(mode: SleepChartMode) = selection.update { it.withSleepChartMode(mode) }
    fun stepSleepCalendarMode(step: Int) = selection.update { it.withAdjacentSleepCalendarMode(step) }
    fun stepSleepChartMode(step: Int) = selection.update { it.withAdjacentSleepChartMode(step) }
    fun setPageVisible(visible: Boolean) {
        if (visible && !pageVisible.value) {
            pageEpoch.update { it + 1L }
            val currentZone = fixedZone ?: RuntimeZoneId.current
            val today = Instant.ofEpochMilli(timeProvider.nowMillis()).atZone(currentZone).toLocalDate()
            selection.update { it.resetDates(today) }
        }
        pageVisible.value = visible
    }

    fun isPageStale(state: StatisticsUiState.Ready): Boolean = state.pageEpoch != pageEpoch.value

    companion object {
        fun factory(observeChanges: ObserveTimelineDataChangesUseCase, getStatistics: GetStatisticsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(observeChanges, getStatistics) as T
            }
    }
}
