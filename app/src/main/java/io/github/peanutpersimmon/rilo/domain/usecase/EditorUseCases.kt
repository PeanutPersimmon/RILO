package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.withMinutePrecision

data class EditorOptions(
    val categories: List<Category>,
)

class GetEditorOptionsUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(): EditorOptions = EditorOptions(
        categories = repository.getCategories(),
    )
}

class GetEventForEditorUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long): Event? = repository.getEvent(id)
}
class SaveEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(event: Event) = repository.upsertEvent(event)
}
class DeleteEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteEvent(id)
}
class RestoreEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(snapshot: DeletedEventSnapshot) = repository.restoreEvent(snapshot)
}

class GetSessionForEditorUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long): Session? = repository.getSession(id)
}

class GetPreviousActualEndUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(dayStartMillis: Long, nowMillis: Long): Long? =
        latestPreviousActualEnd(
            repository.getCompletedActivityIntervalsInRange(dayStartMillis, nowMillis) +
                repository.getNapsInRange(dayStartMillis, nowMillis) +
                repository.getNightSleepsInRange(dayStartMillis, nowMillis),
            dayStartMillis,
            nowMillis,
        )
}

internal fun latestPreviousActualEnd(
    candidates: Iterable<Session>, dayStartMillis: Long, nowMillis: Long,
): Long? = candidates.asSequence()
    .filter { it.kind in setOf(io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY,
        io.github.peanutpersimmon.rilo.domain.model.SessionKind.NAP,
        io.github.peanutpersimmon.rilo.domain.model.SessionKind.NIGHT_SLEEP) &&
        it.temporalType == io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.INTERVAL }
    .mapNotNull { it.endAtMillis }
    .filter { it >= dayStartMillis && it <= nowMillis }
    .maxOrNull()
class SaveSessionEditUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(session: Session): DomainResult<Unit> =
        repository.updateSession(session.withMinutePrecision())
}
class DeleteSessionWithUndoUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteSessionWithSnapshot(id)
}
class RestoreSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(snapshot: DeletedSessionSnapshot) = repository.restoreSession(snapshot)
}
