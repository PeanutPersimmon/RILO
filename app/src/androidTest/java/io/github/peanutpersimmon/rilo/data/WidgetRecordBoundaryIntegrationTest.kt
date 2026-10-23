package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.usecase.CreatePastSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.DeleteSessionWithUndoUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetEditorOptionsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetPreviousActualEndUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetSessionForEditorUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.NewSessionInput
import io.github.peanutpersimmon.rilo.domain.usecase.RestoreSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.SaveSessionEditUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StartSessionUseCase
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rilo.widget.sleep.SleepWidgetGateway
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRecordBoundaryIntegrationTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun widgetNightSleepWakeFeedsRoomBoundaryAndRecordEditor() = runBlocking {
        withDatabase { database, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val startClick = at(day, 0, 30) + 12_345L
            val wakeClick = at(day, 10, 50) + 37_456L
            val start = at(day, 0, 30)
            val wake = at(day, 10, 50)
            val recordNow = at(day, 12, 20)

            val id = widgetSleep(repository, startClick, wakeClick)
            val stored = requireNotNull(database.sessionDao().getById(id))
            assertEquals(id, stored.id)
            assertEquals(SessionKind.NIGHT_SLEEP, stored.kind)
            assertEquals(SessionTemporalType.INTERVAL, stored.temporalType)
            assertEquals(start, stored.startAtMillis)
            assertEquals(wake, stored.endAtMillis)
            assertEquals(start, stored.createdAtMillis)
            assertEquals(wake, stored.updatedAtMillis)
            assertNull(stored.categoryId)
            assertNull(stored.sourceEventId)
            assertNull(database.sessionDao().getActiveSession())

            val daoRows = database.sessionDao().getNightSleepEntitiesInRange(
                at(day, 0, 0), recordNow,
            )
            assertEquals(listOf(id), daoRows.map { it.id })
            assertEquals(wake, previousEnd(repository, day, recordNow))

            val state = editor(repository, recordNow).also { it.load(null).join() }.uiState.value
            assertEquals(day, state.startDate)
            assertEquals(LocalTime.of(10, 50), state.startTime)
            assertEquals(day, state.endDate)
            assertEquals(LocalTime.of(12, 20), state.endTime)
            assertEquals(SessionTemporalType.INTERVAL, state.temporalType)

            val exactBoundary = CreatePastSessionUseCase(repository)(recordInput(
                start = wake,
                end = recordNow,
            ))
            assertTrue(exactBoundary is DomainResult.Success)
        }
    }

    @Test
    fun widgetWakeBoundaryRejectsEarlierMinuteAndAllowsEqualOrLaterMinute() = runBlocking {
        withDatabase { _, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val wake = at(day, 10, 50)
            widgetSleep(repository, at(day, 0, 30) + 999L, wake + 37_456L)

            val earlier = CreatePastSessionUseCase(repository)(recordInput(
                start = at(day, 10, 49),
                end = at(day, 11, 20),
            ))
            assertTrue(earlier is DomainResult.Failure)

            val exact = CreatePastSessionUseCase(repository)(recordInput(
                start = wake,
                end = at(day, 11, 20),
            ))
            assertTrue(exact is DomainResult.Success)
            repository.deleteSession(successValue(exact))

            val later = CreatePastSessionUseCase(repository)(recordInput(
                start = at(day, 10, 51),
                end = at(day, 11, 20),
            ))
            assertTrue(later is DomainResult.Success)
        }
    }

    @Test
    fun widgetNapWakeFeedsRecordEditor() = runBlocking {
        withDatabase { _, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val wake = at(day, 13, 20)
            val id = widgetSleep(repository, at(day, 12, 40), wake)

            val saved = requireNotNull(repository.getSession(id))
            assertEquals(SessionKind.NAP, saved.kind)
            assertEquals(SessionTemporalType.INTERVAL, saved.temporalType)
            assertEquals(wake, saved.endAtMillis)

            val state = editor(repository, at(day, 14, 10)).also { it.load(null).join() }.uiState.value
            assertEquals(LocalTime.of(13, 20), state.startTime)
            assertEquals(LocalTime.of(14, 10), state.endTime)
            assertEquals(SessionTemporalType.INTERVAL, state.temporalType)
            assertTrue(CreatePastSessionUseCase(repository)(recordInput(
                start = wake,
                end = at(day, 14, 10),
            )) is DomainResult.Success)
        }
    }

    @Test
    fun activeWidgetSleepIsNotPreviousBoundary() = runBlocking {
        withDatabase { _, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val start = at(day, 0, 30)
            val gateway = SleepWidgetGateway(repository, { start }, { zone })
            assertTrue(gateway.sleepCheckIn() is DomainResult.Success)

            assertNull(previousEnd(repository, day, at(day, 12, 20)))
        }
    }

    @Test
    fun latestCompletedActivityWinsAfterWidgetWake() = runBlocking {
        withDatabase { _, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            widgetSleep(repository, at(day, 0, 30), at(day, 10, 50))
            val activityEnd = at(day, 11, 40)
            val created = CreatePastSessionUseCase(repository)(NewSessionInput(
                title = "记录",
                startAtMillis = at(day, 10, 50),
                endAtMillis = activityEnd,
                kind = SessionKind.ACTIVITY,
                temporalType = SessionTemporalType.INTERVAL,
                createdAtMillis = activityEnd,
            ))
            assertTrue(created is DomainResult.Success)

            assertEquals(activityEnd, previousEnd(repository, day, at(day, 12, 20)))
        }
    }

    @Test
    fun crossMidnightWidgetNightSleepFeedsFirstRecord() = runBlocking {
        withDatabase { _, repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val wake = at(day, 8, 0)
            widgetSleep(repository, at(day.minusDays(1), 23, 30), wake)

            assertEquals(wake, previousEnd(repository, day, at(day, 9, 0)))
            val state = editor(repository, at(day, 9, 0)).also { it.load(null).join() }.uiState.value
            assertEquals(LocalTime.of(8, 0), state.startTime)
            assertEquals(LocalTime.of(9, 0), state.endTime)
        }
    }

    private suspend fun widgetSleep(
        repository: RoomRidoRepository,
        start: Long,
        wake: Long,
    ): Long {
        var now = start
        val gateway = SleepWidgetGateway(repository, { now }, { zone })
        val id = successValue(gateway.sleepCheckIn())
        now = wake
        val finished = successValue(gateway.wakeCheckIn())
        assertEquals(id, finished.id)
        assertEquals(wake.toMinutePrecision(), finished.endAtMillis)
        return id
    }

    private fun recordInput(start: Long, end: Long) = NewSessionInput(
        title = "记录",
        startAtMillis = start,
        endAtMillis = end,
        kind = SessionKind.ACTIVITY,
        temporalType = SessionTemporalType.INTERVAL,
        createdAtMillis = end,
    )

    private suspend fun previousEnd(
        repository: RoomRidoRepository,
        day: LocalDate,
        now: Long,
    ): Long? = GetPreviousActualEndUseCase(repository)(at(day, 0, 0), now)

    private fun editor(repository: RoomRidoRepository, now: Long) = SessionEditorViewModel(
        getSession = GetSessionForEditorUseCase(repository),
        getOptions = GetEditorOptionsUseCase(repository),
        createPast = CreatePastSessionUseCase(repository),
        startSession = StartSessionUseCase(repository),
        saveEdit = SaveSessionEditUseCase(repository),
        deleteSession = DeleteSessionWithUndoUseCase(repository),
        restoreSession = RestoreSessionUseCase(repository),
        zoneId = zone,
        clock = CurrentTimeProvider { now },
        getPreviousActualEnd = GetPreviousActualEndUseCase(repository),
    )

    private suspend fun withDatabase(
        test: suspend (RidoDatabase, RoomRidoRepository) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            test(database, RoomRidoRepository(database))
        } finally {
            database.close()
        }
    }

    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun <T> successValue(result: DomainResult<T>): T =
        (result as DomainResult.Success<T>).value
}
