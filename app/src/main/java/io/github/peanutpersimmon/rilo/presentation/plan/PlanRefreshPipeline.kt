package io.github.peanutpersimmon.rilo.presentation.plan

import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.service.AwakeDisplayContentProjector
import io.github.peanutpersimmon.rilo.domain.service.AwakeDisplayWindowResolver
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformWhile

internal data class PlanContext(
    val selectedDate: LocalDate,
    val mode: PlanViewMode,
    val monthContent: MonthContent,
)

internal sealed interface PlanQueryKey {
    data class Day(val date: LocalDate) : PlanQueryKey
    data class Week(val firstDate: LocalDate, val lastDate: LocalDate) : PlanQueryKey
    data class Board(val firstDate: LocalDate, val lastDate: LocalDate) : PlanQueryKey
    data class TodoDone(val date: LocalDate) : PlanQueryKey
    data class MonthPlan(val month: YearMonth) : PlanQueryKey
    data class MonthRecord(val month: YearMonth) : PlanQueryKey
}

internal sealed interface PlanStableSources {
    val categoryColorKeys: Map<Long, String>
    data class Sessions(
        val sessions: List<Session>,
        override val categoryColorKeys: Map<Long, String> = emptyMap(),
    ) : PlanStableSources
    data class Scheduled(
        val events: List<Event>,
        override val categoryColorKeys: Map<Long, String> = emptyMap(),
    ) : PlanStableSources
    data class TodoDone(
        val events: List<Event>,
        val sessions: List<Session>,
        val nightSleeps: List<Session>,
        override val categoryColorKeys: Map<Long, String> = emptyMap(),
    ) : PlanStableSources
}

