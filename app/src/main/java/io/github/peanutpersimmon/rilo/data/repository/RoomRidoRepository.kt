package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class DataIntegrityException(message: String) : IllegalStateException(message)

sealed interface CommandExecution<out T> {
    data class Executed<T>(val value: T) : CommandExecution<T>
    data object AlreadyProcessed : CommandExecution<Nothing>
}

/** Transaction boundary for writes that combine DAO operations and domain invariants. */
class RoomRidoRepository(private val database: RidoDatabase) : RidoCoreRepository {
    private val sessionDao = database.sessionDao()
    private val processedCommandDao = database.processedCommandDao()
    private val sessionOperations = RoomSessionOperations(database)
    private val eventOperations = RoomEventOperations(database)
    private val projectionQueries = RoomProjectionQueries(database)
    private val editorOperations = RoomEditorOperations(database)

    override suspend fun <T> voiceTransaction(block: suspend () -> T): T = database.withTransaction { block() }
    override fun observeDataChanges(): Flow<Unit> = projectionQueries.observeDataChanges()
    override fun observeActiveSession(): Flow<Session?> = projectionQueries.observeActiveSession()

    suspend fun saveEvent(event: Event): Long = eventOperations.save(event)
    suspend fun saveSession(session: Session): Long = sessionOperations.save(session)

    override suspend fun startSession(session: Session): DomainResult<Long> = sessionOperations.start(session)
    override suspend fun startSessionIfIdle(session: Session): DomainResult<Long> = database.withTransaction {
        if (sessionDao.getActiveSessions().isNotEmpty())
            DomainResult.Failure(DomainError.DataIntegrity("已有进行中的记录，请先结束它"))
        else sessionOperations.start(session)
    }
    override suspend fun stopConfirmedSession(id: Long, end: Long): DomainResult<Session> = database.withTransaction {
        val active = sessionDao.getActiveSessions().singleOrNull()
        if (active == null || active.id != id)
            DomainResult.Failure(DomainError.DataIntegrity("进行中的记录已改变，请重新输入"))
        else stopActiveSession(end)
    }
    suspend fun getActiveSession(): Session? = database.withTransaction {
        val active = sessionDao.getActiveSessions()
        if (active.size > 1) throw DataIntegrityException("Database contains more than one active Session.")
        active.singleOrNull()?.toDomain()
    }
    override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> = sessionOperations.stop(endAtMillis)
    override suspend fun finishSleepCheckIn(endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> =
        sessionOperations.finishSleepCheckIn(endAtMillis, zoneId)
    override suspend fun finishConfirmedSleepCheckIn(id: Long, endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> =
        sessionOperations.finishSleepCheckIn(endAtMillis, zoneId, expectedSessionId = id)
    override suspend fun createPastSession(session: Session): DomainResult<Long> = sessionOperations.createPast(session)
    override suspend fun updateSession(session: Session): DomainResult<Unit> = sessionOperations.update(session)
    override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> = sessionOperations.delete(sessionId)

    suspend fun getScheduledEvents(firstEpochDay: Long, lastEpochDay: Long): List<Event> =
        database.eventDao().getScheduledInRange(firstEpochDay, lastEpochDay).map { it.toDomain() }
    override suspend fun getNightSleeps(): List<Session> = projectionQueries.getNightSleeps()
    override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        projectionQueries.getSessions(rangeStartMillis, rangeEndMillis)
    override suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        projectionQueries.getCompletedActivityIntervalsInRange(rangeStartMillis, rangeEndMillis)
    override suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        projectionQueries.getActivityFactsInRange(rangeStartMillis, rangeEndMillis)
    override suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        projectionQueries.getNapsInRange(rangeStartMillis, rangeEndMillis)
    override suspend fun getNightSleepsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<Session> =
        projectionQueries.getNightSleepsInRange(rangeStartMillis, rangeEndMillis)
    override suspend fun getEvents(firstEpochDay: Long, lastEpochDay: Long, rangeStartMillis: Long, rangeEndMillis: Long): List<Event> =
        projectionQueries.getEvents(firstEpochDay, lastEpochDay, rangeStartMillis, rangeEndMillis)
    override suspend fun getEnabledKeywordRules(): List<KeywordRule> = projectionQueries.getEnabledKeywordRules()
    override suspend fun getCategories(): List<Category> = projectionQueries.getCategories()
    override suspend fun getEvent(eventId: Long): Event? = editorOperations.getEvent(eventId)
    override suspend fun getEventsByIds(eventIds: Set<Long>): List<Event> = projectionQueries.getEventsByIds(eventIds)
    override suspend fun getSession(sessionId: Long): Session? = editorOperations.getSession(sessionId)
    override suspend fun upsertEvent(event: Event): DomainResult<Long> = editorOperations.upsertEvent(event)
    override suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> = editorOperations.deleteEvent(eventId)
    override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> = editorOperations.restoreEvent(snapshot)
    override suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot> = editorOperations.deleteSession(sessionId)
    override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> = editorOperations.restoreSession(snapshot)

    /** Business writes and the idempotency marker commit or roll back together. */
    suspend fun <T> executeCommandOnce(commandId: String, processedAtMillis: Long,
        action: suspend RoomRidoRepository.() -> T): CommandExecution<T> = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateCommandId(commandId))
        val normalizedId = commandId.trim()
        if (processedCommandDao.exists(normalizedId)) CommandExecution.AlreadyProcessed
        else {
            val value = action()
            processedCommandDao.insert(ProcessedCommandEntity(normalizedId, processedAtMillis))
            CommandExecution.Executed(value)
        }
    }
}
