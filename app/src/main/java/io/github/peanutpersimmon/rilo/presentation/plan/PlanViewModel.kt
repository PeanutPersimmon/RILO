package io.github.peanutpersimmon.rilo.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.data.preferences.PlanViewOrderPreferences
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.presentation.today.DefaultAwakeTimeConfig
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The active view and date are transient UI state; only the view order is persisted (DataStore). */
enum class PlanViewMode(val label: String) {
    DAY("日视图"), WEEK("周视图"), MONTH("月视图"), BOARD("2×4 周计划"),
    TODO_DONE("Todo & Done")
}

enum class MonthContent { PLAN, RECORD }

/** Old six-view orders can still be present in DataStore after an upgrade. */
internal fun normalizedPlanViewOrder(names: List<String>): List<PlanViewMode> {
    val parsed = names.mapNotNull { name -> PlanViewMode.entries.firstOrNull { it.name == name } }
    val retained = if (names.firstOrNull() == "TASK_LIST")
        listOf(PlanViewMode.BOARD) + parsed.filterNot { it == PlanViewMode.BOARD }
    else parsed
    return retained.distinct() + PlanViewMode.entries.filter { it !in retained }
}

internal fun initialPlanViewOrder(names: List<String>): List<PlanViewMode> =
    if (names.isEmpty()) PlanViewModel.DEFAULT_VIEW_ORDER else normalizedPlanViewOrder(names)

internal data class PlanRuntimeSelection(
    val mode: PlanViewMode,
    val dates: Map<PlanViewMode, LocalDate>,
) {
    val selectedDate: LocalDate get() = requireNotNull(dates[mode])
}

internal fun initialPlanRuntimeSelection(
    order: List<PlanViewMode>,
    today: LocalDate,
): PlanRuntimeSelection = PlanRuntimeSelection(
    mode = order.firstOrNull() ?: PlanViewMode.BOARD,
    dates = PlanViewMode.entries.associateWith { today },
)

internal fun PlanRuntimeSelection.withMode(mode: PlanViewMode): PlanRuntimeSelection = copy(mode = mode)
internal fun PlanRuntimeSelection.withDate(date: LocalDate): PlanRuntimeSelection =
    copy(dates = dates + (mode to date))

internal fun PlanRuntimeSelection.withMonth(month: YearMonth): PlanRuntimeSelection =
    withDate(month.atDay(selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())))

internal fun PlanRuntimeSelection.resetDates(today: LocalDate): PlanRuntimeSelection =
    copy(dates = PlanViewMode.entries.associateWith { today })

internal fun PlanRuntimeSelection.withStep(direction: Long): PlanRuntimeSelection {
    val target = when (mode) {
        PlanViewMode.DAY, PlanViewMode.TODO_DONE -> selectedDate.plusDays(direction)
        PlanViewMode.WEEK, PlanViewMode.BOARD -> selectedDate.plusWeeks(direction)
        PlanViewMode.MONTH -> selectedDate.plusMonths(direction)
    }
    return withDate(target)
}

internal fun PlanRuntimeSelection.hasStaleVisibleState(
    state: PlanUiState,
    zone: ZoneId,
): Boolean = state.error == null &&
    (state.zoneId != zone || state.selectedDate != selectedDate || state.mode != mode)
internal fun PlanRuntimeSelection.withAdjacentMode(
    order: List<PlanViewMode>,
    step: Int,
): PlanRuntimeSelection {
    val currentIndex = order.indexOf(mode)
    val adjacent = order.getOrNull(currentIndex + step.coerceIn(-1, 1)) ?: mode
    return copy(mode = adjacent)
}

