package io.github.peanutpersimmon.rilo.presentation.editor.event

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.lang.reflect.Proxy
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorDstTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private var saved: Event? = null
    private var original: Event? = null
    private val repo = Proxy.newProxyInstance(RidoCoreRepository::class.java.classLoader,
        arrayOf(RidoCoreRepository::class.java)) { _, method, args ->
        when (method.name) {
            "getCategories" -> emptyList<Category>()
            "getEvent" -> original
            "upsertEvent" -> { saved = args!![0] as Event; DomainResult.Success(1L) }
            else -> error(method.name)
        }
    } as RidoCoreRepository
    private fun editor() = EventEditorViewModel(GetEventForEditorUseCase(repo), GetEditorOptionsUseCase(repo),
        SaveEventUseCase(repo), DeleteEventUseCase(repo), RestoreEventUseCase(repo), zone,
        CurrentTimeProvider { Instant.parse("2026-01-01T00:00:00Z").toEpochMilli() })

    @Test fun nonexistentStartAndEndAreRejectedByExistingErrorState() = runTest {
        val editor = editor()
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "event", date = LocalDate.of(2026, 3, 8), startTime = LocalTime.of(2, 30)) }
        editor.save { fail("gap saved") }
        advanceUntilIdle()
        assertNull(saved)
        assertTrue(editor.uiState.value.errorMessage!!.contains("不存在"))
        editor.update { it.copy(startTime = LocalTime.of(1, 30), endTime = LocalTime.of(2, 30)) }
        editor.save { fail("gap end saved") }
        advanceUntilIdle()
        assertNull(saved)
        assertTrue(editor.uiState.value.errorMessage!!.contains("不存在"))
    }

    @Test fun newAmbiguousMinuteIsNotSilentlyAssignedAnOffset() = runTest {
        val editor = editor()
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "event", date = LocalDate.of(2026, 11, 1), startTime = LocalTime.of(1, 30)) }
        editor.save { fail("ambiguous minute saved") }
        advanceUntilIdle()
        assertNull(saved)
        assertTrue(editor.uiState.value.errorMessage!!.contains("两次"))
    }

    @Test fun unchangedLegacyEventKeepsLocalMinuteAndReminderOffset() = runTest {
        original = Event(id = 2, title = "event", dateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            startMinuteOfDay = 90, startReminderOffsetMinutes = 17, createdAtMillis = 0, updatedAtMillis = 0)
        val editor = editor()
        editor.load(2)
        advanceUntilIdle()
        editor.save { }
        advanceUntilIdle()
        assertEquals(original!!.dateEpochDay, saved!!.dateEpochDay)
        assertEquals(90, saved!!.startMinuteOfDay)
        assertEquals(17, saved!!.startReminderOffsetMinutes)
    }
}
