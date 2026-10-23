package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUseCasesTest {
    @Test fun statisticsUseCaseRequestsOnlyPurposeSpecificSessionQueries() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 20)))
        val day = java.time.LocalDate.of(1970, 1, 1)
        val result = GetStatisticsUseCase(repository)(
            io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod(
                io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity.DAY, day),
            java.time.YearMonth.from(day), java.time.ZoneOffset.UTC, 100,
        )
        assertTrue(result is DomainResult.Success)
        assertEquals(0, repository.genericSessionReads)
        assertEquals(1, repository.activitySessionReads)
        assertEquals(1, repository.napSessionReads)
    }

    @Test fun startWithoutActiveCreatesActiveSession() = runBlocking {
        val repository = FakeRepository()
        val result = StartSessionUseCase(repository)(input(start = 10))
        assertTrue(result is DomainResult.Success)
        assertEquals(null, repository.sessions.single().endAtMillis)
    }

    @Test fun startWithActiveStopsOldThenCreatesNew() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 8, null)))
        StartSessionUseCase(repository)(input(start = 10))
        assertEquals(10 * 60_000L, repository.sessions.first { it.id == 1L }.endAtMillis)
        assertEquals(null, repository.sessions.first { it.id != 1L }.endAtMillis)
    }

    @Test fun startEqualToOldStartIsRejected() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, null)))
        assertFailure(StartSessionUseCase(repository)(input(start = 10)), DomainError.InvalidTime)
    }

    @Test fun pastSessionOverlapIsRejected() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 12)))
        assertFailure(CreatePastSessionUseCase(repository)(input(11, 13)), DomainError.SessionOverlap)
    }

    @Test fun pointInsideIntervalIsSavedAndNeverStoppedAsActive() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 20)))
        val result = CreatePastSessionUseCase(repository)(input(15).copy(temporalType = SessionTemporalType.POINT))
        assertTrue(result is DomainResult.Success)
        assertEquals(SessionTemporalType.POINT, repository.sessions.last().temporalType)
        assertFailure(StopActiveSessionUseCase(repository)(16), DomainError.NoActiveSession)
        assertFailure(StartSessionUseCase(repository)(input(30).copy(temporalType = SessionTemporalType.POINT)), DomainError.InvalidTime)
    }

    @Test fun stoppingAnActivityUsesTheSameMinutePrecisionAsTheEditor() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 9, null)))
        val rawStop = 10 * 60_000L + 37_456L

        val result = StopActiveSessionUseCase(repository)(rawStop)

        assertTrue(result is DomainResult.Success)
        assertEquals(10 * 60_000L, repository.sessions.single().endAtMillis)
    }

    @Test fun sleepUseCaseRejectsPointAndKeepsIntervalsUnclassified() = runBlocking {
        val repository = FakeRepository()
        val sleep = NewSessionInput(title = "Nap", startAtMillis = 10 * 60_000L,
            endAtMillis = 20 * 60_000L,
            kind = io.github.peanutpersimmon.rilo.domain.model.SessionKind.NAP,
            categoryId = 7, sourceEventId = 9)
        assertTrue(CreateSleepSessionUseCase(repository).create(sleep.copy(temporalType = SessionTemporalType.POINT))
            is DomainResult.Failure)
        assertTrue(CreateSleepSessionUseCase(repository).create(sleep) is DomainResult.Success)
        val saved = repository.sessions.single()
        assertEquals(SessionTemporalType.INTERVAL, saved.temporalType)
        assertEquals(null, saved.categoryId)
        assertEquals(null, saved.sourceEventId)
    }

    @Test fun pastSessionMayOverlapImplicitEvent() = runBlocking {
        val repository = FakeRepository(events = mutableListOf(event(1, 10, 12)))
        assertTrue(CreatePastSessionUseCase(repository)(input(11, 13)) is DomainResult.Success)
    }

    @Test fun deleteRemovesOnlySession() = runBlocking {
        val repository = FakeRepository(
            sessions = mutableListOf(session(1, 10, 12)),
        )
        DeleteSessionUseCase(repository)(1)
        assertTrue(repository.sessions.isEmpty())
    }

    @Test fun manualInitialClassificationWinsOverKeywordRules() = runBlocking {
        val repository = FakeRepository(
            rules = listOf(keywordRule(categoryId = 1)),
        )
        val result = InitialClassificationUseCase(repository)(
            InitialClassificationInput(
                title = "Make PPT",
                explicitCategoryId = 9,
                categoryWasExplicitlySet = true,
                applyKeywordRules = true,
            ),
        )
        assertEquals(9L, result.categoryId)
    }

    @Test fun keywordRulesFillOnlyMissingInitialValues() = runBlocking {
        val repository = FakeRepository(rules = listOf(keywordRule(categoryId = 1)))
        val result = InitialClassificationUseCase(repository)(
            InitialClassificationInput(title = "Make PPT", applyKeywordRules = true),
        )
        assertEquals(1L, result.categoryId)
    }

    @Test fun noRuleStillAllowsEmptyClassification() = runBlocking {
        val result = InitialClassificationUseCase(FakeRepository())(
            InitialClassificationInput(title = "Anything", applyKeywordRules = true),
        )
        assertEquals(null, result.categoryId)
    }

    private fun input(start: Long, end: Long? = null) = NewSessionInput(
        title = "Activity",
        startAtMillis = start * 60_000L,
        endAtMillis = end?.times(60_000L),
    )

    private fun session(id: Long, start: Long, end: Long?) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = start * 60_000L,
        endAtMillis = end?.times(60_000L),
        createdAtMillis = start * 60_000L,
        updatedAtMillis = start * 60_000L,
    )

    private fun event(id: Long, start: Int, end: Int) = Event(
        id = id,
        title = "Event $id",
        dateEpochDay = 0,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun keywordRule(categoryId: Long?) = KeywordRule(
        id = 1, keyword = "PPT", categoryId = categoryId,
        isEnabled = true, priority = 1, createdAtMillis = 0, updatedAtMillis = 0)

    private fun assertFailure(result: DomainResult<*>, error: DomainError) {
        assertTrue(result is DomainResult.Failure)
        assertEquals(error, (result as DomainResult.Failure).error)
    }

    private class FakeRepository(
        val sessions: MutableList<Session> = mutableListOf(),
        val events: MutableList<Event> = mutableListOf(),
        private val rules: List<KeywordRule> = emptyList(),
    ) : RidoCoreRepository {
        private var nextSessionId = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        var genericSessionReads = 0
        var activitySessionReads = 0
        var napSessionReads = 0

        override fun observeDataChanges() = flowOf(Unit)
        override fun observeActiveSession() = flowOf(
            sessions.singleOrNull { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null },
        )

        override suspend fun startSession(session: Session): DomainResult<Long> {
            val active = sessions.filter { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }
            if (active.size > 1) return DomainResult.Failure(DomainError.DataIntegrity("active"))
            val current = active.singleOrNull()
            if (current != null && session.startAtMillis <= current.startAtMillis) {
                return DomainResult.Failure(DomainError.InvalidTime)
            }
            current?.let {
                sessions[sessions.indexOf(it)] = it.copy(endAtMillis = session.startAtMillis)
            }
            val id = nextSessionId++
            sessions += session.copy(id = id, endAtMillis = null)
            return DomainResult.Success(id)
        }

        override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> {
            val active = sessions.singleOrNull { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }
                ?: return DomainResult.Failure(DomainError.NoActiveSession)
            if (endAtMillis <= active.startAtMillis) return DomainResult.Failure(DomainError.InvalidTime)
            val stopped = active.copy(endAtMillis = endAtMillis)
            sessions[sessions.indexOf(active)] = stopped
            return DomainResult.Success(stopped)
        }

        override suspend fun createPastSession(session: Session): DomainResult<Long> {
            val end = session.endAtMillis ?: if (session.temporalType == SessionTemporalType.POINT) session.startAtMillis else return DomainResult.Failure(DomainError.InvalidTime)
            if (session.temporalType == SessionTemporalType.INTERVAL && end <= session.startAtMillis) return DomainResult.Failure(DomainError.InvalidTime)
            if (sessions.any { it.temporalType == SessionTemporalType.INTERVAL && session.temporalType == SessionTemporalType.INTERVAL && DataValidator.sessionsOverlap(it.startAtMillis, it.endAtMillis, session.startAtMillis, end) }) {
                return DomainResult.Failure(DomainError.SessionOverlap)
            }
            val id = nextSessionId++
            sessions += session.copy(id = id)
            return DomainResult.Success(id)
        }

        override suspend fun updateSession(session: Session): DomainResult<Unit> {
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index < 0) return DomainResult.Failure(DomainError.NotFound("Session", session.id))
            val end = session.endAtMillis
            if (sessions.any {
                    it.id != session.id && it.temporalType == SessionTemporalType.INTERVAL && session.temporalType == SessionTemporalType.INTERVAL && DataValidator.sessionsOverlap(
                        it.startAtMillis, it.endAtMillis, session.startAtMillis, end,
                    )
                }
            ) return DomainResult.Failure(DomainError.SessionOverlap)
            sessions[index] = session
            return DomainResult.Success(Unit)
        }

        override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> {
            val removed = sessions.removeAll { it.id == sessionId }
            return if (removed) DomainResult.Success(Unit) else {
                DomainResult.Failure(DomainError.NotFound("Session", sessionId))
            }
        }

        override suspend fun getNightSleeps(): List<Session> = sessions
        override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> {
            genericSessionReads++
            return sessions.toList()
        }
        override suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY &&
                it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis != null &&
                it.startAtMillis < rangeEndMillis && it.endAtMillis > rangeStartMillis }
                .also { activitySessionReads++ }
        override suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY &&
                if (it.temporalType == SessionTemporalType.POINT)
                    it.startAtMillis >= rangeStartMillis && it.startAtMillis < rangeEndMillis
                else it.endAtMillis != null && it.startAtMillis < rangeEndMillis && it.endAtMillis > rangeStartMillis
            }
        override suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.NAP &&
                it.temporalType == SessionTemporalType.INTERVAL && it.startAtMillis < rangeEndMillis &&
                (it.endAtMillis ?: Long.MAX_VALUE) > rangeStartMillis }
                .also { napSessionReads++ }
        override suspend fun getEvents(
            firstEpochDay: Long,
            lastEpochDay: Long,
            rangeStartMillis: Long,
            rangeEndMillis: Long,
        ) = events.toList()
        override suspend fun getEnabledKeywordRules(): List<KeywordRule> = rules
        override suspend fun getCategories() = emptyList<io.github.peanutpersimmon.rilo.domain.model.Category>()
        override suspend fun getEvent(eventId: Long) = events.firstOrNull { it.id == eventId }
        override suspend fun getEventsByIds(eventIds: Set<Long>) = events.filter { it.id in eventIds }
        override suspend fun getSession(sessionId: Long) = sessions.firstOrNull { it.id == sessionId }
        override suspend fun upsertEvent(event: Event): DomainResult<Long> {
            val id = event.id.takeIf { it != 0L } ?: ((events.maxOfOrNull { it.id } ?: 0L) + 1L)
            events.removeAll { it.id == id }; events += event.copy(id = id)
            return DomainResult.Success(id)
        }
        override suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> {
            val event = events.firstOrNull { it.id == eventId }
                ?: return DomainResult.Failure(DomainError.NotFound("Event", eventId))
            events.remove(event)
            return DomainResult.Success(DeletedEventSnapshot(event))
        }
        override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> {
            events += snapshot.record
            return DomainResult.Success(Unit)
        }
        override suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot> {
            val session = sessions.firstOrNull { it.id == sessionId }
                ?: return DomainResult.Failure(DomainError.NotFound("Session", sessionId))
            sessions.remove(session)
            return DomainResult.Success(DeletedSessionSnapshot(session))
        }
        override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> {
            sessions += snapshot.record
            return DomainResult.Success(Unit)
        }
    }
}