data class PlanUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val mode: PlanViewMode = PlanViewMode.BOARD,
    val monthContent: MonthContent = MonthContent.RECORD,
    val events: List<Event> = emptyList(),
    val records: List<Session> = emptyList(),
    val categoryColorKeys: Map<Long, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val awakeDisplayWindow: AwakeDisplayWindow? = null,
    val selectionReady: Boolean = true,
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val repository: RoomRidoRepository,
    private val viewOrderPreferences: PlanViewOrderPreferences,
    private val defaultAwakeTime: DefaultAwakeTimeConfig = DefaultAwakeTimeConfig.Standard,
    zoneId: ZoneId? = null,
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
) : ViewModel() {
    private val fixedZoneId = zoneId
    private fun currentZone(): ZoneId = fixedZoneId ?: RuntimeZoneId.current
    private fun currentDate(zone: ZoneId = currentZone()): LocalDate =
        Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
    private val runtimeSelection = MutableStateFlow<PlanRuntimeSelection?>(null)
    private val monthContent = MutableStateFlow(MonthContent.RECORD)
    private val pageVisible = MutableStateFlow(false)
    private val zoneIds = fixedZoneId?.let(::flowOf) ?: RuntimeZoneId.changes
    private val sourceQueries = RoomPlanSourceQueries(repository)

    /** 用户自定义的视图顺序；首项即计划页冷启动默认视图。 */
    private val loadedViewOrders = viewOrderPreferences.orderNames.map(::initialPlanViewOrder)

    val viewOrder: StateFlow<List<PlanViewMode>> = loadedViewOrders
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_VIEW_ORDER)

    init {
        // 无排序数据时写回默认序列；默认序列以 BOARD 开头，与回退值一致。
        viewModelScope.launch {
            viewOrderPreferences.ensureDefaultOrder(DEFAULT_VIEW_ORDER.map { it.name })
        }
        viewModelScope.launch {
            val order = loadedViewOrders.first()
            runtimeSelection.compareAndSet(
                null,
                initialPlanRuntimeSelection(order, currentDate()),
            )
        }
    }

    private val context = combine(
        runtimeSelection.filterNotNull(),
        monthContent,
    ) { selected, content -> PlanContext(selected.selectedDate, selected.mode, content) }

    val uiState = zoneIds.flatMapLatest { currentZone ->
        planUiStates(
            dataChanges = repository.observeDataChanges(),
            contexts = context,
            pageVisible = pageVisible,
            loadSources = { key -> loadPlanStableSources(sourceQueries, key, currentZone) },
            timeProvider = clock,
            zoneId = currentZone,
            defaultWakeMinute = defaultAwakeTime.wakeMinute,
            defaultSleepMinute = defaultAwakeTime.sleepMinute,
            ticker = { periodMillis ->
                io.github.peanutpersimmon.rilo.presentation.time.alignedTicks(
                    clock,
                    periodMillis,
                )
            },
        )
    }.catch { error ->
        val currentZone = zoneIds.first()
        val selected = runtimeSelection.value ?: initialPlanRuntimeSelection(viewOrder.value, currentDate(currentZone))
        emit(PlanUiState(selected.selectedDate, selected.mode, monthContent.value, loading = false,
            error = error.message ?: "无法读取计划数据", zoneId = currentZone))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlanUiState(selectionReady = false),
    )

    fun selectMode(value: PlanViewMode) {
        runtimeSelection.update { current ->
            (current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate())).withMode(value)
        }
    }

    fun stepMode(step: Int) {
        runtimeSelection.update { current ->
            (current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate()))
                .withAdjacentMode(viewOrder.value, step)
        }
    }

    fun setPageVisible(visible: Boolean) {
        if (visible && !pageVisible.value) {
            val today = currentDate()
            // Initial mode still comes from the first persisted view order emission.
            runtimeSelection.update { it?.resetDates(today) }
        }
        pageVisible.value = visible
    }

    fun isSelectionStale(state: PlanUiState): Boolean {
        val selected = runtimeSelection.value ?: return true
        val zone = currentZone()
        return selected.hasStaleVisibleState(state, zone)
    }

    /** Persists one complete, validated order after a drag/drop or accessibility move. */
    fun setViewOrder(order: List<PlanViewMode>) {
        if (order.size != PlanViewMode.entries.size ||
            order.toSet() != PlanViewMode.entries.toSet() ||
            order == viewOrder.value
        ) return
        viewModelScope.launch { viewOrderPreferences.setOrderNames(order.map { it.name }) }
    }
    fun selectDate(value: LocalDate) {
        runtimeSelection.update { current ->
            (current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate())).withDate(value)
        }
    }
    /** A month cell is an explicit request to open that day in the existing detail view. */
    fun openMonthDate(value: LocalDate, destination: PlanViewMode) {
        runtimeSelection.update { current ->
            (current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate()))
                .withMode(destination).withDate(value)
        }
    }
    fun selectMonth(value: YearMonth) {
        runtimeSelection.update { current ->
            val selected = current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate())
            selected.withMonth(value)
        }
    }
    fun selectMonthContent(value: MonthContent) { monthContent.value = value }
    fun step(direction: Long) {
        runtimeSelection.update { current ->
            val selected = current ?: initialPlanRuntimeSelection(viewOrder.value, currentDate())
            selected.withStep(direction)
        }
    }

    companion object {
        /** 无持久化数据时的默认顺序：以 BOARD 开头，与旧硬编码行为一致。 */
        val DEFAULT_VIEW_ORDER: List<PlanViewMode> = listOf(
            PlanViewMode.BOARD, PlanViewMode.DAY, PlanViewMode.WEEK,
            PlanViewMode.MONTH, PlanViewMode.TODO_DONE,
        )

        fun factory(repository: RoomRidoRepository,
            viewOrderPreferences: PlanViewOrderPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlanViewModel(repository, viewOrderPreferences) as T
            }
    }
}
