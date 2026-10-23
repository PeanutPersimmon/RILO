package io.github.peanutpersimmon.rilo.presentation.today

import androidx.lifecycle.ViewModelStore
import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.presentation.time.*
import io.github.peanutpersimmon.rilo.presentation.timer.elapsedFrom
import java.lang.reflect.Proxy
import java.time.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class TodayClockCorrectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val initialZone = RuntimeZoneId.current
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 19)
    private val store = ViewModelStore()
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
    @Before fun setup() { Dispatchers.setMain(dispatcher); RuntimeZoneId.refresh(zone) }
    @After fun teardown() { store.clear(); RuntimeZoneId.refresh(initialZone); Dispatchers.resetMain() }

    private class Clock(var now: Long) : CurrentTimeProvider {
        override fun nowMillis() = now
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        fun set(value: Long) { now = value; changes.tryEmit(Unit) }
    }
    private val active = Session(id = 3, title = "active", startAtMillis = at(12), endAtMillis = null,
        createdAtMillis = at(12), updatedAtMillis = at(12))
    private var writes = 0
    private fun repository(): RidoCoreRepository = Proxy.newProxyInstance(RidoCoreRepository::class.java.classLoader,
        arrayOf(RidoCoreRepository::class.java)) { _, method, _ ->
        when (method.name) {
            "observeDataChanges" -> flowOf(Unit)
            "observeActiveSession" -> MutableStateFlow(active)
            "getNightSleeps" -> emptyList<Session>()
            "getSessions" -> listOf(active)
            "getEvents", "getEventsByIds" -> emptyList<Event>()
            "getCategories" -> emptyList<Category>()
            else -> { writes++; error("Unexpected write/call ${method.name}") }
        }
    } as RidoCoreRepository

    private fun editor(clock: Clock, repository: RidoCoreRepository = repository()) = TodayViewModel(
        ObserveTimelineDataChangesUseCase(repository), ObserveActiveSessionUseCase(repository),
        GetTodayStableSnapshotUseCase(repository), locale = Locale.ENGLISH,
        timeProvider = clock, tickFlow = { clock.changes.onStart { emit(Unit) }.map { clock.nowMillis() } },
        projectionDispatcher = dispatcher,
    ).also { store.put("today", it) }
    private fun TodayViewModel.content() = uiState.value as TodayUiState.Content

    @Test fun rollbackThenNormalProgressAndForwardCorrectionUseActualWallTime() = runTest {
        val clock = Clock(at(14))
        val vm = editor(clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        assertEquals(at(14), vm.content().nowMillis)
        assertEquals("2h", vm.content().timelineEntries.single { it.isActive }.durationLabel)
        clock.set(at(13))
        runCurrent()
        assertEquals(at(13), vm.content().nowMillis)
        assertEquals(at(13), vm.content().timelineEntries.single { it.isActive }.endAtMillis)
        assertEquals("1h", vm.content().timelineEntries.single { it.isActive }.durationLabel)
        clock.set(at(13, 1))
        runCurrent()
        assertEquals(at(13, 1), vm.content().nowMillis)
        assertEquals("1h 1m", vm.content().timelineEntries.single { it.isActive }.durationLabel)
        clock.set(at(16))
        runCurrent()
        assertEquals(at(16), vm.content().nowMillis)
        assertEquals("4h", vm.content().timelineEntries.single { it.isActive }.durationLabel)
        assertEquals(0, writes)
        assertEquals(at(12), active.startAtMillis)
        assertNull(active.endAtMillis)
    }

    @Test fun beforeSessionStartNeverProducesNegativeElapsedOrWrites() = runTest {
        val clock = Clock(at(14))
        val vm = editor(clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        clock.set(at(11))
        runCurrent()
        assertEquals(at(11), vm.content().nowMillis)
        assertTrue(vm.content().timelineEntries.none { it.durationLabel?.startsWith("-") == true })
        assertEquals(0L, elapsedFrom(active.startAtMillis, clock.nowMillis()).elapsedMillis)
        assertEquals(0, writes)
        assertNull(active.endAtMillis)
    }

    @Test fun resumeReadsNewTimeWithoutAnActivityRestart() = runTest {
        val clock = Clock(at(14))
        val vm = editor(clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        vm.setPageVisible(false)
        runCurrent()
        clock.now = at(13)
        vm.setPageVisible(true)
        runCurrent()
        assertEquals(at(13), vm.content().nowMillis)
        assertEquals(0, writes)
    }

    @Test fun delayedOldQueryCannotRestoreOldTimeOrZone() = runTest {
        val clock = Clock(at(14))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repo = object : RidoCoreRepository by repository() {
            override suspend fun getNightSleeps(): List<Session> {
                if (++calls == 1) withContext(NonCancellable) { gate.await() }
                return emptyList()
            }
        }
        val vm = editor(clock, repo)
        val observed = mutableListOf<TodayUiState.Content>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.filterIsInstance<TodayUiState.Content>().collect { observed += it }
        }
        runCurrent()
        clock.set(at(13))
        val honolulu = ZoneId.of("Pacific/Honolulu")
        RuntimeZoneId.refresh(honolulu)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(at(13), vm.content().nowMillis)
        assertEquals(honolulu, vm.content().zoneId)
        assertTrue(observed.all { it.nowMillis == at(13) && it.zoneId == honolulu })
        RuntimeZoneId.refresh(zone)
        clock.set(at(13, 1))
        runCurrent()
        assertEquals(zone, vm.content().zoneId)
        assertEquals(at(13, 1), vm.content().nowMillis)
        assertEquals(0, writes)
    }

    @Test fun sameDateQueryCompletingAfterRollbackUsesCompletionTime() = runTest {
        val clock = Clock(at(14))
        val gate = CompletableDeferred<Unit>()
        val repo = object : RidoCoreRepository by repository() {
            override suspend fun getNightSleeps(): List<Session> { gate.await(); return emptyList() }
        }
        val vm = editor(clock, repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        clock.set(at(13))
        gate.complete(Unit)
        runCurrent()
        assertEquals(at(13), vm.content().nowMillis)
    }

    @Test fun realAlignedTickerRestartsOnClockCorrectionNotification() = runTest {
        val clock = Clock(at(14))
        val samples = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            alignedTicks(clock, 60_000).collect { samples += it }
        }
        runCurrent()
        clock.set(at(13))
        runCurrent()
        assertEquals(listOf(at(14), at(13)), samples)
        clock.now = at(13, 1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(at(13, 1), samples.last())
    }
}