internal interface PlanSourceQueries {
    suspend fun getCategories(): List<Category>
    suspend fun getScheduledEvents(firstEpochDay: Long, lastEpochDay: Long): List<Event>
    suspend fun getEventCandidates(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Event>
    suspend fun getReviewIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session>
    suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session>
    suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> = emptyList()
    suspend fun getNightSleeps(): List<Session>
}

internal class RoomPlanSourceQueries(
    private val repository: RoomRidoRepository,
) : PlanSourceQueries {
    override suspend fun getCategories(): List<Category> = repository.getCategories()

    override suspend fun getScheduledEvents(firstEpochDay: Long, lastEpochDay: Long): List<Event> =
        repository.getScheduledEvents(firstEpochDay, lastEpochDay)

    override suspend fun getEventCandidates(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Event> = repository.getEvents(
        firstEpochDay = firstEpochDay,
        lastEpochDay = lastEpochDay,
        rangeStartMillis = rangeStartMillis,
        rangeEndMillis = rangeEndMillis,
    )

    override suspend fun getReviewIntervalsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Session> = repository.getSessions(rangeStartMillis, rangeEndMillis)
        .filter(::isReviewInterval)

    override suspend fun getActivityFactsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Session> = repository.getActivityFactsInRange(rangeStartMillis, rangeEndMillis)

    override suspend fun getNapsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Session> = repository.getNapsInRange(rangeStartMillis, rangeEndMillis)

    override suspend fun getNightSleeps(): List<Session> = repository.getNightSleeps()
}

internal fun planQueryKey(context: PlanContext): PlanQueryKey = when (context.mode) {
    PlanViewMode.DAY -> PlanQueryKey.Day(context.selectedDate)
    PlanViewMode.WEEK -> context.selectedDate.weekRange { first, last ->
        PlanQueryKey.Week(first, last)
    }
    PlanViewMode.BOARD -> context.selectedDate.weekRange { first, last ->
        PlanQueryKey.Board(first, last)
    }
    PlanViewMode.TODO_DONE -> PlanQueryKey.TodoDone(context.selectedDate)
    PlanViewMode.MONTH -> when (context.monthContent) {
        MonthContent.PLAN -> PlanQueryKey.MonthPlan(YearMonth.from(context.selectedDate))
        MonthContent.RECORD -> PlanQueryKey.MonthRecord(YearMonth.from(context.selectedDate))
    }
}

private inline fun <T> LocalDate.weekRange(block: (LocalDate, LocalDate) -> T): T {
    val first = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return block(first, first.plusDays(6))
}

internal suspend fun loadPlanStableSources(
    queries: PlanSourceQueries,
    key: PlanQueryKey,
    zoneId: ZoneId,
): PlanStableSources {
    val categories = queries.getCategories()
    val categoryColorKeys = categories.associate { it.id to it.colorKey }
    val hiddenMonthRecordCategoryIds = categories.asSequence()
        .filter { it.name == "生活" }.map { it.id }.toSet()
    return when (key) {
    is PlanQueryKey.Day -> PlanStableSources.Sessions(
        queries.getReviewIntervalsInRange(key.date.startMillis(zoneId), key.date.endMillis(zoneId)),
        categoryColorKeys,
    )
    is PlanQueryKey.Week -> PlanStableSources.Sessions(
        queries.getReviewIntervalsInRange(key.firstDate.startMillis(zoneId), key.lastDate.endMillis(zoneId)),
        categoryColorKeys,
    )
    is PlanQueryKey.Board -> PlanStableSources.Scheduled(
        events = queries.getScheduledEvents(key.firstDate.toEpochDay(), key.lastDate.toEpochDay()),
        categoryColorKeys = categoryColorKeys,
    )
    is PlanQueryKey.TodoDone -> PlanStableSources.TodoDone(
        events = queries.getEventCandidates(
            firstEpochDay = key.date.toEpochDay(),
            lastEpochDay = key.date.toEpochDay(),
            rangeStartMillis = key.date.startMillis(zoneId),
            rangeEndMillis = key.date.endMillis(zoneId),
        ),
        sessions = queries.getActivityFactsInRange(key.date.startMillis(zoneId), key.date.endMillis(zoneId)) +
            queries.getNapsInRange(key.date.startMillis(zoneId), key.date.endMillis(zoneId)),
        nightSleeps = queries.getNightSleeps(),
        categoryColorKeys = categoryColorKeys,
    )
    is PlanQueryKey.MonthPlan -> PlanStableSources.Scheduled(
        events = queries.getScheduledEvents(
            key.month.atDay(1).toEpochDay(),
            key.month.atEndOfMonth().toEpochDay(),
        ),
        categoryColorKeys = categoryColorKeys,
    )
    is PlanQueryKey.MonthRecord -> PlanStableSources.Sessions(
        queries.getActivityFactsInRange(
            key.month.atDay(1).startMillis(zoneId),
            key.month.atEndOfMonth().endMillis(zoneId),
        ).filterNot { session ->
            session.kind == SessionKind.ACTIVITY &&
                session.categoryId in hiddenMonthRecordCategoryIds
        },
        categoryColorKeys,
    )
    }
}

internal fun isReviewInterval(session: Session): Boolean =
    session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis != null &&
        session.kind in setOf(SessionKind.ACTIVITY, SessionKind.NAP, SessionKind.NIGHT_SLEEP)

private fun LocalDate.startMillis(zoneId: ZoneId): Long =
    atStartOfDay(zoneId).toInstant().toEpochMilli()

private fun LocalDate.endMillis(zoneId: ZoneId): Long =
    plusDays(1).startMillis(zoneId)

internal fun planNeedsClock(key: PlanQueryKey, nowMillis: Long, zoneId: ZoneId): Boolean {
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when (key) {
        is PlanQueryKey.Board -> !today.isBefore(key.firstDate) && !today.isAfter(key.lastDate)
        is PlanQueryKey.TodoDone -> key.date == today
        is PlanQueryKey.MonthPlan -> key.month == YearMonth.from(today)
        is PlanQueryKey.Day,
        is PlanQueryKey.Week,
        is PlanQueryKey.MonthRecord,
        -> false
    }
}

private data class PlanClockValue(
    val key: PlanQueryKey,
    val nowMillis: Long,
)

private data class PlanClockRequest(
    val key: PlanQueryKey,
    val pageVisible: Boolean,
)

private fun PlanQueryKey.supportsPeriodicClock(): Boolean = when (this) {
    is PlanQueryKey.Board,
    is PlanQueryKey.TodoDone,
    is PlanQueryKey.MonthPlan,
    -> true
    is PlanQueryKey.Day,
    is PlanQueryKey.Week,
    is PlanQueryKey.MonthRecord,
    -> false
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun planClock(
    queryKeys: Flow<PlanQueryKey>,
    pageVisible: Flow<Boolean>,
    timeProvider: CurrentTimeProvider,
    zoneId: ZoneId,
    ticker: (Long) -> Flow<Long>,
): Flow<PlanClockValue> = combine(queryKeys, pageVisible) { key, visible ->
    PlanClockRequest(
        key = key,
        pageVisible = visible && key.supportsPeriodicClock(),
    )
}.distinctUntilChanged().flatMapLatest { request ->
    val now = timeProvider.nowMillis()
    if (!request.pageVisible || !planNeedsClock(request.key, now, zoneId)) {
        flowOf(PlanClockValue(request.key, now))
    } else {
        ticker(PLAN_TICK_PERIOD_MILLIS).transformWhile { tick ->
            emit(PlanClockValue(request.key, tick))
            planNeedsClock(request.key, tick, zoneId)
        }
    }
}

internal fun projectPlanUiState(
    sources: PlanStableSources,
    context: PlanContext,
    nowMillis: Long,
    defaultWakeMinute: Int,
    defaultSleepMinute: Int,
    zoneId: ZoneId,
): PlanUiState {
    val events = when (sources) {
        is PlanStableSources.Scheduled -> sources.events
        is PlanStableSources.TodoDone -> sources.events
        is PlanStableSources.Sessions -> emptyList()
    }
    val recordsFromQuery = when (sources) {
        is PlanStableSources.Sessions -> sources.sessions
        is PlanStableSources.TodoDone -> sources.sessions
        is PlanStableSources.Scheduled -> emptyList()
    }
    val records = if (sources is PlanStableSources.TodoDone) {
        val start = context.selectedDate.startMillis(zoneId)
        val end = context.selectedDate.endMillis(zoneId)
        recordsFromQuery.filter { session ->
            session.kind in setOf(SessionKind.ACTIVITY, SessionKind.NAP) &&
                when (session.temporalType) {
                    io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.POINT ->
                        session.startAtMillis >= start && session.startAtMillis < end
                    io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.INTERVAL ->
                        session.endAtMillis?.let { it > start && session.startAtMillis < end } == true
                }
        }
    } else {
        recordsFromQuery
    }
    val displayWindow = if (sources is PlanStableSources.TodoDone) {
        val relevantContent = AwakeDisplayContentProjector.project(
            displayDate = context.selectedDate,
            sessions = records,
            events = events,
            zoneId = zoneId,
            asOfMillis = nowMillis,
            includePointRecords = true,
            includeOverlappingActivityIntervals = true,
        )
        AwakeDisplayWindowResolver.resolveHistorical(
            anchorDate = context.selectedDate,
            nightSleeps = sources.nightSleeps,
            relevantContent = relevantContent,
            defaultWakeMinute = defaultWakeMinute,
            defaultSleepMinute = defaultSleepMinute,
            zoneId = zoneId,
        )
    } else {
        null
    }
    return PlanUiState(
        selectedDate = context.selectedDate,
        mode = context.mode,
        monthContent = context.monthContent,
        events = events,
        records = records,
        categoryColorKeys = sources.categoryColorKeys,
        loading = false,
        nowMillis = nowMillis,
        awakeDisplayWindow = displayWindow,
        zoneId = zoneId,
    )
}

private data class LoadedPlanSources(
    val key: PlanQueryKey,
    val sources: PlanStableSources,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun planUiStates(
    dataChanges: Flow<Unit>,
    contexts: Flow<PlanContext>,
    pageVisible: Flow<Boolean>,
    loadSources: suspend (PlanQueryKey) -> PlanStableSources,
    timeProvider: CurrentTimeProvider,
    zoneId: ZoneId,
    defaultWakeMinute: Int,
    defaultSleepMinute: Int,
    ticker: (Long) -> Flow<Long>,
): Flow<PlanUiState> {
    val queryKeys = contexts.map(::planQueryKey).distinctUntilChanged()
    val loadedSources = combine(dataChanges, queryKeys) { _, key -> key }
        .mapLatest { key -> LoadedPlanSources(key, loadSources(key)) }
    val clock = planClock(queryKeys, pageVisible, timeProvider, zoneId, ticker)

    return combine(loadedSources, contexts, clock) { loaded, context, clockValue ->
        val contextKey = planQueryKey(context)
        if (loaded.key != contextKey || clockValue.key != contextKey) {
            null
        } else {
            projectPlanUiState(
                sources = loaded.sources,
                context = context,
                nowMillis = clockValue.nowMillis,
                defaultWakeMinute = defaultWakeMinute,
                defaultSleepMinute = defaultSleepMinute,
                zoneId = zoneId,
            )
        }
    }.filterNotNull()
}

internal const val PLAN_TICK_PERIOD_MILLIS = 60_000L
