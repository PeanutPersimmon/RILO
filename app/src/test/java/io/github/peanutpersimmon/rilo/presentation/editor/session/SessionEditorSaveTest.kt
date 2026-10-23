package io.github.peanutpersimmon.rilo.presentation.editor.session

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import io.github.peanutpersimmon.rilo.domain.validation.ValidationError
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditorSaveTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 10, 5)
    private val now = at(18)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun occurrenceOnlyCreatesPointAndStartNowCreatesActiveInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "取快递", startDate = date, startTime = LocalTime.of(17, 26)) }
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.created!!.temporalType)
        assertEquals(at(17, 26), repo.created!!.startAtMillis)
        assertNull(repo.created!!.endAtMillis)

        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "做实验") }
        editor.startNow { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.started!!.temporalType)
        assertEquals(now, repo.started!!.startAtMillis)
        assertNull(repo.started!!.endAtMillis)
    }

    @Test fun newRecordUsesLatestCompletedBoundaryAndCurrentTime() = runTest {
        val repo = RecordingRepository(boundarySessions = listOf(
            Session(title = "夜间睡眠", kind = SessionKind.NIGHT_SLEEP,
                startAtMillis = at(0, 30), endAtMillis = at(8), createdAtMillis = at(0, 30), updatedAtMillis = at(8)),
            Session(title = "记录", kind = SessionKind.ACTIVITY,
                startAtMillis = at(8), endAtMillis = at(11, 30), createdAtMillis = at(8), updatedAtMillis = at(11, 30)),
            Session(title = "小憩", kind = SessionKind.NAP,
                startAtMillis = at(12, 40), endAtMillis = at(13, 20), createdAtMillis = at(12, 40), updatedAtMillis = at(13, 20)),
        ))
        val currentTime = at(14, 10)
        val editor = editor(repo, currentTime)

        editor.load(null)
        advanceUntilIdle()

        assertEquals(LocalTime.of(13, 20), editor.uiState.value.startTime)
        assertEquals(LocalTime.of(14, 10), editor.uiState.value.endTime)
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        editor.update { it.copy(title = "记录") }
        editor.save { }
        advanceUntilIdle()
        assertEquals(at(13, 20), repo.created!!.startAtMillis)
        assertEquals(at(14, 10), repo.created!!.endAtMillis)
    }

    @Test fun editingASecondPrecisionSessionPersistsTheDisplayedMinute() = runTest {
        val repo = RecordingRepository(record = session(
            SessionTemporalType.INTERVAL,
            at(16) + 37_456L,
        ).copy(startAtMillis = at(14) + 12_345L))
        val editor = editor(repo)

        editor.load(7)
        advanceUntilIdle()
        assertEquals(LocalTime.of(14, 0), editor.uiState.value.startTime)
        assertEquals(LocalTime.of(16, 0), editor.uiState.value.endTime)
        editor.save { }
        advanceUntilIdle()

        assertEquals(at(14), repo.updated!!.startAtMillis)
        assertEquals(at(16), repo.updated!!.endAtMillis)
    }

    @Test fun firstRecordAfterNightSleepUsesWakeTime() = runTest {
        val repo = RecordingRepository(boundarySessions = listOf(
            Session(title = "夜间睡眠", kind = SessionKind.NIGHT_SLEEP,
                startAtMillis = date.minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli(),
                endAtMillis = at(8), createdAtMillis = at(0), updatedAtMillis = at(8)),
        ))
        val editor = editor(repo, at(9))

        editor.load(null)
        advanceUntilIdle()

        assertEquals(date, editor.uiState.value.startDate)
        assertEquals(LocalTime.of(8, 0), editor.uiState.value.startTime)
        assertEquals(LocalTime.of(9, 0), editor.uiState.value.endTime)
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
    }

    @Test fun startAndEndCreateCompletedInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "做实验", startDate = date, startTime = LocalTime.of(14, 0)) }
        editor.setEnd(date, LocalTime.of(16, 0))
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(at(16), repo.created!!.endAtMillis)
    }

    @Test fun pointLoadSaveAndAddingEndPreserveThenConvertType() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.POINT, null))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)

        editor.load(7)
        advanceUntilIdle()
        editor.setEnd(date, LocalTime.of(16, 0))
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertEquals(at(16), repo.updated!!.endAtMillis)
    }

    @Test fun completedIntervalRetainsTypeAndClearingEndCreatesPoint() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.INTERVAL, at(16)))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertEquals(at(16), repo.updated!!.endAtMillis)

        editor.load(7)
        advanceUntilIdle()
        editor.clearEnd()
        assertEquals(SessionTemporalType.POINT, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)
    }

    @Test fun activeIntervalRemainsActiveWhenEndIsAbsent() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.INTERVAL, null))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertTrue(editor.uiState.value.isActive)
        editor.setEnd(date, LocalTime.of(16, 0))
        assertFalse(editor.uiState.value.isActive)
        editor.clearEnd()
        assertTrue(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)
    }

    @Test fun eventPointClearsOldEndAndResetClearsAssociations() = runTest {
        val repo = RecordingRepository(event = event(15 * 60, null))
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(endDate = date, endTime = LocalTime.of(17, 0)) }
        editor.load(null, 9)
        advanceUntilIdle()
        val prefilled = editor.uiState.value
        assertEquals(LocalTime.of(15, 0), prefilled.startTime)
        assertNull(prefilled.endDate)
        assertNull(prefilled.endTime)
        assertEquals(SessionTemporalType.POINT, prefilled.temporalType)
        assertEquals(9L, prefilled.sourceEventId)
        editor.save { }
        advanceUntilIdle()
        assertEquals(9L, repo.created!!.sourceEventId)
        assertEquals(SessionTemporalType.POINT, repo.created!!.temporalType)

        editor.load(null)
        advanceUntilIdle()
        assertNull(editor.uiState.value.sourceEventId)
        assertNull(editor.uiState.value.endDate)
        assertNull(editor.uiState.value.endTime)
    }

    @Test fun eventIntervalPrefillsCompletedIntervalAndDateOnlyRequiresTimeSelection() = runTest {
        val repo = RecordingRepository(event = event(14 * 60, 15 * 60))
        val editor = editor(repo)
        editor.load(null, 9)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertEquals(LocalTime.of(14, 0), editor.uiState.value.startTime)
        assertEquals(LocalTime.of(15, 0), editor.uiState.value.endTime)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(9L, repo.created!!.sourceEventId)

        repo.event = event(null, null)
        editor.load(null, 9)
        advanceUntilIdle()
        assertFalse(editor.uiState.value.startTimeConfirmed)
        assertFalse(editor.uiState.value.canSavePast)
    }

    @Test fun eventIntervalStillInProgressRequiresActualEndBeforeSaving() = runTest {
        val repo = RecordingRepository(event = event(14 * 60, 16 * 60))
        val editor = editor(repo, at(15))
        editor.load(null, 9)
        advanceUntilIdle()
        val seeded = editor.uiState.value
        assertEquals(SessionTemporalType.INTERVAL, seeded.temporalType)
        assertEquals(LocalTime.of(14, 0), seeded.startTime)
        assertNull(seeded.endDate)
        assertNull(seeded.endTime)
        assertFalse(seeded.isActive)
        assertFalse(seeded.canSavePast)
        assertFalse(seeded.canStartNow)
        assertEquals(9L, seeded.sourceEventId)
        editor.save { }
        advanceUntilIdle()
        assertNull(repo.created)

        editor.setEnd(date, LocalTime.of(13, 59))
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(date, LocalTime.of(15, 1))
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(date, LocalTime.of(14, 45))
        assertTrue(editor.uiState.value.canSavePast)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(at(14, 45), repo.created!!.endAtMillis)
        assertEquals(9L, repo.created!!.sourceEventId)
    }

    @Test fun sleepEditorCreatesOnlyInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.loadSleep(null)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertEquals(SessionKind.NIGHT_SLEEP, editor.uiState.value.kind)
        assertEquals(at(18), editor.uiState.value.startReferenceMillis)
        assertTrue(ValidationError.SESSION_END_NOT_AFTER_START in DataValidator.validateSession(
            session(SessionTemporalType.INTERVAL, at(18)).copy(kind = SessionKind.NIGHT_SLEEP,
                startAtMillis = at(18))))
        val endDate = requireNotNull(editor.uiState.value.endDate)
        val endTime = requireNotNull(editor.uiState.value.endTime)
        editor.clearEnd()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(endDate, endTime)
        editor.setSleepStart(date, LocalTime.of(17, 0))
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
    }

    @Test fun manualNapAndNightSleepUseTheSamePreviousActualEnd() = runTest {
        val previous = session(SessionTemporalType.INTERVAL, at(12, 40))
            .copy(startAtMillis = at(12))
        for (kind in listOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP)) {
            val editor = editor(RecordingRepository(boundarySessions = listOf(previous)), at(14, 30))
            editor.loadSleep(null)
            advanceUntilIdle()
            editor.chooseSleepKind(kind)
            assertEquals(at(12, 40), editor.uiState.value.startReferenceMillis)
            assertEquals(at(14, 30), editor.uiState.value.endReferenceMillis)
            assertEquals(kind, editor.uiState.value.kind)
            editor.setSleepStart(date, LocalTime.of(13, 0))
            assertEquals(LocalTime.of(13, 0), editor.uiState.value.startTime)
        }
    }

    @Test fun sleepPrefillSkipsActiveAndPointButKeepsOldCompletedEnd() = runTest {
        val active = session(SessionTemporalType.INTERVAL, null)
        val point = session(SessionTemporalType.POINT, null)
        val old = session(SessionTemporalType.INTERVAL, at(12, 40))
        val editor = editor(RecordingRepository(boundarySessions = listOf(active, point, old)), now + 2 * 24 * 3_600_000L)
        editor.loadSleep(null)
        advanceUntilIdle()
        assertEquals(at(12, 40), editor.uiState.value.startReferenceMillis)
        assertTrue(now + 2 * 24 * 3_600_000L - requireNotNull(editor.uiState.value.startReferenceMillis) >
            24 * 3_600_000L)
        assertTrue(ValidationError.SESSION_SLEEP_TOO_LONG in DataValidator.validateSession(
            old.copy(kind = SessionKind.NIGHT_SLEEP, endAtMillis = now + 2 * 24 * 3_600_000L)))
    }

    @Test fun editingExistingSleepKeepsBothTimes() = runTest {
        val existing = session(SessionTemporalType.INTERVAL, at(14)).copy(
            kind = SessionKind.NAP, startAtMillis = at(13))
        val editor = editor(RecordingRepository(record = existing, boundarySessions = listOf(existing)))
        editor.loadSleep(existing.id)
        advanceUntilIdle()
        assertEquals(at(13), editor.uiState.value.startReferenceMillis)
        assertEquals(at(14), editor.uiState.value.endReferenceMillis)
    }

    @Test fun voiceSleepEditorUsesTheSameKindResolverAndHonorsUserChoice() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        val seed = VoiceEditorSeed(QwenToolCallMapper().map("create_sleep_record",
            """{"start_at":"2026-10-05T13:00:00+08:00","end_at":"2026-10-05T14:00:00+08:00"}""",
            "下午一点到两点睡了一会", VoiceAgentContext(Instant.ofEpochMilli(now), zone)))
        editor.load(null)
        advanceUntilIdle()
        editor.applySleepVoice(seed)
        assertEquals(SessionKind.NAP, editor.uiState.value.kind)
        editor.setSleepEnd(date, LocalTime.of(17, 0))
        assertEquals(SleepKindResolver.resolve(null, at(13), at(17), zone), editor.uiState.value.kind)
        editor.chooseSleepKind(SessionKind.NAP)
        editor.setSleepEnd(date, LocalTime.of(17, 0))
        assertEquals(SessionKind.NAP, editor.uiState.value.kind)
    }

    @Test fun dstGapAndNewAmbiguousTimeShowErrorWithoutWriting() = runTest {
        val dstZone = ZoneId.of("America/Los_Angeles")
        val repo = RecordingRepository()
        val editor = editor(repo, Instant.parse("2026-11-03T00:00:00Z").toEpochMilli(), dstZone)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "record", startDate = LocalDate.of(2026, 3, 8), startTime = LocalTime.of(2, 30)) }
        editor.save { fail("gap saved") }
        advanceUntilIdle()
        assertTrue(editor.uiState.value.errorMessage!!.contains("不存在"))
        assertNull(repo.created)
        editor.update { it.copy(startDate = LocalDate.of(2026, 11, 1), startTime = LocalTime.of(1, 30)) }
        editor.save { fail("unresolved overlap saved") }
        advanceUntilIdle()
        assertTrue(editor.uiState.value.errorMessage!!.contains("两次"))
        assertNull(repo.created)
    }

    @Test fun editingBothOccurrencesPreservesOriginalInstants() = runTest {
        val dstZone = ZoneId.of("America/Los_Angeles")
        val first = java.time.OffsetDateTime.parse("2026-11-01T01:30:00-07:00").toInstant().toEpochMilli()
        val second = first + 3_600_000
        for (kind in listOf(SessionKind.ACTIVITY, SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            val record = Session(id = 7, title = "record", kind = kind,
                startAtMillis = first, endAtMillis = second, createdAtMillis = first, updatedAtMillis = second)
            val repo = RecordingRepository(record = record)
            val editor = editor(repo, second + 3_600_000, dstZone)
            editor.load(7)
            advanceUntilIdle()
            assertEquals(editor.uiState.value.startTime, editor.uiState.value.endTime)
            editor.save { }
            advanceUntilIdle()
            assertEquals(first, repo.updated!!.startAtMillis)
            assertEquals(second, repo.updated!!.endAtMillis)
        }
    }

    @Test fun ambiguousEventPrefillDoesNotSilentlyChooseAnOccurrenceForNewRecord() = runTest {
        val repo = RecordingRepository(event = Event(id = 9, title = "event",
            dateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(), startMinuteOfDay = 90,
            createdAtMillis = 0, updatedAtMillis = 0))
        val editor = editor(repo, Instant.parse("2026-11-03T00:00:00Z").toEpochMilli(),
            ZoneId.of("America/Los_Angeles"))
        editor.load(null, 9)
        advanceUntilIdle()
        assertFalse(editor.uiState.value.startTimeConfirmed)
        assertTrue(editor.uiState.value.errorMessage!!.contains("两次"))
        editor.save { fail("unresolved prefill saved") }
        advanceUntilIdle()
        assertNull(repo.created)
    }

    private fun editor(repo: RecordingRepository, currentTime: Long = now, editorZone: ZoneId = zone) = SessionEditorViewModel(
        GetSessionForEditorUseCase(repo.core), GetEditorOptionsUseCase(repo.core),
        CreatePastSessionUseCase(repo.core), StartSessionUseCase(repo.core),
        SaveSessionEditUseCase(repo.core), DeleteSessionWithUndoUseCase(repo.core),
        RestoreSessionUseCase(repo.core), zoneId = editorZone, clock = CurrentTimeProvider { currentTime },
        getEvent = GetEventForEditorUseCase(repo.core),
        getPreviousActualEnd = GetPreviousActualEndUseCase(repo.core),
    )

    private fun session(type: SessionTemporalType, end: Long?) = Session(
        id = 7, title = "记录", temporalType = type,
        startAtMillis = at(14), endAtMillis = end,
        createdAtMillis = at(14), updatedAtMillis = at(14),
    )

    private fun event(start: Int?, end: Int?) = Event(
        id = 9, title = "组会", dateEpochDay = date.toEpochDay(),
        startMinuteOfDay = start, endMinuteOfDay = end,
        createdAtMillis = at(9), updatedAtMillis = at(9),
    )

    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private class RecordingRepository(
        var record: Session? = null,
        var event: Event? = null,
        val boundarySessions: List<Session> = emptyList(),
    ) {
        var created: Session? = null
        var compound: Session? = null
        var updated: Session? = null
        var started: Session? = null
        val core: RidoCoreRepository = Proxy.newProxyInstance(
            RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCategories" -> emptyList<Any>()
                "getSession" -> record
                "getEvent" -> event
                "getCompletedActivityIntervalsInRange" -> boundarySessions.filter { it.kind == SessionKind.ACTIVITY }
                "getNapsInRange" -> boundarySessions.filter { it.kind == SessionKind.NAP }
                "getNightSleepsInRange" -> boundarySessions.filter { it.kind == SessionKind.NIGHT_SLEEP }
                "createPastSession" -> {
                    created = args!![0] as Session
                    DomainResult.Success(11L)
                }
                "updateSession" -> {
                    updated = args!![0] as Session
                    DomainResult.Success(Unit)
                }
                "startSession", "startSessionIfIdle" -> {
                    started = args!![0] as Session
                    DomainResult.Success(12L)
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
    }
}
