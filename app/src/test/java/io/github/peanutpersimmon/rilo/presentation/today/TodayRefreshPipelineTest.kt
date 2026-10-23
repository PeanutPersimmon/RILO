package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.CategoryPresentation
import io.github.peanutpersimmon.rilo.domain.usecase.TimelineDisplaySources
import io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataProjector
import io.github.peanutpersimmon.rilo.domain.usecase.TodayStableSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayRefreshPipelineTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 1, 1)
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun minuteTicksDoNotReloadStableSnapshot() = runBlocking {
        val harness = Harness(at(10))
        harness.start(this)
        harness.awaitLoad()

        repeat(5) { minute -> harness.tick(at(10, minute + 1)) }

        assertEquals(1, harness.loadCount)
        harness.close()
    }

    @Test fun fiveSecondTicksDoNotReloadAndActiveEndKeepsMoving() = runBlocking {
        val active = activeSession(endAtMillis = null)
        val harness = Harness(at(14, 20)) { snapshot(active) }
        harness.start(this)
        val stable = harness.awaitLoad()

        var previousEnd = Long.MIN_VALUE
        repeat(5) { second ->
            val now = at(14, 20) + second * 1_000L
            harness.tick(now)
            val content = project(stable, active, now)
            val entry = content.timelineEntries.single {
                it.entryType == TimelineEntryType.SESSION_BLOCK
            }
            assertEquals(now, entry.endAtMillis)
            assertTrue(requireNotNull(entry.endAtMillis) >= previousEnd)
            previousEnd = requireNotNull(entry.endAtMillis)
        }

        assertEquals(1, harness.loadCount)
        harness.close()
    }

    @Test fun dataChangeReloadsOnceAndLaterTicksStayInMemory() = runBlocking {
        val harness = Harness(at(10))
        harness.start(this)
        harness.awaitLoad()

        harness.dataChange()
        harness.awaitLoad()
        repeat(3) { minute -> harness.tick(at(10, minute + 1)) }

        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun eventInsertRefreshesCurrentTodayWithoutNewTick() = runBlocking {
        val harness = EventEditHarness()
        harness.start(this)
        assertTrue(harness.awaitContent().timelineEntries.isEmpty())
        harness.events = listOf(event(1, "组会", 14 * 60))
        harness.eventChanged()
        assertEquals("组会", harness.awaitContent().timelineEntries.single().title)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun recordInsertRefreshesCurrentTodayWithoutNewTick() = runBlocking {
        val harness = EventEditHarness(initialTick = at(10, 15))
        harness.start(this)
        assertTrue(harness.awaitContent().timelineEntries.isEmpty())
        val recordedAt = at(10, 15) + 25_000L
        harness.setCurrentTime(recordedAt)
        harness.sessions = listOf(Session(id = 9, title = "记录",
            temporalType = SessionTemporalType.POINT, startAtMillis = at(10, 10),
            endAtMillis = null, createdAtMillis = recordedAt, updatedAtMillis = recordedAt))
        harness.roomChanged()
        assertEquals("记录", harness.awaitContent().timelineEntries.single().title)
        harness.close()
    }

    @Test fun eventUpdateRefreshesTitleAndTimeWithoutNewTick() = runBlocking {
        val harness = EventEditHarness(listOf(event(1, "组会", 14 * 60)))
        harness.start(this)
        assertEquals(at(14), harness.awaitContent().timelineEntries.single().startAtMillis)
        harness.events = listOf(event(1, "改期组会", 15 * 60))
        harness.eventChanged()
        val updated = harness.awaitContent().timelineEntries.single()
        assertEquals("改期组会", updated.title)
        assertEquals(at(15), updated.startAtMillis)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun eventDeleteRemovesItFromCurrentTodayWithoutNewTick() = runBlocking {
        val harness = EventEditHarness(listOf(event(1, "组会", 14 * 60)))
        harness.start(this)
        assertEquals(1, harness.awaitContent().timelineEntries.size)
        harness.events = emptyList()
        harness.eventChanged()
        assertTrue(harness.awaitContent().timelineEntries.isEmpty())
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun categoryChangeReloadsIntervalAndUntimedSummaryKeys() = runBlocking {
        val untimedEvent = Event(id = 7, title = "写论文", dateEpochDay = date.toEpochDay(),
            categoryId = 9, createdAtMillis = start, updatedAtMillis = start)
        val event = Event(id = 8, title = "组会", dateEpochDay = date.toEpochDay(),
            startMinuteOfDay = 14 * 60, endMinuteOfDay = 15 * 60,
            categoryId = 9, createdAtMillis = start, updatedAtMillis = start)
        var key = "blue"
        val harness = Harness(at(10)) {
            snapshot().copy(
                displaySources = TimelineDisplaySources(emptyList(), listOf(event, untimedEvent)),
                metadataSources = TimelineMetadataProjector.project(
                    sessions = emptyList(), events = listOf(event, untimedEvent)),
                labels = ClassificationLabels(mapOf(9L to CategoryPresentation("研究", key, false))),
            )
        }
        harness.start(this)
        val first = project(harness.awaitLoad(), null, at(10))
        assertEquals("blue", first.timelineEntries.single {
            it.entryType == TimelineEntryType.EVENT_BLOCK }.categoryColorKey)
        assertEquals("blue", first.planSummary!!.items.single().categoryColorKey)

        key = "orange"
        harness.dataChange()
        val second = project(harness.awaitLoad(), null, at(10))
        assertEquals("orange", second.timelineEntries.single {
            it.entryType == TimelineEntryType.EVENT_BLOCK }.categoryColorKey)
        assertEquals("orange", second.planSummary!!.items.single().categoryColorKey)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun displayModeProjectionDoesNotReloadStableSnapshot() = runBlocking {
        val harness = Harness(at(10))
        harness.start(this)
        val stable = harness.awaitLoad()

        assertEquals(TimelineDisplayMode.OVERVIEW, project(stable, null, at(10), TimelineDisplayMode.OVERVIEW).displayMode)
        assertEquals(TimelineDisplayMode.DETAIL, project(stable, null, at(10), TimelineDisplayMode.DETAIL).displayMode)
        assertEquals(TimelineDisplayMode.OVERVIEW, project(stable, null, at(10), TimelineDisplayMode.OVERVIEW).displayMode)
        assertEquals(1, harness.loadCount)
        harness.close()
    }

    @Test fun activeStartAndStopSwitchTickPeriodsWithoutTickDrivenReloads() = runBlocking {
        val active = MutableStateFlow<Session?>(null)
        val periods = Channel<Long>(Channel.UNLIMITED)
        val collected = Channel<Long>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            todayTicks(MutableStateFlow(true), active) { period ->
                flow {
                    periods.send(period)
                    emit(at(12))
                    awaitCancellation()
                }
            }.collect { collected.send(it) }
        }

        assertEquals(60_000L, periods.receive())
        collected.receive()
        active.value = activeSession(endAtMillis = null)
        assertEquals(1_000L, periods.receive())
        collected.receive()
        active.value = null
        assertEquals(60_000L, periods.receive())
        collected.receive()

        job.cancelAndJoin()
    }

    @Test fun visibleActiveEmitsImmediatelyThenEverySecond() = runBlocking {
        val harness = TickerHarness(visible = true, active = activeSession(null), initialNow = at(14))
        harness.start(this)
        assertEquals(at(14), harness.awaitProjection())

        repeat(5) { second ->
            val now = at(14) + (second + 1) * 1_000L
            harness.tick(now)
            assertEquals(now, harness.awaitProjection())
        }

        assertEquals(6, harness.projectionCount)
        assertEquals(listOf(1_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun hiddenActiveDoesNotStartTickerOrProject() = runBlocking {
        val harness = TickerHarness(visible = false, active = activeSession(null), initialNow = at(14))
        harness.start(this)

        repeat(5) { second -> harness.tick(at(14) + (second + 1) * 1_000L) }

        assertEquals(0, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun hiddenInactiveDoesNotStartTickerOrProject() = runBlocking {
        val harness = TickerHarness(visible = false, active = null, initialNow = at(14))
        harness.start(this)

        repeat(3) { minute -> harness.tick(at(14, minute + 1)) }

        assertEquals(0, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())
        harness.close()
    }

    @Test fun becomingVisibleEmitsCurrentNowWithoutWaitingForAlignment() = runBlocking {
        val harness = TickerHarness(visible = false, active = activeSession(null), initialNow = at(14))
        harness.start(this)
        val current = at(14) + 37_000L

        harness.setCurrentTime(current)
        harness.setVisible(true)

        assertEquals(current, harness.awaitProjection())
        assertEquals(listOf(1_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun visibleInactiveEmitsImmediatelyAndUsesMinuteTicker() = runBlocking {
        val harness = TickerHarness(visible = true, active = null, initialNow = at(14))
        harness.start(this)

        assertEquals(at(14), harness.awaitProjection())
        harness.tick(at(14, 1))
        assertEquals(at(14, 1), harness.awaitProjection())
        assertEquals(listOf(60_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun becomingHiddenCancelsExistingActiveTicker() = runBlocking {
        val harness = TickerHarness(visible = true, active = activeSession(null), initialNow = at(14))
        harness.start(this)
        harness.awaitProjection()
        harness.tick(at(14) + 1_000L)
        harness.awaitProjection()

        harness.setVisible(false)
        val projections = harness.projectionCount
        repeat(5) { second -> harness.tick(at(14) + (second + 2) * 1_000L) }

        assertEquals(projections, harness.projectionCount)
        assertEquals(listOf(1_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun hiddenActiveStartAndStopNeverStartTicker() = runBlocking {
        val harness = TickerHarness(visible = false, active = null, initialNow = at(14))
        harness.start(this)

        harness.setActive(activeSession(null))
        harness.tick(at(14) + 1_000L)
        harness.setActive(null)
        harness.tick(at(14, 1))

        assertEquals(0, harness.projectionCount)
        assertTrue(harness.tickerPeriods.isEmpty())

        harness.setCurrentTime(at(14, 1) + 37_000L)
        harness.setVisible(true)
        assertEquals(at(14, 1) + 37_000L, harness.awaitProjection())
        assertEquals(listOf(60_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun hiddenAcrossMidnightReloadsNewDateOnBecomingVisible() = runBlocking {
        val harness = SnapshotHarness(visible = true, active = null, initialNow = at(23, 59))
        harness.start(this)
        assertEquals(date, harness.awaitLoad().calendarDate)

        harness.setVisible(false)
        val nextMorning = date.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        harness.setCurrentTime(nextMorning)
        harness.setVisible(true)
        val nextDay = harness.awaitLoad()

        assertEquals(date.plusDays(1), nextDay.calendarDate)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun hiddenDataInvalidationReloadsSnapshotWithoutRestartingTicker() = runBlocking {
        val harness = SnapshotHarness(visible = true, active = null, initialNow = at(14))
        harness.start(this)
        harness.awaitLoad()
        harness.setVisible(false)
        val periods = harness.tickerPeriods.toList()

        harness.dataChange()
        harness.awaitLoad()
        harness.tick(at(14, 1))

        assertEquals(2, harness.loadCount)
        assertEquals(periods, harness.tickerPeriods)
        harness.close()
    }

    @Test fun sameDayBecomingVisibleProjectsButDoesNotReloadSnapshot() = runBlocking {
        val harness = SnapshotHarness(visible = true, active = null, initialNow = at(14))
        harness.start(this)
        harness.awaitLoad()
        harness.setVisible(false)

        harness.setCurrentTime(at(14) + 37_000L)
        harness.setVisible(true)
        harness.awaitTickerStarts(2)

        assertEquals(1, harness.loadCount)
        assertEquals(listOf(60_000L, 60_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun visibleActiveTicksNeverReloadStableSnapshot() = runBlocking {
        val harness = SnapshotHarness(
            visible = true,
            active = activeSession(null),
            initialNow = at(14),
        )
        harness.start(this)
        harness.awaitLoad()

        repeat(5) { second -> harness.tick(at(14) + (second + 1) * 1_000L) }

        assertEquals(1, harness.loadCount)
        assertEquals(listOf(1_000L), harness.tickerPeriods)
        harness.close()
    }

    @Test fun activeStartReloadsOnceAndFollowingSecondTicksStayInMemory() = runBlocking {
        var current = snapshot()
        val active = activeSession(endAtMillis = null)
        val harness = Harness(at(14)) { current }
        harness.start(this)
        harness.awaitLoad()

        current = snapshot(active)
        harness.dataChange()
        val started = harness.awaitLoad()
        repeat(5) { second -> harness.tick(at(14) + second * 1_000L) }
        val content = project(started, active, at(14) + 4_000L)
        val entry = content.timelineEntries.single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(14) + 4_000L, entry.endAtMillis)
        assertTrue(entry.isActive)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun activeStopReloadUsesPersistedEndAndLaterMinuteTicksDoNotReload() = runBlocking {
        var current = snapshot(activeSession(endAtMillis = null))
        val harness = Harness(at(14, 20)) { current }
        harness.start(this)
        harness.awaitLoad()

        current = snapshot(activeSession(endAtMillis = at(14, 25)))
        harness.dataChange()
        val stopped = harness.awaitLoad()
        val content = project(stopped, null, at(14, 30))
        val entry = content.timelineEntries.single { it.entryType == TimelineEntryType.SESSION_BLOCK }
        assertEquals(at(14, 25), entry.endAtMillis)
        assertFalse(entry.isActive)

        repeat(3) { minute -> harness.tick(at(14, 31 + minute)) }
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun midnightReloadsExactlyOnceForTheNewDate() = runBlocking {
        val harness = Harness(at(23, 59))
        harness.start(this)
        harness.awaitLoad()

        harness.tick(at(23, 59) + 59_000L)
        assertEquals(1, harness.loadCount)
        harness.tick(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val nextDay = harness.awaitLoad()
        harness.tick(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000L)

        assertEquals(date.plusDays(1), nextDay.calendarDate)
        assertEquals(2, harness.loadCount)
        harness.close()
    }

    @Test fun defaultWindowStaysAtDefaultEndUntilNowMovesPastIt() {
        val stable = snapshot()
        val defaults = DefaultAwakeTimeConfig(wakeMinute = 7 * 60, sleepMinute = 23 * 60)

        val beforeFallback = projectTodaySnapshot(
            stable, null, at(22), TimelineDisplayMode.OVERVIEW, defaults, zone, Locale.ENGLISH,
        ) as TodayUiState.Content
        val afterFallback = projectTodaySnapshot(
            stable, null, at(23, 30), TimelineDisplayMode.OVERVIEW, defaults, zone, Locale.ENGLISH,
        ) as TodayUiState.Content

        assertEquals(at(23), beforeFallback.awakeDay.endAtMillis)
        assertEquals(io.github.peanutpersimmon.rilo.domain.model.BoundarySource.DEFAULT,
            beforeFallback.awakeDay.endSource)
        assertEquals(at(23, 30), afterFallback.awakeDay.endAtMillis)
        assertEquals(io.github.peanutpersimmon.rilo.domain.model.BoundarySource.NOW,
            afterFallback.awakeDay.endSource)
    }

    private fun project(
        stable: TodayStableSnapshot,
        active: Session?,
        nowMillis: Long,
        mode: TimelineDisplayMode = TimelineDisplayMode.OVERVIEW,
    ): TodayUiState.Content = projectTodaySnapshot(
        snapshot = stable,
        activeSession = active,
        nowMillis = nowMillis,
        displayMode = mode,
        defaultAwakeTime = DefaultAwakeTimeConfig.Standard,
        zoneId = zone,
        locale = Locale.ENGLISH,
    ) as TodayUiState.Content

    private fun snapshot(session: Session? = null, snapshotDate: LocalDate = date): TodayStableSnapshot {
        val sessions = listOfNotNull(session)
        return TodayStableSnapshot(
            calendarDate = snapshotDate,
            nightSleeps = emptyList(),
            displaySources = TimelineDisplaySources(
                sessions = sessions,
                events = emptyList(),
            ),
            metadataSources = TimelineMetadataProjector.project(
                sessions = sessions,
                events = emptyList(),
            ),
            labels = ClassificationLabels(emptyMap()),
        )
    }

    private fun activeSession(endAtMillis: Long?) = Session(
            id = 1,
            title = "Active",
            startAtMillis = at(14),
            endAtMillis = endAtMillis,
            createdAtMillis = start,
            updatedAtMillis = start,
        )

    private fun at(hour: Int, minute: Int = 0): Long =
        start + hour * 3_600_000L + minute * 60_000L

    private fun event(id: Long, title: String, minute: Int) = Event(
        id = id, title = title, dateEpochDay = date.toEpochDay(),
        startMinuteOfDay = minute, createdAtMillis = start, updatedAtMillis = start)

    private inner class EventEditHarness(initialEvents: List<Event> = emptyList(),
        initialTick: Long = at(10),
        initialSessions: List<Session> = emptyList()) {
        var events = initialEvents
        var sessions = initialSessions
        private val signals = TodayRefreshSignals()
        private val roomChanges = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        private val ticks = MutableSharedFlow<Long>(replay = 1).apply { tryEmit(initialTick) }
        private var currentNow = initialTick
        private val contents = Channel<TodayUiState.Content>(Channel.UNLIMITED)
        private var job: Job? = null
        var loadCount = 0
            private set

        fun start(scope: CoroutineScope) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val frames = stableTodaySnapshots(signals.observe(roomChanges), ticks, zone,
                    nowProvider = { currentNow }) { calendarDate, _ ->
                    loadCount++
                    snapshot(snapshotDate = calendarDate).copy(
                        displaySources = TimelineDisplaySources(sessions, events),
                        metadataSources = TimelineMetadataProjector.project(sessions, events),
                    )
                }
                combine(frames, ticks) { frame, tick ->
                    project(frame.snapshot, null, maxOf(frame.referenceNowMillis, tick))
                }.collect { contents.send(it) }
            }
        }

        fun editorChanged() = signals.editorChanged()
        fun roomChanged() { roomChanges.tryEmit(Unit) }
        suspend fun tick(now: Long) { currentNow = now; ticks.emit(now) }
        fun setCurrentTime(now: Long) { currentNow = now }
        fun eventChanged() = editorChanged()
        suspend fun awaitContent(): TodayUiState.Content = contents.receive()
        suspend fun close() { job?.cancelAndJoin() }
    }

    private inner class Harness(
        initialTick: Long,
        private val snapshotFactory: (LocalDate) -> TodayStableSnapshot = { snapshot(snapshotDate = it) },
    ) {
        private val changes = MutableSharedFlow<Unit>(replay = 1)
        private val ticks = MutableSharedFlow<Long>(replay = 1)
        private var currentNow = initialTick
        private val loaded = Channel<TodayStableSnapshot>(Channel.UNLIMITED)
        private var job: kotlinx.coroutines.Job? = null
        var loadCount: Int = 0
            private set

        init {
            changes.tryEmit(Unit)
            ticks.tryEmit(initialTick)
        }

        fun start(scope: kotlinx.coroutines.CoroutineScope) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                stableTodaySnapshots(changes, ticks, zone,
                    nowProvider = { currentNow }) { calendarDate, _ ->
                    loadCount += 1
                    snapshotFactory(calendarDate).also { loaded.send(it) }
                }.collect()
            }
        }

        suspend fun awaitLoad(): TodayStableSnapshot = loaded.receive()

        suspend fun tick(nowMillis: Long) {
            currentNow = nowMillis
            ticks.emit(nowMillis)
            yield()
        }

        suspend fun dataChange() {
            changes.emit(Unit)
        }

        suspend fun close() {
            job?.cancelAndJoin()
        }
    }

    private inner class TickerHarness(
        visible: Boolean,
        active: Session?,
        initialNow: Long,
    ) {
        private val pageVisible = MutableStateFlow(visible)
        private val activeSession = MutableStateFlow(active)
        private val pulses = MutableSharedFlow<Long>()
        private val projections = Channel<Long>(Channel.UNLIMITED)
        private var nowMillis = initialNow
        private var job: Job? = null

        val tickerPeriods = mutableListOf<Long>()
        var projectionCount: Int = 0
            private set

        fun start(scope: CoroutineScope) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                todayTicks(pageVisible, activeSession) { periodMillis ->
                    flow {
                        tickerPeriods += periodMillis
                        emit(nowMillis)
                        pulses.collect { emit(it) }
                    }
                }.collect { now ->
                    projectionCount += 1
                    projections.send(now)
                }
            }
        }

        suspend fun awaitProjection(): Long = withTimeout(2_000L) { projections.receive() }

        suspend fun tick(now: Long) {
            nowMillis = now
            pulses.emit(now)
            yield()
        }

        suspend fun setVisible(value: Boolean) {
            pageVisible.value = value
            yield()
        }

        suspend fun setActive(value: Session?) {
            activeSession.value = value
            yield()
        }

        fun setCurrentTime(value: Long) {
            nowMillis = value
        }

        suspend fun close() {
            job?.cancelAndJoin()
        }
    }

    private inner class SnapshotHarness(
        visible: Boolean,
        active: Session?,
        initialNow: Long,
    ) {
        private val pageVisible = MutableStateFlow(visible)
        private val activeSession = MutableStateFlow(active)
        private val changes = MutableSharedFlow<Unit>(replay = 1)
        private val pulses = MutableSharedFlow<Long>()
        private val loaded = Channel<TodayStableSnapshot>(Channel.UNLIMITED)
        private val tickerStarts = Channel<Unit>(Channel.UNLIMITED)
        private var nowMillis = initialNow
        private var job: Job? = null

        val tickerPeriods = mutableListOf<Long>()
        var loadCount: Int = 0
            private set

        init {
            changes.tryEmit(Unit)
        }

        fun start(scope: CoroutineScope) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                stableTodaySnapshots(
                    changes = changes,
                    ticks = todayTicks(pageVisible, activeSession) { periodMillis ->
                        flow {
                            tickerPeriods += periodMillis
                            tickerStarts.send(Unit)
                            emit(nowMillis)
                            pulses.collect { emit(it) }
                        }
                    },
                    zoneId = zone,
                    nowProvider = { nowMillis },
                ) { calendarDate, _ ->
                    loadCount += 1
                    snapshot(snapshotDate = calendarDate).also { loaded.send(it) }
                }.collect()
            }
        }

        suspend fun awaitLoad(): TodayStableSnapshot = withTimeout(2_000L) { loaded.receive() }

        suspend fun awaitTickerStarts(count: Int) {
            withTimeout(2_000L) { repeat(count) { tickerStarts.receive() } }
        }

        suspend fun tick(now: Long) {
            nowMillis = now
            pulses.emit(now)
            yield()
        }

        suspend fun setVisible(value: Boolean) {
            pageVisible.value = value
            yield()
        }

        fun setCurrentTime(value: Long) {
            nowMillis = value
        }

        suspend fun dataChange() {
            changes.emit(Unit)
        }

        suspend fun close() {
            job?.cancelAndJoin()
        }
    }
}
