package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toEntity
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator

internal class RoomEventOperations(private val database: RidoDatabase) {
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()

    suspend fun save(event: Event): Long = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateEvent(event))
        val id = if (event.id == 0L) eventDao.insert(event.toEntity()) else {
            eventDao.update(event.toEntity())
            event.id
        }
        id
    }

    suspend fun restore(
        event: Event,
        linkedSessionIds: List<Long>,
    ) = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateEvent(event))
        if (event.id == 0L || eventDao.getById(event.id) != null) {
            throw DataIntegrityException("Event cannot be restored with this id.")
        }
        eventDao.insert(event.toEntity())
        if (linkedSessionIds.isNotEmpty()) {
            sessionDao.restoreSourceEventIfNull(linkedSessionIds, event.id)
        }
    }

}
