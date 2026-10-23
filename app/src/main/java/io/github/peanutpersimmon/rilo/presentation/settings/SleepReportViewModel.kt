package io.github.peanutpersimmon.rilo.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.domain.service.SleepReport
import io.github.peanutpersimmon.rilo.domain.usecase.GetSleepReportUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveTimelineDataChangesUseCase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SleepReportPeriod { MONTHLY, ANNUAL }

data class SleepReportUiState(
    val period: SleepReportPeriod? = null,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val earliestYear: Int = LocalDate.now().year,
    val loading: Boolean = false,
    val report: SleepReport? = null,
    val error: String? = null,
)

internal fun reportSelectableMonths(year: Int, current: YearMonth): List<Int> =
    (1..if (year == current.year) current.monthValue else 12).toList()

internal fun reportSelectableYears(earliestYear: Int, current: YearMonth): List<Int> =
    (earliestYear.coerceAtMost(current.year)..current.year).toList()

internal fun reportMonthForYear(selected: YearMonth, year: Int, current: YearMonth): YearMonth =
    YearMonth.of(year, minOf(selected.monthValue, reportSelectableMonths(year, current).last()))

class SleepReportViewModel(
    observeChanges: ObserveTimelineDataChangesUseCase,
    private val getReport: GetSleepReportUseCase,
    zone: ZoneId? = null,
) : ViewModel() {
    private val fixedZoneId = zone
    private val zone: ZoneId get() = fixedZoneId ?: RuntimeZoneId.current
    private val mutableState = MutableStateFlow(SleepReportUiState())
    val state = mutableState
    private var loadJob: Job? = null

    private var invalidationJob: Job? = null
    private val observeChanges = observeChanges

    fun open(period: SleepReportPeriod) {
        if (invalidationJob == null) {
            invalidationJob = viewModelScope.launch {
                observeChanges().collect { if (mutableState.value.period != null) load() }
            }
        }
        val today = LocalDate.now(zone)
        mutableState.value = SleepReportUiState(period = period, month = YearMonth.from(today),
            year = today.year, earliestYear = today.year)
        load()
    }

    fun close() {
        loadJob?.cancel()
        invalidationJob?.cancel()
        invalidationJob = null
        mutableState.value = SleepReportUiState()
    }

    fun month(value: YearMonth) {
        if (mutableState.value.period != SleepReportPeriod.MONTHLY ||
            value.year < mutableState.value.earliestYear || value > YearMonth.now(zone)) return
        mutableState.update { it.copy(month = value) }
        load()
    }

    fun year(value: Int) {
        if (mutableState.value.period != SleepReportPeriod.ANNUAL ||
            value !in mutableState.value.earliestYear..LocalDate.now(zone).year) return
        mutableState.update { it.copy(year = value) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val request = mutableState.value
        if (request.period == null) return
        mutableState.update { it.copy(loading = true, report = null, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val earliestYear = (getReport.earliestSleepYear(zone) ?: LocalDate.now(zone).year)
                    .coerceAtMost(LocalDate.now(zone).year)
                val result = when (request.period) {
                    SleepReportPeriod.MONTHLY -> getReport.monthly(request.month, zone, now)
                    SleepReportPeriod.ANNUAL -> getReport.annual(request.year, zone, now)
                }
                mutableState.update { it.copy(loading = false, report = result, earliestYear = earliestYear) }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = error.message ?: "睡眠报告加载失败") }
            }
        }
    }

    companion object {
        fun factory(observeChanges: ObserveTimelineDataChangesUseCase,
            getReport: GetSleepReportUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SleepReportViewModel(observeChanges, getReport) as T
        }
    }
}
