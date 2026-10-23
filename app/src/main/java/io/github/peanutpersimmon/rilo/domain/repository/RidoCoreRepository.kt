package io.github.peanutpersimmon.rilo.domain.repository

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface RidoCoreRepository {
    suspend fun <T> voiceTransaction(block: suspend () -> T): T = block()
    fun observeDataChanges(): Flow<Unit>
    fun observeActiveSession(): Flow<Session?>
    suspend fun startSession(session: Session): DomainResult<Long>
    suspend fun startSessionIfIdle(session: Session): DomainResult<Long> =
        DomainResult.Failure(DomainError.DataIntegrity("无法安全开始记录"))
    suspend fun stopConfirmedSession(id: Long, end: Long): DomainResult<Session> =
        DomainResult.Failure(DomainError.DataIntegrity("无法安全结束记录"))
    suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session>
    suspend fun finishSleepCheckIn(endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> =
        DomainResult.Failure(DomainError.DataIntegrity("无法安全结束睡眠记录"))
    suspend fun finishConfirmedSleepCheckIn(id: Long, endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> =
        DomainResult.Failure(DomainError.DataIntegrity("无法安全结束指定睡眠记录"))
    suspend fun createPastSession(session: Session): DomainResult<Long>
    suspend fun updateSession(session: Session): DomainResult<Unit>
    suspend fun deleteSession(sessionId: Long): DomainResult<Unit>
    suspend fun getNightSleeps(): List<Session>
    suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<Session>
    suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        error("Completed Activity Interval query is not implemented")
    suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        error("Activity Fact query is not implemented")
    suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        error("Nap query is not implemented")
    suspend fun getNightSleepsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        error("Night Sleep query is not implemented")
    suspend fun getEvents(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Event>
    suspend fun getEnabledKeywordRules(): List<KeywordRule>
    suspend fun getCategories(): List<Category>
    suspend fun getEvent(eventId: Long): Event?
    suspend fun getEventsByIds(eventIds: Set<Long>): List<Event>
    suspend fun getSession(sessionId: Long): Session?
    suspend fun upsertEvent(event: Event): DomainResult<Long>
    suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot>
    suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit>
    suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot>
    suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit>
}

data class DeletedEventSnapshot(val record: Event, val linkedSessionIds: List<Long> = emptyList())
data class DeletedSessionSnapshot(val record: Session)
