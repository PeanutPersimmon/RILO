package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.data.mapper.toEntity
import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.service.SleepCheckInPolicy
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import io.github.peanutpersimmon.rilo.domain.validation.ValidationError
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import io.github.peanutpersimmon.rilo.domain.time.withMinutePrecision
import java.time.ZoneId

internal class RoomSessionOperations(
    private val database: RidoDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val sessionDao = database.sessionDao()

    suspend fun save(session: Session): Long = database.withTransaction {
        val session = session.withMinutePrecision()
        validate(session)?.let { throw DataIntegrityException(it.toString()) }
        val excludedId = session.id.takeIf { it != 0L } ?: Long.MIN_VALUE
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, excludedId) > 0) {
            DataValidator.requireValid(setOf(ValidationError.SESSION_OVERLAP))
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null) {
            val otherActiveCount = sessionDao.getActiveSessions().count { it.id != session.id }
            if (otherActiveCount > 0) {
                throw DataIntegrityException("A second active Session cannot be saved; use startSession().")
            }
        }
        val id = if (session.id == 0L) sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity()) else {
            sessionDao.update(session.copy(sourceEventId = if (session.kind == SessionKind.ACTIVITY)
                sessionDao.getById(session.id)?.sourceEventId else null).toEntity())
            session.id
        }
        id
    }

    suspend fun restore(session: Session) = database.withTransaction {
        val session = session.withMinutePrecision()
        if (session.id == 0L || sessionDao.getById(session.id) != null) {
            throw DataIntegrityException("Session cannot be restored with this id.")
        }
        validate(session)?.let { throw DataIntegrityException(it.toString()) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, session.id) > 0) {
            throw DataIntegrityException("Session restore overlaps another Session.")
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null && sessionDao.getActiveSessions().isNotEmpty()) {
            throw DataIntegrityException("An active Session already exists.")
        }
        sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
    }

    suspend fun start(session: Session): DomainResult<Long> = database.withTransaction {
        val session = session.withMinutePrecision()
        if (session.id != 0L || session.temporalType != SessionTemporalType.INTERVAL || session.endAtMillis != null) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        validate(session)?.let { return@withTransaction DomainResult.Failure(it) }
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()
        if (active != null && session.startAtMillis <= active.startAtMillis) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        if (sessionDao.countOverlaps(session.startAtMillis, null, active?.id ?: Long.MIN_VALUE) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        active?.let { sessionDao.finish(it.id, session.startAtMillis, session.startAtMillis) }
        val id = sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
        DomainResult.Success(id)
    }

    suspend fun stop(endAtMillis: Long): DomainResult<Session> = database.withTransaction {
        val endAtMillis = endAtMillis.toMinutePrecision()
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()?.toDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NoActiveSession)
        if (endAtMillis <= active.startAtMillis || endAtMillis > nowMillis()) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        val stopped = active.copy(endAtMillis = endAtMillis, updatedAtMillis = endAtMillis)
        sessionDao.finish(active.id, endAtMillis, endAtMillis)
        DomainResult.Success(stopped)
    }

    suspend fun finishSleepCheckIn(
        endAtMillis: Long,
        zoneId: ZoneId,
        expectedSessionId: Long? = null,
    ): DomainResult<Session> = database.withTransaction {
        val endAtMillis = endAtMillis.toMinutePrecision()
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()?.toDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NoActiveSession)
        if (expectedSessionId != null && active.id != expectedSessionId)
            return@withTransaction DomainResult.Failure(DomainError.DataIntegrity("进行中的睡眠已改变，请重新输入"))
        if (active.kind !in setOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("当前进行中的记录不是睡眠"),
            )
        }
        if (endAtMillis > nowMillis() || endAtMillis <= active.startAtMillis) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        if (SleepCheckInPolicy.isOpenIntervalTooLong(active.startAtMillis, endAtMillis)) {
            return@withTransaction DomainResult.Failure(DomainError.SleepIntervalTooLong)
        }

        val finalKind = requireNotNull(SleepKindResolver.resolve(
            modelKind = null, startAtMillis = active.startAtMillis,
            endAtMillis = endAtMillis, zoneId = zoneId,
        ))
        val finished = active.copy(
            title = SleepCheckInPolicy.titleFor(finalKind),
            kind = finalKind,
            endAtMillis = endAtMillis,
            updatedAtMillis = endAtMillis,
        )
        validate(finished)?.let { return@withTransaction DomainResult.Failure(it) }
        sessionDao.update(finished.toEntity())
        DomainResult.Success(finished)
    }

    suspend fun createPast(session: Session): DomainResult<Long> = database.withTransaction {
        val session = session.withMinutePrecision()
        if (session.id != 0L || session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        validate(session)?.let { return@withTransaction DomainResult.Failure(it) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        val id = sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
        DomainResult.Success(id)
    }

    suspend fun update(session: Session): DomainResult<Unit> = database.withTransaction {
        val session = session.withMinutePrecision()
        if (session.id == 0L || sessionDao.getById(session.id) == null) {
            return@withTransaction DomainResult.Failure(DomainError.NotFound("Session", session.id))
        }
        validate(session)?.let { return@withTransaction DomainResult.Failure(it) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, session.id) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null && sessionDao.getActiveSessions().any { it.id != session.id }) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        sessionDao.update(session.copy(sourceEventId = if (session.kind == SessionKind.ACTIVITY)
            sessionDao.getById(session.id)?.sourceEventId else null).toEntity())
        DomainResult.Success(Unit)
    }

    suspend fun delete(sessionId: Long): DomainResult<Unit> = database.withTransaction {
        if (sessionDao.deleteById(sessionId) == 0) {
            DomainResult.Failure(DomainError.NotFound("Session", sessionId))
        } else {
            DomainResult.Success(Unit)
        }
    }

    private fun validate(session: Session): DomainError? =
        DataValidator.validateActualSession(session, nowMillis())
}
