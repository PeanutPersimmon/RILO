package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.domain.validation.DataValidationException

internal class RoomEditorOperations(private val database: RidoDatabase) {
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()
    private val eventOperations = RoomEventOperations(database)
    private val sessionOperations = RoomSessionOperations(database)

    suspend fun getEvent(id: Long) = eventDao.getById(id)?.toDomain()
    suspend fun getSession(id: Long) = sessionDao.getById(id)?.toDomain()
    suspend fun upsertEvent(event: Event): DomainResult<Long> = try {
        DomainResult.Success(eventOperations.save(event))
    } catch (error: DataValidationException) {
        DomainResult.Failure(error.toDomainError())
    }
    suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> = database.withTransaction {
        val record = getEvent(eventId)
            ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Event", eventId))
        val linkedSessionIds = sessionDao.getIdsForSourceEvent(eventId)
        eventDao.deleteById(eventId)
        DomainResult.Success(DeletedEventSnapshot(record, linkedSessionIds))
    }
    suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> = try {
        eventOperations.restore(snapshot.record, snapshot.linkedSessionIds)
        DomainResult.Success(Unit)
    } catch (error: Exception) {
        DomainResult.Failure(DomainError.DataIntegrity(error.message ?: "Event restore failed"))
    }
    suspend fun deleteSession(sessionId: Long): DomainResult<DeletedSessionSnapshot> = database.withTransaction {
        val record = getSession(sessionId)
            ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Session", sessionId))
        sessionDao.deleteById(sessionId)
        DomainResult.Success(DeletedSessionSnapshot(record))
    }
    suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> = try {
        sessionOperations.restore(snapshot.record)
        DomainResult.Success(Unit)
    } catch (error: Exception) {
        DomainResult.Failure(DomainError.DataIntegrity(error.message ?: "Session restore failed"))
    }
    private fun DataValidationException.toDomainError(): DomainError = when {
        errors.any { it.name.contains("TITLE") } -> DomainError.EmptyTitle
        else -> DomainError.InvalidTime
    }
}
