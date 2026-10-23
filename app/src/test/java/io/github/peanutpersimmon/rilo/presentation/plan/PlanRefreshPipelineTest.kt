package io.github.peanutpersimmon.rilo.presentation.plan

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRefreshPipelineTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 1, 1)
    private val noon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dayReloadUsesReviewIntervalQueryWithExactBounds() = runBlocking {
        val queries = CountingQueries()
        loadPlanStableSources(queries, PlanQueryKey.Day(today), zone)
        assertEquals(noon - 12 * 3_600_000L to noon + 12 * 3_600_000L, queries.lastIntervalRange)
        assertEquals(null, queries.lastFactsRange)
    }

    @Test fun weekReloadQueriesOnlySessions() = assertQueryCounts(
        PlanQueryKey.Week(today.minusDays(3), today.plusDays(3)), QueryCounts(sessions = 1),
    )

    @Test fun dayAndWeekReviewIntervalsIncludeCompletedActivityNapAndNightSleep() {
        val activity = session(1, noon)
        val night = activity.copy(id = 2, kind = SessionKind.NIGHT_SLEEP)
        val nap = activity.copy(id = 3, kind = SessionKind.NAP)
        val active = activity.copy(id = 4, endAtMillis = null)
        val point = activity.copy(id = 5, temporalType = SessionTemporalType.POINT)
        assertEquals(listOf(true, true, true, false, false),
            listOf(activity, night, nap, active, point).map(::isReviewInterval))
        for (mode in listOf(PlanViewMode.DAY, PlanViewMode.WEEK)) {
            val state = projectPlanUiState(PlanStableSources.Sessions(
                listOf(activity, night, nap, active, point).filter(::isReviewInterval)),
                context(mode), noon, 7 * 60, 22 * 60, zone)
            assertEquals(listOf(1L, 2L, 3L), state.records.map { it.id })
        }
    }

    @Test fun boardReloadQueriesScheduledEvents() = assertQueryCounts(
        PlanQueryKey.Board(today.minusDays(3), today.plusDays(3)),
        QueryCounts(scheduledEvents = 1),
    )

    @Test fun todoDoneReloadQueriesEachRequiredSourceOnce() = assertQueryCounts(
        PlanQueryKey.TodoDone(today),
        QueryCounts(eventCandidates = 1, sessions = 1, nightSleeps = 1),
    )

    @Test fun todoDoneReloadUsesActivityFactsQueryWithExactDayBounds() = runBlocking {
        val queries = CountingQueries()
        loadPlanStableSources(queries, PlanQueryKey.TodoDone(today), zone)
        assertEquals(null, queries.lastIntervalRange)
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli() to
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), queries.lastFactsRange)
    }

    @Test fun todoDoneStateKeepsPointAndOverlappingIntervalButExcludesSleepAndActive() {
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val point = session(10, null).copy(temporalType = SessionTemporalType.POINT,
            startAtMillis = noon, endAtMillis = null)
        val nextPoint = point.copy(id = 11, startAtMillis = nextMidnight)
        val overlap = session(12, midnight + 3_600_000L).copy(
            startAtMillis = midnight - 3_600_000L)
        val active = session(13, null)
        val nap = session(14, noon).copy(kind = SessionKind.NAP)
        val sleep = session(15, noon).copy(kind = SessionKind.NIGHT_SLEEP)
        val state = projectPlanUiState(
            PlanStableSources.TodoDone(emptyList(),
                listOf(point, nextPoint, overlap, active, nap, sleep), emptyList()),
            context(PlanViewMode.TODO_DONE), noon, 7 * 60, 22 * 60, zone)

        assertEquals(listOf(10L, 12L, 14L), state.records.map { it.id })
        assertEquals(midnight, state.awakeDisplayWindow?.displayInterval?.startMillis)
    }

    @Test fun monthPlanReloadQueriesScheduledEvents() = assertQueryCounts(
        PlanQueryKey.MonthPlan(YearMonth.from(today)),
        QueryCounts(scheduledEvents = 1),
    )

    @Test fun monthRecordReloadUsesActivityFactsQuery() = runBlocking {
        val queries = CountingQueries()
        loadPlanStableSources(queries, PlanQueryKey.MonthRecord(YearMonth.from(today)), zone)
        assertEquals(null, queries.lastIntervalRange)
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli() to
            today.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli(), queries.lastFactsRange)
    }

    @Test fun monthRecordHidesOnlyCurrentlyNamedLifeCategory() = runBlocking {
        val life = Category(id = 9, name = "生活", createdAtMillis = noon, updatedAtMillis = noon)
        val research = life.copy(id = 10, name = "科研")
        val rows = listOf(session(1, noon + 60_000).copy(categoryId = 9),
            session(2, noon + 60_000).copy(categoryId = 10),
            session(3, noon + 60_000).copy(categoryId = null),
            session(4, noon + 60_000).copy(kind = SessionKind.NAP, categoryId = 9))
        val queries = CountingQueries(listOf(life, research), reviewRows = rows, factRows = rows,
            scheduledRows = listOf(Event(id = 8, title = "生活日程", dateEpochDay = today.toEpochDay(),
                categoryId = 9, createdAtMillis = noon, updatedAtMillis = noon)))
        val month = loadPlanStableSources(queries, PlanQueryKey.MonthRecord(YearMonth.from(today)), zone)
            as PlanStableSources.Sessions
        assertEquals(listOf(2L, 3L, 4L), month.sessions.map { it.id })
        assertEquals(rows.map { it.id }, (loadPlanStableSources(queries,
            PlanQueryKey.Day(today), zone) as PlanStableSources.Sessions).sessions.map { it.id })
        assertEquals(rows.map { it.id }, (loadPlanStableSources(queries,
            PlanQueryKey.Week(today, today.plusDays(6)), zone) as PlanStableSources.Sessions).sessions.map { it.id })
        assertEquals(listOf(8L), (loadPlanStableSources(queries,
            PlanQueryKey.MonthPlan(YearMonth.from(today)), zone) as PlanStableSources.Scheduled).events.map { it.id })
        assertEquals(rows.map { it.id }, (loadPlanStableSources(queries,
            PlanQueryKey.TodoDone(today), zone) as PlanStableSources.TodoDone).sessions.map { it.id })
        val renamed = loadPlanStableSources(CountingQueries(listOf(life.copy(name = "日常"), research),
            factRows = rows), PlanQueryKey.MonthRecord(YearMonth.from(today)), zone) as PlanStableSources.Sessions
        assertEquals(rows.map { it.id }, renamed.sessions.map { it.id })
        val deleted = loadPlanStableSources(CountingQueries(listOf(research),
            factRows = rows.map { if (it.categoryId == 9L) it.copy(categoryId = null) else it }),
            PlanQueryKey.MonthRecord(YearMonth.from(today)), zone) as PlanStableSources.Sessions
        assertEquals(rows.map { it.id }, deleted.sessions.map { it.id })
    }

    @Test fun categoryColorKeysLoadIntoPlanStateAndRefreshAfterInvalidation() = runBlocking {
        val category = Category(id = 9, name = "研究", createdAtMillis = noon,
            updatedAtMillis = noon, colorKey = "red")
        val queries = CountingQueries(listOf(category))
        val loaded = loadPlanStableSources(queries, PlanQueryKey.Board(today, today.plusDays(6)), zone)
        assertEquals(mapOf(9L to "red"), loaded.categoryColorKeys)
        assertEquals(mapOf(9L to "red"), projectPlanUiState(loaded,
            context(PlanViewMode.BOARD), noon, 7 * 60, 22 * 60, zone).categoryColorKeys)

        var colorKey = "red"
        val harness = Harness(context(PlanViewMode.BOARD), noon,
            sourceFactory = { PlanStableSources.Scheduled(emptyList(), mapOf(9L to colorKey)) })
        harness.start(this)
        assertEquals("red", harness.awaitState().categoryColorKeys[9L])
        colorKey = "blue"
        harness.invalidate()
        assertEquals("blue", harness.awaitState().categoryColorKeys[9L])
        harness.close()
    }

    @Test fun everyPlanQueryLoadsCategoryKeys() = runBlocking {
        val category = Category(id = 9, name = "研究", createdAtMillis = noon,
            updatedAtMillis = noon, colorKey = "blue")
        val keys = listOf(
            PlanQueryKey.Day(today),
            PlanQueryKey.Week(today, today.plusDays(6)),
            PlanQueryKey.MonthPlan(YearMonth.from(today)),
            PlanQueryKey.MonthRecord(YearMonth.from(today)),
            PlanQueryKey.Board(today, today.plusDays(6)),
            PlanQueryKey.TodoDone(today),
            PlanQueryKey.Board(today, today.plusDays(6)),
        )
        keys.forEach { key ->
            val sources = loadPlanStableSources(CountingQueries(listOf(category)), key, zone)
            assertEquals(key.toString(), mapOf(9L to "blue"), sources.categoryColorKeys)
        }
    }

    @Test fun boardAndTodoDoneKeepEventAndRecordCategoryIdsAlongsideStableKeys() {
        val event = Event(id = 2, title = "日程", dateEpochDay = today.toEpochDay(),
            categoryId = 12, createdAtMillis = noon, updatedAtMillis = noon)
        val record = session(3, noon + 60_000L).copy(categoryId = 13)
        val keys = mapOf(12L to "red", 13L to "neutral")

        val board = projectPlanUiState(PlanStableSources.Scheduled(
            listOf(event), keys), context(PlanViewMode.BOARD), noon, 7 * 60, 22 * 60, zone)
        assertEquals(listOf(12L), board.events.map { it.categoryId })
        assertEquals(keys, board.categoryColorKeys)

        val todoDone = projectPlanUiState(PlanStableSources.TodoDone(
            listOf(event), listOf(record), emptyList(), keys),
            context(PlanViewMode.TODO_DONE), noon, 7 * 60, 22 * 60, zone)
        assertEquals(listOf(12L), todoDone.events.map { it.categoryId })
        assertEquals(listOf(13L), todoDone.records.map { it.categoryId })
        assertEquals(keys, todoDone.categoryColorKeys)
    }

    @Test fun dayMinutePassageDoesNotReloadOrProject() = runBlocking {
        val harness = Harness(context(PlanViewMode.DAY), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        repeat(4) { harness.tick(noon + (it + 1) * 60_000L) }

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun weekMinutePassageDoesNotReloadOrProject() = runBlocking {
        val harness = Harness(context(PlanViewMode.WEEK), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        repeat(4) { harness.tick(noon + (it + 1) * 60_000L) }

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun todayTodoDoneMinuteTickProjectsWithoutReloading() = runBlocking {
        assertTickOnlyProjection(context(PlanViewMode.TODO_DONE))
    }

    @Test fun historicalTodoDoneDoesNotSubscribeOrProjectOnMinutePassage() = runBlocking {
        assertHistoricalNoTick(context(PlanViewMode.TODO_DONE, today.minusDays(1)))
    }

    @Test fun currentBoardMinuteTickProjectsWithoutReloading() = runBlocking {
        assertTickOnlyProjection(context(PlanViewMode.BOARD))
    }

    @Test fun historicalBoardDoesNotSubscribeOrProjectOnMinutePassage() = runBlocking {
        assertHistoricalNoTick(context(PlanViewMode.BOARD, today.minusWeeks(2)))
    }

    @Test fun currentMonthPlanMinuteTickProjectsWithoutReloading() = runBlocking {
        assertTickOnlyProjection(context(PlanViewMode.MONTH, content = MonthContent.PLAN))
    }

    @Test fun historicalMonthPlanDoesNotSubscribeOrProjectOnMinutePassage() = runBlocking {
        assertHistoricalNoTick(
            context(PlanViewMode.MONTH, today.minusMonths(2), MonthContent.PLAN),
        )
    }

    @Test fun monthRecordNeverSubscribesOrProjectsOnMinutePassage() = runBlocking {
        assertHistoricalNoTick(context(PlanViewMode.MONTH, content = MonthContent.RECORD))
    }

    @Test fun dataInvalidationReloadsAndProjectsExactlyOnce() = runBlocking {
        val harness = Harness(context(PlanViewMode.DAY), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        harness.invalidate()
        harness.awaitState()

        assertEquals(loads + 1, harness.sourceLoaderCount)
        assertEquals(projections + 1, harness.projectionCount)
        harness.close()
    }

    @Test fun selectedDateChangeInsideSameWeekDoesNotReload() = runBlocking {
        val monday = LocalDate.of(2025, 12, 29)
        val harness = Harness(context(PlanViewMode.WEEK, monday), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.select(context(PlanViewMode.WEEK, monday.plusDays(4)))
        assertEquals(monday.plusDays(4), harness.awaitState().selectedDate)

        assertEquals(loads, harness.sourceLoaderCount)
        harness.close()
    }

    @Test fun selectedDateChangeToDifferentWeekReloads() = runBlocking {
        val monday = LocalDate.of(2025, 12, 29)
        val harness = Harness(context(PlanViewMode.WEEK, monday), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.select(context(PlanViewMode.WEEK, monday.plusWeeks(1)))
        assertEquals(monday.plusWeeks(1), harness.awaitState().selectedDate)

        assertEquals(loads + 1, harness.sourceLoaderCount)
        harness.close()
    }

    @Test fun selectedDateChangeInsideSameMonthDoesNotReload() = runBlocking {
        val december = LocalDate.of(2025, 12, 1)
        val harness = Harness(context(PlanViewMode.MONTH, december), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.select(context(PlanViewMode.MONTH, december.plusDays(20)))
        assertEquals(december.plusDays(20), harness.awaitState().selectedDate)

        assertEquals(loads, harness.sourceLoaderCount)
        harness.close()
    }

    @Test fun selectedDateChangeToDifferentMonthReloads() = runBlocking {
        val december = LocalDate.of(2025, 12, 1)
        val harness = Harness(context(PlanViewMode.MONTH, december), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.select(context(PlanViewMode.MONTH, december.minusMonths(1)))
        assertEquals(december.minusMonths(1), harness.awaitState().selectedDate)

        assertEquals(loads + 1, harness.sourceLoaderCount)
        harness.close()
    }

    @Test fun todoDoneUsesActivityFactsContractAndNeverUpgradesPlanToSecondTicks() = runBlocking {
        val completed = session(id = 2, endAtMillis = noon)
        val harness = Harness(context(PlanViewMode.TODO_DONE), noon) {
            PlanStableSources.TodoDone(emptyList(), listOf(completed), emptyList())
        }
        harness.start(this)
        val state = harness.awaitState()

        assertEquals(listOf(2L), state.records.map { it.id })
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    @Test fun sessionStopInvalidationReloadsAndShowsCompletedSession() = runBlocking {
        var stopped = false
        val harness = Harness(context(PlanViewMode.DAY), noon) {
            PlanStableSources.Sessions(
                if (stopped) listOf(session(id = 1, endAtMillis = noon)) else emptyList(),
            )
        }
        harness.start(this)
        assertTrue(harness.awaitState().records.isEmpty())
        val loads = harness.sourceLoaderCount

        stopped = true
        harness.invalidate()
        val reloaded = harness.awaitState()

        assertEquals(loads + 1, harness.sourceLoaderCount)
        assertEquals(listOf(1L), reloaded.records.map { it.id })
        harness.close()
    }

    @Test fun todoDoneMidnightTickProjectsThenStopsHistoricalClock() = runBlocking {
        val beforeMidnight = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val harness = Harness(context(PlanViewMode.TODO_DONE), beforeMidnight)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.tick(midnight)
        assertEquals(midnight, harness.awaitState().nowMillis)
        val projections = harness.projectionCount
        harness.tick(midnight + 60_000L)

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections, harness.projectionCount)
        harness.close()
    }

    @Test fun switchingIntoClockModeImmediatelyProjectsCurrentTime() = runBlocking {
        val current = noon + 30_000L
        val harness = Harness(context(PlanViewMode.DAY), noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.setCurrentTime(current)
        harness.select(context(PlanViewMode.BOARD))
        val switched = harness.awaitState()

        assertEquals(current, switched.nowMillis)
        assertEquals(loads + 1, harness.sourceLoaderCount)
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    @Test fun currentBoardStopsHiddenAndResumesPureProjectionInsideSameWeek() = runBlocking {
        val harness = Harness(context(PlanViewMode.BOARD), noon)
        harness.start(this)
        harness.awaitState()
        harness.setVisible(false)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val hiddenProjections = harness.projectionCount

        harness.tick(noon + 60_000L)
        assertEquals(hiddenProjections, harness.projectionCount)
        harness.setCurrentTime(noon + 24 * 3_600_000L)
        harness.setVisible(true)
        val visible = harness.awaitState()

        assertEquals(noon + 24 * 3_600_000L, visible.nowMillis)
        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS, PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    @Test fun historicalBoardNeverStartsPeriodicTickerAcrossVisibilityChanges() = runBlocking {
        val historical = context(PlanViewMode.BOARD, today.minusWeeks(2))
        val harness = Harness(historical, noon, initialVisible = false)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.setVisible(true)
        harness.awaitState()
        val projections = harness.projectionCount
        harness.tick(noon + 60_000L)

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun todayTodoDoneStopsHiddenAndResumesImmediately() = runBlocking {
        val harness = Harness(context(PlanViewMode.TODO_DONE), noon)
        harness.start(this)
        harness.awaitState()
        harness.setVisible(false)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val hiddenProjections = harness.projectionCount

        harness.tick(noon + 60_000L)
        assertEquals(hiddenProjections, harness.projectionCount)
        harness.setCurrentTime(noon + 37 * 60_000L)
        harness.setVisible(true)
        assertEquals(noon + 37 * 60_000L, harness.awaitState().nowMillis)

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS, PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    @Test fun currentMonthPlanStopsHiddenAndResumesImmediately() = runBlocking {
        val harness = Harness(context(PlanViewMode.MONTH), noon)
        harness.start(this)
        harness.awaitState()
        harness.setVisible(false)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val hiddenProjections = harness.projectionCount

        harness.tick(noon + 60_000L)
        assertEquals(hiddenProjections, harness.projectionCount)
        harness.setCurrentTime(noon + 37 * 60_000L)
        harness.setVisible(true)
        assertEquals(noon + 37 * 60_000L, harness.awaitState().nowMillis)

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS, PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    @Test fun visibilityDoesNotAddClockToDayWeekOrMonthRecord() = runBlocking {
        val contexts = listOf(
            context(PlanViewMode.DAY),
            context(PlanViewMode.WEEK),
            context(PlanViewMode.MONTH, content = MonthContent.RECORD),
        )
        contexts.forEach { initialContext ->
            val harness = Harness(initialContext, noon, initialVisible = false)
            harness.start(this)
            harness.awaitState()
            val loads = harness.sourceLoaderCount
            val projections = harness.projectionCount

            harness.setVisible(true)
            harness.tick(noon + 60_000L)

            assertEquals(loads, harness.sourceLoaderCount)
            assertEquals(projections, harness.projectionCount)
            assertTrue(harness.tickerPeriods.isEmpty())
            harness.close()
        }
    }

    @Test fun hiddenInvalidationReloadsAndProjectsWithoutStartingTicker() = runBlocking {
        val harness = Harness(context(PlanViewMode.BOARD), noon, initialVisible = false)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        harness.invalidate()
        harness.awaitState()

        assertEquals(loads + 1, harness.sourceLoaderCount)
        assertEquals(projections + 1, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun hiddenTodoDoneBecomesHistoricalAtMidnightWithoutStartingTicker() = runBlocking {
        val beforeMidnight = today.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val nextMorning = today.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val harness = Harness(
            context(PlanViewMode.TODO_DONE),
            beforeMidnight,
            initialVisible = false,
        )
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount

        harness.setCurrentTime(nextMorning)
        harness.setVisible(true)
        val visible = harness.awaitState()

        assertEquals(nextMorning, visible.nowMillis)
        assertEquals(loads, harness.sourceLoaderCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun hiddenBoardQueryKeyChangeAcrossWeekReloadsOnce() = runBlocking {
        val harness = Harness(context(PlanViewMode.BOARD), noon, initialVisible = false)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val nextWeekDate = today.plusWeeks(1)
        val nextWeekNow = nextWeekDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        harness.setCurrentTime(nextWeekNow)
        harness.select(context(PlanViewMode.BOARD, nextWeekDate))
        val changed = harness.awaitState()

        assertEquals(nextWeekDate, changed.selectedDate)
        assertEquals(loads + 1, harness.sourceLoaderCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    private suspend fun assertTickOnlyProjection(initialContext: PlanContext) = coroutineScope {
        val harness = Harness(initialContext, noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        harness.tick(noon + 60_000L)
        harness.awaitState()

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections + 1, harness.projectionCount)
        assertEquals(listOf(PLAN_TICK_PERIOD_MILLIS), harness.tickerPeriods)
        harness.close()
    }

    private suspend fun assertHistoricalNoTick(initialContext: PlanContext) = coroutineScope {
        val harness = Harness(initialContext, noon)
        harness.start(this)
        harness.awaitState()
        val loads = harness.sourceLoaderCount
        val projections = harness.projectionCount

        harness.tick(noon + 60_000L)

        assertEquals(loads, harness.sourceLoaderCount)
        assertEquals(projections, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    private fun assertQueryCounts(key: PlanQueryKey, expected: QueryCounts) = runBlocking {
        val queries = CountingQueries()
        loadPlanStableSources(queries, key, zone)
        assertEquals(expected.copy(categories = 1), queries.counts)
    }

    private fun context(
        mode: PlanViewMode,
        date: LocalDate = today,
        content: MonthContent = MonthContent.PLAN,
    ) = PlanContext(date, mode, content)

    private fun session(id: Long, endAtMillis: Long?) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = noon - 60_000L,
        endAtMillis = endAtMillis,
        createdAtMillis = noon - 60_000L,
        updatedAtMillis = noon,
    )

    private fun emptySources(key: PlanQueryKey): PlanStableSources = when (key) {
        is PlanQueryKey.Day,
        is PlanQueryKey.Week,
        is PlanQueryKey.MonthRecord,
        -> PlanStableSources.Sessions(emptyList())
        is PlanQueryKey.Board,
        is PlanQueryKey.MonthPlan,
        -> PlanStableSources.Scheduled(emptyList())
        is PlanQueryKey.TodoDone -> PlanStableSources.TodoDone(
            emptyList(), emptyList(), emptyList(),
        )
    }

    private inner class Harness(
        initialContext: PlanContext,
        initialNowMillis: Long,
        initialVisible: Boolean = true,
        private val sourceFactory: (PlanQueryKey) -> PlanStableSources = ::emptySources,
    ) {
        private val changes = MutableSharedFlow<Unit>(replay = 1)
        private val contexts = MutableStateFlow(initialContext)
        private val pageVisible = MutableStateFlow(initialVisible)
        private val ticks = MutableSharedFlow<Long>()
        private val states = Channel<PlanUiState>(Channel.UNLIMITED)
        private var nowMillis = initialNowMillis
        private var job: Job? = null

        var sourceLoaderCount: Int = 0
            private set
        var projectionCount: Int = 0
            private set
        val tickerPeriods = mutableListOf<Long>()

        init {
            changes.tryEmit(Unit)
        }

        fun start(scope: CoroutineScope) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                planUiStates(
                    dataChanges = changes,
                    contexts = contexts,
                    pageVisible = pageVisible,
                    loadSources = { key ->
                        sourceLoaderCount += 1
                        sourceFactory(key)
                    },
                    timeProvider = CurrentTimeProvider { nowMillis },
                    zoneId = zone,
                    defaultWakeMinute = 7 * 60,
                    defaultSleepMinute = 22 * 60,
                    ticker = { periodMillis ->
                        flow {
                            tickerPeriods += periodMillis
                            emit(nowMillis)
                            ticks.collect { emit(it) }
                        }
                    },
                ).collect { state ->
                    projectionCount += 1
                    states.send(state)
                }
            }
        }

        suspend fun awaitState(): PlanUiState = withTimeout(2_000L) { states.receive() }

        suspend fun tick(value: Long) {
            nowMillis = value
            ticks.emit(value)
            yield()
        }

        fun setCurrentTime(value: Long) {
            nowMillis = value
        }

        suspend fun setVisible(value: Boolean) {
            pageVisible.value = value
            yield()
        }

        suspend fun invalidate() {
            changes.emit(Unit)
        }

        suspend fun select(value: PlanContext) {
            contexts.value = value
            yield()
        }

        suspend fun close() {
            job?.cancelAndJoin()
        }
    }
}

private data class QueryCounts(
    val categories: Int = 0,
    val tasks: Int = 0,
    val scheduledEvents: Int = 0,
    val eventCandidates: Int = 0,
    val sessions: Int = 0,
    val nightSleeps: Int = 0,
)

private class CountingQueries(private val categoryRows: List<Category> = emptyList(),
    private val reviewRows: List<Session> = emptyList(),
    private val factRows: List<Session> = emptyList(),
    private val scheduledRows: List<Event> = emptyList()) : PlanSourceQueries {
    var counts = QueryCounts()
        private set
    var lastIntervalRange: Pair<Long, Long>? = null
        private set
    var lastFactsRange: Pair<Long, Long>? = null
        private set

    override suspend fun getCategories(): List<Category> {
        counts = counts.copy(categories = counts.categories + 1)
        return categoryRows
    }

    override suspend fun getScheduledEvents(firstEpochDay: Long, lastEpochDay: Long): List<Event> {
        counts = counts.copy(scheduledEvents = counts.scheduledEvents + 1)
        return scheduledRows
    }

    override suspend fun getEventCandidates(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Event> {
        counts = counts.copy(eventCandidates = counts.eventCandidates + 1)
        return emptyList()
    }

    override suspend fun getReviewIntervalsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Session> {
        counts = counts.copy(sessions = counts.sessions + 1)
        lastIntervalRange = rangeStartMillis to rangeEndMillis
        return reviewRows
    }

    override suspend fun getActivityFactsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Session> {
        counts = counts.copy(sessions = counts.sessions + 1)
        lastFactsRange = rangeStartMillis to rangeEndMillis
        return factRows
    }

    override suspend fun getNightSleeps(): List<Session> {
        counts = counts.copy(nightSleeps = counts.nightSleeps + 1)
        return emptyList()
    }
}
