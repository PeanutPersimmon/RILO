package io.github.peanutpersimmon.rilo.presentation.editor.event

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventReminderEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.of(2026, 10, 5)
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startReminderSavesSixtyMinutes() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "会议", date = day, startTime = LocalTime.of(14, 0),
            startReminderOffsetMinutes = 60) }
        editor.save { }
        advanceUntilIdle()
        assertEquals(840, repository.saved!!.startMinuteOfDay)
        assertEquals(60, repository.saved!!.startReminderOffsetMinutes)
    }

    @Test fun newEventSavesNoReminderAndAnySelectedNonnegativeMinutes() = runTest {
        for (offset in listOf<Int?>(null, 0, 5, 17, 150)) {
            val repository = RecordingRepository()
            val editor = editor(repository)
            editor.load(null)
            advanceUntilIdle()
            editor.update { it.copy(title = "会议", date = day, startTime = LocalTime.of(14, 0),
                startReminderOffsetMinutes = offset) }
            editor.save { }
            advanceUntilIdle()
            assertEquals(offset, repository.saved!!.startReminderOffsetMinutes)
        }
    }

    @Test fun removingStartTimeClearsReminderOnSave() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "会议", date = day, startTime = LocalTime.of(14, 0),
            startReminderOffsetMinutes = 60) }
        editor.clearStart()
        editor.save { }
        advanceUntilIdle()
        assertNull(repository.saved!!.startMinuteOfDay)
        assertNull(repository.saved!!.startReminderOffsetMinutes)
    }

    @Test fun editingExistingEventRetainsReminder() = runTest {
        val existing = Event(id = 5, title = "旧日程", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 840, startReminderOffsetMinutes = 60,
            createdAtMillis = 1, updatedAtMillis = 1)
        val repository = RecordingRepository(existing)
        val editor = editor(repository)
        editor.load(5)
        advanceUntilIdle()
        assertEquals(60, editor.uiState.value.startReminderOffsetMinutes)
        editor.update { it.copy(title = "新日程") }
        editor.save { }
        advanceUntilIdle()
        assertEquals(60, repository.saved!!.startReminderOffsetMinutes)
    }

    @Test fun editingCustomReminderKeepsExactValueAndCanTurnItOff() = runTest {
        val existing = Event(id = 5, title = "会议", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 840, startReminderOffsetMinutes = 17,
            createdAtMillis = 1, updatedAtMillis = 1)
        val repository = RecordingRepository(existing)
        val editor = editor(repository)
        editor.load(5)
        advanceUntilIdle()
        assertEquals(17, editor.uiState.value.startReminderOffsetMinutes)
        editor.save { }
        advanceUntilIdle()
        assertEquals(17, repository.saved!!.startReminderOffsetMinutes)
        val clearingEditor = editor(repository)
        clearingEditor.load(5)
        advanceUntilIdle()
        clearingEditor.update { it.copy(startReminderOffsetMinutes = null) }
        clearingEditor.save { }
        advanceUntilIdle()
        assertNull(repository.saved!!.startReminderOffsetMinutes)
    }


    private fun editor(repository: RecordingRepository) = EventEditorViewModel(
        GetEventForEditorUseCase(repository.core), GetEditorOptionsUseCase(repository.core),
        SaveEventUseCase(repository.core), DeleteEventUseCase(repository.core),
        RestoreEventUseCase(repository.core), clock = CurrentTimeProvider { 1000L })

    private class RecordingRepository(private val initial: Event? = null) {
        var saved: Event? = null
        val core: RidoCoreRepository = Proxy.newProxyInstance(RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getAllTasks", "getCategories", "getTags" -> emptyList<Any>()
                "getEvent" -> initial?.takeIf { it.id == args!![0] as Long }?.let { it }
                "upsertEvent" -> {
                    saved = args!![0] as Event
                    DomainResult.Success(1L)
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
    }
}
